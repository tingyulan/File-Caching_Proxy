import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Arrays;

import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.RemoteException;
import java.rmi.registry.*;
import java.rmi.Naming;
import java.net.MalformedURLException;
import java.rmi.NotBoundException;

// class Cache_Struct{
// 	RandomAccessFile raf;
// 	String path;
// 	Integer permission;
// 	Integer version;
// 	File file;
// }


class Proxy {
	public static final int READ = 0;
    public static final int WRITE = 1;
    public static final int CREATE = 2;
    public static final int CREATE_NEW = 3;
	public static final int VERSION_OFFSET = 2000;

	public static int fd_count = 0;
	public static Map <Integer, RandomAccessFile> hmap_fdraf = new ConcurrentHashMap <Integer, RandomAccessFile> ();
	public static Map <Integer, String> hmap_fdpath = new ConcurrentHashMap <Integer, String> ();
	public static Map <Integer, Integer> hmap_fdpermission = new ConcurrentHashMap <Integer, Integer> ();
	public static Map <Integer, Boolean> hmap_fddirty = new ConcurrentHashMap <Integer, Boolean> ();
	public static Map <String, Integer> hmap_pathversion = new ConcurrentHashMap <String, Integer> ();
	private final static Object fd_lock = new Object();
	public static ServerIntf server = null;
	// public static Cache_Struct[] Cache = new Cache_Struct[1024];
	public static String cache_root;
	public static int cache_size;
	public static LRUCache cache;
	// public static int remain_cache_size;

	private static int GetFdFromRaf(HashMap<Integer, RandomAccessFile> hmap_fdraf, RandomAccessFile raf) {
		Set set = hmap_fdraf.entrySet();
		int fd=0;
		Iterator it = set.iterator();
		while (it.hasNext()) {
			HashMap.Entry entry = (HashMap.Entry) it.next();
			if (entry.getValue().equals(raf)) {
				fd = (int) entry.getKey();
				break;
			}
		}
		return fd;
	}

	private static int GetFdFromPath(HashMap<Integer, String> hmap_fdpath, String path) {
		Set set = hmap_fdpath.entrySet();
		int fd=0;
		Iterator it = set.iterator();
		while (it.hasNext()) {
			HashMap.Entry entry = (HashMap.Entry) it.next();
			if (entry.getValue().equals(path)) {
				fd = (int) entry.getKey();
				break;
			}
		}
		return fd;
	}

	private static String permi_to_permission(int permi){
		String permission;
		if(permi==0){ permission = "r"; }
		else{ permission="rw"; }
		return permission;
	}

	private static String remove_cache_root(String cache_path){
		String path_version = cache_path.substring(cache_root.length(), cache_path.length());
		String[] path = path_version.split("\\*"); 
		return path[0];
	}

	private static void CopyFile(File source, File dest) throws IOException {
		InputStream in = null;
		OutputStream out = null;
		byte[] buf = new byte[1024];
		int len;
		try {
			in = new FileInputStream(source);
			out = new FileOutputStream(dest);
			while ((len = in.read(buf)) > 0) {
				out.write(buf, 0, len);
			}
		} finally {
			in.close();
			out.close();
		}
	}
	
	private static class FileHandler implements FileHandling {
		// public static int fd_count = 0;

		public int GenerateFd() {
			synchronized(fd_lock) {
				fd_count = fd_count+1;
				return (fd_count-1);
			}
		}

