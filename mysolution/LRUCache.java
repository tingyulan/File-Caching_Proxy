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
    public static List<CacheFile> ArrivalSeq=null;

    public LRUCache(int cache_size){
        this.cache_size = cache_size;
        this.available = cache_size;
    }

    public AddFile(String path, int len){
        CacheFile new_file = new CacheFile(path, len);
        available = available-len;
        if (available<0) { Evict(); }
        ArrivalSeq.add(new_file);
    }

    public AddUser(String path){
        for (CacheFile file: ArrivalSeq){
            if (file.path.equals(path)){
                file.users -= 1;
                break;
            }
        }
    }

    public DeleteUser(String path){
        for (CacheFile file: ArrivalSeq){
            if (file.path.equals(path)){
                file.users += 1;
                break;
            }
        }
    }

    public Evict(){
        int idx = 0;
        // while(available<0){
            for(CacheFile file: ArrivalSeq){
                if (file.users==0){
                    available += file.len;
                    CacheFile.remove(idx);
                }
                if (available>=0){ break; }
                idx += 1;
            }
        // }
    }


}