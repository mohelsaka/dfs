package com.dfs.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.UUID;

import com.ds.interfaces.ClientInterface;
import com.ds.interfaces.FileContents;
import com.ds.interfaces.MessageNotFoundException;
import com.ds.interfaces.ServerInterface;


public class MainServer implements ServerInterface{
	Hashtable<String, ClientInterface> clients = new Hashtable<String, ClientInterface>();
	
	// default directory
	String directory_path = "~/dfs/";
	String cache_path = directory_path + "cache/";
	
	HashSet<String> lockedFiles = new HashSet<String>();
	Hashtable<Long, Transaction> transactions = new Hashtable<Long, Transaction>(); 
	
	@Override
	public FileContents read(String fileName) throws FileNotFoundException,
			IOException, RemoteException {
		
		// Note: only file of size less that BUFFER_SIZE can be handled correctly.
		FileInputStream instream = new FileInputStream(new File(directory_path + fileName));
		byte[] buffer = new byte[FileContents.BUFFER_SIZE];
		int contentlength = instream.read(buffer);
		instream.close();
		
		// copying the buffer in smaller content byte array to be sent
		byte[] content = new byte[contentlength];
		System.arraycopy(buffer, 0, content, 0, contentlength);
		
		// return FileContent instance
		return new FileContents(content);
	}

	@Override
	public long newTxn(String fileName) throws RemoteException, IOException {
		// check if the file is currently being locked by other transaction
		if(lockedFiles.contains(fileName)){
			throw new IOException("File is locked");
		}
		
		// generate new transaction id
		long txnId = System.currentTimeMillis();
		
		// add lock on the file
		lockedFiles.add(fileName);
		transactions.put(txnId, new Transaction(fileName, Transaction.STARTED, txnId));
		return txnId;
	}

	@Override
	public int write(long txnID, long msgSeqNum, byte[] data)
			throws RemoteException, IOException {
		
		// check if the transaction id is correct
		if (!transactions.containsKey(txnID)) {
			return INVALID_TRANSACTION_ID;
		}
		
		// check if the transaction has been already committed
		if (transactions.get(txnID).state == Transaction.COMMITED) {
			return INVALID_OPERATION;
		}
		
		// build cache file name
		String fileName = cache_path + txnID + "_" + msgSeqNum;
		FileOutputStream outstream = new FileOutputStream(new File(fileName));
		
		// safely write data and close opened stream
		outstream.write(data);
		outstream.flush();
		outstream.close();
		
		return ACK;
	}

	@Override
	public int commit(final long txnID, long numOfMsgs)
			throws MessageNotFoundException, RemoteException {
		
		// check if the transaction id is correct
		if (!transactions.containsKey(txnID)) {
			return INVALID_TRANSACTION_ID;
		}
		
		// check if the transaction has been already committed
		if (transactions.get(txnID).state == Transaction.COMMITED) {
			// the client me request resending the ack message
			return ACK;
		}
		
		// get all cached files by this transaction
		File[] cachedFiles = new File(cache_path).listFiles(new CacheFilesFilter(txnID));
		
		// check if there are unreceived messages and report them to the client
		if (cachedFiles.length < numOfMsgs) {
			long [] msgsIDs = new long [cachedFiles.length];
			
			// convert msgsID to array of Long
			for (int i = 0; i < msgsIDs.length; i++) {
				String fname = cachedFiles[(int) i].getName();
				msgsIDs[i] = Long.parseLong(fname.substring(fname.indexOf('_') + 1));
			}
			
			// prepare exception to be thrown
			MessageNotFoundException exception = new MessageNotFoundException();
			exception.setMsgNum(findLostMessagesIDs(msgsIDs, numOfMsgs));
			
			throw exception;
		}
		
		// append cached data to the file
		Transaction tx = transactions.get(txnID);
		try {
			// create new file if it is not exist yet.
			File fout = new File(tx.fileName);
			fout.createNewFile();
			
			FileOutputStream outsream = new FileOutputStream(fout, true);
			
			byte [] buffer = new byte[FileContents.BUFFER_SIZE];
			for (int i = 1; i <= numOfMsgs; i++) {
				FileInputStream instream = new FileInputStream(new File("" + txnID + '_' + i));
				
				int len = 0;
				while((len = instream.read(buffer)) != 0){
					outsream.write(buffer, 0, len);
				}
				
				instream.close();
			}
			
			// flush and close file output stream
			outsream.flush();
			outsream.close();
		} catch (IOException e) {
			e.printStackTrace();
			// TODO: unhandeled yet
		}
		
		clearTransaction(txnID, Transaction.COMMITED);
		
		return ACK;
	}
	
