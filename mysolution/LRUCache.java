// Eviction Policy: LRU
import java.io.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList; 

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
    // public static List<CacheFile> ArrivalSeq = new ArrayList<CacheFile>();
    public static List<CacheFile> ArrivalSeq = new CopyOnWriteArrayList<CacheFile>();
    // private final static Object lock = new Object();

    public LRUCache(int cache_size){
        this.cache_size = cache_size;
        this.available = cache_size;
    }

    public synchronized void AddFile(String path, int len){
        System.out.println("AddFile");
        CacheFile new_file = new CacheFile(path, len);
        available = available-len;
        System.err.println("AddFile, available:"+available);
        if (available<0) { Evict(); }
        ArrivalSeq.add(new_file);
    }

    public synchronized void AddLen(String path, int len){
        //System.out.println("AddLen");
        for (CacheFile file: ArrivalSeq){
            if (file.path.equals(path)){
                file.len += len;
                available -= len;
                break;
            }
        }
        if (available<0) { Evict(); }
    }

    public synchronized void AddUser(String path){
        System.out.println("AddUser()");

        CacheFile renew_file;
        // for (CacheFile file: ArrivalSeq){
        int idx;
        for(idx=0; idx<ArrivalSeq.size(); idx++){
            CacheFile file = ArrivalSeq.get(idx);
            if (file.path.equals(path)){
                renew_file = new CacheFile(file.path, file.len);
                renew_file.users = file.users+1;
                // file.users += 1;
                ArrivalSeq.remove(idx);
                ArrivalSeq.add(renew_file);
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
        // System.out.println("Going to finish DeleteUser()");
    }

    public synchronized void Evict(){
        int idx = 0;
        System.out.println("EVICT()");
        // while(available<0){
        
        for(CacheFile file: ArrivalSeq){
        // for(Iterator<CacheFile> fileIterator = ArrivalSeq.iterator(); fileIterator.hasNext();){
        //     CacheFile file = fileIterator.next();
            if (file.users==0){
                available += file.len;
                System.err.println("Evict, available:"+available);
                RemoveFileFromProxy(file.path);
                ArrivalSeq.remove(idx);
            }
            if (available>=0){ break; }
            idx += 1;
        }
        // }
    }

    public synchronized void RemoveFileFromArrivalSeq(String path){
        int idx=0;
        for(CacheFile file: ArrivalSeq){
            if (file.path.equals(path)){
                ArrivalSeq.remove(idx);
                break;
            }
            idx += 1;
        }
    }

    public synchronized void RemoveFileFromProxy(String path){
        File file = new File(path);	
        file.delete();  // will also delete the corresponding raf
    }

    public synchronized int GetUsersNum(String path){
        System.out.println("GetUsersNum");
        for (CacheFile file: ArrivalSeq){
            if (file.path.equals(path)){
                return file.users;
            }
        }
        return -1;
    }

    public synchronized void DeleteOldVersion(String clean_path, String latest_path){
        System.out.println("DeleteOldVersion");
        int idx;
        for(idx=0; idx<ArrivalSeq.size(); idx++){
            CacheFile file = ArrivalSeq.get(idx);
            String[] path_array = file.path.split("\\*"); 
            String path = path_array[0];
            if(path.equals(clean_path) && file.users==0){
                RemoveFileFromProxy(file.path);
                ArrivalSeq.remove(idx);
                idx = idx-1;
            }
        }
    }

}