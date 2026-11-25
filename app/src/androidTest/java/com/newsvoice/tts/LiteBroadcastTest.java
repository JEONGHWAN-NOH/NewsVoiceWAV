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
            check(c.getPackageName().equals("com.newsvoice.tts.textlite32"),"Separate package");
            try(android.os.ParcelFileDescriptor fd=getUiAutomation().executeShellCommand("pm list packages com.newsvoice.tts.textlite");
                InputStream in=new FileInputStream(fd.getFileDescriptor())){
                check(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).lines().anyMatch("package:com.newsvoice.tts.textlite"::equals),"v3.0 remains installed");
            }
            MainActivity activity=(MainActivity)startActivitySync(new android.content.Intent(c,MainActivity.class)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            runOnMainSync(()->checkNoImages(activity.getWindow().getDecorView()));
            String raw;
            try (InputStream input = getContext().getAssets().open("v63.txt")) {
                raw = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
            List<NewsStory> all = NewsBroadcastSegments.build(raw);
            check(all.size() == 1 && raw.split("번째 뉴스입니다", -1).length == 13 && raw.trim().endsWith("감사합니다."), "Numbered cues");
            check(NewsBroadcastSegments.audioScript(all).replaceAll("\\s+","").equals(raw.replaceAll("\\s+","")), "Verbatim script");
            List<NewsStory> stories = all;
            File voice=new File(c.getCacheDir(),"spoken.wav");
            try(InputStream in=getContext().getAssets().open("spoken.wav"); OutputStream out=new FileOutputStream(voice)){
                in.transferTo(out);
            }
            WavTools.Piece pcm=WavTools.inspect(voice);
            List<File> waves=Collections.singletonList(voice);
            List<Long> lengths=new ArrayList<>();
            try(Scanner scanner=new Scanner(getContext().getAssets().open("frames.txt"))){
                while(scanner.hasNextLong()) lengths.add(scanner.nextLong());
            }
            List<List<Long>> cues=Collections.singletonList(lengths);
            File merged=new File(c.getCacheDir(),"lite-test.wav");
            WavTools.concatenate(waves,merged);
            check(WavTools.inspect(merged).dataLength==pcm.dataLength*waves.size(),"WAV preserved");
            NewsCoverMeta meta=NewsCoverMeta.now();
            NewsCardPainter.ScrollPlan plan=NewsCardPainter.prepareScroll(stories.get(0));
            Bitmap first=NewsCardPainter.renderScrolling(stories.get(0),0,2,meta,0,plan,0,null);
            check(plan.totalCharacters==NewsSentenceTimeline.visibleLength(raw),"All 12 articles and closing");
            long[] ends=new long[lengths.size()];
            long frames=0;
            for(int i=0;i<ends.length;i++){
                frames+=lengths.get(i);
                ends[i]=6000000+frames*1000000/22050;
            }
            NewsScrollMath.AudioCurve curve=plan.centeredAudioCurve(6000000,ends);
            float previous=0;
            for(long us=0;us<=ends[ends.length-1]+800000;us+=10000){
                float now=curve.delayedPosition(us,700000);
                check(now>=previous && (us>6700000 || now==0),"Continuous 700ms delayed scroll");
                previous=now;
            }
            check(previous==plan.maxOffset,"Closing centered");
            export(c,first,"Lite-layout.png");first.recycle();
            for(boolean lite:new boolean[]{true,false}){
                AppPrefs.setLiteVideo(c,lite);
                File video=new File(c.getCacheDir(),"lite-test.mp4");
                NewsVideoComposer.create(c,stories,waves,cues,video,message->{});
                check(!new File(c.getCacheDir(),"lite-test_cover.jpg").exists(),"No JPG output");
                MediaMetadataRetriever r=new MediaMetadataRetriever();
                try{
                    r.setDataSource(video.getPath());
                    check((lite?"720":"1080").equals(r.extractMetadata(r.METADATA_KEY_VIDEO_WIDTH)),"Width");
                    check((lite?"1280":"1920").equals(r.extractMetadata(r.METADATA_KEY_VIDEO_HEIGHT)),"Height");
                    check("yes".equals(r.extractMetadata(r.METADATA_KEY_HAS_AUDIO)),"Audio track");
                    Bitmap frame=r.getFrameAtTime(7000000,r.OPTION_CLOSEST);
                    check(frame!=null,"Decoded frame");
                    boolean ink=false;
                    for(int y=frame.getHeight()*4/10;y<frame.getHeight()*6/10;y+=2)
                        for(int x=0;x<frame.getWidth();x+=3)
                            ink |= android.graphics.Color.green(frame.getPixel(x,y))>150;
                    check(ink,"Decoded body at center");export(c,frame,lite?"Lite-720.png":"Lite-1080.png");frame.recycle();
                    android.util.Log.i("NewsVoiceLite",(lite?"720p":"1080p")+" bytes="+video.length());
                }finally{r.release();}
            }
            AppPrefs.setLiteVideo(c,true);
            runOnMainSync(activity::finish);
            result.putString("lite_tests","passed");finish(-1,result);
        }catch(Throwable e){result.putString("error",android.util.Log.getStackTraceString(e));finish(0,result);}
    }
    private static void assertNoHeader(Bitmap frame) {
        for (int y=0;y<frame.getHeight()/6;y+=2) {
            for (int x=0;x<frame.getWidth();x+=2)
                check(android.graphics.Color.red(frame.getPixel(x,y))<100,"Unexpected header pixels");
        }
    }
    private static void checkNoImages(android.view.View v){
        if(v instanceof android.widget.TextView){
            String s=((android.widget.TextView)v).getText().toString();
            check(!s.contains("앵커 한마디")&&!s.contains("상단 제목")&&!s.contains("사진 선택")&&!s.contains("표지 JPG")&&!s.contains("이미지 검색"),"Image control: "+s);
        }
        if(v instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)v;for(int i=0;i<g.getChildCount();i++)checkNoImages(g.getChildAt(i));}
    }
    private static void export(Context c,Bitmap b,String name)throws Exception{
        android.content.ContentValues values=new android.content.ContentValues();
        values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,name);
        values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE,"image/png");
        android.net.Uri uri=c.getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
        try(OutputStream out=c.getContentResolver().openOutputStream(uri)){check(b.compress(Bitmap.CompressFormat.PNG,100,out),"Test preview");}
    }
}