		public synchronized int open( String path, OpenOption o ) {
			/* o: READ(read-only), WRITE(read/write), CREATE(read/write, create if needed),
					CREATE_NEW(read/write, but file must not already exist)*/

			System.err.println("OPEN, OpenOption:"+o);
			System.err.println("OPEN, path:"+path);
			int ret=-1, fd=-1;
			// String permission;  //0:read only, 1:read-write
			RandomAccessFile raf;

			byte[] buf = new byte[cache_size];
			int ver = 0;
			int option = -1;
			int server_version = -1;
			// int flg_latest = -1;

			switch(o){
				case READ:
					option=READ;
					break;
				case WRITE:
					option=WRITE;
					break;
				case CREATE:
					option=CREATE;
					break;
				case CREATE_NEW:
					option=CREATE_NEW;
					break;
			}
			
			// boolean first_reference = !hmap_pathversion.containsKey(path);
			// System.err.println("first_reference:"+first_reference);
			int version;
			if(!hmap_pathversion.containsKey(path)) { version = -1; }
			else{ version = hmap_pathversion.get(path); }

			// byte[] version_buf = new byte[cache_size];

			try{
				// version_buf = server.open(path, option, version, cache_size);
				ServerData server_data = server.open(path, option, version, cache_size);
				server_version = server_data.GetVersion();
				buf = server_data.GetData();
				
			} catch(Exception e){
				System.err.println("OPEN EXCEPTION");
			}
			
			fd = GenerateFd();
			if(server_version!=-1 && option==READ){  // Cache does not get latest version. buf is new version
				String cache_path = cache_root+path + "*ver" + server_version;
				File file = new File(cache_path);

				if(!file.getParentFile().exists()) new File(file.getParent()).mkdirs();
				try {
					raf = new RandomAccessFile(file, "rw");
					// System.out.write(buf);
					raf.write(buf);
					raf.seek(0);
					System.err.println("OPEN write buf.length:"+buf.length);

					// fd = GenerateFd();
					// System.err.println("fd:"+ fd); 
					hmap_pathversion.put(path, fd);  //update version
					hmap_fdraf.put(fd, raf);
					hmap_fdpath.put(fd, cache_path);
					hmap_fddirty.put(fd, false);
					if ( option==0 ){
						hmap_fdpermission.put(fd, 0); //read only
					}else{
						hmap_fdpermission.put(fd, 1);
					}

					System.err.println("OPEN  fd:"+fd+" raf:" +raf+ " cache_path:"+cache_path); 
				} catch(IOException e){
					System.err.println("OPEN WRITE EXCEPTION"); 
					e.printStackTrace();
				}
			} else if( option==READ ){ // read-only

				version = hmap_pathversion.get(path);
				String cache_path = cache_root+path + "*ver" + version;
				File file = new File(cache_path);

				try {
					raf = new RandomAccessFile(file, "r");
					
					hmap_fdraf.put(fd, raf);
					hmap_fdpath.put(fd, cache_path);
					hmap_fddirty.put(fd, false);
					hmap_fdpermission.put(fd, 0); //read only
					System.err.println("OPEN  fd:"+fd+" raf:" +raf+ " cache_path:"+cache_path); 
				} catch(IOException e){
					System.err.println("OPEN(read-only) WRITE EXCEPTION"); 
					e.printStackTrace();
				}
			} else if (server_version!=-1){ //fetch new data for write
				String cache_path = cache_root+path + "*ver_w" + server_version;
				File file = new File(cache_path);

				if(!file.getParentFile().exists()) new File(file.getParent()).mkdirs();
				try {
					raf = new RandomAccessFile(file, "rw");
					// System.out.write(buf);
					raf.write(buf);
					raf.seek(0);
					System.err.println("OPEN write buf.length:"+buf.length);

					// fd = GenerateFd();
					// System.err.println("fd:"+ fd); 
					// hmap_pathversion.put(path, fd);  //update version
					hmap_fdraf.put(fd, raf);
					hmap_fdpath.put(fd, cache_path);
					hmap_fddirty.put(fd, false);
					hmap_fdpermission.put(fd, 1); //write

					System.err.println("OPEN  fd:"+fd+" raf:" +raf+ " cache_path:"+cache_path); 
				} catch(IOException e){
					System.err.println("OPEN WRITE EXCEPTION"); 
					e.printStackTrace();
				}
			}else { // got latest version, but is a write RPC. Copy a file in cache.
				version = hmap_pathversion.get(path);
				String latest_version_path = cache_root+path + "*ver" + version;
				String cache_path = cache_root+path + "*ver_w" + fd;
				File file_source = new File(latest_version_path);
				File file = new File(cache_path);

				try {
					CopyFile(file_source, file);
					raf = new RandomAccessFile(file, "rw");
					
					hmap_fdraf.put(fd, raf);
					hmap_fdpath.put(fd, cache_path);
					hmap_fddirty.put(fd, false);
					hmap_fdpermission.put(fd, 1); //write
					System.err.println("OPEN(read-write) fd:"+fd+" raf:" +raf+ " cache_path:"+cache_path); 
				} catch(IOException e){
					System.err.println("OPEN(read-write) WRITE EXCEPTION"); 
					e.printStackTrace();
				}
			}
			
			System.err.println("OPEN just before returning"); 
			return fd;
		}

