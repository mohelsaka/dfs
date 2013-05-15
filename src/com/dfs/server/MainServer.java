package com.dfs.server;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import com.dfs.heartbeats.HeartbeatsResponder;
import com.dfs.log.Logger;
import com.ds.interfaces.ClientInterface;
import com.ds.interfaces.FileContents;
import com.ds.interfaces.MessageNotFoundException;
import com.ds.interfaces.SecondaryServerInterface;
import com.ds.interfaces.ServerInterface;

public class MainServer implements ServerInterface, HeartbeatsResponder {
	// default directories
	String directory_path = System.getProperty("user.home") + "/dfs/";
	String cache_path = directory_path + "cache/";
	String log_path = directory_path + "log/";
	ArrayList<String> replicaservers;
	private long lasttxn = 10000;

	/**
	 * Hashtable of all transaction
	 * */
	Hashtable<Long, Transaction> transactions = new Hashtable<Long, Transaction>();

	/**
	 * Hashtable of all clients
	 * */
	Hashtable<String, ClientInterface> clients = new Hashtable<String, ClientInterface>();
	Hashtable<Long, Long> transIdleTimes; 

	/**
	 * Logger instance to log clients interaction with the server
	 * */
	private Logger logger;
	private Random random;
	private static long idleTimeout = 36000;

	// secondary server attributes
	SecondaryServerInterface secondaryServer;

	public static final String MAIN_SERVER_HEARTBEAT_NAME = "main_server_responder";

	/**
	 * Constructing MainServe object with main attributes, this is used when
	 * running new MainServer instance from the secondary server when the
	 * original main server is failed.
	 * */
	public MainServer(Logger logger,
			Hashtable<String, ClientInterface> clients,
			Hashtable<Long, Transaction> transactions, String directoryPath) {

		this.logger = logger;
		this.directory_path = directoryPath;
		this.clients = clients;
		this.transactions = transactions;
		this.random = new Random(System.currentTimeMillis());
		// creating working directories
		new File(cache_path).mkdir();
		transIdleTimes = new Hashtable<Long, Long>();
	}

	public MainServer(String secondaryServerHost, String directoryPath)
			throws RemoteException, NotBoundException {
		if (directoryPath != null) {
			this.directory_path = directoryPath;
			this.cache_path = directory_path + "cache/";
			this.log_path = directory_path + "log/";
		}
		this.clients = new Hashtable<String, ClientInterface>();
		replicaservers = new ArrayList<String>();
		this.random = new Random(System.currentTimeMillis());
		
		// getting access to the secondary server if it is given as paramter
		if (secondaryServerHost != null) {
			Registry registry = LocateRegistry.getRegistry(secondaryServerHost);
			secondaryServer = (SecondaryServerInterface) registry
					.lookup(DFS_SECONDARY_SERVER_UNIQUE_NAME);
		}

		// creating working directories
		new File(cache_path).mkdir();
		new File(log_path).mkdir();

		// create logger
		logger = new Logger();
		logger.init(log_path + "log.txt");
		transIdleTimes = new Hashtable<Long, Long>();
	}

