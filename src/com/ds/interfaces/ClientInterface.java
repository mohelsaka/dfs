package com.ds.interfaces;
import java.rmi.Remote;
import java.rmi.RemoteException;


public interface ClientInterface extends Remote{
	/**
	 * Update Server IP in case of failure 
	 * @param ip The secondary server IP
	 * @param port The secondary server port
	 * @throws RemoteException
	 */
	
	void updateServerIP(String ip,int port) throws RemoteException;

}
