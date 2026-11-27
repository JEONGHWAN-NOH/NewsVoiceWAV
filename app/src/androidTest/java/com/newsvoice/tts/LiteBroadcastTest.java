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
            check(c.getPackageName().equals("com.newsvoice.tts.v63"),"Separate package");
            MainActivity activity=(MainActivity)startActivitySync(new android.content.Intent(c,MainActivity.class)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            check(AppPrefs.pageMode(c)&&!AppPrefs.keepWav(c),"Lightweight defaults");
            String script="경제 소식\n필리핀의 경제 소식을 전해드립니다.\n===\n사회 소식\n오늘의 사회 소식을 전해드립니다.";
            List<NewsStory> stories=NewsBroadcastSegments.build(script,"",true);
            android.net.Uri intro=fixturePhoto(c,"intro",android.graphics.Color.BLUE,360,480);
            android.net.Uri outro=fixturePhoto(c,"outro",android.graphics.Color.MAGENTA,480,240);
            NewsImagePack.replace(c,0,intro,"");
            NewsImagePack.replace(c,9,outro,"");
            android.net.Uri selectedCover=fixturePhoto(c,"cover",android.graphics.Color.YELLOW,720,960);
            NewsImagePack.replace(c,NewsImagePack.COVER_SLOT,selectedCover,"");
            Bitmap coverPhoto=NewsImagePack.load(c,NewsImagePack.COVER_SLOT);
            check(coverPhoto.getWidth()==720&&coverPhoto.getHeight()==960,"Cover preserves original proportions");
            Bitmap coverFrame=NewsCardPainter.renderCover(c,2,NewsCoverMeta.now(),coverPhoto);
            check(closeColor(coverFrame.getPixel(540,960),android.graphics.Color.YELLOW),"Custom cover rendered");
            check(!closeColor(coverFrame.getPixel(540,10),android.graphics.Color.YELLOW),"Cover letterboxed without cropping");
            coverFrame.recycle();coverPhoto.recycle();
            StringBuilder eight=new StringBuilder();
            for(int i=1;i<=8;i++)eight.append(i==1?"":"\n===\n").append("기사 "+i+"\n기사 내용입니다.");
            for(int i=1;i<=8;i++)NewsImagePack.replace(c,i,intro,eight.toString());
            NewsImagePack.replace(c,4,outro,eight.toString());
            for(int i=1;i<=8;i++){
                Bitmap b=NewsImagePack.load(c,i);
                check(b.getWidth()==(i==4?480:360),"Only fourth picture replaced");
                b.recycle();
            }
            // A one-article parser result no longer prevents registering slots 2–8.
            NewsImagePack.remove(c);
            for(int i=1;i<=8;i++)NewsImagePack.replace(c,i,intro,"첫 기사\n내용입니다.");
            check(NewsImagePack.count(c)==8,"All slots registered despite a short draft");
            NewsImagePack.replace(c,4,outro,eight.toString()+" 변경했습니다.");
            check(NewsImagePack.count(c)==8,"Editing and replacing one slot preserves the rest");
            List<NewsStory> edited=NewsBroadcastSegments.build(eight.toString()+" 변경했습니다.","",true);
            NewsImagePack.bind(c,edited);
            check(NewsImagePack.snapshot(c,edited).size()==11,"Edited script rebinds all eight and portraits");
            installFixture(c,script,false);
            check(NewsImagePack.matches(c,stories),"Pack matches script");
            check(!NewsImagePack.matches(c,NewsBroadcastSegments.build(script+" 변경.","")),"Edited script rejected");
            try{installFixture(c,script,true);throw new AssertionError("Invalid ZIP accepted");}catch(IOException expected){}
            check(NewsImagePack.matches(c,stories),"Failed import preserves old pack");
            Bitmap red=NewsImagePack.load(c,1),green=NewsImagePack.load(c,2);
            check(red!=null&&green!=null&&red.getWidth()==360&&red.getHeight()==480,"Compressed dimensions");
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
                        Bitmap openingCover=r.getFrameAtTime(1000000L,r.OPTION_CLOSEST);
                        check(openingCover!=null&&closeColor(openingCover.getPixel(openingCover.getWidth()/2,openingCover.getHeight()/2),android.graphics.Color.YELLOW),"Selected cover in MP4 opening");
                        openingCover.recycle();
                        int[] colors={android.graphics.Color.BLUE,android.graphics.Color.RED,android.graphics.Color.GREEN,android.graphics.Color.MAGENTA};
                        for(int i=0;i<colors.length;i++){
                            Bitmap sample=r.getFrameAtTime(8000000L+i*6750000L,r.OPTION_CLOSEST);
                            int pixel=sample.getPixel(sample.getWidth()/2,sample.getHeight()*3/20);
                            check(closeColor(pixel,colors[i]),"Wrong picture in MP4 section "+i);
                            // Portrait image spans x=386..694 and y=108..518 in the 1080 design.
                            if(i==1)for(int x:new int[]{397,683})for(int y:new int[]{119,507}){
                                int corner=sample.getPixel(x*sample.getWidth()/1080,y*sample.getHeight()/1920);
                                check(closeColor(corner,android.graphics.Color.WHITE),"Cropped portrait corner");
                            }
                            sample.recycle();
                        }
                    }
                    android.util.Log.i("NewsVoiceLite",("mode="+mode)+" bytes="+video.length());
                }finally{r.release();}
            }
            for(NewsVideoComposer.Quality q:new NewsVideoComposer.Quality[]{NewsVideoComposer.Quality.KAKAO,NewsVideoComposer.Quality.YOUTUBE}){
                File video=new File(c.getCacheDir(),"dual-"+q.name()+".mp4");
                NewsVideoComposer.create(c,stories,waves,cues,video,message->{},q);
                MediaMetadataRetriever r=new MediaMetadataRetriever();try{
                    r.setDataSource(video.getPath());check(Integer.toString(q.width).equals(r.extractMetadata(r.METADATA_KEY_VIDEO_WIDTH)),"Dual width");
                    check(Integer.toString(q.height).equals(r.extractMetadata(r.METADATA_KEY_VIDEO_HEIGHT)),"Dual height");
                    check("yes".equals(r.extractMetadata(r.METADATA_KEY_HAS_AUDIO)),"Dual audio");
                }finally{r.release();video.delete();}
            }
            Bitmap share=ShareCover.bitmap(c,java.time.Instant.now());check(share!=null,"Approved artwork");share.recycle();
            // Eight distinct colors decoded from the actual rendered MP4 in both formats.
            NewsImagePack.remove(c);
            int[] eightColors={0xffee2222,0xff22ee22,0xff2222ee,0xffeeee22,0xffee22ee,0xff22eeee,0xffee8822,0xff6622ee};
            String mixed="안녕하십니까.\n첫 번째 뉴스입니다. 기사입니다.\n";
            for(int i=2;i<=8;i++)mixed+=(i%2==0?"다음 뉴스입니다.":i+"번 뉴스입니다.")+" 기사입니다.\n";
            List<NewsStory> eightStories=NewsBroadcastSegments.build(mixed,"",true);
            check(NewsBroadcastSegments.newsCount(eightStories)==8,"Eight articles parsed");
            for(int i=1;i<=8;i++)NewsImagePack.replace(c,i,fixturePhoto(c,"slot"+i,eightColors[i-1],360,480),mixed);
            List<File> eightWaves=new ArrayList<>();List<List<Long>> eightCues=new ArrayList<>();
            for(NewsStory story:eightStories){eightWaves.add(voice);int n=NewsSentenceTimeline.split(story.audioText()).size();List<Long> lengths=new ArrayList<>();for(int i=0;i<n;i++)lengths.add(frames/n+(i==n-1?frames%n:0));eightCues.add(lengths);}
            AppPrefs.get(c).edit().putBoolean("page_mode",true).commit();
            for(NewsVideoComposer.Quality q:new NewsVideoComposer.Quality[]{NewsVideoComposer.Quality.KAKAO,NewsVideoComposer.Quality.YOUTUBE}){
                File video=new File(c.getCacheDir(),"eight-"+q.name()+".mp4");
                NewsVideoComposer.create(c,eightStories,eightWaves,eightCues,video,message->{},q);
                MediaMetadataRetriever r=new MediaMetadataRetriever();try{
                    r.setDataSource(video.getPath());
                    for(int i=1;i<=8;i++){
                        Bitmap sample=r.getFrameAtTime(8000000L+i*6750000L,MediaMetadataRetriever.OPTION_CLOSEST);
                        check(sample!=null,"Decoded eight-story frame "+i);
                        check(closeColor(sample.getPixel(sample.getWidth()/2,sample.getHeight()*3/20),eightColors[i-1]),"Wrong picture "+q+" slot "+i);
                        sample.recycle();
                    }
                }finally{r.release();video.delete();}
            }
            AppPrefs.setLiteVideo(c,true);AppPrefs.get(c).edit().putBoolean("page_mode",true).commit();
            NewsImagePack.remove(c);check(NewsImagePack.snapshot(c,stories).size()==3,"Cover and portraits survive article removal");
            NewsImagePack.resetCover(c);check(NewsImagePack.load(c,NewsImagePack.COVER_SLOT)==null,"Default cover restored");
            check(NewsImagePack.snapshot(c,stories).size()==2,"Reset preserves portraits");
            runOnMainSync(activity::finish);
            result.putString("lite_tests","passed");finish(-1,result);
        }catch(Throwable e){result.putString("error",android.util.Log.getStackTraceString(e));finish(0,result);}
    }
    private static boolean closeColor(int a,int b){
        return Math.abs(android.graphics.Color.red(a)-android.graphics.Color.red(b))<65 &&
            Math.abs(android.graphics.Color.green(a)-android.graphics.Color.green(b))<65 &&
            Math.abs(android.graphics.Color.blue(a)-android.graphics.Color.blue(b))<65;
    }
    private static android.net.Uri fixturePhoto(Context c,String name,int color,int w,int h)throws Exception{
        File f=new File(c.getCacheDir(),name+".png");
        Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        b.eraseColor(color);
        try(OutputStream out=new FileOutputStream(f)){b.compress(Bitmap.CompressFormat.PNG,100,out);}
        b.recycle();
        return android.net.Uri.fromFile(f);
    }
    private static void installFixture(Context c,String script,boolean bad)throws Exception{
        File zip=new File(c.getCacheDir(),"test-pack.zip");
        try(java.util.zip.ZipOutputStream out=new java.util.zip.ZipOutputStream(new FileOutputStream(zip))){
            out.putNextEntry(new java.util.zip.ZipEntry("PH-News-20261127-PM-v5.43.txt"));out.write(script.getBytes(java.nio.charset.StandardCharsets.UTF_8));out.closeEntry();
            for(int i=1;i<=2;i++){
                out.putNextEntry(new java.util.zip.ZipEntry(bad&&i==2?"../escape.webp":String.format(java.util.Locale.US,"%02d.webp",i)));
                Bitmap b=Bitmap.createBitmap(360,480,Bitmap.Config.ARGB_8888);b.eraseColor(i==1?android.graphics.Color.RED:android.graphics.Color.GREEN);
                android.graphics.Canvas canvas=new android.graphics.Canvas(b);
                android.graphics.Paint paint=new android.graphics.Paint();paint.setColor(android.graphics.Color.WHITE);
                for(int x:new int[]{0,325})for(int y:new int[]{0,445})canvas.drawRect(x,y,x+35,y+35,paint);
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
