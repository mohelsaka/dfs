package com.dfs.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Arrays;
import com.ds.interfaces.ClientInterface;
import com.ds.interfaces.FileContents;
import com.ds.interfaces.MessageNotFoundException;
import com.ds.interfaces.ReplicaServerInterface;
import com.ds.interfaces.ServerInterface;

public class ReplicaServer implements ReplicaServerInterface {
	// default directories
	String directory_path = System.getProperty("user.home") + "/dfs/";
	String cache_path = directory_path + "cache/";
	String log_path = directory_path + "log/";

	public ReplicaServer(String host, String directoryPath)
			throws RemoteException, NotBoundException {
		if (directoryPath != null) {
			this.directory_path += directoryPath+"/";
			this.cache_path = directory_path + "cache/";
			this.log_path = directory_path + "log/";
		}
		
		// creating working directories
		new File(directory_path).mkdir();
		new File(cache_path).mkdir();
		new File(log_path).mkdir();
	}

	@Override
	public FileContents read(String fileName) throws FileNotFoundException,
			IOException, RemoteException {

		// Note: only file of size less that BUFFER_SIZE can be handled
		// correctly.
		FileInputStream instream = new FileInputStream(new File(directory_path + fileName));
		byte[] buffer = new byte[FileContents.BUFFER_SIZE];
		int contentlength = instream.read(buffer);
		instream.close();

		// copying the buffer in smaller content byte array to be sent
		byte[] content = new byte[contentlength];
		System.arraycopy(buffer, 0, content, 0, contentlength);

		// return FileContent instance
		return new FileContents(content);
	}

	@Override
	public long newTxn(String fileName) throws RemoteException, IOException {
		return 0;
	}

	@Override
	public int write(long txnID, long msgSeqNum, byte[] data)
			throws RemoteException, IOException {
		// build cache file name
		File cacheFile = new File(cache_path + txnID + "_" + msgSeqNum);
		cacheFile.createNewFile();
		FileOutputStream outstream = new FileOutputStream(cacheFile);

		// safely write data and close opened stream
		outstream.write(data);
		outstream.flush();
		outstream.close();


		return ACK;
	}

	@Override
	public int commit(final long txnID, long numOfMsgs, String filename)
			throws MessageNotFoundException, RemoteException {

		// get all cached files by this transaction
		File[] cachedFiles = new File(cache_path).listFiles(new CacheFilesFilter(txnID));

		// check if there are unreceived messages and report them to the client
		if (cachedFiles.length < numOfMsgs) {
			long[] msgsIDs = new long[cachedFiles.length];

			// convert msgsID to array of Long
			for (int i = 0; i < msgsIDs.length; i++) {
				String fname = cachedFiles[(int) i].getName();
				msgsIDs[i] = Long
						.parseLong(fname.substring(fname.indexOf('_') + 1));
			}

			// prepare exception to be thrown
			MessageNotFoundException exception = new MessageNotFoundException();
			exception.setMsgNum(findLostMessagesIDs(msgsIDs, numOfMsgs));

			throw exception;
		}

		try {
			// create new file if it is not exist yet.
			File fout = new File(directory_path + filename);
			fout.createNewFile();

			FileOutputStream outsream = new FileOutputStream(fout, true);

			byte[] buffer = new byte[FileContents.BUFFER_SIZE];
			for (int i = 1; i <= numOfMsgs; i++) {
				FileInputStream instream = new FileInputStream(new File(
						cache_path + txnID + '_' + i));

				int len = 0;
				while ((len = instream.read(buffer)) != -1) {
					outsream.write(buffer, 0, len);
				}

				instream.close();
			}

			// flush and close file output stream
			outsream.flush();
			outsream.close();
		} catch (IOException e) {
			e.printStackTrace();
			// TODO: unhandeled yet
		}

		clearTransaction(txnID, Transaction.COMMITED);


		return ACK;
	}

	/**
	 * clear cached files, release file lock and set a new state for the
	 * transaction
	 * */
	private synchronized void clearTransaction(long txnID, int txnNewState) {
		File[] cachedFiles = new File(cache_path)
				.listFiles(new CacheFilesFilter(txnID));
		for (File file : cachedFiles) {
			file.delete();
		}
	}

	/**
	 * getting ids if message that are nore received.
	 * 
	 * @param msgsIDs
	 *            array of all received messages
	 * @param numOfMsgs
	 *            the total number of message that should be received.
	 * 
	 * @return array of all missing messages ids
	 * */
	private int[] findLostMessagesIDs(long[] msgsIDs, long numOfMsgs) {
		Arrays.sort(msgsIDs);

		int missedMessagesNumner = (int) numOfMsgs - msgsIDs.length;
		int[] missedMessages = new int[missedMessagesNumner];
		int mIndex = 0;

		for (int i = 1; i < msgsIDs.length; i++) {
			if ((msgsIDs[i] - msgsIDs[i - 1]) != 1) {
				for (long j = msgsIDs[i - 1] + 1; j < msgsIDs[i]; j++) {
					missedMessages[mIndex++] = (int) j;
				}
			}
		}
		return missedMessages;
	}

	/**
	 * FilenameFilter used to filter cached files for specific transaction
	 * */
	class CacheFilesFilter implements FilenameFilter {
		long txnID = 0;

		public boolean accept(File dir, String name) {
			name = name.substring(0, name.indexOf('_'));
			return name.equals("" + txnID);
		}

		CacheFilesFilter(long txnID) {
			this.txnID = txnID;
		}
	}

	@Override
	public int abort(long txnID) throws RemoteException {
		// clear all changes made by this transaction
		clearTransaction(txnID, Transaction.ABORTED);

		return ACK;
	}

	@Override
	public boolean registerClient(ClientInterface client)
			throws RemoteException {
		return true;
	}

	@Override
	public boolean unregisterClient(ClientInterface client)
			throws RemoteException {

		return true;

	}


	public void init(String name, int port) throws RemoteException,
			java.rmi.AlreadyBoundException {
		Object mainServerExportedObject = UnicastRemoteObject.exportObject(this, port);
		ServerInterface serverStub = (ServerInterface) mainServerExportedObject;

		Registry registry = LocateRegistry.getRegistry();
		registry.rebind(name, serverStub);
	}

	public static void main(String[] args) throws RemoteException,
			AlreadyBoundException, NotBoundException,
			java.rmi.AlreadyBoundException {
	}

	@Override
	public int commit(long txnID, long numOfMsgs)
			throws MessageNotFoundException, RemoteException {
		// TODO Auto-generated method stub
		return 0;
	}

}
