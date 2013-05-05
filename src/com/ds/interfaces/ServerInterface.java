package com.ds.interfaces;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.rmi.Remote;
import java.rmi.RemoteException;


public interface ServerInterface extends Remote {
	final static int ACK = 100;
	/**
	 * the server asks the client to resend the message sent from client. 
	 */
	final static int ACK_RSND = 101;

	/**
	 * Invalid transaction ID. Sent by the server if the client had sent a
	 * message that included an invalid transaction ID, i.e., a transaction ID
	 * that the server does not remember.
	 */
	final static int INVALID_TRANSACTION_ID = 201;
	/**
	 * Invalid operation. Sent by the server if the client attempts to execute
	 * an invalid operation - i.e., write as part of a transaction that had been
	 * committed
	 * 
	 */
	final static int INVALID_OPERATION = 202;
	
	final static String DFServerUniqyeName = "dsf_name";
	
	final static int SERVER_PORT = 5555;
	
	/**
	 * the client reads the file from the server
	 * 
	 * @param fileName
	 * @return File data
	 * @throws FileNotFoundException
	 * @throws IOException
	 * @throws RemoteException
	 */
	public FileContents read(String fileName) throws FileNotFoundException,
			IOException, RemoteException;

	/**
	 * the client asks the server to begin a new transaction
	 * 
	 * @param fileName
	 * @return the new transaction ID
	 * @throws RemoteException
	 * @throws IOException
	 */
	public long newTxn(String fileName) throws RemoteException, IOException;

	/**
	 * the client asks the server to write data as part of an existing
	 * transaction
	 * 
	 * @param txnID
	 *            : the ID of the transaction to which this message relates
	 * @param msgSeqNum
	 *            : the message sequence number. Each transaction starts with
	 *            message sequence number 1.
	 * @param data
	 *            : data to write in the file
	 * @return ACK in case of success, ACK_RSND, INVALID_TRANSACTION_ID, or INVALID_OPERATION
	 * @throws IOException
	 * @throws RemoteException
	 */
	public int write(long txnID, long msgSeqNum, byte[] data)
			throws RemoteException, IOException;

	/**
	 * the client asks the server to commit the transaction. In this case, the
	 * message sequence number field includes the total number of writes that
	 * were sent by the client as part of this transaction.
	 * 
	 * @param txnID
	 *            : the ID of the transaction to which this message relates
	 * @param numOfMsgs
	 *            : Number of messages sent to the server
	 * @return ACK in case of success,INVALID_OPERATION, or INVALID_TRANSACTION_ID for wrong txn id,
	 *         or through MessageNotFoundException calling for missing data.
	 * @throws MessageNotFoundException
	 * @throws RemoteException
	 */
	public int commit(long txnID, long numOfMsgs)
			throws MessageNotFoundException, RemoteException;

	/**
	 * the client asks the server to abort the transaction.
	 * 
	 * @param txnID
	 *            : the ID of the transaction to which this message relates
	 * @return ACK in case of success, INVALID_OPERATION, or INVALID_TRANSACTION_ID in case of wrong
	 *         txn id
	 * @throws RemoteException
	 */
	public int abort(long txnID) throws RemoteException;

	/**
	 * register client
	 * 
	 * @param client
	 * @return true in case of successful registering
	 * @throws RemoteException
	 */
	public boolean registerClient(ClientInterface client)
			throws RemoteException;

	/**
	 * 
	 * @param client
	 * @return true in case of successful unregistering
	 * @throws RemoteException
	 */
	public boolean unregisterClient(ClientInterface client)
			throws RemoteException;
}