		public int close( int fd ) {
			System.out.println("CLOSE");
			try{
				if(!hmap_fdpath.containsKey(fd)){ System.err.println("CLOSE ERROR, no fd"); return Errors.EBADF; }

				String cache_path = hmap_fdpath.get(fd);
				File file = new File(cache_path);
				if(!file.exists()) {System.err.println("CLOSE ERROR, file not exists"); return Errors.ENOENT; } // File does not exists
				boolean exists = file.exists();
				boolean isDirectory = file.isDirectory(); 
				if (isDirectory){ System.out.println("WRITE ERROR, isDirectory"); return Errors.EISDIR; }
				if (!exists) { System.out.println("WRITE ERROR, !exists"); return Errors.ENOENT; }

				System.err.println("CLOSE, cache_path:"+ cache_path + " exists:"+ exists + " isDir:"+isDirectory);

				Boolean flg_dirty = hmap_fddirty.get(fd);
				// System.err.println("CLOSE, dirty:"+ flg_dirty);
				if (flg_dirty){
					System.err.println("In dirty");
					RandomAccessFile raf = hmap_fdraf.get(fd);
					raf.seek(0);
					// System.err.println("AAAAAAA");
					byte[] buf = new byte[cache_size];
					// System.err.println("BBBBBBB");
					int buf_len = raf.read(buf);
					// System.err.println("buf_len:"+buf_len);
					byte[] exact_buf = new byte[buf_len];
					// System.err.println("DDDDD");
					exact_buf = Arrays.copyOfRange(buf, 0, buf_len);
					// System.err.println("EEEEE");
					
					// String clean_path = path.substring(cache_root.length(), path.length());
					// String[] clean_path2 = clean_path.split("\\*"); 
					// System.err.println("clean_path:"+ clean_path2);
					// System.err.println("CLOSE clean_path2[0]:"+ clean_path2[0]);
					String path = remove_cache_root(cache_path);
					server.write(path, exact_buf);
				}
				// System.err.println("Hihihihi");
				RandomAccessFile raf = hmap_fdraf.get(fd);
				System.err.println("CLOSE fd:"+fd+" cache_path:"+cache_path+" raf:"+raf);
				raf.close();  //Does not return a value

				RandomAccessFile flg_hmap_fdraf;
				String flg_hmap_fdpath;
				int flg_hmap_fdpermission;
				Boolean flg_hmap_fddirty;
				flg_hmap_fdraf = hmap_fdraf.remove(fd);
				flg_hmap_fdpath = hmap_fdpath.remove(fd);
				flg_hmap_fdpermission = hmap_fdpermission.remove(fd);
				flg_hmap_fddirty = hmap_fddirty.remove(fd);
				// System.err.println("flg_hmap_fdraf:"+flg_hmap_fdraf+" flg_hmap_fdpath:"+flg_hmap_fdpath+" flg_hmap_fdpermission:"+flg_hmap_fdpermission);
				// if(flg_hmap_fdraf==null || flg_hmap_fdpath==null || flg_hmap_fdpermission==null){ System.err.println("CLOSE, remove hmap error"); }

				return 0;  // close() returns zero on success
			}catch(IOException e){
				System.err.println("CLOSE ERROR");
				e.printStackTrace();
				return Errors.ENOENT;
			}
			// return Errors.ENOSYS;
		}

		public long write( int fd, byte[] buf ) {
			System.out.println("WRITE");
			if(!hmap_fdpath.containsKey(fd)){ System.err.println("WRITE ERROR, no fd:"+fd); return Errors.EBADF; }

			// fd should be open
			String path = hmap_fdpath.get(fd);
			if(path==null) { System.err.println("WRITE ERROR, path is null"); return Errors.EBADF; } //fd is not a valid file descriptor or is not open for writing.
			
			int permi = hmap_fdpermission.get(fd);
			if (permi==0){ System.out.println("WRITE ERROR, can not write to read only file"); return Errors.EBADF; }
			
			// path have to exists, and can not be a directory
			File file = new File(path);
			boolean exists = file.exists();
			boolean isDirectory = file.isDirectory(); 
			if (isDirectory){ System.out.println("WRITE ERROR, isDirectory"); return Errors.EISDIR; }
			if (!exists) { System.out.println("WRITE ERROR, !exists"); return Errors.ENOENT; }

			System.err.println("WRITE, path:"+path+" exists:"+exists+" isDirectory:"+isDirectory);

			// int ret;
			try{
				RandomAccessFile raf = hmap_fdraf.get(fd);
				System.err.println("WRITE raf:"+raf+" fd:"+fd);
				raf.write(buf, 0, buf.length);
				hmap_fddirty.put(fd, true);
				System.err.println("WRITE, length:"+ buf.length);
				return buf.length;
			}catch(IOException e){
				System.err.println("WRITE ERROR");
				e.printStackTrace();
				return -1;
			}
		}

