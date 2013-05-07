package com.dfs.server;

public class Transaction {
	static final int COMMITED = 10;
	static final int STARTED = 20;
	static final int ABORTED = 30;
	
	String fileName;
	int state;
	long id;
	
	public Transaction(String fileName, int state, long id) {
		this.fileName = fileName;
		this.state = state;
		this.id = id;
	}
}
