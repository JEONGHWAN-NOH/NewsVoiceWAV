package com.newsvoice.tts;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.pm.ServiceInfo;
import androidx.work.ForegroundInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Automatic TTS pipeline: HTTPS job fetch -> sequential long-text synthesis ->
 * RIFF/WAVE validation/merge -> phone Downloads -> HTTPS audio upload.
 * Does not require or request accessibility, microphone, or storage permissions.
 */
public final class NewsVoiceWorker extends Worker {
    public static final String INPUT_LOCAL_FILE="local_file";
    public static final String UNIQUE_SYNC="newsvoice_server_sync";
    private static final int TEXT_LIMIT=60000;
    private static final String CHANNEL_ID="newsvoice_video_creation";
    private static final int NOTIFY_ID=5402;
    private void runAsForeground() throws Exception {
        NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager==null)throw new IOException("알림 서비스를 열 수 없습니다.");
        NotificationChannel channel=new NotificationChannel(CHANNEL_ID,"뉴스보이스 영상 제작",NotificationManager.IMPORTANCE_LOW);
        manager.createNotificationChannel(channel);
        Notification n=new Notification.Builder(context,CHANNEL_ID)
            .setContentTitle("뉴스보이스 WAV 영상 제작")
            .setContentText("원고를 합성하고 MP4를 저장하는 중입니다.")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build();
        setForegroundAsync(new ForegroundInfo(NOTIFY_ID,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC))
            .get(20,TimeUnit.SECONDS);
    }
    private String stopDetail(){
        int reason=android.os.Build.VERSION.SDK_INT>=31?getStopReason():-999;
        return "시스템 또는 작업 관리자가 영상 작업을 중단했습니다 (원인 코드 "+reason+").";
    }
    @Override public void onStopped() {
        AppPrefs.status(context,stopDetail());
        super.onStopped();
    }
    private static final class VideoResult {
        final File wav,mp4,credits;
        VideoResult(File wav,File mp4,File credits){
            this.wav=wav;this.mp4=mp4;this.credits=credits;
        }
    }
    static File makeImageCredits(List<NewsStory> pages,
                                         List<NewsImageFetcher.Photo> photos,
                                         File folder,String jobId)throws IOException{
        StringBuilder b=new StringBuilder("뉴스보이스 WAV · 영상 이미지 출처 및 저작권 표시\n"
            +"실제 사건의 현장 사진임을 의미하지 않는 Wikimedia Commons 자료 이미지입니다.\n\n");
        for(int i=0;i<pages.size();i++){
            NewsImageFetcher.Photo photo=photos.get(i);
            if(photo==null)continue;
            b.append("기사 ").append(pages.get(i).articleNumber).append(": ")
                .append(pages.get(i).title).append("\n")
                .append("제작자: ").append(photo.credit.isEmpty()?"Commons 기여자":photo.credit).append("\n")
                .append("라이선스: ").append(photo.license).append("\n")
                .append("출처: ").append(photo.sourceUrl).append("\n")
                .append("라이선스 안내: ").append(photo.licenseUrl).append("\n\n");
        }
        if(b.indexOf("제작자:")<0)return null;
        File output=new File(folder,"newsvoice_"+jobId+"_image_credits.txt");
        try(java.io.OutputStreamWriter w=new java.io.OutputStreamWriter(
                new java.io.FileOutputStream(output),java.nio.charset.StandardCharsets.UTF_8)){
            w.write(b.toString());
        }
        return output;
    }
    private VideoResult makeVideo(String script,String jobId) throws Exception {
        List<NewsStory> sections=NewsBroadcastSegments.build(script,AppPrefs.anchorComment(context));
        List<NewsStory> pages=NewsCardPainter.paginateBroadcast(sections);
        List<File> waveFiles=new ArrayList<>();
        List<List<Long>> sentenceFrames=new ArrayList<>();
        List<NewsImageFetcher.Photo> photos=new ArrayList<>();
        try {
            for(int i=0;i<pages.size();i++) {
                if(isStopped())throw new IOException(stopDetail());
                AppPrefs.status(context,"화면별 음성 합성: "+(i+1)+"/"+pages.size());
                List<Long> cues=new ArrayList<>();
                waveFiles.add(synthesize(pages.get(i).audioText(),jobId+"_page"+(i+1),cues));
                sentenceFrames.add(cues);
            }
            // Search relevant openly licensed photo references for each news item.
            // Network failure is non-fatal: NewsCardPainter draws a neutral icon.
            for(int i=0;i<pages.size();i++){
                if(isStopped())throw new IOException(stopDetail());
                AppPrefs.status(context,"기사 관련 이미지 검색: "+(i+1)+"/"+pages.size());
                NewsImageFetcher.Photo local=pages.get(i).kind==NewsStory.NEWS?
                    NewsImageFetcher.localNewsPhoto(context,pages.get(i).articleNumber):null;
                photos.add(local!=null?local:(pages.get(i).kind==NewsStory.NEWS?
                    NewsImageFetcher.fetch(pages.get(i)):
                    NewsImageFetcher.anchorPortrait(context,pages.get(i))));
            }
            File credits=makeImageCredits(pages,photos,context.getCacheDir(),jobId);
            File audio=new File(context.getCacheDir(),"newsvoice_"+jobId+"_combined.wav");
            WavTools.concatenate(waveFiles,audio);
            File video=new File(context.getCacheDir(),"newsvoice_"+jobId+".mp4");
            NewsVideoComposer.create(context,pages,waveFiles,sentenceFrames,video,message->{
                if(isStopped())throw new IOException(stopDetail());
                AppPrefs.status(context,message);
            },photos);
            return new VideoResult(audio,video,credits);
        }finally{
            for(File temp:waveFiles)temp.delete();
            for(NewsImageFetcher.Photo photo:photos)
                if(photo!=null&&photo.bitmap!=null&&!photo.bitmap.isRecycled())
                    photo.bitmap.recycle();
        }
    }
    private final Context context;
    public NewsVoiceWorker(@NonNull Context context,@NonNull WorkerParameters params) {
        super(context,params);this.context=context.getApplicationContext();
    }
    @NonNull @Override public Result doWork() {
        File input=null;
        try {
            SharedPreferences prefs=AppPrefs.get(context);
            String localFile=getInputData().getString(INPUT_LOCAL_FILE);
            if(localFile!=null){
                if(!localFile.matches("news_[a-zA-Z0-9_-]{1,80}\\.txt"))throw new IOException("Invalid local file");
                runAsForeground();
                input=new File(new File(context.getFilesDir(),"inbox"),localFile);
                String text=readString(input);
                String stamp=String.valueOf(System.currentTimeMillis());
                NewsOutputNaming.Names names=NewsOutputNaming.at(java.time.Instant.now());
                if(AppPrefs.videoEnabled(context)){
                    VideoResult made=makeVideo(text,"manual_"+stamp);
                    saveDownloads(made.wav,names.wav());
                    AppPrefs.status(context,"MP4 다운로드 폴더에 저장 중...");
                    saveDownloads(made.mp4,names.mp4());
                    saveDownloads(NewsCoverExporter.coverFile(made.mp4),names.jpg());
                    if(made.credits!=null){
                        saveDownloads(made.credits,names.credits());
                        made.credits.delete();
                    }
                    AppPrefs.status(context,"저장 완료: 다운로드 → 1. 필리핀뉴스 → "+names.dateFolder+" → WAV · MP4 · JPG");
                    made.wav.delete();NewsCoverExporter.coverFile(made.mp4).delete();made.mp4.delete();
                }else{
                    List<NewsStory> sections=NewsBroadcastSegments.build(text,AppPrefs.anchorComment(context));
                    File wav=synthesize(NewsBroadcastSegments.audioScript(sections),"manual_"+stamp);
                    saveDownloads(wav,names.wav());
                    AppPrefs.status(context,"저장 완료: 다운로드 → 1. 필리핀뉴스 → "+names.dateFolder+" → "+names.wav());
                    wav.delete();
                }
                return Result.success();
            }
            if(!prefs.getBoolean("auto",false))return Result.success();
            String base=prefs.getString("server","").trim();
            String token=prefs.getString("token","");
            validateServer(base,token);
            Job job=fetch(base,token);
            if(job==null){AppPrefs.status(context,"새 뉴스 원고 없음");return Result.success();}
            String broadcast=job.text;
            NewsOutputNaming.Names names=NewsOutputNaming.at(java.time.Instant.now());
            AppPrefs.status(context,"자동 음성 생성: "+job.id);
            if(AppPrefs.videoEnabled(context)){
                VideoResult made=makeVideo(broadcast,job.id);
                saveDownloads(made.wav,names.wav());
                saveDownloads(made.mp4,names.mp4());
                saveDownloads(NewsCoverExporter.coverFile(made.mp4),names.jpg());
                if(made.credits!=null){
                    saveDownloads(made.credits,names.credits());
                    made.credits.delete();
                }
                upload(base,token,job.id,made.wav);
                made.wav.delete();NewsCoverExporter.coverFile(made.mp4).delete();made.mp4.delete();
                AppPrefs.status(context,"WAV + MP4 생성 및 WAV 전송 완료: "+job.id);
            }else{
                List<NewsStory> sections=NewsBroadcastSegments.build(broadcast,AppPrefs.anchorComment(context));
                File wav=synthesize(NewsBroadcastSegments.audioScript(sections),job.id);
                saveDownloads(wav,names.wav());
                upload(base,token,job.id,wav);
                wav.delete();
                AppPrefs.status(context,"WAV 자동 생성·전송 완료: "+job.id);
            }
            return Result.success();
        } catch (Exception e) {
            String step=AppPrefs.get(context).getString("status","단계 미확인");
            String msg=e.getMessage()==null?"원인 메시지 없음":e.getMessage();
            AppPrefs.status(context,"작업 실패 | "+step+" | "+e.getClass().getSimpleName()+" : "+msg);
            // Do not automatically delete local text on failure, and keep queued job on server.
            return input == null ? Result.retry() : Result.failure();
        } finally {
            if(input!=null && input.exists() && AppPrefs.get(context).getString("status","").startsWith("저장 완료:"))input.delete();
        }
    }
    private static final class Job {final String id,text;Job(String i,String t){id=i;text=t;}}
    private static void validateServer(String base,String token) throws IOException {
        if(!base.startsWith("https://") || base.contains("#") || base.contains("?"))throw new IOException("HTTPS server URL required");
        if(token.length()<12)throw new IOException("Access token must be at least 12 characters");
    }
    private static HttpURLConnection connection(String url,String token,String method) throws IOException {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setInstanceFollowRedirects(false);
        c.setConnectTimeout(15000);c.setReadTimeout(30000);
        c.setRequestMethod(method);
        c.setRequestProperty("Authorization","Bearer "+token);
        c.setRequestProperty("Accept","application/json");
        return c;
    }
    private static String readAll(InputStream is,int max) throws IOException {
        try(InputStream in=is;ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){
                if(out.size()+n>max)throw new IOException("Response too large");out.write(b,0,n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
    private static String readString(File file) throws IOException {
        if(!file.isFile()||file.length()>TEXT_LIMIT*4)throw new IOException("Script is missing or too large");
        try(FileInputStream in=new FileInputStream(file)){return readAll(in,TEXT_LIMIT*4).trim();}
    }
    private static Job fetch(String base,String token) throws Exception {
        HttpURLConnection c=connection(base.replaceAll("/+$","")+"/api/jobs/next",token,"GET");
        try {
            int status=c.getResponseCode();if(status==204)return null;
            if(status!=200)throw new IOException("GET failed: HTTP "+status);
            JSONObject obj=new JSONObject(readAll(c.getInputStream(),TEXT_LIMIT*5));
            String id=obj.getString("id"), text=obj.getString("text").trim();
            if(!id.matches("[a-zA-Z0-9_-]{1,80}")||text.isEmpty()||text.length()>TEXT_LIMIT)throw new IOException("Invalid job payload");
            return new Job(id,text);
        }finally{c.disconnect();}
    }
    private static void upload(String base,String token,String id,File wav) throws Exception {
        HttpURLConnection c=connection(base.replaceAll("/+$","")+"/api/jobs/"+id+"/audio",token,"POST");
        try{
            c.setDoOutput(true);c.setRequestProperty("Content-Type","audio/wav");
            c.setFixedLengthStreamingMode(wav.length());
            try(OutputStream out=c.getOutputStream();InputStream in=new FileInputStream(wav)){
                byte[] buf=new byte[32768];int n;while((n=in.read(buf))>=0)out.write(buf,0,n);
            }
            int status=c.getResponseCode();if(status!=200&&status!=201)throw new IOException("POST failed: HTTP "+status);
        }finally{c.disconnect();}
    }
    private File synthesize(String script,String jobId) throws Exception {
        return synthesize(script,jobId,null);
    }
    /** Cues use exact frames from each separately synthesized sentence WAV. */
    private File synthesize(String script,String jobId,List<Long> sentenceFrames) throws Exception {
        CountDownLatch init=new CountDownLatch(1);AtomicInteger initStatus=new AtomicInteger(TextToSpeech.ERROR);
        String selectedEngine=AppPrefs.enginePackage(context);
        TextToSpeech tts=new TextToSpeech(context, s->{initStatus.set(s);init.countDown();},
            selectedEngine.isEmpty()?null:selectedEngine);
        List<File> chunks=new ArrayList<>();
        try {
            if(!init.await(25,TimeUnit.SECONDS)||initStatus.get()!=TextToSpeech.SUCCESS)throw new IOException("TTS engine initialization failed");
            if(!selectedEngine.isEmpty()){
                boolean found=false;
                for(TextToSpeech.EngineInfo info:tts.getEngines()){
                    if(selectedEngine.equals(info.name)){found=true;break;}
                }
                if(!found)throw new IOException("Locked TTS engine not installed: "+selectedEngine);
            }
            int lang=tts.setLanguage(Locale.KOREAN);
            if(lang==TextToSpeech.LANG_MISSING_DATA||lang==TextToSpeech.LANG_NOT_SUPPORTED)throw new IOException("Korean TTS voice not installed");
            String preferred=AppPrefs.voiceName(context);
            if(!preferred.isEmpty()){
                Voice found=null;
                if(tts.getVoices()!=null)for(Voice v:tts.getVoices())if(v!=null && preferred.equals(v.getName())){found=v;break;}
                if(found==null)throw new IOException("Locked voice unavailable: "+preferred);
                if(tts.setVoice(found)!=TextToSpeech.SUCCESS)throw new IOException("Cannot activate locked voice");
            }
            tts.setSpeechRate(AppPrefs.speed(context));tts.setPitch(AppPrefs.pitch(context));
            Map<String,CountDownLatch> pending=new ConcurrentHashMap<>();
            Map<String,Integer> errors=new ConcurrentHashMap<>();
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
                @Override public void onStart(String utteranceId) { }
                @Override public void onDone(String id){CountDownLatch l=pending.get(id);if(l!=null)l.countDown();}
                @Override public void onError(String id){errors.put(id,1);CountDownLatch l=pending.get(id);if(l!=null)l.countDown();}
                @Override public void onError(String id,int code){onError(id);}
            });
            int max=Math.min(1800,TextToSpeech.getMaxSpeechInputLength()-50);
            List<String> sentences=sentenceFrames==null?
                java.util.Collections.singletonList(script):NewsSentenceTimeline.split(script);
            List<String> segments=new ArrayList<>();
            List<Integer> sentenceEnds=new ArrayList<>();
            for(String sentence:sentences){
                segments.addAll(WavTools.splitText(sentence,max));
                sentenceEnds.add(segments.size());
            }
            int sentenceIndex=0;
            long elapsedSentenceFrames=0;
            File folder=new File(context.getCacheDir(),"wav_parts_"+UUID.randomUUID());
            if(!folder.mkdirs())throw new IOException("Cache folder creation failed");
            try {
                for(int i=0;i<segments.size();i++){
                    if(isStopped())throw new IOException("TTS job stopped");
                    String id="part_"+i+"_"+UUID.randomUUID();File part=new File(folder,String.format(Locale.US,"%04d.wav",i));
                    CountDownLatch finish=new CountDownLatch(1);pending.put(id,finish);
                    int result=tts.synthesizeToFile(segments.get(i),new android.os.Bundle(),part,id);
                    if(result!=TextToSpeech.SUCCESS)throw new IOException("TTS synthesis rejected part "+(i+1));
                    if(!finish.await(120,TimeUnit.SECONDS))throw new IOException("TTS synthesis timed out at part "+(i+1));
                    if(errors.containsKey(id))throw new IOException("TTS engine failed at part "+(i+1));
                    WavTools.Piece written=WavTools.inspect(part);chunks.add(part);
                    if(sentenceFrames!=null){
                        int channels=(written.format[2]&255)|((written.format[3]&255)<<8);
                        if(channels<1||channels>2||written.dataLength%(channels*2)!=0)
                            throw new IOException("문장 WAV 프레임이 유효하지 않습니다.");
                        elapsedSentenceFrames+=written.dataLength/(channels*2);
                        if(i+1==sentenceEnds.get(sentenceIndex)){
                            sentenceFrames.add(elapsedSentenceFrames);
                            elapsedSentenceFrames=0;
                            sentenceIndex++;
                        }
                    }
                    pending.remove(id);
                    AppPrefs.status(context,"음성 합성 중: "+(i+1)+"/"+segments.size());
                }
                File output=new File(context.getCacheDir(),"newsvoice_"+jobId+".wav");
                WavTools.concatenate(chunks,output);
                if(sentenceFrames!=null && sentenceFrames.size()!=sentences.size())
                    throw new IOException("문장별 음성 길이가 일치하지 않습니다.");
                return output;
            }finally{for(File f:folder.listFiles()==null?new File[0]:folder.listFiles())f.delete();folder.delete();}
        }finally{tts.stop();tts.shutdown();}
    }
    private Uri saveDownloads(File source,String filename) throws Exception {
        AppPrefs.status(context,"다운로드 저장 중: "+filename);
        Uri uri=DownloadsPublisher.publish(context,source,filename);
        if(filename.endsWith(".mp4"))
            AppPrefs.get(context).edit().putString("last_mp4_uri",uri.toString())
                .putString("last_mp4_name",filename).apply();
        if(filename.endsWith(".jpg"))
            AppPrefs.get(context).edit().putString("last_cover_uri",uri.toString())
                .putString("last_cover_name",filename).apply();
        AppPrefs.status(context,"다운로드에 저장됨: "+filename);
        return uri;
    }
}
