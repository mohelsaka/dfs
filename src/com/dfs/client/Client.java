package com.dfs.client;

import java.rmi.RemoteException;

import com.ds.interfaces.ClientInterface;

public class Client implements ClientInterface{
	private String auth_token;
	
	@Override
	public void updateServerIP(String ip, int port) throws RemoteException {
		// TODO Auto-generated method stub
		
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
