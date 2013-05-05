package com.dfs.server;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Hashtable;
import java.util.UUID;

import com.ds.interfaces.ClientInterface;
import com.ds.interfaces.FileContents;
import com.ds.interfaces.MessageNotFoundException;
import com.ds.interfaces.ServerInterface;


public class MainServer implements ServerInterface{
	Hashtable<String, ClientInterface> clients;
	
	@Override
	public FileContents read(String fileName) throws FileNotFoundException,
			IOException, RemoteException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public long newTxn(String fileName) throws RemoteException, IOException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int write(long txnID, long msgSeqNum, byte[] data)
			throws RemoteException, IOException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int commit(long txnID, long numOfMsgs)
			throws MessageNotFoundException, RemoteException {
		// TODO Auto-generated method stub
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
