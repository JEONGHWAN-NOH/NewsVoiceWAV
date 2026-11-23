package com.newsvoice.tts;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.Html;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;

/**
 * Optional Wikimedia Commons photo lookup for each news item.
 *
 * A successful result is explicitly labeled an illustrative/reference photo;
 * never imply that it depicts the reported event. Missing network access or
 * uncertain rights results in a locally drawn neutral thumbnail instead.
 */
public final class NewsImageFetcher {
    private NewsImageFetcher() {}
    private static final int PHOTO_LIMIT_BYTES=3*1024*1024;
    private static final String TAG="NewsVoiceImages";

    public static final class Photo {
        public final Bitmap bitmap;
        public final String credit,sourceUrl,license,licenseUrl;
        Photo(Bitmap bitmap,String credit,String sourceUrl,String license,String licenseUrl){
            this.bitmap=bitmap;this.credit=credit;this.sourceUrl=sourceUrl;
            this.license=license;this.licenseUrl=licenseUrl;
        }
    }
    static boolean hasOpeningPortrait(android.content.Context context){
        return new java.io.File(context.getFilesDir(),"portrait_opening.webp").isFile() ||
            new java.io.File(context.getFilesDir(),"portrait_common.webp").isFile();
    }
    /** User-approved gallery portrait for opening, anchor comment and closing. */
    public static Photo anchorPortrait(android.content.Context context,NewsStory story){
        if(story==null||story.kind==NewsStory.NEWS)return null;
        String slot=story.kind==NewsStory.OPENING?"opening":
            story.kind==NewsStory.ANCHOR?"anchor":"closing";
        java.io.File file=new java.io.File(context.getFilesDir(),"portrait_"+slot+".webp");
        if(!file.isFile())file=new java.io.File(context.getFilesDir(),"portrait_common.webp");
        if(!file.isFile()){
            // Bundled default is used on a clean install. Gallery selection remains an override.
            try(InputStream in=context.getAssets().open("anchors/portrait_"+slot+".jpg")){
                Bitmap bundled=BitmapFactory.decodeStream(in);
                return bundled==null?null:new Photo(bundled,"기본 내장 아나운서 이미지","","앱 내장 이미지","");
            }catch(IOException e){
                Log.w(TAG,"Bundled anchor image unavailable: "+slot,e);
                return null;
            }
        }
        BitmapFactory.Options opts=new BitmapFactory.Options();
        opts.inJustDecodeBounds=true;
        BitmapFactory.decodeFile(file.getAbsolutePath(),opts);
        opts.inSampleSize=Math.max(1,Math.max(opts.outWidth,opts.outHeight)/1200);
        opts.inJustDecodeBounds=false;
        Bitmap bmp=BitmapFactory.decodeFile(file.getAbsolutePath(),opts);
        return bmp==null?null:new Photo(bmp,"사용자 지정 아나운서 이미지","","개인 등록 이미지","");
    }
    /** Offline gallery image takes precedence over online lookup, indexed by news article. */
    public static Photo localNewsPhoto(android.content.Context context,int articleNumber){
        if(articleNumber<1)return null;
        java.io.File file=new java.io.File(new java.io.File(context.getFilesDir(),"news_images"),
            String.format(Locale.US,"news_%03d.webp",articleNumber));
        if(!file.isFile())return null;
        BitmapFactory.Options opts=new BitmapFactory.Options();
        opts.inJustDecodeBounds=true;
        BitmapFactory.decodeFile(file.getAbsolutePath(),opts);
        opts.inSampleSize=Math.max(1,Math.max(opts.outWidth,opts.outHeight)/1400);
        opts.inJustDecodeBounds=false;
        Bitmap bmp=BitmapFactory.decodeFile(file.getAbsolutePath(),opts);
        return bmp==null?null:new Photo(bmp,"사용자 등록 뉴스 사진","","사용자 제공 이미지","");
    }
    private static String searchTerm(String title){
        // Reference images are illustrative; never claim a current incident photo.
        if(title.contains("광명역")||title.contains("KTX"))return "Gwangmyeong Station South Korea";
        if(title.contains("광명시청")||title.contains("시의회"))return "Gwangmyeong City Hall Gyeonggi";
        if(title.contains("철산"))return "Cheolsan Gwangmyeong";
        if(title.contains("하안"))return "Haan Gwangmyeong";
        if(title.contains("소하"))return "Soha Gwangmyeong";
        if(title.contains("일직"))return "Iljik Gwangmyeong";
        if(title.contains("학온"))return "Gwangmyeong Gyeonggi rural district";
        if(title.contains("광명동"))return "Gwangmyeong-dong South Korea";
        if(title.contains("전통시장")||title.contains("상권"))return "Gwangmyeong traditional market";
        if(title.contains("재개발")||title.contains("재건축")||title.contains("주택"))
            return "Gwangmyeong Gyeonggi residential district";
        if(title.contains("교육")||title.contains("학교"))return "Gwangmyeong Gyeonggi school";
        if(title.contains("지하철")||title.contains("철도"))return "Gwangmyeong Station railway";
        if(title.contains("사고")||title.contains("사건")||title.contains("화재"))
            return ""; // Do not show unrelated photos as if they document an incident.
        return "";
    }
    private static String decodePlain(String html){
        if(html==null)return "";
        return Html.fromHtml(html,Html.FROM_HTML_MODE_COMPACT).toString()
            .replaceAll("\\s+"," ").trim();
    }
    private static String meta(JSONObject info,String key){
        return decodePlain(info.optJSONObject("extmetadata")==null?""
            :info.optJSONObject("extmetadata").optJSONObject(key)==null?""
            :info.optJSONObject("extmetadata").optJSONObject(key).optString("value",""));
    }
    private static boolean allowedLicense(String license){
        String n=license.toLowerCase(Locale.ROOT);
        return n.equals("cc0")||n.contains("public domain")||n.equals("pd")
            ||n.startsWith("cc by ")||n.startsWith("cc by-sa ");
    }
    private static HttpURLConnection request(String address,int timeoutMs)throws IOException{
        URL url=new URL(address);
        if(!"https".equalsIgnoreCase(url.getProtocol()))throw new IOException("HTTPS only");
        HttpURLConnection c=(HttpURLConnection)url.openConnection();
        c.setConnectTimeout(timeoutMs);c.setReadTimeout(timeoutMs);
        c.setRequestProperty("User-Agent","NewsVoiceWAV/5.25 (Korean news bulletin; Wikimedia Commons image attribution)");
        c.setRequestProperty("Accept","application/json,image/jpeg,image/png,image/webp");
        c.setInstanceFollowRedirects(true);
        return c;
    }
    private static byte[] readBytes(InputStream input,int cap)throws IOException{
        ByteArrayOutputStream buffer=new ByteArrayOutputStream();
        byte[] chunk=new byte[8192];int n;
        while((n=input.read(chunk))!=-1){
            if(buffer.size()+n>cap)throw new IOException("Image payload too large");
            buffer.write(chunk,0,n);
        }
        return buffer.toByteArray();
    }
    public static Photo fetch(NewsStory story){
        if(story==null||story.kind!=NewsStory.NEWS)return null;
        HttpURLConnection api=null;
        try{
            String subject=searchTerm(story.title+" "+story.preview);
            if(subject.isEmpty())return null;
            String q=URLEncoder.encode("filetype:bitmap "+subject,"UTF-8");
            String endpoint="https://commons.wikimedia.org/w/api.php?action=query"
                +"&generator=search&gsrnamespace=6&gsrlimit=8&gsrsearch="+q
                +"&prop=imageinfo&iiprop=url%7Cextmetadata&iiurlwidth=600"
                +"&format=json&formatversion=2";
            api=request(endpoint,4500);
            if(api.getResponseCode()!=200)return null;
            JSONObject root;
            try(InputStream stream=api.getInputStream()){
                root=new JSONObject(new String(readBytes(stream,600*1024),"UTF-8"));
            }
            JSONArray pages=root.optJSONObject("query")==null?null:
                root.optJSONObject("query").optJSONArray("pages");
            if(pages==null)return null;
            for(int i=0;i<pages.length();i++){
                JSONObject page=pages.optJSONObject(i);
                if(page==null)continue;
                String title=page.optString("title","").toLowerCase(Locale.ROOT);
                if(!(title.endsWith(".jpg")||title.endsWith(".jpeg")||
                    title.endsWith(".png")||title.endsWith(".webp")))continue;
                JSONArray infos=page.optJSONArray("imageinfo");
                if(infos==null||infos.length()==0)continue;
                JSONObject info=infos.optJSONObject(0);
                if(info==null)continue;
                String license=meta(info,"LicenseShortName");
                if(!allowedLicense(license))continue;
                String imageUrl=info.optString("thumburl","");
                if(!imageUrl.startsWith("https://"))continue;
                String origin=info.optString("descriptionurl","");
                if(!origin.startsWith("https://commons.wikimedia.org/"))continue;
                HttpURLConnection img=null;
                try{
                    img=request(imageUrl,5000);
                    if(img.getResponseCode()!=200)continue;
                    String type=img.getContentType();
                    if(type==null||!type.toLowerCase(Locale.ROOT).startsWith("image/"))continue;
                    byte[] bytes;
                    try(InputStream stream=img.getInputStream()){
                        bytes=readBytes(stream,PHOTO_LIMIT_BYTES);
                    }
                    Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
                    if(bitmap==null)continue;
                    String artist=meta(info,"Artist");
                    if(artist.length()>90)artist=artist.substring(0,90)+"…";
                    String licenseUrl=meta(info,"LicenseUrl");
                    return new Photo(bitmap,artist,origin,license,licenseUrl);
                }catch(Exception e){
                    Log.w(TAG,"Image candidate unavailable",e);
                }finally{if(img!=null)img.disconnect();}
            }
        }catch(Exception e){Log.w(TAG,"Using neutral thumbnail; photo lookup unavailable",e);}
        finally{if(api!=null)api.disconnect();}
        return null;
    }
}
