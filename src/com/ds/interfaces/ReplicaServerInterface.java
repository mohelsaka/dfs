package com.ds.interfaces;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.rmi.RemoteException;

public interface ReplicaServerInterface extends ServerInterface{

	FileContents read(String fileName) throws FileNotFoundException, IOException, RemoteException;

	long newTxn(String fileName) throws RemoteException, IOException;

	int write(long txnID, long msgSeqNum, byte[] data) throws RemoteException, IOException;

	int commit(long txnID, long numOfMsgs, String filename)	throws MessageNotFoundException, RemoteException;

	int abort(long txnID) throws RemoteException;

	boolean registerClient(ClientInterface client) throws RemoteException;

	boolean unregisterClient(ClientInterface client) throws RemoteException;

	int commit(long txnID, long numOfMsgs) throws MessageNotFoundException,	RemoteException;

}
