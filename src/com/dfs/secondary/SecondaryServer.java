package com.dfs.secondary;

import java.rmi.RemoteException;
import java.util.Hashtable;

import com.dfs.heartbeats.HearbeatsManager;
import com.dfs.heartbeats.HeartbeatsListener;
import com.dfs.heartbeats.HeartbeatsResponder;
import com.dfs.log.Logger;
import com.dfs.server.Transaction;
import com.ds.interfaces.ClientInterface;
import com.ds.interfaces.SecondaryServerInterface;

public class SecondaryServer implements HeartbeatsListener, SecondaryServerInterface{
	
	HeartbeatsResponder mainServer;
	HearbeatsManager hearbeatsManager;
	Logger logger;
	
	Hashtable<String, ClientInterface> clients = new Hashtable<String, ClientInterface>();
	
	public SecondaryServer(HeartbeatsResponder mainServer, String logFilePath) throws RemoteException {
		this.logger = new Logger();
		this.logger.init(logFilePath);
		
		this.mainServer = mainServer;
		
		// listen to main server heartbeats
		this.hearbeatsManager = new HearbeatsManager(this, 100);
		this.hearbeatsManager.attachResponder(mainServer, 0);
		this.hearbeatsManager.start();
	}
	
	@Override
	public void onReponderFailure(HeartbeatsResponder failedResponder, int id) {
		// TODO: read current log and create main server object
	}

	@Override
	public void read(String fileName, long time) throws RemoteException {
		logger.logReadFile(fileName, time);
	}

	@Override
	public void newTxn(String fileName, long txnId, long time)
			throws RemoteException {
		logger.logTransaction(new Transaction(fileName, Transaction.STARTED, txnId), time);
	}

	@Override
	public void write(long txnID, long msgSeqNum, int dataLength, long time)
			throws RemoteException {
		logger.logWriteRequest(txnID, msgSeqNum, dataLength, time);
	}

	@Override
	public void commit(long txnID, String fileName, long time)
			throws RemoteException {
		logger.logTransaction(new Transaction(fileName, Transaction.COMMITED, txnID), time);
	}

	@Override
	public void abort(long txnID, String fileName, long time) throws RemoteException {
		logger.logTransaction(new Transaction(fileName, Transaction.ABORTED, txnID), time);
	}

	@Override
	public void registerClient(ClientInterface client, String auth_token)
			throws RemoteException {
		clients.put(auth_token, client);
	}

	@Override
	public void unregisterClient(ClientInterface client, String auth_token)
			throws RemoteException {
		clients.remove(auth_token);
	}
}
