package com.example.cache;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ExpiryCache<K, V> implements Map<K, V> {
    
    private final ConcurrentHashMap<K, ExpiringObject> io;
    private final CopyOnWriteArrayList<ExpirationListener<V>> listenersExp;
    private final ExpireOld expire;
    public static final int DEFAULT_TIME_TO_LIVE = 60;
    public static final int DEFAULT_EXPIRATION_INTERVAL = 1;
    private static volatile int expiryCount = 1;
    
    public ExpiryCache() {
        this(DEFAULT_TIME_TO_LIVE, DEFAULT_EXPIRATION_INTERVAL);
    }

    public ExpiryCache(int timeToLive) {
        this(timeToLive, DEFAULT_EXPIRATION_INTERVAL);
    }

    public ExpiryCache(int timeToLive, int expirationInterval) {
        this(new ConcurrentHashMap<K, ExpiringObject>(), new CopyOnWriteArrayList<ExpirationListener<V>>(), timeToLive, expirationInterval);
    }

    private ExpiryCache(ConcurrentHashMap<K, ExpiringObject> io, CopyOnWriteArrayList<ExpirationListener<V>> listenersExp, int timeToLive, 
    		int expirationInterval) {
       
    	this.io = io;
        this.listenersExp = listenersExp;

        this.expire = new ExpireOld();
        expire.setTimeToLive(timeToLive);
        expire.setExpirationInterval(expirationInterval);
    }

    public V put(K key, V value) {
        
    	ExpiringObject answer = io.put(key, new ExpiringObject(key, value, System.currentTimeMillis()));
        if (answer == null) {
            return null;
        }

        return answer.getValue();
    }

    public V get(Object key) {
        
    	ExpiringObject object = io.get(key);

        if (object != null) {
            object.setLastAccessTime(System.currentTimeMillis());

            return object.getValue();
        }

        return null;
    }

    public V remove(Object key) {
       
    	ExpiringObject answer = io.remove(key);
        if (answer == null) {
            return null;
        }

        return answer.getValue();
    }

    public boolean containsKey(Object key) {
    	// TODO Auto-generated method stub
        return io.containsKey(key);
    }

    public boolean containsValue(Object value) {
    	// TODO Auto-generated method stub
        return io.containsValue(value);
    }

    public int size() {
    	// TODO Auto-generated method stub
    	return io.size();
    }

    public boolean isEmpty() {
    	// TODO Auto-generated method stub
        return io.isEmpty();
    }

    public void clear() {
    	// TODO Auto-generated method stub
        io.clear();
    }

    @Override
    public int hashCode() {
    	// TODO Auto-generated method stub
        return io.hashCode();
    }

    public Set<K> keySet() {
    	// TODO Auto-generated method stub
        return io.keySet();
    }

    @Override
    public boolean equals(Object obj) {
    	// TODO Auto-generated method stub
        return io.equals(obj);
    }

    public void putAll(Map<? extends K, ? extends V> inMap) {
    	// TODO Auto-generated method stub
        for (Entry<? extends K, ? extends V> e : inMap.entrySet()) {
            this.put(e.getKey(), e.getValue());
        }
    }

    public Collection<V> values() {
    	// TODO Auto-generated method stub
        throw new UnsupportedOperationException();
    }

    public Set<Map.Entry<K, V>> entrySet() {
    	// TODO Auto-generated method stub
        throw new UnsupportedOperationException();
    }

    public void addExpirationListener(ExpirationListener<V> listener) {
        listenersExp.add(listener);
    }

    public void removeExpirationListener(
            ExpirationListener<V> listener) {
        listenersExp.remove(listener);
    }

    public ExpireOld getExpire() {
        return expire;
    }

    public int getExpirationInterval() {
        return expire.getExpirationInterval();
    }

    public void setExpirationInterval(int expirationInterval) {
        expire.setExpirationInterval(expirationInterval);
    }

    public void setTimeToLive(int timeToLive) {
        expire.setTimeToLive(timeToLive);
    }

    private class ExpiringObject {
        
    	private K key;
        private V value;

        private long lastAccessTime;

        private final ReadWriteLock lastAccessTimeLock = new ReentrantReadWriteLock();

        ExpiringObject(K key, V value, long lastAccessTime) {
            
            this.key = key;
            this.value = value;
            this.lastAccessTime = lastAccessTime;
        }

        public long getLastAccessTime() {
           
        	lastAccessTimeLock.readLock().lock();

            try {
                return lastAccessTime;
            } finally {
                lastAccessTimeLock.readLock().unlock();
            }
        }

        public void setLastAccessTime(long lastAccessTime) {
            
        	lastAccessTimeLock.writeLock().lock();

            try {
                this.lastAccessTime = lastAccessTime;
            } finally {
                lastAccessTimeLock.writeLock().unlock();
            }
        }

        public K getKey() {
            return key;
        }

        public V getValue() {
            return value;
        }

        @Override
        public boolean equals(Object obj) {
            return value.equals(obj);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }

    public class ExpireOld implements Runnable {
        
    	private long timeToLiveMillis;
        private long expirationIntervalMillis;
        
        private final ReadWriteLock stateLock = new ReentrantReadWriteLock();
        private boolean running = false;
        private final Thread expireThread;

        public ExpireOld() {
            expireThread = new Thread(this, "Expire no -"
                    + expiryCount++);
            expireThread.setDaemon(true);
        }

        public void run() {
            while (running) {
                processExpires();

                try {
                    Thread.sleep(expirationIntervalMillis);
                } catch (InterruptedException e) {
                }
            }
        }

        private void processExpires() {
            long timeNow = System.currentTimeMillis();

            for (ExpiringObject o : io.values()) {
                if (timeToLiveMillis <= 0) {
                    continue;
                }

                long timeIdle = timeNow - o.getLastAccessTime();

                if (timeIdle >= timeToLiveMillis) {
                    io.remove(o.getKey());

                    for (ExpirationListener<V> listener : listenersExp) {
                        listener.expired(o.getValue());
                    }
                }
            }
        }

        public void startExpiring() {
            stateLock.writeLock().lock();

            try {
                if (!running) {
                    running = true;
                    expireThread.start();
                }
            } finally {
                stateLock.writeLock().unlock();
            }
        }

        public void startExpiringIfNotStarted() {
            stateLock.readLock().lock();
            try {
                if (running) {
                    return;
                }
            } finally {
                stateLock.readLock().unlock();
            }

            stateLock.writeLock().lock();
            try {
                if (!running) {
                    running = true;
                    expireThread.start();
                }
            } finally {
                stateLock.writeLock().unlock();
            }
        }

        public void stopExpiring() {
            stateLock.writeLock().lock();

            try {
                if (running) {
                    running = false;
                    expireThread.interrupt();
                }
            } finally {
                stateLock.writeLock().unlock();
            }
        }

        public boolean isRunning() {
            stateLock.readLock().lock();

            try {
                return running;
            } finally {
                stateLock.readLock().unlock();
            }
        }

        public void setTimeToLive(long timeToLive) {
            stateLock.writeLock().lock();

            try {
                this.timeToLiveMillis = timeToLive * 1000;
            } finally {
                stateLock.writeLock().unlock();
            }
        }

        public int getExpirationInterval() {
            stateLock.readLock().lock();

            try {
                return (int) expirationIntervalMillis / 1000;
            } finally {
                stateLock.readLock().unlock();
            }
        }

        public void setExpirationInterval(long expirationInterval) {
            stateLock.writeLock().lock();

            try {
                this.expirationIntervalMillis = expirationInterval * 1000;
            } finally {
                stateLock.writeLock().unlock();
            }
        }
    }
}

interface ExpirationListener<E> {
    void expired(E expiredObject);
}

