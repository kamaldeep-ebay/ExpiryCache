package com.example.cache;

public class Test {

	public static void main(String[] args) {
		// TODO Auto-generated method stub

		ExpiryCache<String, String> emap = new ExpiryCache<String,String>();
		emap.put("abc", "xyz");
		emap.put("abc1", "xyz1");
		
		TestSync ts = new TestSync(emap);
		ts.runThreads();
		emap.getExpire().startExpiringIfNotStarted();
		T3 th = new T3(emap);
		th.stTh();
	}	
}

 class TestSync implements Runnable {
	 
	 Thread t1;
	 Thread t2;
	 ExpiryCache<String, String> cache;
	  
	 public TestSync(ExpiryCache<String, String> cache) {
		// TODO Auto-generated constructor stub
		t1 = new Thread(this,"t1");
		t2 = new Thread(this,"t2");
		this.cache = cache; 
	}
	 
	public void runThreads(){
		t1.start();
		t2.start();
	}
	 
	@Override
	public void run() {
		// TODO Auto-generated method stub
		for ( int i=0;i<10000;i++){
			if(Thread.currentThread().getName().equalsIgnoreCase("t1"))
				cache.put("keyt1"+i, "valuet1"+i);
			else
				cache.put("keyt2"+i, "valuet2"+i);
			
			try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
            }
		}
	}
	 
 }
 
 class T3 implements Runnable{

	 ExpiryCache<String, String> cache;
	 Thread getter;
	 public T3(ExpiryCache<String, String> cache){
		 this.cache = cache;
		 getter = new Thread(this,"getter");
	 }
	 
	 public void stTh(){
		 getter.start();
	 }
	 
	 
	@Override
	public void run() {
		// TODO Auto-generated method stub
		for( int i=0;i<100;i++){
			System.out.println(cache.get("keyt1"+i));
			System.out.println(cache.get("keyt2"+i));
			
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	 
	 
 }
