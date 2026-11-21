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
 * Original built-in A-style instrumental news opening, precisely six seconds.
 * A user's exact approved A WAV, if imported, takes priority over this fallback.
 * The generated audio is original rather than a recording of the earlier A option.
 */
public final class OpeningMusicA {
    private OpeningMusicA(){}
    private static final int RATE=22050, CHANNELS=2, DURATION=6;
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
    private static double note(double hz,double t){
        return Math.sin(TWO_PI*hz*t)+0.21*Math.sin(TWO_PI*2*hz*t);
    }
    private static double sample(double t,int channel){
        // Original, six-second ceremonial fanfare. All musical material is
        // synthesized on the device; no embedded third-party recording.
        double[][] harmony={{261.63,329.63,392.00},{196,246.94,392.00},
                            {220,261.63,329.63},{174.61,261.63,349.23}};
        double[] top={523.25,659.25,783.99,659.25,698.46,1046.50};
        int h=Math.min(3,(int)(t/1.5)),beat=Math.min(5,(int)t);
        double time=t-beat, pad=0;
        for(int i=0;i<3;i++)
            pad+=note(harmony[h][i]*(channel==0?0.998:1.002),t)/3.0;
        double pulseTime=t-Math.floor(t*2.0)/2.0;
        double pulse=Math.sin(TWO_PI*harmony[h][0]*0.5*t)*Math.exp(-8*pulseTime);
        double chime=(Math.sin(TWO_PI*top[beat]*time)+
            .32*Math.sin(TWO_PI*top[beat]*2*time))*Math.exp(-3.5*time);
        double start=Math.min(1.0,t/.18);
        double end=Math.min(1.0,Math.max(0.0,(DURATION-t)/.95));
        return Math.max(-.9,Math.min(.9,(.29*pad+.18*pulse+.20*chime)*start*end));
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
    /** Install once; preserve an existing valid imported WAV between app launches. */
    public static synchronized File ensureInstalled(Context context)throws IOException {
        File dest=new File(context.getFilesDir(),"newsvoice_opening_A.wav");
        if(valid(dest))return dest;
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
        AppPrefs.get(context).edit().putBoolean("opening_a_builtin",true).apply();
        return dest;
    }
}
