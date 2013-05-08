package com.dfs.server;

public class Transaction {
	static final int COMMITED = 10;
	static final int STARTED = 20;
	static final int ABORTED = 30;
	
	private String fileName;
	private int state;
	private long id;
	
	public Transaction(String fileName, int state, long id) {
		this.fileName = fileName;
		this.state = state;
		this.id = id;
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
}
