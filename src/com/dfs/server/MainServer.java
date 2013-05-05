package com.dfs.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
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
	public int commit(long txnID, long numOfMsgs)
			throws MessageNotFoundException, RemoteException {
		return 0;
	}

	@Override
	public int abort(long txnID) throws RemoteException {
		// TODO Auto-generated method stub
		return 0;
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
