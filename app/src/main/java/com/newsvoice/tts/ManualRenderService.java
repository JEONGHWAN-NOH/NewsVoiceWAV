package com.newsvoice.tts;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Direct foreground media service for user-initiated MP4 creation (no JobScheduler quotas). */
public final class ManualRenderService extends Service {
    public static final String EXTRA_FILE="local_file";
    private static final String CHANNEL="newsvoice_render_v56";
    private static final int NOTIFY_ID=5601;
    private final AtomicBoolean busy=new AtomicBoolean(false);
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private Context context;
    private long startedAt;
    private int percent;
    private int renderedPercent;
    private int pageCount;
    private void progress(String stage,int value){
        int bounded=Math.max(0,Math.min(100,value));
        percent=Math.max(percent,bounded);
        long elapsed=Math.max(0,System.currentTimeMillis()-startedAt);
        long eta=percent>=100?0:percent<8?-1:Math.max(0,elapsed*(100L-percent)/Math.max(1,percent));
        AppPrefs.get(context).edit().putInt("render_percent",percent)
            .putLong("render_eta_ms",eta).putString("render_stage",stage)
            .putBoolean("render_active",percent<100).apply();
        report(stage);
    }

    private NotificationManager notices;

    @Override public void onCreate(){
        super.onCreate();
        context=getApplicationContext();
        notices=(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        if(notices!=null)notices.createNotificationChannel(
            new NotificationChannel(CHANNEL,"뉴스보이스 MP4 제작",NotificationManager.IMPORTANCE_LOW));
    }
    private Notification notification(String status){
        return new Notification.Builder(this,CHANNEL).setContentTitle("뉴스보이스 영상 제작")
            .setContentText(status).setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true).build();
    }
    private void report(String status){
        AppPrefs.status(context,status);
        if(notices!=null)notices.notify(NOTIFY_ID,notification(status));
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null){stopSelfResult(startId);return START_NOT_STICKY;}
        if(!busy.compareAndSet(false,true)){
            AppPrefs.status(context,"이미 영상이 제작 중입니다. 중복 요청은 무시했습니다.");
            return START_NOT_STICKY;
        }
        String file=intent.getStringExtra(EXTRA_FILE);
        try{
            int type=Build.VERSION.SDK_INT>=35?
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING:
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
            startForeground(NOTIFY_ID,notification("영상 제작을 준비하고 있습니다."),type);
            startedAt=System.currentTimeMillis();percent=0;renderedPercent=0;
            progress("영상 제작 시작됨",1);
        }catch(Exception e){
            AppPrefs.status(context,"전경 제작 시작 실패: "+e.getClass().getSimpleName()+" - "+e.getMessage());
            busy.set(false);stopSelfResult(startId);return START_NOT_STICKY;
        }
        executor.execute(()->{
            try{render(file);}
            catch(Exception e){
                String last=AppPrefs.get(context).getString("status","시작");
                AppPrefs.get(context).edit().putBoolean("render_active",false).putLong("render_eta_ms",-1).apply();
                AppPrefs.status(context,"영상 제작 실패 | 마지막 단계: "+last+" | "+
                    e.getClass().getSimpleName()+" : "+e.getMessage()+
                    " | WAV는 먼저 저장됐을 수 있습니다.");
            }finally{
                busy.set(false);
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelfResult(startId);
            }
        });
        return START_NOT_STICKY;
    }
    private void render(String filename)throws Exception{
        if(filename==null||!filename.matches("news_[a-zA-Z0-9_-]{1,80}\\.txt"))
            throw new IOException("원고 파일 이름이 잘못됐습니다.");
        File input=new File(new File(context.getFilesDir(),"inbox"),filename);
        if(!input.isFile()||input.length()>240000)
            throw new IOException("원고를 읽을 수 없습니다.");
        String text=new String(Files.readAllBytes(input.toPath()),StandardCharsets.UTF_8).trim();
        if(text.isEmpty()||text.length()>60000)throw new IOException("원고 길이를 확인해 주세요.");
        List<NewsStory> sections=NewsBroadcastSegments.build(text,AppPrefs.anchorComment(context));
        String stamp=String.valueOf(System.currentTimeMillis());
        NewsOutputNaming.Names names=NewsOutputNaming.at(java.time.Instant.now());
        List<File> parts=new ArrayList<>();
        List<List<Long>> sentenceFrames=new ArrayList<>();
        List<NewsImageFetcher.Photo> photos=new ArrayList<>();
        File wav=null,mp4=null,credits=null;
        boolean success=false;
        try{
            if(!AppPrefs.videoEnabled(context)){
                progress("WAV 음성 생성 중",10);
                wav=synthesize(NewsBroadcastSegments.audioScript(sections),"manual_"+stamp);
                DownloadsPublisher.publish(context,wav,names.wav());
                progress("WAV 저장 완료",100);
                report("저장 완료: 다운로드/1. 필리핀뉴스/"+names.dateFolder+"/"+names.wav());
                success=true;return;
            }
            List<NewsStory> pages=NewsCardPainter.paginateBroadcast(sections);
            pageCount=pages.size();
            progress("음성 생성 준비",5);
            for(int i=0;i<pages.size();i++){
                if(Thread.currentThread().isInterrupted())
                    throw new IOException("영상 처리 스레드가 시스템에서 중단됐습니다.");
                progress("화면별 WAV 생성: "+(i+1)+"/"+pages.size(),5+35*i/Math.max(1,pages.size()));
                List<Long> cues=new ArrayList<>();
                parts.add(synthesize(pages.get(i).audioText(),"manual_"+stamp+"_p"+(i+1),cues));
                sentenceFrames.add(cues);
            }
            wav=new File(context.getCacheDir(),"newsvoice_manual_"+stamp+"_combined.wav");
            WavTools.concatenate(parts,wav);
            DownloadsPublisher.publish(context,wav,names.wav());
            progress("WAV 저장 성공 · MP4 변환 준비",40);
            for(int i=0;i<pages.size();i++){
                if(Thread.currentThread().isInterrupted())
                    throw new IOException("이미지 검색 중 작업이 중단됐습니다.");
                progress("관련 자료 이미지 검색 "+(i+1)+"/"+pages.size(),40+12*i/Math.max(1,pages.size()));
                photos.add(pages.get(i).kind==NewsStory.NEWS?
                    NewsImageFetcher.fetch(pages.get(i)):
                    NewsImageFetcher.anchorPortrait(context,pages.get(i)));
            }
            credits=NewsVoiceWorker.makeImageCredits(pages,photos,context.getCacheDir(),"manual_"+stamp);
            mp4=new File(context.getCacheDir(),"newsvoice_manual_"+stamp+".mp4");
            progress("영상 렌더링 준비",52);
            NewsVideoComposer.create(context,pages,parts,sentenceFrames,mp4,step->{
                if(Thread.currentThread().isInterrupted())
                    throw new IOException("영상 작업 스레드가 시스템에서 중단됐습니다.");
                if(step.startsWith("뉴스 화면 렌더링 ")){
                    try{
                        int x=Integer.parseInt(step.replaceAll("[^0-9]",""));
                        progress(step,56+Math.min(36,36*x/100));
                    }catch(NumberFormatException ignored){report(step);}
                }else if(step.contains("AAC"))progress(step,54);
                else if(step.contains("결합"))progress(step,93);
                else progress(step,Math.max(52,percent));
            },photos);
            progress("MP4 다운로드 폴더에 기록하는 중",95);
            Uri saved=DownloadsPublisher.publish(context,mp4,names.mp4());
            File cover=NewsCoverExporter.coverFile(mp4);
            Uri savedCover=DownloadsPublisher.publish(context,cover,names.jpg());
            AppPrefs.get(context).edit().putString("last_mp4_uri",saved.toString())
                .putString("last_mp4_name",names.mp4())
                .putString("last_cover_uri",savedCover.toString())
                .putString("last_cover_name",names.jpg()).apply();
            cover.delete();
            if(credits!=null)
                DownloadsPublisher.publish(context,credits,names.credits());
            progress("저장 완료: 다운로드/1. 필리핀뉴스/"+names.dateFolder+" → MP4 · WAV · JPG",100);
            success=true;
        }finally{
            for(File f:parts)f.delete();
            for(NewsImageFetcher.Photo photo:photos)
                if(photo!=null&&photo.bitmap!=null&&!photo.bitmap.isRecycled())
                    photo.bitmap.recycle();
            if(credits!=null)credits.delete();
            if(wav!=null)wav.delete();
            if(mp4!=null){
                NewsCoverExporter.coverFile(mp4).delete();
                mp4.delete();
            }
            if(success)input.delete();
        }
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
                    if(Thread.currentThread().isInterrupted())throw new IOException("음성 합성 작업이 시스템에서 중단됐습니다.");
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
                    report("음성 합성 구간: "+(i+1)+"/"+segments.size());
                }
                File output=new File(context.getCacheDir(),"newsvoice_"+jobId+".wav");
                WavTools.concatenate(chunks,output);
                if(sentenceFrames!=null && sentenceFrames.size()!=sentences.size())
                    throw new IOException("문장별 음성 길이가 일치하지 않습니다.");
                return output;
            }finally{for(File f:folder.listFiles()==null?new File[0]:folder.listFiles())f.delete();folder.delete();}
        }finally{tts.stop();tts.shutdown();}
    }

    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){executor.shutdownNow();super.onDestroy();}
}
