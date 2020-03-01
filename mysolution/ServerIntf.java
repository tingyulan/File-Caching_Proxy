import java.rmi.Remote;
import java.io.*;
import java.rmi.RemoteException;

public interface ServerIntf extends Remote {
	public int GetFileLength(String path) throws RemoteException;
	public ServerData GetLengthAndVersion(String path) throws RemoteException;
	public ServerData open(String path, int request_len, int offset) throws RemoteException;
	public long write(String path, byte buf[], long offset) throws RemoteException;
	public int unlink(String path) throws RemoteException;
}
