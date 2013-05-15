package com.dfs.client;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import com.ds.interfaces.ClientInterface;
import com.ds.interfaces.MessageNotFoundException;
import com.ds.interfaces.ServerInterface;

public class Client implements ClientInterface{
	private String auth_token;
	private String hostIP;
	ServerInterface server;
	int port;
	
	public Client(String serverHostIP, int port) throws RemoteException, NotBoundException{
		this.hostIP = serverHostIP;
		this.port = port;
		
		Registry reg = LocateRegistry.getRegistry();
		server = (ServerInterface)reg.lookup(ServerInterface.DFSERVER_UNIQUE_NAME);
		
		UnicastRemoteObject.exportObject(this, 5412);
		server.registerClient(this);
	}
	
	@Override
	public void updateServerIP(String ip, int port) throws RemoteException {
		this.hostIP = ip;
		this.port = port;

		try {
			Registry reg = LocateRegistry.getRegistry(hostIP, port);
			server = (ServerInterface)reg.lookup(ServerInterface.DFSERVER_UNIQUE_NAME);
		} catch (NotBoundException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void setAuthenticationToken(String auth_token)
			throws RemoteException {
		this.auth_token = auth_token;
	}

	@Override
	public String getAuthenticationToken() throws RemoteException {
		return this.auth_token;
	}
	
	public String read(String fileName) throws FileNotFoundException, RemoteException, IOException{
		byte[] content = server.read(fileName).get();
		return new String(content);
	}
	
	public void write(String fileName, String content1, String content2) throws RemoteException, IOException, MessageNotFoundException{
		long txid = server.newTxn(fileName);
		server.write(txid, 1, content1.getBytes());
		server.write(txid, 2, content2.getBytes());
		server.commit(txid, 2);
	}
	
	public static void main(String[] args) throws NotBoundException, FileNotFoundException, IOException, MessageNotFoundException {
		Client c = new Client("localhost", 5555); //5892
		c.write("geeks story1.txt", "Once upon a time,\n", "there was a geek named saka");
		System.out.println(c.read("geeks story1.txt"));
	}
}
