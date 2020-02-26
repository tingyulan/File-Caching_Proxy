import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import java.rmi.server.UnicastRemoteObject;
import java.rmi.RemoteException;
import java.rmi.registry.*;
import java.rmi.Naming;

import java.net.MalformedURLException;
import java.util.Arrays;

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

    public ServerData open(String path, int o, int proxy_version, int maxlen) throws RemoteException{
        /* o: READ(read-only), WRITE(read/write), CREATE(read/write, create if needed),
					CREATE_NEW(read/write, but file must not already exist)*/
                    
        System.out.println("OPEN");
        String server_path = server_root+path;
        String permission;
        // Boolean flg_first_reference=false;

        File file = new File(server_path);
        boolean exist = file.exists();
		boolean isDirectory = file.isDirectory(); 
        // if(o == OpenOption.READ){ permission="r"; }
        if(o == READ){ permission="r"; }
        else{ permission="rw"; }

        System.err.println("OPEN, exist:"+exist+" permission:"+permission+ " path:"+server_path+" o:"+o);

        // if(exist && o == OpenOption.CREATE_NEW){
        if(exist && o == CREATE_NEW){
            System.err.println("OPEN CREATE_NEW ERROR, file already exists");
            // return Errors.EEXIST; // pathname alread exists
        }else if(isDirectory && permission.equals("rw")){  //pathname refers to a directory and the access requeste involved writing (that is, O_WRONLY or O_RDWR is set).
            System.err.println("OPEN ERROR, try to open a directory with rw permission.");
            // return Errors.EISDIR;
        // }else if( !exist && (o == OpenOption.READ||o == OpenOption.WRITE) ){
        }else if( !exist && (o == READ||o == WRITE) ){
            System.err.println("OPEN ERROR, permission read or write, but file not exists.");
            // return Errors.ENOENT; //no entry exists
        }else {
            try{
                if(!hmap_version.containsKey(path)){
                    hmap_version.put(path, 0);
                }
                int version = hmap_version.get(path);
                System.err.println("OPEN version:"+version);

                // System.err.println("IN OPEN ELSE");
                if( proxy_version!=version ) {
                    RandomAccessFile raf = new RandomAccessFile(server_path, "rw");
                    byte[] buf = new byte[maxlen];
                    int ret = raf.read(buf);
                    raf.close();
                    System.err.println("AOPEN, ret:"+ret);
                    System.err.write(buf);
                    if(ret==-1){ret=0;}
                    byte[] result = new byte[ret];
                    System.err.println("BOPEN, ret:"+ret);
                    System.err.write(result);
                    result = Arrays.copyOfRange(buf, 0, ret);

                    System.err.println("result.length:"+result.length);
                    
                    ServerData server_data = new ServerData(result, version);
                    // return version + "*@#*@#"  + result;

                    System.err.println("server_data.GetVersion():" + server_data.GetVersion());
                    System.out.write(server_data.GetData());

                    return server_data;
                } else {
                    // byte[] result = "**YouGetTheLatestVersion".getBytes();
                    byte[] result = new byte[0];
                    ServerData server_data = new ServerData(result, -1); //latest version
                    return server_data;
                    // return result;
                }
            }catch(IOException e){
                System.err.println("OPEN, IOException");
                e.printStackTrace();
                // return -1;
            }
        }

        byte[] result = new byte[0];
        ServerData server_data = new ServerData(result, -2); //latest version
        return server_data;
        // return result;
    }

    public boolean write(String path, byte buf[]) throws RemoteException{
        System.out.println("OPEN");
        String server_path = server_root+path;
        
        try {
            File file = new File(server_path);
            RandomAccessFile raf = new RandomAccessFile(file, "rw");
            System.err.println("WRITE, buf.length:"+buf.length+" ");
            raf.write(buf);
            raf.close();
            int version = hmap_version.get(path);
            hmap_version.put(path, version+1);
        }catch (Exception e){
            System.err.println("WRITE, Exception");
            e.printStackTrace();
        }
        return true;
    }
    
    public boolean unlink(String path) throws RemoteException{
        System.out.println("UNLINK");
        String server_path = server_root+path;
        try {
            File file = new File(server_path);
            boolean exists = file.exists();
			boolean isDirectory = file.isDirectory(); 
			System.err.println("UNLINK, path:"+path+" exists:"+exists+" isDirectory:"+isDirectory);
			if (isDirectory){ System.err.println("UNLINK ERROR, isDirectory");} //return Errors.EISDIR;
			if (!exists) { System.err.println("UNLINK ERROR, !exist");} //return Errors.ENOENT;

            file.delete();
        }catch (Exception e){
            System.err.println("UNLINK, Exception");
            e.printStackTrace();
        }
        return true;        
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
