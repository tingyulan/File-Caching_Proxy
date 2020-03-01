import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import java.rmi.server.UnicastRemoteObject;
import java.rmi.RemoteException;
import java.rmi.registry.*;
import java.rmi.Naming;

import java.net.MalformedURLException;


public class Server extends UnicastRemoteObject implements ServerIntf {  // ServerInterface
    public static final int READ = 0;
    public static final int WRITE = 1;
    public static final int CREATE = 2;
    public static final int CREATE_NEW = 3;
    public static final int VERSION_OFFSET = 2000;
    public static Map <String, Integer> hmap_version = new ConcurrentHashMap <String, Integer> ();
    private final static Object lock = new Object();


    public static String server_root;

    protected Server() throws RemoteException { super(0); }

    // Return file length
    public int GetFileLength(String path) throws RemoteException{
        String server_path = server_root+path;
        File file = new File(server_path);
        if(!file.exists()) { return -2; }
        if(file.isDirectory()) { return -3; }
        
        return (int) file.length();
    }

    // Return a ServerData with 0 content data
    // Return file length, version, and errno
    public ServerData GetLengthAndVersion(String path) throws RemoteException{
        ServerData server_data;
        String server_path = server_root+path;
        

        File file = new File(server_path);
        if(!file.exists()) {
            server_data= new ServerData(-1, -1, -2);
            return server_data;
        }
        if(file.isDirectory()) {
            server_data= new ServerData(-1, -1, -2);
            return server_data;
        }

        int length = (int) file.length();

        if(!hmap_version.containsKey(path)){
            hmap_version.put(path, 0);
        }
        int server_version = hmap_version.get(path);

        server_data = new ServerData(server_version, length, 0);
        return server_data;
    }

    // Return file's content data to proxy
    public ServerData open(String path, int request_len, int offset) throws RemoteException{
        /* o: READ(read-only), WRITE(read/write), CREATE(read/write, create if needed),
					CREATE_NEW(read/write, but file must not already exist)*/
        String server_path = server_root+path;
        File file = new File(server_path);

        if(!hmap_version.containsKey(path)){
            hmap_version.put(path, 0);
        }
        int server_version = hmap_version.get(path);

        byte[] buf = new byte[request_len];
        try{
            RandomAccessFile raf = new RandomAccessFile(server_path, "rw");
            raf.seek(offset);
            int ret = raf.read(buf, 0, request_len);
            raf.close();
            if(ret!=request_len){ System.err.println("OPEN, not read exactly request_len"); }
        }catch(IOException e){
            System.err.println("OPEN, IOException");
            e.printStackTrace();
        }
        ServerData server_data = new ServerData(buf, server_version, (int)file.length());
        return server_data;
    
    }

    // Write byte into file and return current pointer position
    public long write(String path, byte buf[], long offset) throws RemoteException{
        String server_path = server_root+path;
        
        try {
            File file = new File(server_path);
            RandomAccessFile raf = new RandomAccessFile(file, "rw");
            raf.seek(offset);
            raf.write(buf);
            offset = raf.getFilePointer();
            raf.close();
            
            int version = -1;
            if( hmap_version.containsKey(path) ){ version = hmap_version.get(path); }
            hmap_version.put(path, version+1); //update version
        }catch (Exception e){
            System.err.println("WRITE, Exception");
            e.printStackTrace();
        }
        return offset;
    }
    
    // Delete a file
    public int unlink(String path) throws RemoteException{
        System.out.println("UNLINK");
        String server_path = server_root+path;
        try {
            File file = new File(server_path);
            boolean exists = file.exists();
			boolean isDirectory = file.isDirectory(); 
			if (isDirectory){ System.err.println("UNLINK ERROR, isDirectory"); return -2; } //return Errors.EISDIR;
			if (!exists) { System.err.println("UNLINK ERROR, !exist"); return -3; } //return Errors.ENOENT;

            file.delete();
        }catch (Exception e){
            System.err.println("UNLINK, Exception");
            e.printStackTrace();
        }
        return 0;        
    }

    public static void main(String[] args) throws RemoteException {
        if(args.length != 2) { System.err.println("ERROR, Server should contains 2 args"); return;}

		int port = Integer.parseInt(args[0]);
		server_root = args[1]+"/";
        Server server;

        // create RMI registry
		try {
			LocateRegistry.createRegistry(port);
            server = new Server();
            Naming.rebind(String.format("//127.0.0.1:%d/Server", port), server);
		}catch(Exception e) {
			System.err.println("ERROR, main exception");
            e.printStackTrace();
		}
	}
}
