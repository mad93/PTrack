package cat.ptrack;

import java.io.*;

public final class Streams {
    private Streams() {}
    public static void copy(InputStream in,OutputStream out)throws IOException {byte[] buffer=new byte[16384];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);}
    public static byte[] bytes(InputStream in)throws IOException {ByteArrayOutputStream out=new ByteArrayOutputStream();copy(in,out);return out.toByteArray();}
}
