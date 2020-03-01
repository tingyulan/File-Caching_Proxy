// Implementation of LRU cache replacement policy

import java.io.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList; 

public class LRUCache{
    private static final boolean DEBUG = true;

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

    public static int cache_size; //total cache size
    public static int available; //remain cache size
    public static List<CacheFile> ArrivalSeq = new CopyOnWriteArrayList<CacheFile>(); // Record LRU sequence

    public LRUCache(int cache_size){
        this.cache_size = cache_size;
        this.available = cache_size;
    }

    public synchronized void PrintCache(){
        for (CacheFile file: ArrivalSeq){
            System.out.println(file.path+"  clients:"+file.users);
        }
    }

    // Add a file to cache
    // Would update add file to ArricalSeq and update available
    public synchronized List AddFile(String path, int len){
        CacheFile new_file = new CacheFile(path, len);
        available = available-len;

        List<String> EvictList = new CopyOnWriteArrayList<String>();
        if (available<0) { EvictList = Evict(); }
        ArrivalSeq.add(new_file);

        return EvictList;
    }

    // Update file length in cache
    // This fuction is called for chunking files
    public synchronized List AddLen(String path, int len){
        for (CacheFile file: ArrivalSeq){
            if (file.path.equals(path)){
                file.len += len;
                available -= len;
                break;
            }
        }
        List<String> EvictList = new CopyOnWriteArrayList<String>();
        if (available<0) { EvictList = Evict(); }
        return EvictList;
    }

    // Updata file's reference user number
    // This function is called for read-only user
    public synchronized void AddUser(String path){
        CacheFile renew_file;
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

    // Update file's refernce user number
    // This fuction is called if a user close()
    public synchronized void DeleteUser(String path){
        for (CacheFile file: ArrivalSeq){
            if (file.path.equals(path)){
                file.users -= 1;
                break;
            }
        }
    }

    // Pick victims to delete
    // This fuction would be called if any detection of no enough cache size
    public synchronized List Evict(){
        int idx = 0;
        List<String> victims = new CopyOnWriteArrayList<String>();
        // while(available<0){
        
        for(CacheFile file: ArrivalSeq){
            if (file.users==0){
                victims.add(file.path);
                available += file.len;
            }
            if (available>=0){ break; }
            idx += 1;
        }
        RemoveVictims(victims, false);
        // }
        return victims;
    }

    // Remove victims and update relevant information
    public synchronized void RemoveVictims(List<String> victims, boolean updateLen){
        int i=0;
        int idx;
        for(i=0; i<victims.size(); i++){
            idx = 0;
            for(CacheFile file: ArrivalSeq){
                String path = victims.get(i);
                if (file.path.equals(path)){
                    ArrivalSeq.remove(idx);
                    RemoveFileFromProxy(file.path, updateLen);
                    break;
                }
                idx += 1;
            }
        }
    }

    // Remove a specific file from ArrivalSeq
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

    // Delete a specific file from cache
    public synchronized void RemoveFileFromProxy(String path, boolean updateLen){
        File file = new File(path);
        if (updateLen) { available += file.length(); }	
        file.delete();  // will also delete the corresponding raf
    }

    // Return how many user is currently use a specific file
    public synchronized int GetUsersNum(String path){
        for (CacheFile file: ArrivalSeq){
            if (file.path.equals(path)){
                return file.users;
            }
        }
        return -1;
    }

    public synchronized String PathRemoveVersion(String version_path){
        String[] path_split = version_path.split("\\*");
        String path = path_split[0];
        return path;
    }

    // Delete all older version that no clients using
    public synchronized void DeleteOldVersion(String clean_path, String proxy_latest_cache_path){
        List<String> victims = new CopyOnWriteArrayList<String>();

        int idx;
        for(idx=0; idx<ArrivalSeq.size(); idx++){
            CacheFile file = ArrivalSeq.get(idx);
            String server_no_version_path = PathRemoveVersion(file.path);
            String proxy_no_version_path = PathRemoveVersion(proxy_latest_cache_path);

            if(server_no_version_path.equals(proxy_no_version_path) && file.users==0){
                victims.add(file.path);
            }
        }
        RemoveVictims(victims, true);
    }

}