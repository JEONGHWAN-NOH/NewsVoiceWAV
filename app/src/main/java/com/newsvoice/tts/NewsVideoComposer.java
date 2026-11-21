package com.newsvoice.tts;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.view.Surface;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Offline MP4 renderer: news-card Canvas -> EGL -> hardware H.264 encoder;
 * 16-bit PCM WAV -> AAC encoder; MediaExtractor/MediaMuxer join to playable MP4.
 * No internet or unlicensed third-party codecs required.
 * An original built-in A-style six-second opening plays automatically. A registered original A WAV overrides it.
 * Each card starts 0.30s before the corresponding narrated article.
 */
public final class NewsVideoComposer {
    private NewsVideoComposer() { }
    public static final int VIDEO_WIDTH=1080,VIDEO_HEIGHT=1920,FPS=30;
    private static final long US_PER_S=1_000_000L;
    private static final long SCROLL_DELAY_US=300_000L; // narration leads the scroll by 0.3 seconds
    private static final int PCM_CHUNK=16384;

    public interface Progress { void message(String message) throws IOException; }
    private static final class Span {
        final NewsStory story;
        final long startUs,endUs,audioStartUs;
        final long[] sentenceEndUs;
        Span(NewsStory story,long startUs,long endUs,long audioStartUs,long[] sentenceEndUs){
            this.story=story;this.startUs=startUs;this.endUs=endUs;
            this.audioStartUs=audioStartUs;this.sentenceEndUs=sentenceEndUs;
        }
        int activeSentence(long presentationUs){
            if(presentationUs<audioStartUs)return -1; // Audio pre-roll: text remains stationary.
            for(int i=0;i<sentenceEndUs.length;i++)
                if(presentationUs<sentenceEndUs[i])return i;
            return -1; // Speech for this card has finished.
        }
        float sentenceProgress(long presentationUs,int active){
            if(active<0)return 0f;
            long first=active==0?audioStartUs:sentenceEndUs[active-1];
            long last=sentenceEndUs[active];
            if(last<=first)return 1f;
            return NewsScrollMath.clamp((float)(presentationUs-first)/(last-first),0f,1f);
        }
        boolean narrationFinished(long presentationUs){
            return sentenceEndUs.length>0 &&
                presentationUs>=sentenceEndUs[sentenceEndUs.length-1];
        }
    }
    private static final class Timeline {
        final File pcm;
        final int sampleRate,channels;
        final long endUs;
        final List<Span> spans;
        Timeline(File pcm,int sr,int channels,long endUs,List<Span> spans){this.pcm=pcm;this.sampleRate=sr;this.channels=channels;this.endUs=endUs;this.spans=spans;}
    }
    private static int shortLE(byte[] a,int off){return (a[off]&255)|((a[off+1]&255)<<8);}
    private static int intLE(byte[] a,int off){return (a[off]&255)|((a[off+1]&255)<<8)|((a[off+2]&255)<<16)|((a[off+3]&255)<<24);}
    private static long toUs(long samples,int sampleRate){return samples*US_PER_S/sampleRate;}
    private static void writeSilence(OutputStream out,long bytes)throws IOException {
        byte[] zeros=new byte[8192];while(bytes>0){int n=(int)Math.min(bytes,zeros.length);out.write(zeros,0,n);bytes-=n;}
    }
    private static void appendWave(OutputStream out,WavTools.Piece piece)throws IOException {
        try(RandomAccessFile f=new RandomAccessFile(piece.file,"r")){
            f.seek(piece.dataOffset);byte[] chunk=new byte[32768];long remaining=piece.dataLength;
            while(remaining>0){int n=f.read(chunk,0,(int)Math.min(chunk.length,remaining));if(n<0)throw new EOFException();out.write(chunk,0,n);remaining-=n;}
        }
    }

