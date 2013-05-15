package com.dfs.heartbeats;

import java.rmi.RemoteException;
import java.util.Enumeration;
import java.util.Hashtable;

public class HearbeatsManager extends Thread{
	
	/**
	 * each manager is responsible for one HeartbeatListner
	 * */
	HeartbeatsListener listener;
	
	/**
	 * time between successive checks on responders 
	 * */
	long period;
	
	/**
	 * reponders hash table
	 * */
	Hashtable<Integer, HeartbeatsResponder> responders;
	
	/**
	 * setting this flag to false will keep checking any dead listenr
	 * */
	boolean deattachOnFailure = true;
	
	public HearbeatsManager(HeartbeatsListener listener, long period){
		this.listener = listener;
		this.period = period;

		this.responders = new Hashtable<Integer, HeartbeatsResponder>();
	}
	
	/**
	 * attachs new responder with id to this manager
	 * */
	public void attachResponder(HeartbeatsResponder responder, int id){
		responders.put(id, responder);
	}
	
	public void deattachResponder(int id){
		responders.remove(id);
	}
	
	public void setDeattachOnFailure(boolean deattachOnFailure) {
		this.deattachOnFailure = deattachOnFailure;
	}
	
	@Override
	public synchronized void start() {
		while(true){
			Enumeration<Integer> keys = responders.keys();
			
			while (keys.hasMoreElements()) {
				Integer key = keys.nextElement();
				HeartbeatsResponder responder = responders.get(key);
				
				boolean alive = false;
				try {
					alive = responder.isAlive();
				} catch (RemoteException e) {
//					e.printStackTrace();
				}
				
				if (!alive) {
					listener.onReponderFailure(responder, key);
					
					if(deattachOnFailure)
						responders.remove(key);
				}
			}
			
			try {
				sleep(period);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}
