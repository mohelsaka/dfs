package com.dfs.heartbeats;

public interface HeartbeatsListener {
	public void onReponderFailure(HeartbeatsResponder failedResponder, int id);
}
