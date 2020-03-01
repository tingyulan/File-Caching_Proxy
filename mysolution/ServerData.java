import java.io.*;
// import java.util.*;

public class ServerData implements Serializable{ 
    public byte[] data = new byte[0];
    public int version=-1;
    public int length=-1;
    public int errno=0;

    public ServerData(byte[] data, int version, int length){
        this.data = data;
        this.version = version;
        this.length = length;
    }

    public ServerData(int version, int length, int errno){
        this.version = version;
        this.length = length;
        this.errno = errno;
    }

    // public byte[] GetData() { return this.data; }
    // public int GetVersion() {return this.version; }
    // public int GetLength() { return this.length; }

}