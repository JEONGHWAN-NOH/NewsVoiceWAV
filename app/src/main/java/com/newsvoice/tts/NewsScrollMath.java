package com.newsvoice.tts;

/**
 * Scroll math shared by the video renderer and JVM tests.
 *
 * v5.20 fits a monotone cubic through measured sentence-WAV boundaries and
 * manuscript character positions. The curve's velocity is continuous at
 * every sentence boundary, avoiding both the constant-speed voice mismatch
 * and the stepped / stop-start motion from earlier versions.
 */
public final class NewsScrollMath {
    private NewsScrollMath(){ }

    public static float clamp(float value,float min,float max){
        return Math.max(min,Math.min(max,value));
    }

    public static float rowPosition(int[] visibleCharactersPerRow,float spokenCharacters){
        if(visibleCharactersPerRow==null||visibleCharactersPerRow.length==0)
            throw new IllegalArgumentException("뉴스 본문의 행이 없습니다.");
        float left=Math.max(0,spokenCharacters);
        for(int i=0;i<visibleCharactersPerRow.length;i++){
            int length=visibleCharactersPerRow[i];
            if(length<0)throw new IllegalArgumentException("행 길이가 잘못됐습니다.");
            if(length==0)continue;
            if(left<length)return i+left/length;
            left-=length;
        }
        return visibleCharactersPerRow.length;
    }

    public static float offset(int[] rows,float spokenCharacters,
                               float rowStep,float focusRows,float maxOffset){
        if(rowStep<=0||maxOffset<0)
            throw new IllegalArgumentException("스크롤 크기가 잘못됐습니다.");
        return clamp((rowPosition(rows,spokenCharacters)-focusRows)*rowStep,0,maxOffset);
    }

    /** Legacy constant-speed reference for old videos and regression comparisons. */
    public static float continuousOffset(long nowUs,long narrationStartUs,
                                         long narrationEndUs,float maxOffset){
        if(maxOffset<0||narrationEndUs<=narrationStartUs)
            throw new IllegalArgumentException("음성 시간 또는 스크롤 거리가 올바르지 않습니다.");
        if(nowUs<=narrationStartUs)return 0f;
        if(nowUs>=narrationEndUs)return maxOffset;
        double ratio=(double)(nowUs-narrationStartUs)/
            (double)(narrationEndUs-narrationStartUs);
        return (float)(ratio*maxOffset);
    }

    /**
     * A cached, C1-continuous monotone interpolation curve for ONE article.
     * Time anchors are exact elapsed PCM durations, not guessed TTS timings.
     * Character anchors reflect what portion of the unabridged prose was read.
     */
    public static final class AudioCurve {
        private final long[] timesUs;
        private final double[] pixels,velocity;
        private final float lastPixel;

        private AudioCurve(long[] times,double[] pixels,double[] velocity,float maxPixel){
            this.timesUs=times;this.pixels=pixels;this.velocity=velocity;
            this.lastPixel=maxPixel;
        }

        public float position(long nowUs){
            if(nowUs<=timesUs[0])return 0f;
            int end=timesUs.length-1;
            if(nowUs>=timesUs[end])return lastPixel;
            // Binary search gives O(log N) lookups for arbitrarily long scripts.
            int lo=0,hi=end;
            while(lo+1<hi){
                int mid=(lo+hi)>>>1;
                if(nowUs<timesUs[mid])hi=mid;else lo=mid;
            }
            double dt=(double)(timesUs[lo+1]-timesUs[lo]);
            double t=(nowUs-timesUs[lo])/dt;
            double t2=t*t,t3=t2*t;
            double val=(2*t3-3*t2+1)*pixels[lo]
                +(t3-2*t2+t)*dt*velocity[lo]
                +(-2*t3+3*t2)*pixels[lo+1]
                +(t3-t2)*dt*velocity[lo+1];
            return clamp((float)val,0f,lastPixel);
        }

        /**
         * Scroll trails the rendered audio timeline by a fixed number of
         * microseconds. The original PCM playback remains untouched.
         */
        public float delayedPosition(long playbackUs,long delayUs){
            if(delayUs<0)throw new IllegalArgumentException("스크롤 지연은 음수가 될 수 없습니다.");
            long queryUs=playbackUs<Long.MIN_VALUE+delayUs?
                Long.MIN_VALUE:playbackUs-delayUs;
            return position(queryUs);
        }

        public int sentenceCount(){return timesUs.length-1;}
    }

    public static AudioCurve audioCurve(long voiceStartUs,long[] sentenceEndsUs,
                                        int[] sentenceStartCharacters,int totalCharacters,
                                        float maxOffset){
        if(sentenceEndsUs==null||sentenceStartCharacters==null||
           sentenceEndsUs.length<1||
           sentenceStartCharacters.length!=sentenceEndsUs.length+1||
           totalCharacters<=0||!Float.isFinite(maxOffset)||maxOffset<0)
            throw new IllegalArgumentException("문장 시간표·본문 길이가 올바르지 않습니다.");
        int n=sentenceEndsUs.length;
        if(sentenceStartCharacters[0]!=0||
           sentenceStartCharacters[n]!=totalCharacters)
            throw new IllegalArgumentException("본문 시작·종료 문장 경계가 일치하지 않습니다.");
        long[] x=new long[n+1];x[0]=voiceStartUs;
        double[] y=new double[n+1],slope=new double[n],m=new double[n+1];
        for(int i=0;i<n;i++){
            long end=sentenceEndsUs[i];
            if(end<=x[i]||sentenceStartCharacters[i+1]<=sentenceStartCharacters[i]||
               sentenceStartCharacters[i+1]>totalCharacters)
                throw new IllegalArgumentException("문장별 실제 WAV 길이·본문 경계가 올바르지 않습니다.");
            x[i+1]=end;
            y[i+1]=maxOffset*(double)sentenceStartCharacters[i+1]/totalCharacters;
            slope[i]=(y[i+1]-y[i])/(x[i+1]-x[i]);
        }
        // Positive endpoint velocity: never deliberately freeze at sentence starts.
        m[0]=slope[0];m[n]=slope[n-1];
        for(int i=1;i<n;i++){
            // PCHIP weighted harmonic slopes: continuous velocity, no overshoot.
            double leftDuration=x[i]-x[i-1],rightDuration=x[i+1]-x[i];
            double before=slope[i-1],after=slope[i];
            if(before<=0||after<=0){
                m[i]=0;
            }else{
                double w1=2*rightDuration+leftDuration;
                double w2=rightDuration+2*leftDuration;
                m[i]=(w1+w2)/(w1/before+w2/after);
            }
        }
        return new AudioCurve(x,y,m,maxOffset);
    }
}
