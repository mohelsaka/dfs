package com.dfs.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.UUID;

import com.dfs.heartbeats.HeartbeatsResponder;
import com.dfs.log.Logger;
import com.ds.interfaces.ClientInterface;
import com.ds.interfaces.FileContents;
import com.ds.interfaces.MessageNotFoundException;
import com.ds.interfaces.ReplicaServerInterface;
import com.ds.interfaces.SecondaryServerInterface;
import com.ds.interfaces.ServerInterface;

public class MainServer implements ServerInterface, HeartbeatsResponder {
	// default directories
	String directory_path = System.getProperty("user.home") + "/dfs/";
	String log_path = directory_path + "log/";
	private ArrayList<ReplicaServerInfo> replicaservers = new ArrayList<MainServer.ReplicaServerInfo>();

	/**
	 * Hashtable of all transaction
	 * */
	private Hashtable<Long, Transaction> transactions = new Hashtable<Long, Transaction>();

	/**
	 * Hashtable of all clients
	 * */
	private Hashtable<String, ClientInterface> clients = new Hashtable<String, ClientInterface>();

	/**
	 * Logger instance to log clients interaction with the server
	 * */
	private Logger logger;
	private Random random;
	private static long idleTimeout = 60000; // 1 minute

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
	}

	public MainServer(String secondaryServerHost, int secondaryServerPort, String directoryPath)
			throws RemoteException, NotBoundException {
		if (directoryPath != null) {
			this.directory_path = directoryPath;
			this.log_path = directory_path + "log/";
		}
		
		this.clients = new Hashtable<String, ClientInterface>();
		this.random = new Random(System.currentTimeMillis());
		
		// getting access to the secondary server if it is given as paramter
		if (secondaryServerHost != null) {
			Registry registry = LocateRegistry.getRegistry(secondaryServerHost, secondaryServerPort);
			secondaryServer = (SecondaryServerInterface) registry.lookup(DFS_SECONDARY_SERVER_UNIQUE_NAME);
		}

		// creating working directories
		new File(log_path).mkdir();

		// create logger
		this.logger = new Logger();
		this.logger.init(log_path + "log.txt");
	}

	ReplicaServerInterface getServer(ReplicaServerInfo replicaServerInfo) {
		Registry registry;
		try {
			registry = LocateRegistry.getRegistry(replicaServerInfo.hostName, replicaServerInfo.port);
			return (ReplicaServerInterface) registry.lookup(replicaServerInfo.uniqueName);
		} catch (RemoteException e) {
			e.printStackTrace();
		} catch (NotBoundException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public FileContents read(String fileName) throws FileNotFoundException,
			IOException, RemoteException {
		
		int idx = random.nextInt(replicaservers.size());
		
		ServerInterface rServer = (ServerInterface) getServer(replicaservers.get(idx));
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
		long time = System.currentTimeMillis();
		long txnId = time;
		// create transaction object and log it
		Transaction tx = new Transaction(fileName, Transaction.STARTED, txnId, time);
		
		logger.logTransaction(tx, time);
		transactions.put(txnId, tx);
		
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
		
		for (ReplicaServerInfo name : replicaservers) {
			ReplicaServerInterface server = (ReplicaServerInterface) getServer(name);
			
			if (server != null)
				server.write(txnID, msgSeqNum, data);
		}
		
		// log this write request
		long time = System.currentTimeMillis();
		logger.logWriteRequest(txnID, msgSeqNum, data.length, time);
		
		if (secondaryServer != null)
			secondaryServer.write(txnID, msgSeqNum, data.length, time);
		
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
		
		for (ReplicaServerInfo name : replicaservers) {
			ReplicaServerInterface server = (ReplicaServerInterface) getServer(name);
			
			if (server != null)
				server.commit(txnID, numOfMsgs, transactions.get(txnID).getFileName());
		}
		
		// update transaction state and log it
		Transaction tx = transactions.get(txnID);
		tx.setState(Transaction.COMMITED);
		long time = System.currentTimeMillis();
		logger.logTransaction(tx, time);
		
		if (secondaryServer != null)
			secondaryServer.commit(txnID, tx.getFileName(), time);
		
		return ACK;
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

		for (ReplicaServerInfo name : replicaservers) {
			ReplicaServerInterface server = (ReplicaServerInterface) getServer(name);
			
			if (server != null)
				server.abort(txnID);
		}
		
		// update transaction state and log it
		long time = System.currentTimeMillis();
		transactions.get(txnID).setState(Transaction.ABORTED);
		logger.logTransaction(transactions.get(txnID), time);

		if (secondaryServer != null)
			secondaryServer.abort(txnID, transactions.get(txnID).getFileName(), time);

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

	public void init(int port) throws java.rmi.AlreadyBoundException, IOException {
		Object mainServerExportedObject = UnicastRemoteObject.exportObject(this, port);
		ServerInterface serverStub = (ServerInterface) mainServerExportedObject;
		HeartbeatsResponder heartbeatResponderStub = (HeartbeatsResponder) mainServerExportedObject;

		Registry registry = LocateRegistry.createRegistry(port);
		registry.rebind(DFSERVER_UNIQUE_NAME, serverStub);
		registry.rebind(MAIN_SERVER_HEARTBEAT_NAME, heartbeatResponderStub);
		
		// running transaction time out checker thread
		transactionsTimeoutChecker.start();
		
		// read ReplicaServer configuration file
		InputStreamReader converter = new InputStreamReader(new FileInputStream(new File("ReplicaServers")));
		BufferedReader in = new BufferedReader(converter);
		String line = null;
		
		in.readLine(); // skip first line, it is a comment for specifying the format
		while ((line = in.readLine()) != null) {
			replicaservers.add(new ReplicaServerInfo(line));
		}
	}
	
	private Thread transactionsTimeoutChecker = new Thread(new Runnable() {
		
		@Override
		public void run() {
			while(true){
				long now = System.currentTimeMillis();
				Object[] keys = MainServer.this.transactions.keySet().toArray();
				for (Object key : keys) {
					Transaction t = MainServer.this.transactions.get((Long)key);
					
					// clean aborted and commited transactions from transaction hash table
					if(t.getState() == Transaction.COMMITED || t.getState() == Transaction.ABORTED){
						MainServer.this.transactions.remove(key);
					}
					
					// check transaction time and state
					if((now - t.getLastEdited()) > idleTimeout){
						try {
							MainServer.this.abort((Long)key);
						} catch (RemoteException e) {
							e.printStackTrace();
						}
						MainServer.this.transactions.remove(key);
					}
				}
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	});
	
	class ReplicaServerInfo{
		String uniqueName;
		String hostName;
		int port;
		
		public ReplicaServerInfo(String info){
			StringTokenizer st = new StringTokenizer(info, "\t");
			hostName = st.nextToken();
			port = Integer.parseInt(st.nextToken());
			uniqueName = st.nextToken();
		}
	}
	
	public static void main(String[] args) throws AlreadyBoundException, NotBoundException,
			java.rmi.AlreadyBoundException, IOException {
		
		final MainServer server = new MainServer("localhost", 4135, System.getProperty("user.home") + "/dfs/dfs2/");
		server.init(5555);

		System.out.println("server is running ...");
	}

}