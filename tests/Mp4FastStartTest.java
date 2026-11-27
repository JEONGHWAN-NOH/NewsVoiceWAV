package com.newsvoice.tts;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
public class Mp4FastStartTest {
    static byte[] box(String type,byte[] data){return ByteBuffer.allocate(8+data.length).putInt(8+data.length).put(type.getBytes(java.nio.charset.StandardCharsets.US_ASCII)).put(data).array();}
    public static void main(String[] args)throws Exception{
        File f=File.createTempFile("fast-start",".mp4");
        byte[] ftyp=box("ftyp",new byte[8]),mdat=box("mdat",new byte[32]);
        byte[] stco=box("stco",ByteBuffer.allocate(12).putInt(0).putInt(1).putInt(ftyp.length+8).array());
        byte[] moov=box("moov",box("trak",box("mdia",box("minf",box("stbl",stco)))));
        try(OutputStream out=new FileOutputStream(f)){out.write(ftyp);out.write(mdat);out.write(moov);}
        Mp4FastStart.optimize(f);byte[] b=Files.readAllBytes(f.toPath());
        if(!new String(b,ftyp.length+4,4).equals("moov"))throw new AssertionError("moov placement");
        if(ByteBuffer.wrap(b).getInt(ftyp.length+moov.length-4)!=ftyp.length+8+moov.length)throw new AssertionError("offset");
        Mp4FastStart.optimize(f);if(!java.util.Arrays.equals(b,Files.readAllBytes(f.toPath())))throw new AssertionError("idempotence");f.delete();System.out.println("Fast Start passed");
    }
}
