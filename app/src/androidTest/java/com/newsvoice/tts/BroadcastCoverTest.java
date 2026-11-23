package com.newsvoice.tts;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.app.Instrumentation;
import android.os.Bundle;
import java.io.*;
import java.util.Collections;

public class BroadcastCoverTest extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }

    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            verifyPhotoRemoval();
            verifyAnnouncerPhotos();
            importPreviewExportAndRestore();
            result.putString("cover_tests","passed");
            finish(android.app.Activity.RESULT_OK,result);
        } catch(Throwable failure) {
            result.putString("error",android.util.Log.getStackTraceString(failure));
            finish(android.app.Activity.RESULT_CANCELED,result);
        }
    }

    private static void fail(String message) { throw new AssertionError(message); }
    private static void assertTrue(String message,boolean ok) { if(!ok)fail(message); }
    private static void assertTrue(boolean ok) { assertTrue("Assertion failed",ok); }
    private static void assertNotNull(Object value) { assertTrue(value!=null); }
    private static void assertEquals(Object expected,Object actual) {
        assertTrue("Expected "+expected+" but got "+actual,java.util.Objects.equals(expected,actual));
    }
    private static void checkGreen(Bitmap b) {
        assertNotNull(b);
        int color=b.getPixel(b.getWidth()/2,b.getHeight()/2);
        assertTrue("Green cover center: "+Integer.toHexString(color),
            Color.green(color)>180 && Color.red(color)<50 && Color.blue(color)<50);
    }

    private void verifyPhotoRemoval() throws Exception {
        Context context=getTargetContext();
        File dir=new File(context.getFilesDir(),"news_images");
        assertTrue(dir.isDirectory() || dir.mkdir());
        File first=new File(dir,"news_001.webp"),last=new File(dir,"news_012.webp");
        assertTrue(first.createNewFile());
        assertTrue(last.createNewFile());
        MainActivity activity=(MainActivity)startActivitySync(new android.content.Intent(context,MainActivity.class)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        runOnMainSync(()->{
            android.app.AlertDialog dialog=activity.showPhotoRemoval();
            assertEquals(2,dialog.getListView().getCount());
            dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick();
        });
        waitForIdleSync();
        assertTrue(first.exists() && last.exists());
        runOnMainSync(()->{
            android.app.AlertDialog dialog=activity.showPhotoRemoval();
            android.widget.ListView list=dialog.getListView();
            assertEquals("뉴스 12 사진",list.getItemAtPosition(1));
            list.performItemClick(list.getChildAt(1),1,list.getItemIdAtPosition(1));
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
        });
        waitForIdleSync();
        assertTrue(first.exists() && !last.exists());
        assertTrue(first.delete());
        runOnMainSync(()->{ assertTrue(activity.showPhotoRemoval()==null); activity.finish(); });
    }

    private void verifyAnnouncerPhotos() throws Exception {
        Context c=getTargetContext();
        MainActivity activity=(MainActivity)startActivitySync(new android.content.Intent(c,MainActivity.class)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        int[] colors={Color.RED,Color.GREEN,Color.BLUE,Color.YELLOW};
        java.util.List<NewsStory> stories=NewsBroadcastSegments.build("제목\n오늘의 소식입니다.","",true);
        for(int i=0;i<colors.length;i++){
            File input=new File(c.getCacheDir(),"portrait-test.png");
            Bitmap b=Bitmap.createBitmap(48,48,Bitmap.Config.ARGB_8888);
            b.eraseColor(colors[i]);
            try(FileOutputStream out=new FileOutputStream(input)){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,out));}
            b.recycle();
            final int code=5120+i;
            runOnMainSync(()->activity.onActivityResult(code,android.app.Activity.RESULT_OK,
                new android.content.Intent().setData(Uri.fromFile(input))));
            waitForIdleSync();
            for(int j=0;j<stories.size();j++){
                NewsImageFetcher.Photo photo=NewsImageFetcher.anchorPortrait(c,stories.get(j));
                if(j==1){assertTrue(photo==null);continue;}
                int slot=j==0?1:j;
                int expected=colors[slot<=i?slot:0];
                assertNotNull(photo);
                assertColor(expected,photo.bitmap.getPixel(10,10));
                photo.bitmap.recycle();
            }
        }
        java.util.List<NewsImageFetcher.Photo> photos=new java.util.ArrayList<>();
        for(NewsStory story:stories)photos.add(NewsImageFetcher.anchorPortrait(c,story));
        File voice=OpeningMusicA.ensureInstalled(c);
        WavTools.Piece wav=WavTools.inspect(voice);
        long frames=wav.dataLength/(wav.format[2]*2);
        File video=new File(c.getCacheDir(),"portrait-test.mp4");
        NewsVideoComposer.create(c,stories,Collections.nCopies(stories.size(),voice),
            Collections.nCopies(stories.size(),Collections.singletonList(frames)),video,message->{},photos);
        MediaMetadataRetriever retriever=new MediaMetadataRetriever();
        try {
            retriever.setDataSource(video.getPath());
            long duration=Long.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            int matched=0;
            for(long ms=5000;ms<duration && matched<3;ms+=2000){
                Bitmap frame=retriever.getFrameAtTime(ms*1000,MediaMetadataRetriever.OPTION_CLOSEST);
                int pixel=frame.getPixel(frame.getWidth()/3,frame.getHeight()/4);
                if(closeColor(colors[matched+1],pixel))matched++;
                frame.recycle();
            }
            assertEquals(3,matched);
        } finally {retriever.release();video.delete();}
        runOnMainSync(activity::finish);
    }

    private static boolean closeColor(int a,int b){
        return Math.abs(Color.red(a)-Color.red(b))<40 && Math.abs(Color.green(a)-Color.green(b))<40
            && Math.abs(Color.blue(a)-Color.blue(b))<40;
    }
    private static void assertColor(int a,int b){assertTrue("Unexpected portrait "+Integer.toHexString(b),closeColor(a,b));}

    private void importPreviewExportAndRestore() throws Exception {
        Context c=getTargetContext();
        BroadcastCover.restore(c);
        NewsCoverMeta meta=NewsCoverMeta.now();
        Bitmap original=NewsCardPainter.render(c,null,0,1,0,meta);
        File input=new File(c.getCacheDir(),"input-image");
        Bitmap sample=Bitmap.createBitmap(160,90,Bitmap.Config.ARGB_8888);
        sample.eraseColor(Color.GREEN);
        for(Bitmap.CompressFormat format:new Bitmap.CompressFormat[]{Bitmap.CompressFormat.WEBP,
                Bitmap.CompressFormat.JPEG,Bitmap.CompressFormat.PNG}) {
            try(FileOutputStream out=new FileOutputStream(input)) { assertTrue(sample.compress(format,95,out)); }
            BroadcastCover.select(c,Uri.fromFile(input));
            Bitmap preview=NewsCardPainter.render(c,null,0,1,0,meta);
            checkGreen(preview);
            assertEquals(Color.BLACK,preview.getPixel(0,0));
            preview.recycle();
        }
        try(FileOutputStream out=new FileOutputStream(input)) { out.write(1); }
        try { BroadcastCover.select(c,Uri.fromFile(input)); fail("Invalid image accepted"); }
        catch(IOException expected) { }
        File voice=OpeningMusicA.ensureInstalled(c);
        WavTools.Piece wav=WavTools.inspect(voice);
        long frames=wav.dataLength/(wav.format[2]*2);
        assertTrue(AppPrefs.liteVideo(c));
        long liteSize=0;
        for (boolean lite : new boolean[]{true,false}) {
        AppPrefs.setLiteVideo(c,lite);
        File video=new File(c.getCacheDir(),lite?"lite.mp4":"full.mp4");
        NewsVideoComposer.create(c,NewsStory.parse("필리핀 산업 뉴스\n필리핀 산업단지에서 생산한 제품을 마닐라 항구로 운송합니다."),Collections.singletonList(voice),
            Collections.singletonList(Collections.singletonList(frames)),video,message->{});
        checkGreen(BitmapFactory.decodeFile(NewsCoverExporter.coverFile(video).getPath()));
        MediaMetadataRetriever retriever=new MediaMetadataRetriever();
        try {
            retriever.setDataSource(video.getPath());
            assertEquals(lite?"720":"1080", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            assertEquals(lite?"1280":"1920", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            Bitmap card=retriever.getFrameAtTime(8000000,MediaMetadataRetriever.OPTION_CLOSEST);
            android.content.ContentValues values=new android.content.ContentValues();
            values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,lite?"NewsVoice720.jpg":"NewsVoice1080.jpg");
            values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE,"image/jpeg");
            Uri uri=c.getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
            try(OutputStream out=c.getContentResolver().openOutputStream(uri)) {
                assertTrue(card.compress(Bitmap.CompressFormat.JPEG,95,out));
            }
            android.util.Log.i("NewsVoiceSize",(lite?"720p":"1080p")+" bytes="+video.length()
                +" durationMs="+retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            checkGreen(retriever.getFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST));
            checkGreen(retriever.getFrameAtTime(3000000,MediaMetadataRetriever.OPTION_CLOSEST));
            assertEquals("yes",retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO));
        } finally { retriever.release(); }
        if(lite) liteSize=video.length();
        else assertTrue("Lightweight MP4 should be smaller",liteSize<video.length());
        }
        BroadcastCover.restore(c);
        assertTrue(original.sameAs(NewsCardPainter.render(c,null,0,1,0,meta)));
    }
}
