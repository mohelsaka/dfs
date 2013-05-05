package com.ds.interfaces;

public class MessageNotFoundException extends Exception {

	/**
	 * Exception thrown when the server finds missing messages in a committed transaction
	 */
	private static final long serialVersionUID = 1L;
	/**
	 * Missing messages sequence numbers
	 */
	private int[] msgNum = null; 
	public int[] getMsgNum() {
		return msgNum;
	}
	public void setMsgNum(int[] msgNum) {
		this.msgNum = msgNum;
	}
	

}
