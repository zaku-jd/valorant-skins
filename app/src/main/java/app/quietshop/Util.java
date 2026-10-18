package app.quietshop;
import java.util.*;
import java.io.*;
final class Util {
    static <T> Set<T> set(T... values) { return new HashSet<>(Arrays.asList(values)); }
    static Map<String,String> map(String... values) { Map<String,String> out=new HashMap<>(); for(int i=0;i<values.length;i+=2)out.put(values[i],values[i+1]); return out; }
    static byte[] read(InputStream in) throws IOException { ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buf=new byte[8192]; int n; while((n=in.read(buf))!=-1){out.write(buf,0,n); if(out.size()>32*1024*1024)throw new IOException("Response too large.");} return out.toByteArray(); }
}
