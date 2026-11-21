import com.newsvoice.tts.WavTools;
import java.io.*;
import java.util.*;
public class WavToolsTest {
    static void le16(OutputStream out,int v)throws Exception{out.write(v&255);out.write((v>>8)&255);}
    static void le32(OutputStream out,int v)throws Exception{le16(out,v&65535);le16(out,(v>>16)&65535);}
    static void wav(File f,int samples,int sampleRate)throws Exception{
        try(OutputStream out=new FileOutputStream(f)){
            out.write("RIFF".getBytes("US-ASCII"));le32(out,36+samples*2);out.write("WAVEfmt ".getBytes("US-ASCII"));le32(out,16);
            le16(out,1);le16(out,1);le32(out,sampleRate);le32(out,sampleRate*2);le16(out,2);le16(out,16);
            out.write("data".getBytes("US-ASCII"));le32(out,samples*2);
            for(int i=0;i<samples;i++)le16(out,(short)(Math.sin(i/10.0)*15000));
        }
    }
    public static void main(String[] args)throws Exception {
        File d=new File(System.getProperty("java.io.tmpdir"),"nvwavtest_"+System.nanoTime());d.mkdir();
        File a=new File(d,"a.wav"),b=new File(d,"b.wav"),out=new File(d,"merged.wav");
        wav(a,11025,22050);wav(b,22050,22050);
        WavTools.concatenate(Arrays.asList(a,b),out);
        if(out.length()!=44+(11025+22050)*2)throw new AssertionError("wrong merged size: "+out.length());
        if(WavTools.inspect(out)==null)throw new AssertionError("unable to inspect merged WAV");
        List<String> parts=WavTools.splitText("뉴스 원고입니다. 자세한 내용입니다. ".repeat(120),140);
        String together=String.join("",parts).replace(" ","");
        if(!together.equals("뉴스 원고입니다. 자세한 내용입니다. ".repeat(120).replace(" ","")))throw new AssertionError("text lost");
        boolean failed=false;File bad=new File(d,"bad.wav");wav(bad,10,16000);
        try{WavTools.concatenate(Arrays.asList(a,bad),out);}catch(IOException expected){failed=true;}
        if(!failed)throw new AssertionError("must reject incompatible WAV");
        for(File f:d.listFiles())f.delete();d.delete();
        System.out.println("PASS WAV merge, format mismatch rejection, Korean text splitting");
    }
}
