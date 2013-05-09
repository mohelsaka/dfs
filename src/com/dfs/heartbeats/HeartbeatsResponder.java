package com.dfs.heartbeats;

import java.rmi.RemoteException;

public interface HeartbeatsResponder{
	public boolean isAlive() throws RemoteException;
}