    /** Resample the user's approved original opening A into the TTS output format.
     * Do not accept an arbitrary MP3: the imported WAV is verified as PCM16. */
    private static void appendOpeningMusic(OutputStream out,File wav,int rate,int channels,
                                           long frames)throws IOException{
        WavTools.Piece piece=WavTools.inspect(wav);
        byte[] format=piece.format;
        int sourceChannels=shortLE(format,2),sourceRate=intLE(format,4);
        if(shortLE(format,0)!=1||shortLE(format,14)!=16||
           sourceChannels<1||sourceChannels>2||sourceRate<8000||sourceRate>96000||
           piece.dataLength<sourceRate*sourceChannels*2L*5L||
           piece.dataLength>6L*1024*1024)
            throw new IOException("오프닝 A안은 6초 길이의 PCM 16-bit WAV로 선택하세요.");
        byte[] source=new byte[(int)piece.dataLength];
        try(RandomAccessFile file=new RandomAccessFile(wav,"r")){
            file.seek(piece.dataOffset);
            file.readFully(source);
        }
        long sourceFrames=piece.dataLength/(sourceChannels*2L);
        byte[] chunk=new byte[8192];
        int at=0;
        for(long f=0;f<frames;f++){
            long sourceFrame=Math.min(sourceFrames-1,(f*sourceRate)/rate);
            int base=(int)(sourceFrame*sourceChannels*2L);
            int left=(short)shortLE(source,base);
            int right=sourceChannels==2?(short)shortLE(source,base+2):left;
            double seconds=(double)f/rate;
            double fade=Math.min(1.,Math.max(0.,(6.-seconds)/0.9));
            double gain=0.66*fade;
            for(int channel=0;channel<channels;channel++){
                int current=channel==0?left:right;
                short sample=(short)Math.max(Short.MIN_VALUE,
                    Math.min(Short.MAX_VALUE,Math.round(current*gain)));
                chunk[at++]=(byte)(sample&255);
                chunk[at++]=(byte)((sample>>8)&255);
                if(at>=chunk.length-4){out.write(chunk,0,at);at=0;}
            }
        }
        if(at>0)out.write(chunk,0,at);
    }
    private static Timeline makeTimeline(Context context,List<NewsStory> stories,
                                         List<File> waves,List<List<Long>> sentenceFrames,
                                         File pcm) throws IOException {
        if(stories.size()!=waves.size()||stories.isEmpty()||
           sentenceFrames.size()!=waves.size())throw new IOException("뉴스 카드와 WAV의 개수가 일치하지 않습니다.");
        List<WavTools.Piece> pieces=new ArrayList<>();byte[] expected=null;
        for(File wav:waves){
            WavTools.Piece part=WavTools.inspect(wav);
            if(expected!=null&&!Arrays.equals(expected,part.format))throw new IOException("뉴스 WAV 형식이 서로 다릅니다.");
            expected=part.format;pieces.add(part);
        }
        if(shortLE(expected,0)!=1||shortLE(expected,14)!=16)throw new IOException("MP4 변환은 PCM 16-bit WAV를 지원합니다.");
        int channels=shortLE(expected,2), rate=intLE(expected,4);
        if(channels<1||channels>2||rate<8000||rate>48000)throw new IOException("지원하지 않는 음성 WAV 채널 또는 샘플 속도입니다.");
        int frameBytes=channels*2;long frameCount=0;
        List<Span> spans=new ArrayList<>();
        try(OutputStream out=new BufferedOutputStream(new FileOutputStream(pcm))){
            long introFrames=6L*rate;
            File introWav=OpeningMusicA.ensureInstalled(context);
            appendOpeningMusic(out,introWav,rate,channels,introFrames);
            frameCount+=introFrames;
            for(int i=0;i<pieces.size();i++){
                long start=toUs(frameCount,rate);
                long gapFrames=rate*3L/10; // Cards appear ~0.3s before narration.
                writeSilence(out,gapFrames*frameBytes);frameCount+=gapFrames;
                WavTools.Piece part=pieces.get(i);
                if(part.dataLength%frameBytes!=0)throw new IOException("WAV 데이터의 샘플 경계가 맞지 않습니다.");
                List<Long> lengths=sentenceFrames.get(i);
                int expectedSentences=NewsSentenceTimeline.split(stories.get(i).spokenText()).size();
                if(lengths==null||lengths.size()!=expectedSentences)
                    throw new IOException("문장별 WAV 타임라인이 빠졌습니다.");
                long audioStart=frameCount;
                long[] cueEnds=new long[lengths.size()];
                long totalFrames=0;
                for(int cue=0;cue<lengths.size();cue++){
                    long duration=lengths.get(cue);
                    if(duration<=0)throw new IOException("빈 문장의 WAV가 있습니다.");
                    totalFrames+=duration;
                    cueEnds[cue]=toUs(audioStart+totalFrames,rate);
                }
                if(totalFrames!=part.dataLength/frameBytes)
                    throw new IOException("문장별 재생 시간과 전체 WAV 길이가 다릅니다.");
                appendWave(out,part);
                frameCount+=totalFrames;
                spans.add(new Span(stories.get(i),start,toUs(frameCount,rate),
                    toUs(audioStart,rate),cueEnds));
            }
            long tailFrames=rate/3;writeSilence(out,tailFrames*frameBytes);frameCount+=tailFrames;
        }
        return new Timeline(pcm,rate,channels,toUs(frameCount,rate),spans);
    }
    public static File create(Context context,List<NewsStory> stories,List<File> waves,
                              List<List<Long>> sentenceFrames,File output,Progress progress)throws Exception {
        File folder=new File(context.getCacheDir(),"video_work_"+System.nanoTime());
        if(!folder.mkdirs())throw new IOException("임시 영상 폴더를 만들 수 없습니다.");
        File raw=new File(folder,"audio.pcm"),aac=new File(folder,"audio.m4a"),video=new File(folder,"cards.mp4");
        try {
            Timeline timeline=makeTimeline(context,stories,waves,sentenceFrames,raw);
            if(timeline.endUs>12L*60*US_PER_S)throw new IOException("한 영상은 12분 이내 원고를 사용하세요.");
            progress.message("WAV 음성을 AAC로 변환 중");
            encodeAudio(raw,aac,timeline.sampleRate,timeline.channels);
            progress.message("뉴스 카드 영상 렌더링 중");
            NewsCoverMeta coverMeta=NewsCoverMeta.now();
            encodeVideo(context,video,timeline,stories.get(0).articleCount>0?
                stories.get(0).articleCount:stories.size(),coverMeta,progress);
            progress.message("MP4 음성·영상 결합 중");
            join(video,aac,output);
            if(!output.isFile()||output.length()<1024)throw new IOException("MP4가 완성되지 않았습니다.");
            // Save a JPEG with the same cover pixels and date as the video's first frame.
            NewsCoverExporter.save(context,coverMeta,
                stories.get(0).articleCount>0?stories.get(0).articleCount:stories.size(),
                output);
            progress.message("MP4와 카카오톡용 표지 JPG 생성 완료");
            return output;
        } finally {
            File[] files=folder.listFiles();if(files!=null)for(File f:files)f.delete();folder.delete();
        }
    }
    private static void encodeAudio(File pcm,File out,int rate,int channels)throws Exception {
        MediaCodec codec=MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        MediaMuxer mux=null;FileInputStream in=null;boolean muxStarted=false;
        try {
            MediaFormat format=MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC,rate,channels);
            format.setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            format.setInteger(MediaFormat.KEY_BIT_RATE,channels==1?96000:128000);
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,PCM_CHUNK);
            codec.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
            codec.start();in=new FileInputStream(pcm);
            mux=new MediaMuxer(out.getAbsolutePath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            byte[] buf=new byte[PCM_CHUNK];int frameBytes=channels*2;long sentFrames=0;
            boolean inputDone=false,outputDone=false;int track=-1,idles=0;
            while(!outputDone) {
                if(!inputDone) {
                    int index=codec.dequeueInputBuffer(10000);
                    if(index>=0) {
                        ByteBuffer dst=codec.getInputBuffer(index);if(dst==null)throw new IOException("AAC input buffer missing");dst.clear();
                        int max=Math.min(dst.remaining(),buf.length);max-=max%frameBytes;
                        if(max<=0)throw new IOException("AAC input buffer too small");
                        int got=0;while(got<max){int n=in.read(buf,got,max-got);if(n<0)break;got+=n;}
                        if(got>0 && got%frameBytes!=0)throw new IOException("AAC PCM input misaligned");
                        if(got==0){codec.queueInputBuffer(index,0,0,toUs(sentFrames,rate),MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputDone=true;}
                        else {dst.put(buf,0,got);codec.queueInputBuffer(index,0,got,toUs(sentFrames,rate),0);sentFrames+=got/frameBytes;}
                    }
                }
                int i=codec.dequeueOutputBuffer(info,10000);
                if(i==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if(muxStarted)throw new IOException("AAC format changed twice");
                    track=mux.addTrack(codec.getOutputFormat());mux.start();muxStarted=true;
                } else if(i>=0) {
                    ByteBuffer encoded=codec.getOutputBuffer(i);
                    if(encoded==null)throw new IOException("AAC output buffer missing");
                    if(info.size>0 && (info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0){
                        if(!muxStarted)throw new IOException("AAC muxer not started");
                        encoded.position(info.offset);encoded.limit(info.offset+info.size);
                        mux.writeSampleData(track,encoded,info);
                    }
                    outputDone=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
                    codec.releaseOutputBuffer(i,false);idles=0;
                }else if(i==MediaCodec.INFO_TRY_AGAIN_LATER && ++idles>150)throw new IOException("AAC encoder stalled");
            }
        }finally{
            if(in!=null)in.close();
            try{codec.stop();}catch(Exception ignored){}codec.release();
            if(mux!=null){if(muxStarted)mux.stop();mux.release();}
        }
    }
    private static void encodeVideo(Context context,File output,Timeline t,int articleCount,NewsCoverMeta meta,Progress progress)throws Exception {
        MediaCodec codec=MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        MediaMuxer mux=null;Surface inputSurface=null;EglFrameRenderer renderer=null;
        Bitmap reusableSlide=null;
        boolean started=false,muxStarted=false;
        try {
            MediaFormat fmt=MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,VIDEO_WIDTH,VIDEO_HEIGHT);
            fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            fmt.setInteger(MediaFormat.KEY_BIT_RATE,3000000);
            fmt.setInteger(MediaFormat.KEY_FRAME_RATE,FPS);
            fmt.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);
            codec.configure(fmt,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface=codec.createInputSurface();codec.start();started=true;
            mux=new MediaMuxer(output.getAbsolutePath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            renderer=new EglFrameRenderer(inputSurface,VIDEO_WIDTH,VIDEO_HEIGHT);
            inputSurface=null; // Renderer owns and releases the surface.
            int track=-1;
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            int frames=(int)((t.endUs*FPS+US_PER_S-1)/US_PER_S);
            reusableSlide=Bitmap.createBitmap(VIDEO_WIDTH,VIDEO_HEIGHT,Bitmap.Config.ARGB_8888);
            int previousCard=-10;float previousScroll=Float.NaN;
            NewsCardPainter.ScrollPlan scrollPlan=null;
            NewsScrollMath.AudioCurve scrollCurve=null;
            long cardStart=0,cardEnd=0;
            for(int f=0;f<frames;f++) {
                long us=f*US_PER_S/FPS;
                int which;
                // Full six-second fixed opening cover, including all of the music.
                if(us<6L*US_PER_S)which=-2;
                else {
                    which=0;
                    // The previous article remains on screen for its final 300ms
                    // after its narration ends, while the next article uses
                    // the remaining 200ms silent pre-roll before its voice starts.
                    for(int i=0;i<t.spans.size();i++)
                        if(us>=t.spans.get(i).startUs+(i==0?0L:SCROLL_DELAY_US))which=i;
                }
                Span current=which>=0?t.spans.get(which):null;
                int activeSentence=current!=null?current.activeSentence(us):-1;
                if(which!=previousCard){
                    if(which==-2) {cardStart=0;cardEnd=6L*US_PER_S;}
                    else {
                        cardStart=current.startUs;
                        cardEnd=which+1<t.spans.size()?t.spans.get(which+1).startUs:t.endUs;
                    }
                    // Prepare text layout and voice-aligned smoothing once per article.
                    scrollPlan=current!=null?NewsCardPainter.prepareScroll(current.story):null;
                    scrollCurve=current!=null?NewsScrollMath.audioCurve(
                        current.audioStartUs,current.sentenceEndUs,
                        scrollPlan.sentenceStarts,scrollPlan.totalCharacters,
                        scrollPlan.maxOffset):null;
                }
                // Each sentence endpoint matches actual WAV length and prose position.
                // C1-continuous interpolation follows the voice with a 300ms lag.
                float scrollPixels=scrollCurve==null?0f:
                    scrollCurve.delayedPosition(us,SCROLL_DELAY_US);
                if(which!=previousCard || Float.isNaN(previousScroll) ||
                   Math.abs(scrollPixels-previousScroll)>0.0001f){
                    Bitmap card=current!=null?
                        NewsCardPainter.renderScrolling(current.story,Math.max(0,which),
                            articleCount,meta,activeSentence,scrollPlan,scrollPixels,reusableSlide):
                        NewsCardPainter.render(context,null,0,articleCount,which+2,meta);
                    renderer.upload(card);
                    if(card!=reusableSlide)card.recycle();
                    previousCard=which;
                    previousScroll=scrollPixels;
                }
                float motion=cardEnd<=cardStart?1f:(float)(us-cardStart)/(cardEnd-cardStart);
                // Keep news text stable and sharp during smooth upward scroll.
                float zoom=1.0f; // Stable cover/thumbnail and legible scrolling text.
                renderer.draw(us*1000L,zoom);
                int newTrack=drainVideo(codec,mux,track,info,false);
                if(newTrack>=0){track=newTrack;muxStarted=true;}
                if(f%Math.max(1,FPS*8)==0)progress.message("뉴스 화면 렌더링 " +(100L*f/Math.max(1,frames))+"%");
            }
            codec.signalEndOfInputStream();
            boolean eos=false;int stalls=0;
            while(!eos){
                int index=codec.dequeueOutputBuffer(info,15000);
                if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                    if(muxStarted)throw new IOException("Video format changed twice");
                    track=mux.addTrack(codec.getOutputFormat());mux.start();muxStarted=true;
                }else if(index>=0){
                    writeVideoBuffer(codec,mux,track,info,index);eos=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;stalls=0;
                }else if(index==MediaCodec.INFO_TRY_AGAIN_LATER&&++stalls>150)throw new IOException("H264 video encoder stalled");
            }
        } finally {
            if(reusableSlide!=null&&!reusableSlide.isRecycled())reusableSlide.recycle();
            if(renderer!=null)renderer.close();
            if(inputSurface!=null)inputSurface.release();
            if(started){try{codec.stop();}catch(Exception ignored){}}
            codec.release();
            if(mux!=null){if(muxStarted)mux.stop();mux.release();}
        }
    }
    private static int drainVideo(MediaCodec codec,MediaMuxer mux,int track,MediaCodec.BufferInfo info,boolean eos) throws IOException {
        while(true) {
            int index=codec.dequeueOutputBuffer(info,eos?15000:0);
            if(index==MediaCodec.INFO_TRY_AGAIN_LATER)return track;
            if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                if(track>=0)throw new IOException("H264 format changed twice");
                track=mux.addTrack(codec.getOutputFormat());mux.start();
            }else if(index>=0)writeVideoBuffer(codec,mux,track,info,index);
        }
    }
    private static void writeVideoBuffer(MediaCodec codec,MediaMuxer mux,int track,MediaCodec.BufferInfo info,int index)throws IOException {
        ByteBuffer output=codec.getOutputBuffer(index);
        if(output==null)throw new IOException("H264 encoder output missing");
        if((info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)!=0)info.size=0;
        if(info.size>0){
            if(track<0)throw new IOException("H264 track not yet ready");
            output.position(info.offset);output.limit(info.offset+info.size);
            mux.writeSampleData(track,output,info);
        }
        codec.releaseOutputBuffer(index,false);
    }
    private static void join(File video,File audio,File dest)throws Exception {
        MediaExtractor v=new MediaExtractor(), a=new MediaExtractor();MediaMuxer mux=null;boolean started=false;
        try {
            v.setDataSource(video.getAbsolutePath());a.setDataSource(audio.getAbsolutePath());
            if(v.getTrackCount()<1||a.getTrackCount()<1)throw new IOException("인코딩된 MP4 트랙을 찾을 수 없습니다.");
            v.selectTrack(0);a.selectTrack(0);
            mux=new MediaMuxer(dest.getAbsolutePath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int vid=mux.addTrack(v.getTrackFormat(0)),aud=mux.addTrack(a.getTrackFormat(0));
            mux.start();started=true;
            ByteBuffer videoBytes=ByteBuffer.allocateDirect(4*1024*1024);
            ByteBuffer audioBytes=ByteBuffer.allocateDirect(256*1024);
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            long videoUs=v.getSampleTime(),audioUs=a.getSampleTime();
            while(videoUs>=0 || audioUs>=0){
                if(audioUs<0 || (videoUs>=0 && videoUs<=audioUs)){
                    copySample(v,mux,vid,videoBytes,info);
                    v.advance();videoUs=v.getSampleTime();
                }else{
                    copySample(a,mux,aud,audioBytes,info);
                    a.advance();audioUs=a.getSampleTime();
                }
            }
        }finally{
            v.release();a.release();
            if(mux!=null){if(started)mux.stop();mux.release();}
        }
    }
    private static void copySample(MediaExtractor extractor,MediaMuxer mux,int index,
                                   ByteBuffer buffer,MediaCodec.BufferInfo info)throws IOException {
        buffer.clear();int n=extractor.readSampleData(buffer,0);
        if(n<0)throw new IOException("MP4 트랙이 예상보다 빨리 종료됐습니다.");
        info.set(0,n,extractor.getSampleTime(),extractor.getSampleFlags());
        buffer.position(0);buffer.limit(n);mux.writeSampleData(index,buffer,info);
    }
}
