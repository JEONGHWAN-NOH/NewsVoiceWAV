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
            check(c.getPackageName().equals("com.newsvoice.tts.lite"),"Separate package");
            c.getPackageManager().getPackageInfo("com.newsvoice.tts.stable.lite541",0);
            MainActivity activity=(MainActivity)startActivitySync(new android.content.Intent(c,MainActivity.class)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            runOnMainSync(()->checkNoImages(activity.getWindow().getDecorView()));
            List<NewsStory> stories=NewsBroadcastSegments.build("경제 소식\n필리핀의 경제 소식을 전해드립니다.\n===\n사회 소식\n오늘의 사회 소식을 전해드립니다.","");
            check(stories.get(0).articleNumber==1 && stories.get(1).articleNumber==2,"Article order");
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
            Bitmap first=NewsCardPainter.renderScrolling(stories.get(0),0,2,meta,0,plan,0,null);
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
                    Bitmap frame=r.getFrameAtTime(8000000,r.OPTION_CLOSEST);
                    check(frame!=null,"Decoded frame");export(c,frame,lite?"Lite-720.png":"Lite-1080.png");frame.recycle();
                    android.util.Log.i("NewsVoiceLite",(lite?"720p":"1080p")+" bytes="+video.length());
                }finally{r.release();}
            }
            AppPrefs.setLiteVideo(c,true);
            runOnMainSync(activity::finish);
            result.putString("lite_tests","passed");finish(-1,result);
        }catch(Throwable e){result.putString("error",android.util.Log.getStackTraceString(e));finish(0,result);}
    }
    private static void checkNoImages(android.view.View v){
        if(v instanceof android.widget.TextView){
            String s=((android.widget.TextView)v).getText().toString();
            check(!s.contains("사진 선택")&&!s.contains("표지 JPG")&&!s.contains("이미지 검색"),"Image control: "+s);
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
