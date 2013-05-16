package com.dfs.secondary;

import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Enumeration;
import java.util.Hashtable;

import com.dfs.heartbeats.HearbeatsManager;
import com.dfs.heartbeats.HeartbeatsListener;
import com.dfs.heartbeats.HeartbeatsResponder;
import com.dfs.log.Logger;
import com.dfs.server.MainServer;
import com.dfs.server.Transaction;
import com.ds.interfaces.ClientInterface;
import com.ds.interfaces.SecondaryServerInterface;
import com.ds.interfaces.ServerInterface;

public class SecondaryServer implements HeartbeatsListener, SecondaryServerInterface{
	
	String directoryPath;
	String mainServerDirectoryPath;
	HearbeatsManager hearbeatsManager;
	Logger logger;
	
	Hashtable<String, ClientInterface> clients = new Hashtable<String, ClientInterface>();
	Hashtable<Long, Transaction> transactions = new Hashtable<Long, Transaction>();
	
	int mainServerPort;
	
	public SecondaryServer(String directoryPath, String mainServerDirectoryPath,int mainServerPort) throws RemoteException {
		this.directoryPath = directoryPath;
		this.mainServerPort = mainServerPort;
		this.mainServerDirectoryPath = mainServerDirectoryPath;
		
		// create log directory
		new File(directoryPath + "log/").mkdir();
		
		// initialize new logger
		this.logger = new Logger();
		this.logger.init(directoryPath + "log/log.txt");
		
	}
	
	public void init(String currentMainServerHostName, int currentMainServerPort, int secondaryServerPort) throws RemoteException, AlreadyBoundException{
		// register secondary server object to RMI
		SecondaryServerInterface secondaryServerStub = (SecondaryServerInterface) UnicastRemoteObject.exportObject(this, secondaryServerPort);
		Registry registry = LocateRegistry.getRegistry();
		registry.bind(ServerInterface.DFS_SECONDARY_SERVER_UNIQUE_NAME, secondaryServerStub);
		
		// get connection with currently running main server
		HeartbeatsResponder mainServerHeartbeats = null;
		System.out.println("Trying to connect with currently running main server ... ");
		while(mainServerHeartbeats == null){
			try {
				registry = LocateRegistry.getRegistry(currentMainServerHostName, currentMainServerPort);
				mainServerHeartbeats = (HeartbeatsResponder)registry.lookup(MainServer.MAIN_SERVER_HEARTBEAT_NAME);
			} catch (Exception e) {
//				System.out.println("Attempt failed");
				
				try {
					Thread.sleep(2000);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
			}
		}
		System.out.println("Connected with main server");
		
		
		// listen to main server heartbeats
		this.hearbeatsManager = new HearbeatsManager(this, 100);
		this.hearbeatsManager.attachResponder(mainServerHeartbeats, 0);
		this.hearbeatsManager.start();
	}
	
	@Override
	public void onReponderFailure(HeartbeatsResponder failedResponder, int id) {
		try {
			MainServer server = new MainServer(logger, clients, transactions, mainServerDirectoryPath);
			server.init(mainServerPort);

			String ipAddress = getLanIPAddress();
			
			// announce the new server ip to all clients
			Enumeration<String> keys = clients.keys();
			while (keys.hasMoreElements()) {
				String key = keys.nextElement();
				
				try{
					clients.get(key).updateServerIP(ipAddress, mainServerPort);
				}catch (RemoteException e) {
					System.err.println("unable to update clinet: " + key);
				}
			}
			
		} catch (RemoteException e) {
			e.printStackTrace();
		} catch (AlreadyBoundException e) {
			e.printStackTrace();
		} catch (SocketException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void read(String fileName, long time) throws RemoteException {
		logger.logReadFile(fileName, time);
	}

	@Override
	public void newTxn(String fileName, long txnId, long time)
			throws RemoteException {
		Transaction tx = new Transaction(fileName, Transaction.STARTED, txnId, System.currentTimeMillis());
		transactions.put(tx.getId(), tx);
		logger.logTransaction(tx, time);
	}

	@Override
	public void write(long txnID, long msgSeqNum, int dataLength, long time)
			throws RemoteException {
		logger.logWriteRequest(txnID, msgSeqNum, dataLength, time);
	}

	@Override
	public void commit(long txnID, String fileName, long time)
			throws RemoteException {
		Transaction tx = transactions.get(txnID);
		tx.setState(Transaction.COMMITED);
		logger.logTransaction(tx, time);
	}

	@Override
	public void abort(long txnID, String fileName, long time) throws RemoteException {
		Transaction tx = transactions.get(txnID);
		tx.setState(Transaction.ABORTED);
		logger.logTransaction(tx, time);
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
	
	private String getLanIPAddress() throws SocketException{
		Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();
        while(e.hasMoreElements()){
            Enumeration<InetAddress> ee = e.nextElement().getInetAddresses();
            
            while(ee.hasMoreElements()) {
                InetAddress i= (InetAddress) ee.nextElement();
                
                if (i instanceof Inet4Address) {
                	return i.getHostAddress();
				}
            }
        }
        return null;
	}
	
	public static void main(String[] args) throws RemoteException, AlreadyBoundException {
		SecondaryServer secondayServer = new SecondaryServer(System.getProperty("user.home")+"/dfs/dfs1/", 
															 System.getProperty("user.home")+"/dfs/dfs2/",
															 5892);
		
		secondayServer.init("localhost", 5555, 4135);
	}
}
