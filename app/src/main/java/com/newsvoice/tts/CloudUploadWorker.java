package com.newsvoice.tts;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import androidx.work.*;
import com.google.android.gms.auth.api.identity.*;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Tasks;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/** User-approved uploads only. Google Play services manages credentials; never persist tokens. */
public final class CloudUploadWorker extends Worker {
    private static final String DRIVE="https://www.googleapis.com/auth/drive.file";
    private static final String YOUTUBE="https://www.googleapis.com/auth/youtube.upload";
    public CloudUploadWorker(Context c,WorkerParameters p){super(c,p);}
    static AuthorizationRequest request(int mode){return AuthorizationRequest.builder().setRequestedScopes(mode==1?java.util.Collections.singletonList(new Scope(DRIVE)):mode==2?java.util.Collections.singletonList(new Scope(YOUTUBE)):Arrays.asList(new Scope(DRIVE),new Scope(YOUTUBE))).build();}
    static void enqueue(Context c,int mode){
        SharedPreferences p=AppPrefs.get(c);
        if(p.getBoolean("upload_active",false))return;
        String drive=p.getString("last_mp4_uri",""),yt=p.getString("last_youtube_uri","");
        if(((mode&1)!=0&&drive.isEmpty())||((mode&2)!=0&&yt.isEmpty())){p.edit().putString("upload_status","영상 제작 후 업로드하세요.").apply();return;}
        p.edit().putBoolean("upload_active",true).putString("upload_status","자동 업로드 대기").apply();
        Data data=new Data.Builder().putInt("mode",mode).putString("drive",drive).putString("yt",yt).build();
        OneTimeWorkRequest job=new OneTimeWorkRequest.Builder(CloudUploadWorker.class).setInputData(data)
            .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build();
        WorkManager.getInstance(c).enqueueUniqueWork("newsvoice_cloud_upload",ExistingWorkPolicy.KEEP,job);
    }
    private SharedPreferences prefs(){return AppPrefs.get(getApplicationContext());}
    private void status(String s){prefs().edit().putString("upload_status",s).apply();}
    @Override public Result doWork(){
        try{
            Context context=getApplicationContext();android.app.NotificationManager nm=(android.app.NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new android.app.NotificationChannel("newsvoice_upload","뉴스 영상 업로드",android.app.NotificationManager.IMPORTANCE_LOW));
            android.app.Notification notification=new android.app.Notification.Builder(context,"newsvoice_upload").setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle("뉴스보이스 자동 업로드").setContentText("영상 전송 중").setOngoing(true).build();
            setForegroundAsync(new ForegroundInfo(6311,notification,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)).get(30,TimeUnit.SECONDS);
            int mode=getInputData().getInt("mode",0);
            AuthorizationResult result=Tasks.await(Identity.getAuthorizationClient(getApplicationContext()).authorize(request(mode)),60,TimeUnit.SECONDS);
            if(result.hasResolution()||result.getAccessToken()==null)throw new IOException("Google 계정 권한을 다시 승인하세요.");
            String token=result.getAccessToken();
            String failures="";
            if((mode&1)!=0)try{status("Drive 업로드 진행 중");String uri=getInputData().getString("drive");if(uri==null||!uri.equals(prefs().getString("last_mp4_uri","")))throw new IOException("영상이 변경됐습니다.");upload(true,uri,token);}catch(Exception e){failures="Drive: "+(e instanceof IOException?e.getMessage():"전송 실패");}
            if((mode&2)!=0)try{status("YouTube 업로드 진행 중");String uri=getInputData().getString("yt");if(uri==null||!uri.equals(prefs().getString("last_youtube_uri","")))throw new IOException("영상이 변경됐습니다.");upload(false,uri,token);}catch(Exception e){failures+=(failures.isEmpty()?"":" · ")+"YouTube: "+(e instanceof IOException?e.getMessage():"전송 실패");}
            if(!failures.isEmpty()){status("일부 전송 실패 · "+failures);return Result.failure();}
            status("업로드 완료 · 영상 주소 확인");return Result.success();
        }catch(Exception e){status("업로드 중단 · "+(e instanceof IOException?e.getMessage():"Google 인증/네트워크 확인 후 재시도"));return Result.failure();}
        finally{prefs().edit().putBoolean("upload_active",false).apply();}
    }
    private void upload(boolean drive,String uriText,String token)throws Exception{
        String prefix=drive?"drive_":"youtube_",linkKey=drive?"kakao_url":"youtube_url";
        SharedPreferences p=prefs();String id=p.getString(prefix+"uploaded_id","");
        if(!uriText.equals(p.getString(prefix+"uploaded_uri","")))id="";
        Uri uri=Uri.parse(uriText);Context ctx=getApplicationContext();long size;
        try(ParcelFileDescriptor fd=ctx.getContentResolver().openFileDescriptor(uri,"r")){if(fd==null||(size=fd.getStatSize())<=0)throw new IOException("영상 파일 크기를 확인할 수 없습니다.");}
        String title="오늘의 종합뉴스 · "+p.getString("last_mp4_name","방송 영상").replace("_kakao.mp4","");
        if(id.isEmpty()){
            String session=p.getString(prefix+"session","");if(!uriText.equals(p.getString(prefix+"session_uri","")))session="";
            long offset=0;
            if(session.isEmpty()){
                JSONObject meta=drive?new JSONObject().put("name",title+".mp4"):new JSONObject().put("snippet",new JSONObject().put("title",title).put("description","오늘의 종합뉴스")).put("status",new JSONObject().put("privacyStatus","unlisted").put("selfDeclaredMadeForKids",false));
                String endpoint=drive?"https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&fields=id":"https://www.googleapis.com/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status";
                HttpURLConnection c=connect(endpoint,"POST",token);c.setRequestProperty("X-Upload-Content-Type","video/mp4");c.setRequestProperty("X-Upload-Content-Length",Long.toString(size));json(c,meta);int code=c.getResponseCode();session=c.getHeaderField("Location");c.disconnect();
                if(code<200||code>=300||session==null)throw new IOException("업로드 시작 실패 (HTTP "+code+")");checkSession(session);
                p.edit().putString(prefix+"session",session).putString(prefix+"session_uri",uriText).commit();
            }else{
                checkSession(session);HttpURLConnection q=connect(session,"PUT",token);q.setRequestProperty("Content-Range","bytes */"+size);q.setFixedLengthStreamingMode(0);q.setDoOutput(true);q.getOutputStream().close();int code=q.getResponseCode();
                if(code==200||code==201)id=readUpload(q,drive);else if(code==308)offset=next(q.getHeaderField("Range"));else{q.disconnect();throw new IOException("기존 업로드 확인 실패 (HTTP "+code+"). 중복 방지를 위해 중단했습니다.");}q.disconnect();
            }
            if(id.isEmpty())try(InputStream in=ctx.getContentResolver().openInputStream(uri)){
                if(in==null)throw new IOException("영상 파일을 읽을 수 없습니다.");long skip=offset;while(skip>0){long n=in.skip(skip);if(n<=0)throw new IOException("영상 재개 위치 오류");skip-=n;}
                byte[] buffer=new byte[1024*1024];
                while(offset<size){if(isStopped())throw new IOException("업로드 중단됨");int len=(int)Math.min(buffer.length,size-offset),pos=0;while(pos<len){int n=in.read(buffer,pos,len-pos);if(n<0)throw new EOFException("영상 파일이 짧습니다.");pos+=n;}
                    HttpURLConnection c=connect(session,"PUT",token);c.setRequestProperty("Content-Type","video/mp4");c.setRequestProperty("Content-Range","bytes "+offset+"-"+(offset+len-1)+"/"+size);c.setFixedLengthStreamingMode(len);c.setDoOutput(true);try(OutputStream out=c.getOutputStream()){out.write(buffer,0,len);}int code=c.getResponseCode();
                    if(code==200||code==201){id=readUpload(c,drive);c.disconnect();break;}if(code!=308){c.disconnect();throw new IOException("업로드 실패 (HTTP "+code+"). 최근 영상 자동 업로드로 재시도하세요.");}long ack=next(c.getHeaderField("Range"));c.disconnect();if(ack!=offset+len)throw new IOException("서버 수신 위치 변경 · 재시도하세요.");offset=ack;status((drive?"Drive":"YouTube")+" 업로드 "+(100*offset/size)+"%");
                }
            }
            if(id.isEmpty())throw new IOException("업로드 완료 응답을 확인하지 못했습니다.");
            if(!id.matches("[A-Za-z0-9_-]+"))throw new IOException("영상 ID 오류");
            p.edit().putString(prefix+"uploaded_id",id).putString(prefix+"uploaded_uri",uriText).remove(prefix+"session").remove(prefix+"session_uri").commit();
        }
        if(!drive&&!"unlisted".equals(p.getString("youtube_privacy_status",""))&&!"public".equals(p.getString("youtube_privacy_status","")))throw new IOException("영상 업로드는 완료됐지만 공개 범위 확인이 필요합니다. YouTube Studio에서 일부 공개로 변경 후 시청 주소를 등록하세요.");
        if(drive){
            HttpURLConnection c=connect("https://www.googleapis.com/drive/v3/files/"+id+"/permissions","POST",token);json(c,new JSONObject().put("type","anyone").put("role","reader"));int code=c.getResponseCode();c.disconnect();if(code<200||code>=300)throw new IOException("Drive 링크 공유 권한 설정 실패 (HTTP "+code+")");
        }
        p.edit().putString(linkKey,drive?"https://drive.google.com/file/d/"+id+"/view":"https://www.youtube.com/watch?v="+id).commit();
    }
    private String readUpload(HttpURLConnection c,boolean drive)throws Exception{JSONObject body=read(c);String id=body.getString("id");if(!drive){JSONObject s=body.optJSONObject("status");prefs().edit().putString("youtube_privacy_status",s==null?"":s.optString("privacyStatus","")).commit();}return id;}
    private static long next(String range)throws IOException{if(range==null)return 0;if(!range.matches("bytes=0-[0-9]+"))throw new IOException("업로드 범위 오류");return Long.parseLong(range.substring(8))+1;}
    private static void checkSession(String url)throws IOException{URL u=new URL(url);if(!"https".equals(u.getProtocol())||!(u.getHost().equals("www.googleapis.com")||u.getHost().equals("upload.youtube.com")||u.getHost().equals("youtube.googleapis.com")))throw new IOException("업로드 주소 확인 실패");}
    private static HttpURLConnection connect(String url,String method,String token)throws IOException{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(30000);c.setReadTimeout(60000);c.setRequestMethod(method);c.setRequestProperty("Authorization","Bearer "+token);return c;}
    private static void json(HttpURLConnection c,JSONObject object)throws IOException{byte[] b=object.toString().getBytes(StandardCharsets.UTF_8);c.setRequestProperty("Content-Type","application/json; charset=UTF-8");c.setFixedLengthStreamingMode(b.length);c.setDoOutput(true);try(OutputStream out=c.getOutputStream()){out.write(b);}}
    private static JSONObject read(HttpURLConnection c)throws Exception{try(InputStream in=c.getInputStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(bytes.size()+n>1024*1024)throw new IOException("응답 크기 초과");bytes.write(buf,0,n);}return new JSONObject(bytes.toString("UTF-8"));}}
}
