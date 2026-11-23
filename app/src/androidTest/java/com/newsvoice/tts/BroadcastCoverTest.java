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
