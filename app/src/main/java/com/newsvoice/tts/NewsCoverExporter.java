package com.newsvoice.tts;

import android.content.Context;
import android.graphics.Bitmap;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Creates a standalone KakaoTalk share cover from the EXACT same rendered
 * cover and NewsCoverMeta as frame zero of the MP4.
 */
public final class NewsCoverExporter {
    private NewsCoverExporter(){ }
    public static File coverFile(File video){
        if(video==null||!video.getName().endsWith(".mp4"))
            throw new IllegalArgumentException("MP4 파일이 필요합니다.");
        return new File(video.getParentFile(),
            video.getName().substring(0,video.getName().length()-4)+"_cover.jpg");
    }
    public static File save(Bitmap cover,File video)
        throws IOException {
        File output=coverFile(video);
        try{
            try(FileOutputStream stream=new FileOutputStream(output)){
                if(!cover.compress(Bitmap.CompressFormat.JPEG,95,stream))
                    throw new IOException("표지 JPG 압축에 실패했습니다.");
                stream.flush();
            }
            if(output.length()<1024)throw new IOException("표지 JPG가 정상적으로 생성되지 않았습니다.");
            return output;
        }catch(IOException|RuntimeException ex){
            output.delete();
            if(ex instanceof IOException)throw (IOException)ex;
            throw new IOException("뉴스 표지 생성 오류",ex);
        }
    }
}
