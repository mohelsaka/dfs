package com.dfs.secondary;

import java.io.File;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
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

public class SecondaryServer implements HeartbeatsListener, SecondaryServerInterface{
	
	String directoryPath;
	HearbeatsManager hearbeatsManager;
	Logger logger;
	
	Hashtable<String, ClientInterface> clients = new Hashtable<String, ClientInterface>();
	Hashtable<Long, Transaction> transactions = new Hashtable<Long, Transaction>();
	
	int port;
	
	public SecondaryServer(HeartbeatsResponder mainServer, String directoryPath, int port) throws RemoteException {
		this.directoryPath = directoryPath;
		this.port = port;
		
		// create log directory
		new File(directoryPath + "log/").mkdir();
		
		// initialize new logger
		this.logger = new Logger();
		this.logger.init(directoryPath + "log/log.txt");
		
		// listen to main server heartbeats
		this.hearbeatsManager = new HearbeatsManager(this, 100);
		this.hearbeatsManager.attachResponder(mainServer, 0);
		this.hearbeatsManager.start();
	}
	
	@Override
	public void onReponderFailure(HeartbeatsResponder failedResponder, int id) {
		try {
			MainServer server = new MainServer(logger, clients, transactions, directoryPath);
			server.init(port);

			String ipAddress = getLanIPAddress();
			
			// announce the new server ip to all clients
			Enumeration<String> keys = clients.keys();
			while (keys.hasMoreElements()) {
				clients.get(keys.nextElement()).updateServerIP(ipAddress, port);
			}
			
		} catch (RemoteException e) {
			e.printStackTrace();
		} catch (AlreadyBoundException e) {
			e.printStackTrace();
		} catch (SocketException e) {
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
		Transaction tx = new Transaction(fileName, Transaction.STARTED, txnId);
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
}
