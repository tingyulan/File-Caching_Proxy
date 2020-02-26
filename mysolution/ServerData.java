import java.io.*;
// import java.util.*;

public class ServerData implements Serializable{ 
    public byte[] data;
    public int version=-1;

    public ServerData(byte[] data, int version){
        this.data = data;
        this.version = version;
    }

    public byte[] GetData() { return this.data; }
    public int GetVersion() {return this.version; }

}