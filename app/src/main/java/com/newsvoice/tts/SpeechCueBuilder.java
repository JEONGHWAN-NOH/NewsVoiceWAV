package com.newsvoice.tts;

import android.content.Context;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Per-sentence WAV synthesis yields sample-accurate speech start/end highlights. */
public final class SpeechCueBuilder {
    private SpeechCueBuilder() {}
    public interface Synthesizer {
        File synthesize(String sentence,String partId) throws Exception;
    }
    public static final class Result {
        public final File wav;
        public final long[] sentenceEndUs;
        Result(File wav,long[] sentenceEndUs){this.wav=wav;this.sentenceEndUs=sentenceEndUs;}
    }
    private static int ushort(byte[] b,int off){return (b[off]&255)|((b[off+1]&255)<<8);}
    private static int uint(byte[] b,int off){return (b[off]&255)|((b[off+1]&255)<<8)|((b[off+2]&255)<<16)|((b[off+3]&255)<<24);}
    public static Result make(Context context,String spoken,String jobId,Synthesizer synthesizer) throws Exception {
        List<String> sentences=NewsSentenceCues.split(spoken);
        if(sentences.size()>120)throw new IOException("한 화면의 문장이 120개를 초과했습니다.");
        ArrayList<File> pieces=new ArrayList<>();
        File merged=new File(context.getCacheDir(),"gm_cued_"+UUID.randomUUID()+".wav");
        boolean ok=false;
        try {
            long[] end=new long[sentences.size()];
            long samples=0;
            int sampleRate=0,channels=0;
            byte[] expected=null;
            for(int i=0;i<sentences.size();i++){
                File part=synthesizer.synthesize(sentences.get(i),jobId+"_sentence_"+(i+1));
                if(part==null||!part.isFile())throw new IOException("문장 WAV 생성에 실패했습니다: "+(i+1));
                pieces.add(part);
                WavTools.Piece wave=WavTools.inspect(part);
                if(expected!=null&&!Arrays.equals(expected,wave.format))
                    throw new IOException("문장별 TTS WAV 형식이 서로 다릅니다.");
                expected=wave.format;
                sampleRate=uint(expected,4);channels=ushort(expected,2);
                if(ushort(expected,0)!=1 || ushort(expected,14)!=16 ||
                   sampleRate<8000||sampleRate>48000||channels<1||channels>2)
                    throw new IOException("PCM 16-bit 한국어 음성 WAV가 필요합니다.");
                if(wave.dataLength%(2L*channels)!=0)
                    throw new IOException("WAV 프레임 수가 올바르지 않습니다.");
                samples+=wave.dataLength/(2L*channels);
                end[i]=samples*1000000L/sampleRate;
            }
            WavTools.concatenate(pieces,merged);
            WavTools.Piece check=WavTools.inspect(merged);
            long expectedSize=samples*channels*2L;
            if(check.dataLength!=expectedSize)throw new IOException("문장 병합 후 WAV 길이가 달라졌습니다.");
            ok=true;
            return new Result(merged,end);
        }finally {
            for(File part:pieces)part.delete();
            if(!ok)merged.delete();
        }
    }
}
