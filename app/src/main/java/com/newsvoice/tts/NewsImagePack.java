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
        for(int slot:new int[]{OPENING_SLOT,CLOSING_SLOT,COVER_SLOT}){
            Bitmap portrait=load(c,slot);
            if(portrait!=null)images.put(slot,portrait);
        }
        return images;
    }
    public static synchronized boolean matches(Context c,List<NewsStory> stories){
        try{return new String(Files.readAllBytes(new File(folder(c),"fingerprint.txt").toPath()),StandardCharsets.UTF_8).equals(fingerprint(stories));}
        catch(Exception e){return false;}
    }
    public static synchronized int count(Context c){
        int n=0;for(int i=1;i<=8;i++)if(photoFile(folder(c),i).isFile())n++;return n;
    }
    public static synchronized String missing(Context c,int total){
        StringBuilder result=new StringBuilder();for(int i=1;i<=Math.min(8,total);i++)if(!photoFile(folder(c),i).isFile()){
            if(result.length()>0)result.append(", ");result.append(i);
        }return result.toString();
    }
    public static synchronized void bind(Context c,List<NewsStory> stories)throws Exception{
        File dir=folder(c);if(!dir.exists()&&!dir.mkdirs())throw new IOException("사진 폴더 생성 실패");
        File pending=File.createTempFile("fingerprint-",".tmp",dir);
        try{Files.write(pending.toPath(),fingerprint(stories).getBytes(StandardCharsets.UTF_8));Files.move(pending.toPath(),new File(dir,"fingerprint.txt").toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{pending.delete();}
    }
    public static final int OPENING_SLOT=0, CLOSING_SLOT=9, COVER_SLOT=10;
    private static File photoFile(File base,int number){
        return new File(base,String.format(Locale.US,"news_%03d.webp",number));
    }
    private static boolean portrait(int slot){return slot==OPENING_SLOT||slot==CLOSING_SLOT||slot==COVER_SLOT;}
    public static int slotFor(NewsStory story){
        if(story.kind==NewsStory.OPENING)return OPENING_SLOT;
        if(story.kind==NewsStory.CLOSING)return CLOSING_SLOT;
        return story.articleNumber<=8?story.articleNumber:-1;
    }
    public static synchronized Bitmap load(Context c,int slot){
        if(slot<0||slot>COVER_SLOT)return null;
        File file=photoFile(portrait(slot)?c.getFilesDir():folder(c),slot);
        return file.isFile()?BitmapFactory.decodeFile(file.getPath()):null;
    }
    public static synchronized void replace(Context c,int slot,Uri uri,String script)throws Exception{
        if(slot<0||slot>COVER_SLOT)throw new IOException("사진 번호 오류");
        // A photo slot is independent of parsing and editing the script.
        List<NewsStory> stories=Collections.emptyList();
        if(!portrait(slot))try{stories=NewsBroadcastSegments.build(script,"");}catch(IllegalArgumentException ignored){}
        Bitmap image=ImageDecoder.decodeBitmap(ImageDecoder.createSource(c.getContentResolver(),uri),
            (decoder,info,source)->{
                int w=info.getSize().getWidth(),h=info.getSize().getHeight();
                float scale=Math.min(1f,(slot==COVER_SLOT?1920f:480f)/Math.max(w,h));
                decoder.setTargetSize(Math.max(1,Math.round(w*scale)),Math.max(1,Math.round(h*scale)));
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            });
        File stage=Files.createTempDirectory(c.getFilesDir().toPath(),"photo-stage").toFile();
        try{
            File imageFile=photoFile(stage,slot);
            save(image,imageFile);
            boolean first=!portrait(slot)&&count(c)==0;
            File dir=portrait(slot)?c.getFilesDir():folder(c);
            if(!dir.exists()&&!dir.mkdirs())throw new IOException("사진 폴더 생성 실패");
            Files.move(imageFile.toPath(),photoFile(dir,slot).toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            if(first&&!stories.isEmpty())bind(c,stories);
        }finally{image.recycle();clear(stage);}
    }
    private static void clear(File dir){File[] files=dir.listFiles();if(files!=null)for(File f:files)f.delete();dir.delete();}
    public static synchronized void remove(Context c){clear(folder(c));}
    public static synchronized void resetCover(Context c)throws IOException{
        Files.deleteIfExists(photoFile(c.getFilesDir(),COVER_SLOT).toPath());
    }
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
                    if(name.matches("0[1-8]\\.webp"))name="news_0"+name;
                    if(name.endsWith(".txt")&&!name.equals("sources.txt"))name="script.txt";
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
            activate(c,stage);
            return script;
        }finally{clear(stage);}
    }
    private static void activate(Context c,File stage)throws IOException{
            File live=folder(c),backup=new File(c.getFilesDir(),"image_pack_backup_"+System.nanoTime());
            boolean old=live.exists();if(old&&!live.renameTo(backup))throw new IOException("기존 자료 보존 실패");
            if(!stage.renameTo(live)){if(old)backup.renameTo(live);throw new IOException("자료 적용 실패");}
            if(old)clear(backup);
    }
    private static void save(Bitmap image,File output)throws IOException{
        try(OutputStream stream=new FileOutputStream(output)){
            if(!image.compress(Bitmap.CompressFormat.WEBP,40,stream))throw new IOException("사진 저장 실패");
        }
    }
    private static void compress(byte[] bytes,File output)throws IOException{
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
        if(options.outWidth<1||options.outHeight<1||options.outWidth>12000||options.outHeight>12000)throw new IOException("잘못된 이미지");
        options.inSampleSize=Math.max(1,Math.max(options.outWidth,options.outHeight)/960);options.inJustDecodeBounds=false;
        Bitmap original=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
        if(original==null)throw new IOException("이미지를 읽을 수 없습니다");
        float scale=Math.min(1f,480f/Math.max(original.getWidth(),original.getHeight()));
        Bitmap resized=Bitmap.createScaledBitmap(original,Math.max(1,Math.round(original.getWidth()*scale)),
            Math.max(1,Math.round(original.getHeight()*scale)),true);
        try{save(resized,output);}finally{if(original!=resized)resized.recycle();original.recycle();}
    }
}