		public long read( int fd, byte[] buf ) {
			System.out.println("READ");
			if(!hmap_fdpath.containsKey(fd)){ System.err.println("READ ERROR, no fd"); return Errors.EBADF; }
			
			// fd should be open
			String path = hmap_fdpath.get(fd);
			System.err.println("READ fd:"+fd+" path:"+path);

			// path have to exists, and can not be a directory
			File file = new File(path);
			boolean exists = file.exists();
			boolean isDirectory = file.isDirectory(); 
			System.err.println("READ, path:"+path+" exists:"+exists+" isDirectory:"+isDirectory);
			if (isDirectory){ System.err.println("READ ERROR, isDirectory"); return Errors.EISDIR; }
			if (!exists) { System.err.println("READ ERROR, !exist"); return Errors.ENOENT; }

			long ret;
			try{
				RandomAccessFile fd_java = hmap_fdraf.get(fd);
				System.err.println("READ, fd:"+fd+ " raf:"+fd_java);
				ret = fd_java.read(buf);
				System.err.println("READ, ret:"+ret);
				if (ret==-1){return 0;}  // End of file has been reached
				else {return ret;}
			}catch(IOException e){
				e.printStackTrace();
				return -1;
			}
		}

		// need to return new position
		public long lseek( int fd, long pos, LseekOption o ) {
			System.out.println("LSEEK");
			System.out.println("LSEEK option:"+o);
			long new_pos;
			if(!hmap_fdpath.containsKey(fd)){ System.err.println("LSEEK ERROR, no fd"); return Errors.EBADF; }

			String path = hmap_fdpath.get(fd);
			System.err.println("LSEEk fd:"+fd+" path:"+path);
			// if(path==null) { System.err.println("LSEEK ERROR, IN path==null"); return Errors.EBADF; }

			// int permi = hmap_fdpermission.get(fd);
			// String permission = permi_to_permission(permi);
			File file = new File(path);
			boolean exists = file.exists();
			boolean isDirectory = file.isDirectory(); 
			System.err.println("LSEEK, path:"+path+" exists:"+exists+" isDirectory:"+isDirectory);
			if (isDirectory){ System.err.println("LSEEK ERROR, isDirectory"); return Errors.EISDIR; }
			if (!exists) { System.err.println("LSEEK ERROR, !exist"); return Errors.ENOENT; }		
			
			try{
				RandomAccessFile raf = hmap_fdraf.get(fd);
				if(o == LseekOption.FROM_START){ new_pos=pos; }
				else if(o == LseekOption.FROM_CURRENT){ new_pos = raf.getFilePointer()+pos; }
				else if(o == LseekOption.FROM_END){new_pos = raf.length()-pos;}
				else { return -1; } //error

				raf.seek(new_pos); // no return value, just set pointer to new_pos
				return new_pos;
			}catch(IOException e){
				e.printStackTrace();
				return Errors.EPERM;
			}
		}

		public int unlink( String path ) {
			System.out.println("UNLINK");		

			// int fd = GetFdFromPath(hmap_fdpath, path);
			// int permi = hmap_fdpermission.get(fd);
			// String permission = permi_to_permission(permi);
			File file = new File(path);	
			
			// File file = new File(path);

			boolean exists = file.exists();
			boolean isDirectory = file.isDirectory(); 
			System.err.println("UNLINK, path:"+path+" exists:"+exists+" isDirectory:"+isDirectory);
			if (isDirectory){ System.err.println("UNLINK ERROR, isDirectory"); return Errors.EISDIR; }
			if (!exists) { System.err.println("UNLINK ERROR, !exist"); return Errors.ENOENT; }

			file.delete();  // will also delete the corresponding raf
			String pure_path = remove_cache_root(path);
			try{
				server.unlink(pure_path);
			} catch (Exception e){
				System.err.println("UNLINK, server.unlink exception");
				e.printStackTrace();
			}
			return 0;
		}

		public void clientdone() {
			System.out.println("CLIENTDONE");
			return;
		}

	}
	
	private static class FileHandlingFactory implements FileHandlingMaking {
		public FileHandling newclient() {
			return new FileHandler();
		}
	} 

	public static void main(String[] args) throws IOException {
		if(args.length != 4) {System.err.println("ERROR, Server should contains 4 args"); return;}
		String ip = args[0];
		int port = Integer.parseInt(args[1]);
		String url = String.format("//%s:%d/Server", ip, port);
		cache_root = args[2]+"/";
		cache_size = Integer.parseInt(args[3]);
		cache = new LRUCache(cache_size);
		// remain_cache_size = cache_size;

		System.err.println("url, "+url);
		
		try {
			server =  (ServerIntf) Naming.lookup(url);
		} catch (Exception e) {
			System.err.println("EXCEPTION, " + e.toString());
            e.printStackTrace();
		}

		// String str = server.HelloWorld();
		// System.err.println("hihi, "+str);

		// byte[] buf = new byte[cache_size];
		// buf = server.open("README", 0, 0, cache_size);
		// System.err.println("buf.length:"+buf.length+" buf:"+buf);

		(new RPCreceiver(new FileHandlingFactory())).run();
	}
}

