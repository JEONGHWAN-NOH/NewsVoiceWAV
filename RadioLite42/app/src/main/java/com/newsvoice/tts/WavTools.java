package com.newsvoice.tts;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Chunked RIFF/WAVE merger. Rejects incompatible formats instead of corrupting output. */
public final class WavTools {
    private WavTools() { }
    public static final class Piece {
        final byte[] format;
        final File file;
        final long dataOffset, dataLength;
        Piece(File file, byte[] format, long dataOffset, long dataLength) {
            this.file=file;this.format=format;this.dataOffset=dataOffset;this.dataLength=dataLength;
        }
    }
    private static int readIntLE(RandomAccessFile f) throws IOException {return Integer.reverseBytes(f.readInt());}
    private static void writeIntLE(OutputStream out, long v) throws IOException {
        if(v < 0 || v > 0xffffffffL)throw new IOException("WAV size exceeds 4 GB");
        out.write((int)v&255);out.write((int)(v>>8)&255);out.write((int)(v>>16)&255);out.write((int)(v>>24)&255);
    }
    private static void writeChunkTag(OutputStream out, String tag) throws IOException {out.write(tag.getBytes(StandardCharsets.US_ASCII));}
    public static Piece inspect(File wav) throws IOException {
        try(RandomAccessFile f=new RandomAccessFile(wav,"r")) {
            if(f.length()<44)throw new IOException("WAV too short: " + wav.getName());
            byte[] header=new byte[12];f.readFully(header);
            if(!Arrays.equals(Arrays.copyOfRange(header,0,4),"RIFF".getBytes(StandardCharsets.US_ASCII)) ||
                    !Arrays.equals(Arrays.copyOfRange(header,8,12),"WAVE".getBytes(StandardCharsets.US_ASCII)))throw new IOException("Invalid RIFF/WAVE");
            byte[] fmt=null;long pos=12,dataAt=-1,dataSize=-1;
            while(pos+8<=f.length()){
                f.seek(pos);byte[] tag=new byte[4];f.readFully(tag);
                long size=Integer.toUnsignedLong(readIntLE(f));long payload=pos+8;
                if(size>f.length()-payload)throw new IOException("Truncated WAV chunk");
                String type=new String(tag,StandardCharsets.US_ASCII);
                if(type.equals("fmt ") && fmt==null) {
                    if(size<16 || size>4096)throw new IOException("Unexpected WAV fmt chunk");
                    fmt=new byte[(int)size];f.readFully(fmt);
                } else if(type.equals("data")&&dataAt<0){ dataAt=payload;dataSize=size; }
                pos=payload+size+(size&1L);
            }
            if(fmt==null||dataAt<0||dataSize<=0)throw new IOException("Missing WAV format or audio data");
            int encoding=(fmt[0]&255)|((fmt[1]&255)<<8);
            if(encoding!=1 && encoding!=3)throw new IOException("Unsupported WAV encoding: "+encoding);
            return new Piece(wav,fmt,dataAt,dataSize);
        }
    }
    public static File concatenate(List<File> inputs, File result) throws IOException {
        if(inputs.isEmpty())throw new IOException("No WAV segments");
        List<Piece> parts=new ArrayList<>();long total=0;
        byte[] format=null;
        for(File in:inputs){
            Piece p=inspect(in);
            if(format!=null&&!Arrays.equals(format,p.format))throw new IOException("TTS WAV segment formats differ");
            format=p.format;total+=p.dataLength;parts.add(p);
        }
        if(total > 0xffffffffL - format.length - 36)throw new IOException("Audio exceeds WAV size limit");
        File parent=result.getParentFile();if(parent!=null&&!parent.exists()&&!parent.mkdirs())throw new IOException("Cannot create output directory");
        try(OutputStream out=new BufferedOutputStream(new FileOutputStream(result))){
            long fmtPad=format.length&1L;long dataPad=total&1L;
            writeChunkTag(out,"RIFF");writeIntLE(out,4+8+format.length+fmtPad+8+total+dataPad);
            writeChunkTag(out,"WAVE");writeChunkTag(out,"fmt ");writeIntLE(out,format.length);out.write(format);
            if(fmtPad!=0)out.write(0);
            writeChunkTag(out,"data");writeIntLE(out,total);
            byte[] buf=new byte[32768];
            for(Piece p:parts){
                try(RandomAccessFile f=new RandomAccessFile(p.file,"r")){
                    f.seek(p.dataOffset);long left=p.dataLength;
                    while(left>0){int n=f.read(buf,0,(int)Math.min(buf.length,left));if(n<0)throw new EOFException();out.write(buf,0,n);left-=n;}
                }
            }
            if(dataPad!=0)out.write(0);
        }
        inspect(result);
        return result;
    }
    /** Keep each Android TTS request below its max input length, preferring sentence boundaries. */
    public static List<String> splitText(String text, int maxChars) {
        if(maxChars<50)throw new IllegalArgumentException("maxChars too small");
        List<String> pieces=new ArrayList<>();String remaining=text.trim();
        while(!remaining.isEmpty()){
            if(remaining.length()<=maxChars){pieces.add(remaining);break;}
            int cut=-1;
            for(int i=maxChars-1;i>maxChars/3;i--){
                char c=remaining.charAt(i);
                if(c=='\n'||c==' '||c=='.'||c=='!'||c=='?'||c=='。'){cut=i+1;break;}
            }
            if(cut<1)cut=maxChars;
            String part=remaining.substring(0,cut).trim();if(!part.isEmpty())pieces.add(part);
            remaining=remaining.substring(cut).trim();
        }
        return pieces;
    }
}
