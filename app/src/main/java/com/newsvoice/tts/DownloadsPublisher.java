package com.newsvoice.tts;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Write exactly once to the public Downloads collection, not an app-private duplicate. */
public final class DownloadsPublisher {
    /** Shared storage folder for every WAV, MP4, JPG and image-credit TXT. */
    public static final String DIRECTORY_NAME = NewsOutputNaming.DIRECTORY_NAME;
    private DownloadsPublisher() {}
    public static Uri publish(Context context,File source,String filename) throws IOException {
        if(!source.isFile()||source.length()<44)throw new IOException("생성된 파일이 없거나 크기가 잘못되었습니다.");
        boolean namedNews=filename.matches(
            "\\d{4}-\\d{2}-\\d{2}_(?:오전6시|오후6시)_필리핀뉴스_\\d{6}(?:_cover|_image_credits|_kakao|_youtube)?\\.(?:wav|mp4|jpg|txt)");
        boolean olderFormat=filename.matches("newsvoice_[a-zA-Z0-9_-]{1,110}\\.(?:wav|mp4|jpg|txt)");
        if(!namedNews && !olderFormat)
            throw new IOException("저장할 파일 이름이 올바르지 않습니다.");
        boolean video=filename.endsWith(".mp4");
        boolean image=filename.endsWith(".jpg");
        boolean text=filename.endsWith(".txt");
        ContentValues values=new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME,filename);
        values.put(MediaStore.Downloads.MIME_TYPE,video?"video/mp4":image?"image/jpeg":text?"text/plain":"audio/wav");
        String dateFolder=NewsOutputNaming.folderFor(filename,java.time.Instant.now());
        values.put(MediaStore.Downloads.RELATIVE_PATH,
            Environment.DIRECTORY_DOWNLOADS+"/"+DIRECTORY_NAME+"/"+dateFolder);
        values.put(MediaStore.Downloads.IS_PENDING,1);
        ContentResolver resolver=context.getContentResolver();
        Uri entry=resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
        if(entry==null)throw new IOException("갤럭시 다운로드 폴더에 파일을 만들지 못했습니다.");
        boolean complete=false;
        try {
            long written=0L;
            try(InputStream from=new FileInputStream(source);
                OutputStream out=resolver.openOutputStream(entry,"w")){
                if(out==null)throw new IOException("다운로드 파일의 쓰기 스트림을 열 수 없습니다.");
                byte[] chunk=new byte[32768];
                int size;
                while((size=from.read(chunk))!=-1) {
                    out.write(chunk,0,size);
                    written+=size;
                }
                out.flush();
            }
            if(written!=source.length())throw new IOException("다운로드 저장 중 파일 크기가 불일치합니다.");
            values.clear();
            values.put(MediaStore.Downloads.IS_PENDING,0);
            if(resolver.update(entry,values,null,null)!=1)
                throw new IOException("다운로드 완료 상태를 기록하지 못했습니다.");
            complete=true;
            return entry;
        }finally {
            if(!complete)resolver.delete(entry,null,null);
        }
    }
}
