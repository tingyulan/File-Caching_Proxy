import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
// import java.util.Arrays;

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
	public static int RemoteMaxLen = 100000;  //100000
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

	private static int CopyFile(File source, File dest) throws IOException {
		InputStream in = null;
		OutputStream out = null;
		byte[] buf = new byte[1024];
		int len;
		int total_len = 0;
		try {
			in = new FileInputStream(source);
			out = new FileOutputStream(dest);
			while ((len = in.read(buf)) > 0) {
				total_len += len;
				// out.write(buf, 0, len);
			}
		} finally {
			in.close();
			out.close();
		}
		return total_len;
	}

	private static void mkdirParents(File file){
		while(!file.getParentFile().exists()){
			mkdirParents(file.getParentFile());
			new File(file.getParent()).mkdirs();
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
			// synchronized (FileHandler.class) {
				// path = "largelargefile.txt";
				// path = "slow_write.txt";
				System.out.println("OPEN");
				System.err.println("OPEN, path:"+path);
				System.err.println("OPEN, OpenOption:"+o);
				
				// path is not reference out of root
				String can_path=null, can_path2=null;
				try {
					can_path = new File(cache_root + path).getCanonicalPath();
					can_path2 = new File(cache_root).getCanonicalPath();
					if(!can_path.contains(can_path2)) { 
						System.err.println("OPEN ERROR, Refernce out of root dir");
						return Errors.EPERM;
					}
				} catch (IOException e1) {
					e1.printStackTrace();
					System.err.println("Open ERROR, Canonical Path Error");
				}

				// change path to identical pathname
				path = can_path.substring(can_path2.length()+1, can_path.length());

				// Initial parameters
				int ret=-1, fd=-1;
				RandomAccessFile raf=null;
				byte[] buf = new byte[RemoteMaxLen];
				// int ver = 0;
				// int option = -1;
				// int server_version = -1;
				int offset = 0;
				boolean flg_first = true;
				String cache_path = null;
				File file = null;
				boolean flg_cache_latest_version = false;
				int request_len = -1;
				String permission;

				// switch(o){
				// 	case READ:
				// 		option=READ; break;
				// 	case WRITE:
				// 		option=WRITE; break;
				// 	case CREATE:
				// 		option=CREATE; break;
				// 	case CREATE_NEW:
				// 		option=CREATE_NEW; break;
				// }
				
				
				// Check file len on server side
				// int total_len = -1; 
				ServerData server_lenver=null;
				try { server_lenver = server.GetLengthAndVersion(path); }
				// try{ total_len = server.GetFileLength(path); } //return -2:file not exits, -3:isDirectory
				catch(Exception e) { e.printStackTrace(); }
				// System.err.println("file.GetFileLength:"+ total_len);

				// Deal with Error Messages
				boolean exists = true;
				boolean isDirectory = false;
				if(server_lenver.errno==-2) { exists=false; }
				if(server_lenver.errno==-3) { isDirectory=true; }
				// Set permission
				if(o == OpenOption.READ){ permission="r"; }
        		else{ permission="rw"; }
				// Check Errors
				if(exists && o == OpenOption.CREATE_NEW){
					// pathname alread exists
					System.err.println("OPEN CREATE_NEW ERROR, Errors.EEXIST");
					return Errors.EEXIST;
				}else if(isDirectory && permission.equals("rw")){ 
					// pathname refers to a directory and the access requeste involved writing (that is, O_WRONLY or O_RDWR is set).
					System.err.println("OPEN ERROR, Errors.EISDIR");
					return Errors.EISDIR;
				}else if( !exists && (o == OpenOption.READ||o == OpenOption.WRITE) ){
					// No entry exists
					System.err.println("OPEN ERROR, Errors.ENOENT");
					return Errors.ENOENT;
				}


				fd = GenerateFd();
				System.err.println("OPEN fd:"+fd);

				// Check local version
				int version; // local cache version, -1:no local copy
				if(!hmap_pathversion.containsKey(path)) { version = -1; }
				else{ version = hmap_pathversion.get(path); }
				flg_cache_latest_version = ( server_lenver.version==version );
				System.err.println("server_version:"+server_lenver.version+"  cache_version:"+version);
				
				// This file does not exist on the server, create a file locally.
				if(!exists && (o == OpenOption.CREATE || o == OpenOption.CREATE_NEW)){  //can delete option conditioon
					cache_path = cache_root+path + "*ver_w0";
					file = new File(cache_path);
					mkdirParents(file);

					try {raf = new RandomAccessFile(file, "rw"); }
					catch(Exception e) {e.printStackTrace();}

					hmap_fdraf.put(fd, raf);
					hmap_fdpath.put(fd, cache_path);
					hmap_fddirty.put(fd, true);
					hmap_fdpermission.put(fd, 1); //write
					cache.AddFile(cache_path, 0);
					return fd;
				}

				// File exists on server side. Proxy get latest version
				int total_len = server_lenver.length;
				if(flg_cache_latest_version){
					if(o == OpenOption.READ){ // read-only
						// version = hmap_pathversion.get(path);
						cache_path = cache_root+path + "*ver" + version;
						file = new File(cache_path);
						// mkdirParents(file);
						try {raf = new RandomAccessFile(file, "r"); }
						catch(Exception e) {e.printStackTrace();}

						hmap_fdraf.put(fd, raf);
						hmap_fdpath.put(fd, cache_path);
						hmap_fddirty.put(fd, false);
						hmap_fdpermission.put(fd, 0); //read only
						System.err.println("OPEN  fd:"+fd+" raf:" +raf+ " cache_path:"+cache_path); 
						cache.AddUser(cache_path);
					}else{ // write is possible.
						cache_path = cache_root+path + "*verw" + fd;
						file = new File(cache_path);
						// mkdirParents(file);
						try {raf = new RandomAccessFile(file, "rw"); }
						catch(Exception e) {e.printStackTrace();}

						// version = hmap_pathversion.get(path);
						String latest_version_path = cache_root+path + "*ver" + version;
						// String cache_path = cache_root+path + "*verw" + fd;
						File file_source = new File(latest_version_path);
						// File file = new File(cache_path);

						try {
							int file_len = CopyFile(file_source, file);
							
							hmap_fdraf.put(fd, raf);
							hmap_fdpath.put(fd, cache_path);
							hmap_fddirty.put(fd, false);
							hmap_fdpermission.put(fd, 1); //write
							System.err.println("OPEN(read-write) fd:"+fd+" raf:" +raf+ " cache_path:"+cache_path); 

							cache.AddFile(cache_path, file_len);
						} catch(IOException e){
							System.err.println("OPEN(read-write) WRITE EXCEPTION"); 
							e.printStackTrace();
						}
					}
				} else if(total_len==0){
					if(o == OpenOption.READ){ // read-only
						cache_path = cache_root+path + "*ver" + server_lenver.version;
						file = new File(cache_path);
						mkdirParents(file);
						try {raf = new RandomAccessFile(file, "r"); }
						catch(Exception e) {e.printStackTrace();}

						hmap_fdraf.put(fd, raf);
						hmap_fdpath.put(fd, cache_path);
						hmap_fddirty.put(fd, false);
						hmap_fdpermission.put(fd, 0); //read only
						System.err.println("OPEN  fd:"+fd+" raf:" +raf+ " cache_path:"+cache_path); 
						cache.AddUser(cache_path);
					}else{ // write is possible.
						cache_path = cache_root+path + "*verw" + fd;
						file = new File(cache_path);
						mkdirParents(file);
						try {raf = new RandomAccessFile(file, "rw"); }
						catch(Exception e) {e.printStackTrace();}

						// try {
							hmap_fdraf.put(fd, raf);
							hmap_fdpath.put(fd, cache_path);
							hmap_fddirty.put(fd, false);
							hmap_fdpermission.put(fd, 1); //write
							System.err.println("OPEN(read-write) fd:"+fd+" raf:" +raf+ " cache_path:"+cache_path); 

							cache.AddFile(cache_path, 0);
						// } catch(IOException e){
						// 	System.err.println("OPEN(read-write) WRITE EXCEPTION"); 
						// 	e.printStackTrace();
						// }
					}
				}else { // Server file exits, proxy file is not latest version
					System.err.println("OPEN, Going to request data from server!!");

					while(total_len > 0){
						try{
							if(total_len<=RemoteMaxLen){ request_len = total_len; }
							else{ request_len = RemoteMaxLen; }
							
							ServerData server_data = server.open(path, request_len, offset);
							// server_version = server_data.GetVersion();
							
							buf = server_data.data;
							// System.out.write(buf);
							total_len -= request_len;
							offset += request_len;
							System.err.println("OPEN, buf.length:"+buf.length+"  total_len:"+total_len + " request_len:"+request_len);
						} catch(Exception e){ System.err.println("OPEN EXCEPTION"); }

						if(flg_first){
							if(o == OpenOption.READ){
								cache_path = cache_root+path + "*ver" + server_lenver.version;
								file = new File(cache_path);
								mkdirParents(file);
								try {raf = new RandomAccessFile(file, "rw"); }
								catch(Exception e) {e.printStackTrace();}
							}else{
								cache_path = cache_root+path + "*verw" + fd;
								file = new File(cache_path);
								mkdirParents(file);
								try {raf = new RandomAccessFile(file, "rw"); }
								catch(Exception e) {e.printStackTrace();}
							}
							cache.AddFile(cache_path, 0);
							flg_first=false;
						}

						try {
							raf.write(buf);
							if(total_len==0) {raf.seek(0);}
							System.err.println("OPEN write buf.length:"+buf.length);

							hmap_fdraf.put(fd, raf);
							hmap_fdpath.put(fd, cache_path);
							hmap_fddirty.put(fd, false);
							if(o == OpenOption.READ){
								hmap_pathversion.put(path, server_lenver.version);  //update version
								hmap_fdpermission.put(fd, 0); //read only
							}else{
								hmap_fdpermission.put(fd, 1); //write
							}

							// cache.AddFile(cache_path, buf.length);
							cache.AddLen(cache_path, buf.length);
							System.err.println("OPEN  fd:"+fd+" raf:" +raf+ " cache_path:"+cache_path); 
						} catch(IOException e){
							System.err.println("OPEN WRITE EXCEPTION"); 
							e.printStackTrace();
						}
					}

					// Check cache to delete all older version that is no on refernce to it now
					cache.DeleteOldVersion(path, cache_path);

				}

				// System.err.println("OPEN just before returning"); 
				return fd;
			// }
		}

		public synchronized int close( int fd ) {
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
				boolean chunk=false;
				String path=remove_cache_root(cache_path);
				if (flg_dirty){
					System.err.println("In dirty, fd:"+fd);
					RandomAccessFile raf = hmap_fdraf.get(fd);
					raf.seek(0);
					int file_len = (int)raf.length();
					byte[] buf = new byte[RemoteMaxLen];
					int push_len;
					while(file_len>0) {
						if(file_len>RemoteMaxLen){ push_len = RemoteMaxLen; }
						else{push_len = file_len;}
						file_len -= push_len;

						int buf_len = raf.read(buf, 0, push_len);
						byte[] exact_buf = new byte[buf_len];
						exact_buf = Arrays.copyOfRange(buf, 0, buf_len);
						path = remove_cache_root(cache_path);
						server.write(path, exact_buf, chunk);
						chunk = true;
					}
				}
				
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
				cache.DeleteUser(cache_path);
				// System.err.println("Finish cache.DeleteUser");

				if(flg_dirty){
					// File file_detele = new File(cache_path);
					// file_detele.delete();
					cache.RemoveFileFromProxy(cache_path);
					cache.RemoveFileFromArrivalSeq(cache_path);
				} else if( cache.GetUsersNum(path)==0 ){
					System.err.println("Check fresh cache");
					int proxy_version = hmap_pathversion.get(path);

					int file_version = Integer.parseInt( cache_path.substring(path.length()+1, cache_path.length()) );
					if (file_version<proxy_version){
						cache.RemoveFileFromProxy(cache_path);
						cache.RemoveFileFromArrivalSeq(cache_path);
					}
				}

				return 0;  // close() returns zero on success
			}catch(IOException e){
				System.err.println("CLOSE ERROR");
				e.printStackTrace();
				return Errors.ENOENT;
			}
			// return Errors.ENOSYS;
		}

		public synchronized long write( int fd, byte[] buf ) {
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
			// System.err.println("READ, path:"+path+" exists:"+exists+" isDirectory:"+isDirectory);
			if (isDirectory){ System.err.println("READ ERROR, isDirectory"); return Errors.EISDIR; }
			if (!exists) { System.err.println("READ ERROR, !exist"); return Errors.ENOENT; }

			long ret;
			try{
				RandomAccessFile fd_java = hmap_fdraf.get(fd);
				// System.err.println("READ, fd:"+fd+ " raf:"+fd_java);
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

		public synchronized int unlink( String path ) {
			System.out.println("UNLINK");
			System.err.println("UNLINK, path:"+path);
			int ret=-1;

			try{
				ret = server.unlink(path);
			} catch (Exception e){
				System.err.println("UNLINK, server.unlink exception");
				e.printStackTrace();
			}

			if(ret==-1) { System.err.println("UNLINK ERROR, other errors"); return -1;}
			else if(ret==-2) { System.err.println("UNLINK ERROR, isDirectory"); return Errors.EISDIR; }
			else if(ret==-3) { System.err.println("UNLINK ERROR, !exist"); return Errors.ENOENT; }
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

		(new RPCreceiver(new FileHandlingFactory())).run();
	}
}

