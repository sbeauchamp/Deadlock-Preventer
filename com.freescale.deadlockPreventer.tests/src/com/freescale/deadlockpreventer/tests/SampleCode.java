package com.freescale.deadlockpreventer.tests;

public class SampleCode {
	
	public static void main(String[] args) {
		new SampleCode().test();
	}
	
	Object lock = new Object();
	
	private void test() {
		synchronized(lock) {
			synchronized(this) {
				code();
			}
		}
		Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				synchronized(SampleCode.this) {
					synchronized(lock) {
						code();
					}
				}
			}
		});
		thread.start();
		try {
			thread.join();
		} catch (InterruptedException e1) {
		}
	}

	private void code() {
		System.out.println("test");
	}
}
