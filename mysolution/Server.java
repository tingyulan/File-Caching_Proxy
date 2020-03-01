import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import java.rmi.server.UnicastRemoteObject;
import java.rmi.RemoteException;
import java.rmi.registry.*;
import java.rmi.Naming;

import java.net.MalformedURLException;
// import java.util.Arrays;

// OFFSET indicate copy from
// class version_struct{
//     int write_version;
//     int read_version;
// }

public class Server extends UnicastRemoteObject implements ServerIntf {  // ServerInterface
    public static final int READ = 0;
    public static final int WRITE = 1;
    public static final int CREATE = 2;
    public static final int CREATE_NEW = 3;
    public static final int VERSION_OFFSET = 2000;
    // 0:not updated, 1:copy for
    public static Map <String, Integer> hmap_version = new ConcurrentHashMap <String, Integer> ();
    private final static Object lock = new Object();


    public static String server_root;

    protected Server() throws RemoteException { super(0); }

    public String HelloWorld() throws RemoteException{
		return "HelloWrold!!";
	}

    public int GetFileLength(String path) throws RemoteException{
        System.out.println("GetFileLength");

        String server_path = server_root+path;
        System.out.println("server_path:"+server_path);

        File file = new File(server_path);
        if(!file.exists()) { return -2; }
        if(file.isDirectory()) { return -3; }
        
        System.out.println("GetFileLength:"+file.length());
        return (int) file.length();
    }

    // GetLengthAndVersion
    public ServerData GetLengthAndVersion(String path) throws RemoteException{
        System.out.println("GetLengthAndVersion");
        ServerData server_data;

        String server_path = server_root+path;
        System.out.println("server_path:"+server_path);
        

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
        System.err.println("GetFileLength:"+file.length());

        if(!hmap_version.containsKey(path)){
            hmap_version.put(path, 0);
            System.err.println("No this file");
        }
        int server_version = hmap_version.get(path);
        System.err.println("OPEN version:"+server_version);

        server_data = new ServerData(server_version, length, 0);
        return server_data;
        // return (int) file.length();
    }

    public ServerData open(String path, int request_len, int offset) throws RemoteException{
        /* o: READ(read-only), WRITE(read/write), CREATE(read/write, create if needed),
					CREATE_NEW(read/write, but file must not already exist)*/
                    
        System.out.println("OPEN");
        String server_path = server_root+path;
        // String permission;
        File file = new File(server_path);

        // boolean exist = file.exists();
		// boolean isDirectory = file.isDirectory(); 
        
        // if(o == READ){ permission="r"; }
        // else{ permission="rw"; }

        // System.err.println("OPEN, exist:"+exist+" permission:"+permission+ " path:"+server_path+" o:"+o);


        // if(exist && o == CREATE_NEW){
        //     System.err.println("OPEN CREATE_NEW ERROR, file already exists");
        //     byte[] result = new byte[0];
        //     ServerData server_data = new ServerData(result, -2);
        //     return server_data;
        //     //return Errors.EEXIST; // pathname alread exists
        // }else if(isDirectory && permission.equals("rw")){ 
        //     //pathname refers to a directory and the access requeste involved writing (that is, O_WRONLY or O_RDWR is set).
        //     System.err.println("OPEN ERROR, try to open a directory with rw permission.");
        //     byte[] result = new byte[0];
        //     ServerData server_data = new ServerData(result, -3);
        //     return server_data;
        //     //return Errors.EISDIR;
        // }else if( !exist && (o == READ||o == WRITE) ){
        //     System.err.println("OPEN ERROR, permission read or write, but file not exists.");
        //     byte[] result = new byte[0];
        //     ServerData server_data = new ServerData(result, -4);
        //     return server_data;
        //     //return Errors.ENOENT; //no entry exists
        // }else {


            if(!hmap_version.containsKey(path)){
                hmap_version.put(path, 0);
                System.err.println("Add file to record version");
            }
            int server_version = hmap_version.get(path);
            System.err.println("OPEN version:"+server_version);

            // if(proxy_version!=server_version) {
                byte[] buf = new byte[request_len];
                try{
                    RandomAccessFile raf = new RandomAccessFile(server_path, "rw");
                    System.err.println("offset:"+offset+ " request_len:"+request_len);
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
            // }else{
            //     byte[] buf = new byte[0];
            //     ServerData server_data = new ServerData(buf, server_version);
            //     return server_data;
            // }
    }

    // chunk: A flg to inficate chunk or not. 0:normal call, 1:chunk
    public boolean write(String path, byte buf[], boolean chunk) throws RemoteException{
        System.out.println("OPEN");
        String server_path = server_root+path;
        
        try {
            File file = new File(server_path);
            RandomAccessFile raf = new RandomAccessFile(file, "rw");
            System.err.println("WRITE, buf.length:"+buf.length+" ");
            if(chunk){ raf.seek(raf.length()); } //append to file.
            raf.write(buf);
            raf.close();
            
            int version = -1;
            if( hmap_version.containsKey(path) ){ version = hmap_version.get(path); }
            hmap_version.put(path, version+1);
        }catch (Exception e){
            System.err.println("WRITE, Exception");
            e.printStackTrace();
        }
        return true;
    }
    
    public int unlink(String path) throws RemoteException{
        System.out.println("UNLINK");
        String server_path = server_root+path;
        try {
            File file = new File(server_path);
            boolean exists = file.exists();
			boolean isDirectory = file.isDirectory(); 
			System.err.println("UNLINK, path:"+path+" exists:"+exists+" isDirectory:"+isDirectory);
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
        if(args.length != 2) {System.err.println("ERROR, Server should contains 2 args"); return;}

		int port = Integer.parseInt(args[0]);
		server_root = args[1]+"/";
        Server server;

        System.err.println("server_root:"+server_root);

		try {
			//create the RMI registry if it doesn't exist.
			LocateRegistry.createRegistry(port);
            server = new Server();
            Naming.rebind(String.format("//127.0.0.1:%d/Server", port), server);
		}catch(Exception e) {
			System.err.println("ERROR, main exception");
            e.printStackTrace();
		}
	}
}
