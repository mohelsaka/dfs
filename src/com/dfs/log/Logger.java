package com.dfs.log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import com.dfs.server.Transaction;

public class Logger {
	public static final String TRANSACTION_LOG_ENTRY = "TRXN";
	public static final String READ_LOG_ENTRY = "READ";
	public static final String WRITE_LOG_ENTRY = "WMSG";
	
	private static final String LOG_FILE_PATH = "log";
	protected static BufferedWriter log;
	
	/**
	 * Initilaizes the logger and opening stream on log file or create it
	 * */
	public static void init(){
		try {
			File logFIle = new File(LOG_FILE_PATH);
			logFIle.createNewFile();
			log = new BufferedWriter(new FileWriter(logFIle, true));
		} catch (IOException e) {
			System.err.println("unable to open log file");
			e.printStackTrace();
			
			// system can not start without log file
			System.exit(1);
		}
	}
	
	/**
	 * printing transaction information to the log
	 * 
	 * @param	tx	transaction to be logged
	 * */
	public static void logTransaction(Transaction tx){
		String msg = String.format("%d:%d:%s", tx.getId(), tx.getState(), tx.getFileName());
		writeLogEntry(TRANSACTION_LOG_ENTRY, msg);
	}
	
	/**
	 * printing log entry when file is read
	 * 
	 * @param fileName	name of the file that is being read 
	 * */
	public static void logReadFile(String fileName){
		writeLogEntry(READ_LOG_ENTRY, fileName);
	}
	
	
	/**
	 * printing a log entry for a transaction message
	 * 
	 * @param	txnid	id transaction
	 * @param	msgid	id of the message that has been writen
	 * @param	msgSize	size of the message that has been writen
	 * */
	public static void logWriteRequest(long txnid, long msgid, long msgSize){
		String msg = String.format("%d:%d:%d", txnid, msgid, msgSize);
		writeLogEntry(WRITE_LOG_ENTRY, msg);
	}

	protected static void writeLogEntry(String entryType, String msg){
		try {
			log.write(String.format("%s:%d", entryType, System.currentTimeMillis()));
			log.write('\t');
			log.write(msg);
			log.append('\n');
			
			log.flush();
		} catch (IOException e) {
			e.printStackTrace();

			// system can not live without logging
			System.exit(1);
		}
	}
}
