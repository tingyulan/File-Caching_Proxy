/* Sample skeleton for proxy */
// Question 1. dir error 2. autolab bad file (trace) 3. auto lab concur 4. get tests files after each project or checkpoint


import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

class Proxy {
	public static int fd_count = 0;
	public static Map <Integer, RandomAccessFile> hmap_fdraf = new ConcurrentHashMap <Integer, RandomAccessFile> ();
	public static Map <Integer, String> hmap_fdpath = new ConcurrentHashMap <Integer, String> ();
	public static Map <Integer, Integer> hmap_fdpermission = new ConcurrentHashMap <Integer, Integer> ();
	private final static Object fd_lock = new Object();

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

			System.out.println("OPEN, OpenOption:"+o);
			// FileDescriptor fd = null;
			// FileInputStream file = null;
			int ret;
			String permission;  //0:read only, 1:read-write
			RandomAccessFile new_file;

			File file = new File(path);
			boolean exist = file.exists();
			boolean isDirectory = file.isDirectory(); 

			if(o == OpenOption.READ){ permission="r"; }
			else{ permission="rw"; }

			System.err.println("OPEN, exist:"+exist+" permission:"+permission);

			if(exist && o == OpenOption.CREATE_NEW){
				System.err.println("OPEN CREATE_NEW ERROR, file already exists");
				return Errors.EEXIST; // pathname alread exists
			}else if(isDirectory && permission.equals("rw")){  //pathname refers to a directory and the access requeste involved writing (that is, O_WRONLY or O_RDWR is set).
				// && permission.equals("rw")
				System.err.println("OPEN ERROR, try to open a directory with rw permission.");
				return Errors.EISDIR;
			}else if( !exist && (o == OpenOption.READ||o == OpenOption.WRITE) ){
				System.err.println("OPEN ERROR, permission read or write, but file not exists.");
				return Errors.ENOENT; //no entry exists
			}else{
				try{
					if(isDirectory){
						ret = GenerateFd();
						// hmap_fdraf.put(ret, new_file);
						hmap_fdpath.put(ret, path);
						hmap_fdpermission.put(ret, 0);
						return ret;
					}else{
						new_file = new RandomAccessFile(path, permission);
					}
					// RandomAccessFile new_file = new RandomAccessFile(path, permission);
					// ret = fd_count;
					// fd_count++;
					ret = GenerateFd();
					hmap_fdraf.put(ret, new_file);
					hmap_fdpath.put(ret, path);
					if ( permission.equals("r") ){
						hmap_fdpermission.put(ret, 0); //read only
					}else{
						hmap_fdpermission.put(ret, 1);
					}
					System.err.println("OPEN, fd:"+ret+" raf:"+new_file);
					// System.err.println("open test hashmap, fd:"+ret+" raf:"+hmap_fdraf.get(ret));
					// System.err.println("open hashmap"+hmap_fdraf);
					return ret;
				}catch(FileNotFoundException e){
					System.err.println("OPEN, File not Found Error");
					e.printStackTrace();
					return -1;  //?
				}

				// Print whole hashmap
				// hmap_fdraf.entrySet().forEach(entry->{
				// 	System.out.println(entry.getKey() + " " + entry.getValue());  
				// });

			}
			// return ret;
		}

		public int close( int fd ) {
			System.out.println("CLOSE");
			try{
				if(!hmap_fdpath.containsKey(fd)){ System.err.println("CLOSE ERROR, no fd"); return Errors.EBADF; }

				String path = hmap_fdpath.get(fd);
				// int permi = hmap_fdpermission.get(fd);
				// String permission = permi_to_permission(permi);
				File file = new File(path);
				if(!file.exists()) {System.err.println("CLOSE ERROR, file not exists"); return Errors.ENOENT; } // File does not exists
				boolean exists = file.exists();
				boolean isDirectory = file.isDirectory(); 
				if (isDirectory){ System.out.println("WRITE ERROR, isDirectory"); return Errors.EISDIR; }
				if (!exists) { System.out.println("WRITE ERROR, !exists"); return Errors.ENOENT; }

				RandomAccessFile raf = hmap_fdraf.get(fd);
				System.err.println("CLOSE fd:"+fd+" path:"+path+" raf:"+raf);
				// if(raf==null) { System.err.println("CLOSE ERROR, raf==null"); return Errors.EBADF; }  //fd isn't a valid open file descriptor
				raf.close();  //Does not return a value

				RandomAccessFile flg_hmap_fdraf;
				String flg_hmap_fdpath;
				int flg_hmap_fdpermission;
				flg_hmap_fdraf = hmap_fdraf.remove(fd);
				flg_hmap_fdpath = hmap_fdpath.remove(fd);
				flg_hmap_fdpermission = hmap_fdpermission.remove(fd);
				System.err.println("flg_hmap_fdraf:"+flg_hmap_fdraf+" flg_hmap_fdpath:"+flg_hmap_fdpath+" flg_hmap_fdpermission:"+flg_hmap_fdpermission);
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
				raf.write(buf);
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
			// if(path==null) { System.err.println("READ ERROR, IN path==null"); return Errors.EBADF; } //fd is not a valid file descriptor or is not open for writing.
			// int permi = hmap_fdpermission.get(fd);
			// String permission = permi_to_permission(permi);
			// File file = new File(path, permission);

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
		System.out.println("Hello World");
		(new RPCreceiver(new FileHandlingFactory())).run();
	}
}

