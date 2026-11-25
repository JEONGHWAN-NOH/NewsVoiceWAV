package com.newsvoice.radio;

import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;
import java.io.*;

public class RadioService extends Service {
    private MediaPlayer player;
    private final IBinder binder = new LocalBinder();
    class LocalBinder extends Binder { RadioService get(){return RadioService.this;} }
    public IBinder onBind(Intent intent){return binder;}
    public void onCreate(){super.onCreate(); getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("radio","Radio playback",NotificationManager.IMPORTANCE_LOW));}
    void load(File audio) throws IOException {
        if(player!=null) player.release();
        player=new MediaPlayer();
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
        player.setDataSource(audio.getAbsolutePath()); player.prepare();
        player.setOnCompletionListener(p->stopForeground(STOP_FOREGROUND_REMOVE));
    }
    boolean ready(){return player!=null;}
    boolean playing(){return ready() && player.isPlaying();}
    int position(){return ready()?player.getCurrentPosition():0;}
    int duration(){return ready()?player.getDuration():0;}
    void seek(int at){if(ready()) player.seekTo(at);}
    void speed(float rate){if(ready()){boolean was=playing(); player.setPlaybackParams(new PlaybackParams().setSpeed(rate).setPitch(1)); if(!was)player.pause();}}
    void toggle(){if(!ready())return; if(playing()){player.pause();stopForeground(STOP_FOREGROUND_REMOVE);}else{startService(new Intent(this,RadioService.class));startForeground(4,new Notification.Builder(this,"radio").setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("NewsVoice Radio Lite").setContentText("뉴스 재생 중").setContentIntent(PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE)).setOngoing(true).build());player.start();}}
    public int onStartCommand(Intent i,int f,int id){return START_NOT_STICKY;}
    public void onDestroy(){if(player!=null)player.release();super.onDestroy();}
}
