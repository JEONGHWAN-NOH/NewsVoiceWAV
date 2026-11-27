package com.newsvoice.tts;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Local, bounded, all-or-nothing image packs. Never performs a network request. */
public final class NewsImagePack {
    private NewsImagePack(){}
    static String fingerprint(List<NewsStory> stories)throws Exception{
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        for(NewsStory s:stories)if(s.kind==NewsStory.NEWS){
            digest.update((s.title+"\n"+s.spokenText()+"\n===\n").getBytes(StandardCharsets.UTF_8));
        }
        StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format(Locale.US,"%02x",b&255));
        return hex.toString();
    }
    private static File folder(Context c){return new File(c.getFilesDir(),"image_pack");}
    public static synchronized Map<Integer,Bitmap> snapshot(Context c,List<NewsStory> stories){
        Map<Integer,Bitmap> images=new HashMap<>();
        if(matches(c,stories))for(int i=1;i<=8;i++){Bitmap b=load(c,i);if(b!=null)images.put(i,b);}
        return images;
    }
    public static synchronized boolean matches(Context c,List<NewsStory> stories){
        try{return new String(Files.readAllBytes(new File(folder(c),"fingerprint.txt").toPath()),StandardCharsets.UTF_8).equals(fingerprint(stories));}
        catch(Exception e){return false;}
    }
    public static synchronized Bitmap load(Context c,int number){
        if(number<1||number>8)return null;
        try{
            File f=new File(folder(c),String.format(Locale.US,"news_%03d.webp",number));
            if(!f.isFile()||f.length()>25600)return null;
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
            BitmapFactory.decodeFile(f.getPath(),bounds);
            if(bounds.outWidth!=480||bounds.outHeight!=160)return null;
            return BitmapFactory.decodeFile(f.getPath());
        }catch(RuntimeException e){return null;}
    }
    private static void clear(File dir){File[] files=dir.listFiles();if(files!=null)for(File f:files)f.delete();dir.delete();}
    public static synchronized void remove(Context c){clear(folder(c));}
    public static synchronized String install(Context c,Uri uri)throws Exception{
        File stage=new File(c.getFilesDir(),"image_pack_stage_"+System.nanoTime());
        if(!stage.mkdir())throw new IOException("임시 자료 폴더 생성 실패");
        String script=null;Set<String> seen=new HashSet<>();long total=0;
        try{
            try(InputStream raw=c.getContentResolver().openInputStream(uri);ZipInputStream zip=new ZipInputStream(raw)){
                ZipEntry entry;int entries=0;
                while((entry=zip.getNextEntry())!=null){
                    if(++entries>24)throw new IOException("ZIP 파일 수 초과");
                    String name=entry.getName();
                    if(entry.isDirectory()||name.contains("/")||name.contains("\\")||name.contains(".."))throw new IOException("ZIP 안에는 파일만 넣어 주세요");
                    boolean text=name.equals("script.txt"),photo=name.matches("news_00[1-8]\\.webp");
                    if(!text&&!photo&&!name.equals("sources.txt"))throw new IOException("지원하지 않는 자료: "+name);
                    if(!seen.add(name))throw new IOException("중복 파일: "+name);
                    ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
                    while((n=zip.read(buf))!=-1){
                        total+=n;if(total>8*1024*1024||bytes.size()+n>(photo?1024*1024:240000))throw new IOException("자료 용량 초과");
                        bytes.write(buf,0,n);
                    }
                    if(text)script=new String(bytes.toByteArray(),StandardCharsets.UTF_8).replace("\uFEFF","").trim();
                    if(photo)compress(bytes.toByteArray(),new File(stage,name));
                }
            }
            if(script==null||script.isEmpty()||script.length()>60000)throw new IOException("script.txt 대본을 확인하세요");
            List<NewsStory> stories=NewsBroadcastSegments.build(script,"",false);
            int count=0;for(NewsStory s:stories)if(s.kind==NewsStory.NEWS)count++;
            if(count<1||count>8)throw new IOException("이미지 방송은 뉴스 1~8건을 지원합니다");
            for(int i=1;i<=count;i++)if(!seen.contains(String.format(Locale.US,"news_%03d.webp",i)))throw new IOException("뉴스 "+i+" 이미지 누락");
            if(seen.stream().filter(x->x.endsWith(".webp")).count()!=count)throw new IOException("대본과 이미지 수가 다릅니다");
            Files.write(new File(stage,"fingerprint.txt").toPath(),fingerprint(stories).getBytes(StandardCharsets.UTF_8));
            File live=folder(c),backup=new File(c.getFilesDir(),"image_pack_backup_"+System.nanoTime());
            boolean old=live.exists();if(old&&!live.renameTo(backup))throw new IOException("기존 자료 보존 실패");
            if(!stage.renameTo(live)){if(old)backup.renameTo(live);throw new IOException("자료 적용 실패");}
            if(old)clear(backup);
            return script;
        }finally{clear(stage);}
    }
    private static void compress(byte[] bytes,File output)throws IOException{
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
        if(options.outWidth<1||options.outHeight<1||options.outWidth>12000||options.outHeight>12000)throw new IOException("잘못된 이미지");
        options.inSampleSize=Math.max(1,Math.max(options.outWidth,options.outHeight)/960);options.inJustDecodeBounds=false;
        Bitmap original=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
        if(original==null)throw new IOException("이미지를 읽을 수 없습니다");
        Bitmap resized=Bitmap.createBitmap(480,160,Bitmap.Config.ARGB_8888);
        try{
            int w=original.getWidth(),h=original.getHeight(),cw=w,ch=h;
            if(w>h*3)cw=h*3;else ch=Math.max(1,w/3);
            new Canvas(resized).drawBitmap(original,new Rect((w-cw)/2,(h-ch)/2,(w+cw)/2,(h+ch)/2),new Rect(0,0,480,160),new Paint(Paint.FILTER_BITMAP_FLAG));
            for(int quality:new int[]{60,50,40,30,20,10}){
                ByteArrayOutputStream encoded=new ByteArrayOutputStream();resized.compress(Bitmap.CompressFormat.WEBP,quality,encoded);
                if(encoded.size()<=25600){Files.write(output.toPath(),encoded.toByteArray());return;}
            }
            throw new IOException("이미지를 25KB 이하로 줄일 수 없습니다");
        }finally{original.recycle();resized.recycle();}
    }
}
