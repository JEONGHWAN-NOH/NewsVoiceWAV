package com.newsvoice.tts;

import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;

/** Relocate the single moov box in non-fragmented Android muxer output. */
final class Mp4FastStart {
    private static final Set<String> CONTAINERS=Set.of("moov","trak","mdia","minf","stbl");
    static void optimize(File file) throws IOException {
        File temp=new File(file.getParentFile(),file.getName()+".fast");
        try(RandomAccessFile in=new RandomAccessFile(file,"r")) {
            long moov=-1,mdat=-1; int moovSize=0;
            for(long p=0;p<in.length();) {
                in.seek(p);long n=in.readInt()&0xffffffffL;String type=type(in);
                if(n==1)n=in.readLong();else if(n==0)n=in.length()-p;
                if(n<8||n>in.length()-p)throw new IOException("Unsupported MP4 box");
                if(type.equals("moov")){if(moov!=-1||n>32*1024*1024)throw new IOException("Invalid moov");moov=p;moovSize=(int)n;}
                if(type.equals("mdat")){if(mdat!=-1)throw new IOException("Multiple mdat boxes");mdat=p;}
                if(type.equals("moof"))throw new IOException("Fragmented MP4 not supported");
                p+=n;
            }
            if(moov<0||mdat<0)throw new IOException("Missing MP4 metadata");
            if(moov<mdat)return;
            byte[] data=new byte[moovSize];in.seek(moov);in.readFully(data);
            patch(ByteBuffer.wrap(data),0,data.length,mdat,moov,moovSize);
            try(OutputStream out=new BufferedOutputStream(new FileOutputStream(temp))) {
                copy(in,out,0,mdat);out.write(data);copy(in,out,mdat,moov-mdat);copy(in,out,moov+moovSize,in.length()-moov-moovSize);
            }
            if(temp.length()!=in.length())throw new IOException("Fast Start length mismatch");
        }catch(IOException|RuntimeException e){temp.delete();throw e;}
        try{Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);}finally{temp.delete();}
    }
    private static String type(RandomAccessFile in)throws IOException {byte[] b=new byte[4];in.readFully(b);return new String(b,java.nio.charset.StandardCharsets.US_ASCII);}
    private static void patch(ByteBuffer b,int start,int end,long mdat,long moov,int shift)throws IOException {
        for(int p=start;p<end;) {
            if(end-p<8)throw new IOException("Truncated box");int size=b.getInt(p);
            if(size<8||size>end-p)throw new IOException("Invalid child box");
            String t=new String(b.array(),p+4,4,java.nio.charset.StandardCharsets.US_ASCII);
            if(CONTAINERS.contains(t))patch(b,p+8,p+size,mdat,moov,shift);
            if(t.equals("stco")||t.equals("co64")) {
                int width=t.equals("stco")?4:8;if(size<16)throw new IOException("Truncated offsets");
                int count=b.getInt(p+12);if(count<0||count>(size-16)/width)throw new IOException("Invalid offsets");
                for(int i=0;i<count;i++){int at=p+16+i*width;long old=width==4?b.getInt(at)&0xffffffffL:b.getLong(at);
                    if(old<mdat||old>=moov)throw new IOException("Unexpected chunk offset");long next=old+shift;
                    if(width==4){if(next>0xffffffffL)throw new IOException("Offset overflow");b.putInt(at,(int)next);}else b.putLong(at,next);
                }
            }
            p+=size;
        }
    }
    private static void copy(RandomAccessFile in,OutputStream out,long pos,long size)throws IOException {
        in.seek(pos);byte[] b=new byte[65536];while(size>0){int n=(int)Math.min(size,b.length);in.readFully(b,0,n);out.write(b,0,n);size-=n;}
    }
}
