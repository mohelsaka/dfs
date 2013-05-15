package com.dfs.server;

public class Transaction {
	public static final int COMMITED = 10;
	public static final int STARTED = 20;
	public static final int ABORTED = 30;
	
	private String fileName;
	private int state;
	private long id;
	private long lastEdited;
	
	public Transaction(String fileName, int state, long id, long lastEdited) {
		this.fileName = fileName;
		this.state = state;
		this.id = id;
		this.lastEdited = lastEdited;
	}
	
	public String toString(){
		return String.format("%d\t%d\t%d\t%s", System.currentTimeMillis(),
										id,
										state,
										fileName);
	}
	
	public String getFileName() {
		return fileName;
	}
	
	public int getState() {
		return state;
	}
	
	public long getId() {
		return id;
	}
	
	public void setState(int state) {
		this.state = state;
	}
	public void setLastEdited(Long time) {
		lastEdited = time;
	}
	public long getLastEdited() {
		return lastEdited;
	}
}
