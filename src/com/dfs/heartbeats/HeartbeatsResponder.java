package com.dfs.heartbeats;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface HeartbeatsResponder extends Remote{
	public boolean isAlive() throws RemoteException;
}
