// Eviction Policy: LRU
import java.io.*;
import java.util.*;

public class LRUCache{

    public class CacheFile {
        public String path;
        public int len;
        public int users=0;
        
        public CacheFile(String path, int len){
            this.path = path;
            this.len = len;
            this.users = 1;
        }
    }

    public static int cache_size;
    public static int available; //remain_cache_size
    public static List<CacheFile> ArrivalSeq = new ArrayList<CacheFile>();

    public LRUCache(int cache_size){
        this.cache_size = cache_size;
        this.available = cache_size;
    }

    public synchronized void AddFile(String path, int len){
        CacheFile new_file = new CacheFile(path, len);
        available = available-len;
        System.out.println("AddFile, available:"+available);
        if (available<0) { Evict(); }
        ArrivalSeq.add(new_file);
    }

    public synchronized void AddUser(String path){
        for (CacheFile file: ArrivalSeq){
            if (file.path.equals(path)){
                file.users -= 1;
                break;
            }
        }
    }

    public synchronized void DeleteUser(String path){
        System.out.println("DeleteUser()");
        for (CacheFile file: ArrivalSeq){
            if (file.path.equals(path)){
                file.users -= 1;
                break;
            }
        }
        System.out.println("Going to finish DeleteUser()");
    }

    public synchronized void Evict(){
        int idx = 0;
        System.out.println("EVICT()");
        // while(available<0){
        
        // for(CacheFile file: ArrivalSeq){
        for(Iterator<CacheFile> fileIterator = ArrivalSeq.iterator(); fileIterator.hasNext();){
            CacheFile file = fileIterator.next();
            if (file.users==0){
                available += file.len;
                System.out.println("Evict, available:"+available);
                RemoveFileFromProxy(file);
                ArrivalSeq.remove(idx);
            }
            if (available>=0){ break; }
            idx += 1;
        }
        // }
    }

    public synchronized void RemoveFileFromProxy(CacheFile victim){
        File file = new File(victim.path);	
			
        // File file = new File(path);

        // boolean exists = file.exists();
        // boolean isDirectory = file.isDirectory(); 
        // System.err.println("UNLINK, path:"+path+" exists:"+exists+" isDirectory:"+isDirectory);
        // if (isDirectory){ System.err.println("UNLINK ERROR, isDirectory"); return Errors.EISDIR; }
        // if (!exists) { System.err.println("UNLINK ERROR, !exist"); return Errors.ENOENT; }

        file.delete();  // will also delete the corresponding raf

    }

    // public GetLength(String path){

    // }

}