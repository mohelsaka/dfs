package com.dfs.heartbeats;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RemoteHeartbeatsResponder extends HeartbeatsResponder, Remote{
	public void onReponderFailure(HeartbeatsResponder failedResponder, int id) throws RemoteException;
}
