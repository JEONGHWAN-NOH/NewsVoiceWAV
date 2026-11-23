package com.newsvoice.tts;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.util.Collections;

public class BroadcastCoverTest {
    private static void checkGreen(Bitmap b) {
        assertNotNull(b);
        int color=b.getPixel(b.getWidth()/2,b.getHeight()/2);
        assertTrue("Green cover center: "+Integer.toHexString(color),
            Color.green(color)>180 && Color.red(color)<50 && Color.blue(color)<50);
    }

    @Test public void importPreviewExportAndRestore() throws Exception {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
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
        File video=new File(c.getCacheDir(),"test.mp4");
        NewsVideoComposer.create(c,NewsStory.parse("뉴스 제목\n기사 본문입니다."),Collections.singletonList(voice),
            Collections.singletonList(Collections.singletonList(frames)),video,message->{});
        checkGreen(BitmapFactory.decodeFile(NewsCoverExporter.coverFile(video).getPath()));
        MediaMetadataRetriever retriever=new MediaMetadataRetriever();
        try {
            retriever.setDataSource(video.getPath());
            checkGreen(retriever.getFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST));
            checkGreen(retriever.getFrameAtTime(3000000,MediaMetadataRetriever.OPTION_CLOSEST));
            assertEquals("yes",retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO));
        } finally { retriever.release(); }
        BroadcastCover.restore(c);
        assertTrue(original.sameAs(NewsCardPainter.render(c,null,0,1,0,meta)));
    }
}
