import java.io.FileNotFoundException;
import java.io.IOException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import com.ds.interfaces.FileContents;
import com.ds.interfaces.ServerInterface;


public class Test {
	public static void main(String[] args) throws NotBoundException, FileNotFoundException, IOException {
		Registry registry = LocateRegistry.getRegistry();
		
		ServerInterface server = (ServerInterface) registry.lookup(ServerInterface.DFServerUniqyeName);
		
		System.out.println(server);
	}
}
