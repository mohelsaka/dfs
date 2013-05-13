package com.ds.interfaces;

import java.rmi.RemoteException;

public interface SecondaryServerInterface {
	public void read(String fileName, long time) throws RemoteException;

	public void newTxn(String fileName, long txnId, long time) throws RemoteException;

	public void write(long txnID, long msgSeqNum, int dataLength, long time)
			throws RemoteException;

	public void commit(long txnID, String fileName, long time)
			throws RemoteException;

	public void abort(long txnID, String fileName, long time) throws RemoteException;

	public void registerClient(ClientInterface client, String auth_token)
			throws RemoteException;

	public void unregisterClient(ClientInterface client, String auth_token)
			throws RemoteException;
}
