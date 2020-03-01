import java.rmi.Remote;
import java.io.*;
import java.rmi.RemoteException;

public interface ServerIntf extends Remote {
	public String HelloWorld() throws RemoteException; //RemoteException;
	public int GetFileLength(String path) throws RemoteException;
	public ServerData GetLengthAndVersion(String path) throws RemoteException;
	public ServerData open(String path, int request_len, int offset) throws RemoteException;
	public boolean write(String path, byte buf[], boolean chunk) throws RemoteException;
	public int unlink(String path) throws RemoteException;
}
