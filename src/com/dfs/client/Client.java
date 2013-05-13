package com.dfs.client;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import com.ds.interfaces.ClientInterface;
import com.ds.interfaces.ServerInterface;

public class Client implements ClientInterface{
	private String auth_token;
	private String hostIP;
	ServerInterface server;
	int port;
	
	public Client(String serverHostIP, int port) throws RemoteException, NotBoundException{
		this.hostIP = serverHostIP;
		this.port = port;
		
		Registry reg = LocateRegistry.getRegistry(hostIP, port);
		server = (ServerInterface)reg.lookup(ServerInterface.DFSERVER_UNIQUE_NAME);
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

}
