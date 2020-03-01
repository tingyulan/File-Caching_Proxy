// Data tye use to transfer from server to proxy

import java.io.*;

public class ServerData implements Serializable{ 
    public byte[] data;
    public int version=-1;
    public int length=-1;
    public int errno=0;

    public ServerData(byte[] data, int version, int length){
        this.data = data;
        this.version = version;
        this.length = length;
    }

    public ServerData(int version, int length, int errno){
        this.data = new byte[0];
        this.version = version;
        this.length = length;
        this.errno = errno;
    }
}