	/**
	 * clear cached files, release file lock and set a new state for the transaction
	 * */
	private synchronized void clearTransaction(long txnID, int txnNewState){
		File[] cachedFiles = new File(cache_path).listFiles(new CacheFilesFilter(txnID));
		for (File file : cachedFiles) {
			file.delete();
		}
		Transaction tx = transactions.get(txnID);
		lockedFiles.remove(tx.fileName);
		tx.state = txnNewState;
	}
	
	/**
	 * getting ids if message that are nore received.
	 * 
	 * @param msgsIDs array of all received messages
	 * @param numOfMsgs the total number of message that should be received.
	 * 
	 * @return array of all missing messages ids
	 * */
	private int[] findLostMessagesIDs(long [] msgsIDs, long numOfMsgs){
		Arrays.sort(msgsIDs);
		
		int missedMessagesNumner = (int)numOfMsgs - msgsIDs.length; 
		int[] missedMessages = new int[missedMessagesNumner];
		int mIndex = 0;
		
		for (int i = 1; i < msgsIDs.length; i++) {
			if ((msgsIDs[i] - msgsIDs[i - 1]) != 1) {
				for (long j = msgsIDs[i-1] + 1; j < msgsIDs[i]; j++) {
					missedMessages[mIndex++] = (int)j;
				}
			}
		}
		return missedMessages;
	}
	
	/**
	 * FilenameFilter used to filter cached files for specific transaction
	 * */
	class CacheFilesFilter implements FilenameFilter{
		long txnID = 0;
		
		public boolean accept(File dir, String name) {
			name = name.substring(0, name.indexOf('_'));
			return name.equals("" + txnID);
		}
		
		CacheFilesFilter(long txnID) {
			this.txnID = txnID;
		}
	}
	

	@Override
	public int abort(long txnID) throws RemoteException {
		// check if the transaction id is correct
		if (!transactions.containsKey(txnID)) {
			return INVALID_TRANSACTION_ID;
		}
		
		// check if the transaction has been already committed
		if (transactions.get(txnID).state == Transaction.COMMITED) {
			// aborting commited transaction is invalid operation
			return INVALID_OPERATION;
		}
		
		// check if the transaction has been already aborted
		if (transactions.get(txnID).state == Transaction.ABORTED) {
			return ACK;
		}
		
		// clear all changes made by this transaction
		clearTransaction(txnID, Transaction.ABORTED);
		
		return ACK;
	}

	@Override
	public boolean registerClient(ClientInterface client)
			throws RemoteException {
		String auth_token  = client.getAuthenticationToken();
		
		if(auth_token == null){
			// generate new auth token
			auth_token = UUID.randomUUID().toString();
			client.setAuthenticationToken(auth_token);
			
			// add this new client to the list of authenticated clients
			this.clients.put(auth_token, client);
			return true;
		}else{
			if(clients.containsKey(auth_token))
				return true;
			else
				return false;
		}
	}

	@Override
	public boolean unregisterClient(ClientInterface client)
			throws RemoteException {
		String auth_token  = client.getAuthenticationToken();
		
		if(auth_token == null){
			// Unresisted client
			return false;
		}else{
			if(clients.containsKey(auth_token)){
				// safely remove this client
				clients.remove(auth_token);
				return true;
			}
			else{
				// unrecognized auth token, safely return false 
				return false;
			}
		}
	}
	
	public static void main(String[] args) throws RemoteException, AlreadyBoundException {
		MainServer server = new MainServer();
		ServerInterface serverStub = (ServerInterface) UnicastRemoteObject.exportObject(server, 0);
		
		Registry registry = LocateRegistry.getRegistry();
		registry.bind(DFServerUniqyeName, serverStub);
		System.out.println("server is running ...");
	}
}