	ServerInterface getServer(String name) {
		Registry registry;
		try {
			registry = LocateRegistry.getRegistry();
			return (ServerInterface) registry.lookup(name);
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NotBoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public FileContents read(String fileName) throws FileNotFoundException,
			IOException, RemoteException {
		int idx = random.nextInt(replicaservers.size());
		ServerInterface rServer = (ServerInterface) getServer(replicaservers
				.get(idx));
		FileContents contents = rServer.read(fileName);
		long time = System.currentTimeMillis();
		logger.logReadFile(fileName, time);

		if (secondaryServer != null)
			secondaryServer.read(fileName, time);

		// return FileContent instance
		return contents;
	}


	@Override
	public long newTxn(String fileName) throws RemoteException, IOException {
		// generate new transaction id
		long txnId = lasttxn++;
		// create transaction object and log it
		Transaction tx = new Transaction(fileName, Transaction.STARTED, txnId);
		
		long time = System.currentTimeMillis();
		logger.logTransaction(tx, time);
		transactions.put(txnId, tx);
		for (String name : replicaservers) {
			ServerInterface server = (ServerInterface) getServer(name);
			if (server != null)
				server.newTxn(fileName);
		}
		if (secondaryServer != null)
			secondaryServer.newTxn(fileName, txnId, time);
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
		if (transactions.get(txnID).getState() == Transaction.COMMITED) {
			return INVALID_OPERATION;
		}
		for (String name : replicaservers) {
			ServerInterface server = (ServerInterface) getServer(name);
			if (server != null)
				server.write(txnID, msgSeqNum, data);
		}
		long time = System.currentTimeMillis();
		logger.logWriteRequest(txnID, msgSeqNum, data.length, time);
		if (secondaryServer != null)
			secondaryServer.write(txnID, msgSeqNum, data.length, time);
		transIdleTimes.put(txnID, time); // the transaction became idle
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
		if (transactions.get(txnID).getState() == Transaction.COMMITED) {
			// the client me request resending the ack message
			return ACK;
		}
		for (String name : replicaservers) {
			ServerInterface server = (ServerInterface) getServer(name);
			if (server != null)
				server.commit(txnID, numOfMsgs);
		}
		Transaction tx = transactions.get(txnID);
		long time = System.currentTimeMillis();
		logger.logTransaction(tx, time);
		if (secondaryServer != null)
			secondaryServer.commit(txnID, tx.getFileName(), time);
		Object[] keys = transIdleTimes.keySet().toArray();
		for (Object key : keys) {
			long lkey = (Long)key;
			if(time - transIdleTimes.get(key) > idleTimeout){
				abort(lkey);
				transIdleTimes.remove(lkey);
			}
		}
		return ACK;
	}

	/**
	 * FilenameFilter used to filter cached files for specific transaction
	 * */
	class CacheFilesFilter implements FilenameFilter {
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
		if (transactions.get(txnID).getState() == Transaction.COMMITED) {
			// aborting commited transaction is invalid operation
			return INVALID_OPERATION;
		}

		// check if the transaction has been already aborted
		if (transactions.get(txnID).getState() == Transaction.ABORTED) {
			return ACK;
		}

		for (String name : replicaservers) {
			ServerInterface server = (ServerInterface) getServer(name);
			if (server != null)
				server.abort(txnID);
		}

		long time = System.currentTimeMillis();
		logger.logTransaction(transactions.get(txnID), time);

		if (secondaryServer != null)
			secondaryServer.abort(txnID, transactions.get(txnID).getFileName(),
					time);

		return ACK;
	}

	@Override
	public boolean registerClient(ClientInterface client)
			throws RemoteException {
		String auth_token = client.getAuthenticationToken();

		if (auth_token == null) {
			// generate new auth token
			auth_token = UUID.randomUUID().toString();
			client.setAuthenticationToken(auth_token);

			// add this new client to the list of authenticated clients
			this.clients.put(auth_token, client);

			if (secondaryServer != null)
				secondaryServer.registerClient(client, auth_token);

			return true;
		} else {
			if (clients.containsKey(auth_token))
				return true;
			else
				return false;
		}
	}

	@Override
	public boolean unregisterClient(ClientInterface client)
			throws RemoteException {
		String auth_token = client.getAuthenticationToken();
		
		if (auth_token == null) {
			// Unresisted client
			return false;
		} else {
			if (clients.containsKey(auth_token)) {
				// safely remove this client
				clients.remove(auth_token);

				if (secondaryServer != null)
					secondaryServer.unregisterClient(client, auth_token);

				return true;
			} else {
				// unrecognized auth token, safely return false
				return false;
			}
		}
	}

	@Override
	public boolean isAlive() throws RemoteException {
		return true;
	}

	public void init(int port) throws RemoteException,
			java.rmi.AlreadyBoundException {
		Object mainServerExportedObject = UnicastRemoteObject.exportObject(
				this, port);
		ServerInterface serverStub = (ServerInterface) mainServerExportedObject;
		HeartbeatsResponder heartbeatResponderStub = (HeartbeatsResponder) mainServerExportedObject;

		Registry registry = LocateRegistry.createRegistry(port);
		registry.bind(DFSERVER_UNIQUE_NAME, serverStub);
		registry.bind(MAIN_SERVER_HEARTBEAT_NAME, heartbeatResponderStub);
	}

	public static void main(String[] args) throws RemoteException,
			AlreadyBoundException, NotBoundException,
			java.rmi.AlreadyBoundException {
		MainServer server = new MainServer("localhost",
				System.getProperty("user.home") + "/dfs/dfs2/");
		server.init(5555);
		new ReplicaServer("localhost", "1").init("replica1", 5678);
		new ReplicaServer("localhost", "2").init("replica2", 5679);
		server.replicaservers.add("replica1");
		server.replicaservers.add("replica2");
		System.out.println("server is running ...");
	}

}