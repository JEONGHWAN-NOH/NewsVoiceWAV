package com.newsvoice.tts;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import java.io.*;
import java.util.*;

/** Device checks use a known PCM fixture; vendor TTS voices need a real handset. */
public class LiteBroadcastTest extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            Context c=getTargetContext();
            check(c.getPackageName().equals("com.newsvoice.tts.lite543"),"Separate package");
            try(android.os.ParcelFileDescriptor fd=getUiAutomation().executeShellCommand("pm list packages com.newsvoice.tts.lite");
                InputStream in=new FileInputStream(fd.getFileDescriptor())){
                check(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).contains("package:com.newsvoice.tts.lite"),"v5.42 remains installed");
            }
            MainActivity activity=(MainActivity)startActivitySync(new android.content.Intent(c,MainActivity.class)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            check(AppPrefs.pageMode(c)&&!AppPrefs.keepWav(c),"Lightweight defaults");
            String script="경제 소식\n필리핀의 경제 소식을 전해드립니다.\n===\n사회 소식\n오늘의 사회 소식을 전해드립니다.";
            List<NewsStory> stories=NewsBroadcastSegments.build(script,"");
            installFixture(c,script,false);
            check(NewsImagePack.matches(c,stories),"Pack matches script");
            check(!NewsImagePack.matches(c,NewsBroadcastSegments.build(script+" 변경.","")),"Edited script rejected");
            try{installFixture(c,script,true);throw new AssertionError("Invalid ZIP accepted");}catch(IOException expected){}
            check(NewsImagePack.matches(c,stories),"Failed import preserves old pack");
            Bitmap red=NewsImagePack.load(c,1),green=NewsImagePack.load(c,2);
            check(red!=null&&green!=null&&red.getWidth()==480&&red.getHeight()==160,"Compressed dimensions");
            check(android.graphics.Color.red(red.getPixel(100,50))>200,"Slot one red");
            check(android.graphics.Color.green(green.getPixel(100,50))>200,"Slot two green");
            File voice=OpeningMusicA.ensureInstalled(c);
            WavTools.Piece pcm=WavTools.inspect(voice);
            long frames=pcm.dataLength/((pcm.format[2]&255)*2L);
            List<File> waves=new ArrayList<>();List<List<Long>> cues=new ArrayList<>();
            for(NewsStory story:stories){
                waves.add(voice);
                int n=NewsSentenceTimeline.split(story.audioText()).size();
                List<Long> lengths=new ArrayList<>();
                for(int i=0;i<n;i++)lengths.add(frames/n+(i==n-1?frames%n:0));
                cues.add(lengths);
            }
            File merged=new File(c.getCacheDir(),"lite-test.wav");
            WavTools.concatenate(waves,merged);
            check(WavTools.inspect(merged).dataLength==pcm.dataLength*waves.size(),"WAV preserved");
            NewsCoverMeta meta=NewsCoverMeta.now();
            NewsCardPainter.ScrollPlan plan=NewsCardPainter.prepareScroll(stories.get(0));
            Bitmap first=NewsCardPainter.renderBroadcast(stories.get(0),0,2,meta,0,plan,0,0,red,true,null);
            export(c,first,"Lite-layout.png");first.recycle();red.recycle();green.recycle();
            StringBuilder longBody=new StringBuilder("긴 원고\n");
            for(int i=0;i<100;i++)longBody.append("화면에 표시되는 모든 문장을 빠짐없이 확인합니다. ");
            NewsStory longStory=NewsBroadcastSegments.build(longBody.toString(),"").get(0);
            NewsCardPainter.ScrollPlan longPlan=NewsCardPainter.prepareScroll(longStory);
            int count=NewsSentenceTimeline.split(longStory.audioText()).size();long[] ends=new long[count];
            for(int i=0;i<count;i++)ends[i]=(i+1)*1000000L;
            float last=0;for(long t=0;t<=ends[count-1]+1000000;t+=100000){float offset=longPlan.pageOffset(t,0,ends);check(offset>=last&&offset%960==0,"Monotonic whole pages");last=offset;}
            check(last==((longPlan.lines.size()-1)/10)*960,"Last line reached");
            long ultraSize=0;
            for(int mode=0;mode<3;mode++){
                boolean lite=mode!=2;
                AppPrefs.setLiteVideo(c,lite);AppPrefs.get(c).edit().putBoolean("page_mode",mode==0).commit();
                File video=new File(c.getCacheDir(),"lite-test.mp4");
                NewsVideoComposer.create(c,stories,waves,cues,video,message->{});
                if(mode==0)ultraSize=video.length();
                if(mode==1)check(ultraSize<video.length(),"Ultra MP4 smaller than scroll MP4");
                MediaMetadataRetriever r=new MediaMetadataRetriever();
                try{
                    r.setDataSource(video.getPath());
                    check((lite?"720":"1080").equals(r.extractMetadata(r.METADATA_KEY_VIDEO_WIDTH)),"Width");
                    check((lite?"1280":"1920").equals(r.extractMetadata(r.METADATA_KEY_VIDEO_HEIGHT)),"Height");
                    check("yes".equals(r.extractMetadata(r.METADATA_KEY_HAS_AUDIO)),"Audio track");
                    Bitmap frame=r.getFrameAtTime(8000000,r.OPTION_CLOSEST);
                    check(frame!=null,"Decoded frame");export(c,frame,mode==0?"Lite-720.png":mode==1?"Lite-scroll.png":"Lite-1080.png");frame.recycle();
                    if(mode==0){
                        Bitmap one=r.getFrameAtTime(8000000,r.OPTION_CLOSEST),two=r.getFrameAtTime(15000000,r.OPTION_CLOSEST);
                        check(android.graphics.Color.red(one.getPixel(300,220))>150,"First image in encoded MP4");
                        check(android.graphics.Color.green(two.getPixel(300,220))>150,"Next image in encoded MP4");one.recycle();two.recycle();
                    }
                    android.util.Log.i("NewsVoiceLite",("mode="+mode)+" bytes="+video.length());
                }finally{r.release();}
            }
            AppPrefs.setLiteVideo(c,true);AppPrefs.get(c).edit().putBoolean("page_mode",true).commit();
            NewsImagePack.remove(c);check(NewsImagePack.snapshot(c,stories).isEmpty(),"Missing images safe");
            runOnMainSync(activity::finish);
            result.putString("lite_tests","passed");finish(-1,result);
        }catch(Throwable e){result.putString("error",android.util.Log.getStackTraceString(e));finish(0,result);}
    }
    private static void installFixture(Context c,String script,boolean bad)throws Exception{
        File zip=new File(c.getCacheDir(),"test-pack.zip");
        try(java.util.zip.ZipOutputStream out=new java.util.zip.ZipOutputStream(new FileOutputStream(zip))){
            out.putNextEntry(new java.util.zip.ZipEntry("script.txt"));out.write(script.getBytes(java.nio.charset.StandardCharsets.UTF_8));out.closeEntry();
            for(int i=1;i<=2;i++){
                out.putNextEntry(new java.util.zip.ZipEntry(bad&&i==2?"../escape.webp":String.format(java.util.Locale.US,"news_%03d.webp",i)));
                Bitmap b=Bitmap.createBitmap(960,320,Bitmap.Config.ARGB_8888);b.eraseColor(i==1?android.graphics.Color.RED:android.graphics.Color.GREEN);
                b.compress(Bitmap.CompressFormat.WEBP,80,out);b.recycle();out.closeEntry();
            }
        }
        NewsImagePack.install(c,android.net.Uri.fromFile(zip));
    }
    private static void export(Context c,Bitmap b,String name)throws Exception{
        android.content.ContentValues values=new android.content.ContentValues();
        values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,name);
        values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE,"image/png");
        android.net.Uri uri=c.getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
        try(OutputStream out=c.getContentResolver().openOutputStream(uri)){check(b.compress(Bitmap.CompressFormat.PNG,100,out),"Test preview");}
    }
}
