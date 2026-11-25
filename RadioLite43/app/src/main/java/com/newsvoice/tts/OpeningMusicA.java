package com.newsvoice.tts;

import android.content.Context;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Original upbeat 6-second news opener, with bright brass-like lead,
 * rhythmic stabs, bass, kick, snare, and a rising resolved final flourish.
 * Always auto-generated if not installed; a user's imported WAV takes priority.
 * This is newly synthesized original music, not third-party recorded music.
 */
public final class OpeningMusicA {
    private OpeningMusicA(){}
    private static final int RATE=22050, CHANNELS=2, DURATION=6, MUSIC_REVISION=2;
    private static final double TWO_PI=2.0*Math.PI;

    private static void le16(DataOutputStream out,int v)throws IOException{
        out.writeByte(v&255);out.writeByte((v>>>8)&255);
    }
    private static void le32(DataOutputStream out,int v)throws IOException{
        le16(out,v);le16(out,v>>>16);
    }
    private static void header(DataOutputStream out)throws IOException{
        int bytes=RATE*CHANNELS*2*DURATION;
        out.writeBytes("RIFF");le32(out,36+bytes);
        out.writeBytes("WAVEfmt ");le32(out,16);le16(out,1);
        le16(out,CHANNELS);le32(out,RATE);
        le32(out,RATE*CHANNELS*2);le16(out,CHANNELS*2);le16(out,16);
        out.writeBytes("data");le32(out,bytes);
    }
    private static double harmonic(double hz,double t){
        return Math.sin(TWO_PI*hz*t)+
            0.26*Math.sin(TWO_PI*2*hz*t)+
            0.10*Math.sin(TWO_PI*3*hz*t);
    }
    /** Deterministic noise for short hi-hat/snare hits; no asset or network dependency. */
    private static double noise(double t,int channel){
        double n=Math.sin((t*14531+channel*1.7)*12.9898)*43758.5453;
        return 2*(n-Math.floor(n))-1;
    }
    private static double sample(double t,int channel){
        // Three bars at 120 BPM, entirely original melody and arrangement.
        final double[][] chords={
            {261.63,329.63,392.00},  // C major: bright introduction
            {349.23,440.00,523.25},  // F major: energetic lift
            {392.00,493.88,587.33}   // G major: final flourish
        };
        final double[] lead={
            659.25,783.99,1046.50,783.99,
            880.00,1046.50,1174.66,1046.50,
            987.77,1174.66,1318.51,1567.98
        };
        int eighth=Math.min(11,(int)(t*2.0));
        int bar=Math.min(2,(int)(t/2.0));
        double eighthTime=t-eighth*0.5;
        double leadEnv=Math.exp(-3.0*eighthTime);
        double melodic=harmonic(lead[eighth]*(channel==0?0.999:1.001),t)*leadEnv;
        double brass=0;
        for(int i=0;i<3;i++)
            brass+=harmonic(chords[bar][i]*(channel==0?0.998:1.002),t)/3.0;
        // Chord stabs follow each beat, leaving rhythmic space.
        double beatTime=t-Math.floor(t*2.0)/2.0;
        brass*=Math.exp(-3.0*beatTime);
        double bassHz=chords[bar][0]*0.5;
        double bass=Math.sin(TWO_PI*bassHz*t)*Math.exp(-3.1*beatTime);
        double kickTime=t-Math.floor(t*2.0)/2.0;
        double kick=Math.sin(TWO_PI*(105-45*kickTime)*kickTime)*Math.exp(-26*kickTime);
        double snareTime=t-(Math.floor(t*2.0-1.0)+1.0)/2.0;
        // Snare on offbeats; clamp when no snare is scheduled yet.
        double snare=0;
        if(snareTime>=0 && Math.floor(t*2.0)%2==1){
            snare=noise(t,channel)*Math.exp(-33*snareTime);
        }
        double hatTime=t-Math.floor(t*4.0)/4.0;
        double hat=noise(t*1.3+0.1,channel)*Math.exp(-65*hatTime);
        // Bright resolving sustained chord for the last 0.7 seconds.
        double finale=0;
        if(t>=5.20){
            double dt=t-5.20;
            finale=(harmonic(523.25,t)+harmonic(659.25,t)+
                harmonic(783.99,t))/3.0*Math.exp(-1.7*dt);
        }
        double attack=Math.min(1.0,t/0.025);
        double release=Math.min(1.0,Math.max(0,(DURATION-t)/0.38));
        double mix=0.26*melodic+0.22*brass+0.17*bass+
            0.15*kick+0.065*snare+0.035*hat+0.25*finale;
        return Math.max(-0.92,Math.min(0.92,mix*attack*release));
    }
    private static boolean valid(File file){
        try {
            if(!file.isFile()||file.length()<100)return false;
            WavTools.Piece wave=WavTools.inspect(file);
            byte[] fmt=wave.format;
            int kind=(fmt[0]&255)|((fmt[1]&255)<<8);
            int count=(fmt[2]&255)|((fmt[3]&255)<<8);
            int rate=(fmt[4]&255)|((fmt[5]&255)<<8)|((fmt[6]&255)<<16)|((fmt[7]&255)<<24);
            int bits=(fmt[14]&255)|((fmt[15]&255)<<8);
            return kind==1&&bits==16&&count>=1&&count<=2&&rate>=8000&&rate<=96000&&
                wave.dataLength/(double)(rate*count*2)>=5.5&&
                wave.dataLength/(double)(rate*count*2)<=6.3;
        }catch(Exception ignored){return false;}
    }
    /** Install the current upbeat opener automatically; retain user-imported custom WAV. */
    public static synchronized File ensureInstalled(Context context)throws IOException {
        File dest=new File(context.getFilesDir(),"newsvoice_opening_A.wav");
        boolean exists=valid(dest);
        boolean builtin=AppPrefs.get(context).getBoolean("opening_a_builtin",true);
        int revision=AppPrefs.get(context).getInt("opening_a_revision",0);
        if(exists && (!builtin || revision==MUSIC_REVISION))return dest;
        File tmp=new File(context.getFilesDir(),"newsvoice_opening_A_builtin.tmp");
        try(DataOutputStream out=new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(tmp)))){
            header(out);
            for(int frame=0;frame<RATE*DURATION;frame++){
                double t=frame/(double)RATE;
                for(int channel=0;channel<CHANNELS;channel++)
                    le16(out,(short)Math.round(28000*sample(t,channel)));
            }
        }catch(IOException error){tmp.delete();throw error;}
        if(!valid(tmp)){
            tmp.delete();
            throw new IOException("A 타입 오프닝 음악을 생성하지 못했습니다.");
        }
        Files.move(tmp.toPath(),dest.toPath(),StandardCopyOption.REPLACE_EXISTING);
        AppPrefs.get(context).edit().putBoolean("opening_a_builtin",true)
            .putInt("opening_a_revision",MUSIC_REVISION).apply();
        return dest;
    }
}
