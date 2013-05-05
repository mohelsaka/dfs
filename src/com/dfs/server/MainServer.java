package com.dfs.server;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.rmi.RemoteException;

import com.ds.interfaces.ClientInterface;
import com.ds.interfaces.FileContents;
import com.ds.interfaces.MessageNotFoundException;
import com.ds.interfaces.ServerInterface;


public class MainServer implements ServerInterface{

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
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean unregisterClient(ClientInterface client)
			throws RemoteException {
		// TODO Auto-generated method stub
		return false;
	}

}
