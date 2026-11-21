package com.newsvoice.tts;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.text.InputType;
import androidx.work.WorkManager;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ExistingPeriodicWorkPolicy;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * NewsVoice: offline device-engine Korean TTS sample.
 * Voice gender is not exposed by Android TTS API; user must verify chosen voice.
 * synthesizeToFile format depends on the TTS engine: this app verifies RIFF/WAVE.
 */
public final class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int DARK = 0xff0e2942;
    private static final int MINT = 0xff157e71;
    private TextToSpeech tts;
    private boolean ready = false;
    private EditText editor;
    private Spinner voicesSpinner, enginesSpinner, presetsSpinner;
    private TextView status, speedValue, pitchValue;
    private SeekBar speedBar, pitchBar;
    private final List<Voice> koreanVoices = new ArrayList<>();
    private final List<TextToSpeech.EngineInfo> installedEngines = new ArrayList<>();
    private static final String[] PRESET_NAMES = {"표준형 뉴스 앵커", "차분형 뉴스 앵커", "명료형 뉴스 앵커", "부드러운형 뉴스 앵커", "직접 조절"};
    private static final float[] PRESET_RATES = {0.95f, 0.90f, 1.00f, 0.90f};
    private static final float[] PRESET_PITCHES = {1.00f, 0.95f, 1.05f, 1.05f};
    private String currentEngine = "";
    private boolean engineUiReady = false;
    private int selectedPreset = 0;
    private EditText serverEdit, tokenEdit;
    private CheckBox automaticCheck;
    private long taskToken = 0;
    private int lastQueuedIndex = -1;
    private final Handler main = new Handler(Looper.getMainLooper());

    private static int dp(Activity a, int d) { return Math.round(d*a.getResources().getDisplayMetrics().density); }
    private LinearLayout.LayoutParams lp(int w, int h, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w < 0 ? w : dp(this,w), h < 0 ? h : dp(this,h));
        p.topMargin = dp(this,top); return p;
    }
    private TextView text(String s, int size, boolean bold) {
        TextView t = new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(DARK);
        if(bold)t.setTypeface(null,Typeface.BOLD);return t;
    }
    private static GradientDrawable rounded(int color, int r) {
        GradientDrawable d = new GradientDrawable();d.setColor(color);d.setCornerRadius(r);return d;
    }
    private Button btn(String label, int bg, int foreground) {
        Button b = new Button(this); b.setAllCaps(false);b.setText(label);b.setTextSize(14);
        b.setTextColor(foreground);b.setBackground(rounded(bg,dp(this,13)));return b;
    }
    private void updateStatus(String message) { main.post(() -> { if(status != null) status.setText(message); }); }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xfff7fbff);getWindow().setNavigationBarColor(0xfff7fbff);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView sc = new ScrollView(this);sc.setFillViewport(true);sc.setBackgroundColor(0xfff7fbff);
        LinearLayout body = new LinearLayout(this);body.setPadding(dp(this,20),dp(this,27),dp(this,20),dp(this,30));body.setOrientation(LinearLayout.VERTICAL);sc.addView(body);
        TextView brand=text("●  뉴스보이스",19,true);brand.setTextColor(MINT);body.addView(brand);
        TextView headline=text("한국어 뉴스 음성 자동 생성",24,true);body.addView(headline,lp(-1,-2,14));
        TextView sub=text("원고를 입력하고 한국어 음성을 선택해 낭독하세요.",13,false);sub.setTextColor(0xff61778e);body.addView(sub,lp(-1,-2,5));
        body.addView(text("01  TTS 엔진과 한국어 목소리 · 미리 듣기",17,true),lp(-1,-2,25));
        TextView enginesHelp=text("설치된 삼성·구글 등 TTS 엔진 중 선택합니다. 표시되는 엔진과 음성 개수는 휴대폰마다 다릅니다.",12,false);
        enginesHelp.setTextColor(0xff61778e);body.addView(enginesHelp,lp(-1,-2,6));
        enginesSpinner = new Spinner(this); enginesSpinner.setBackground(rounded(0xffe7f1f6,dp(this,12)));
        body.addView(enginesSpinner,lp(-1,52,9));
        voicesSpinner = new Spinner(this); voicesSpinner.setBackground(rounded(0xffe7f1f6,dp(this,12)));
        body.addView(voicesSpinner,lp(-1,52,10));
        body.addView(text("아나운서 낭독 스타일",14,true),lp(-1,-2,14));
        presetsSpinner = new Spinner(this);presetsSpinner.setBackground(rounded(0xffe7f1f6,dp(this,12)));
        body.addView(presetsSpinner,lp(-1,52,8));
        TextView presetNote=text("스타일 4종은 같은 목소리의 속도·음높이를 조정합니다. 서로 다른 성우 4명이 아닙니다.",12,false);
        presetNote.setTextColor(0xff61778e);body.addView(presetNote,lp(-1,-2,5));
        android.content.SharedPreferences voicePrefs=AppPrefs.get(this);
        selectedPreset=Math.max(0,Math.min(PRESET_NAMES.length-1,voicePrefs.getInt("preset",0)));
        ArrayAdapter<String> presetAdapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,PRESET_NAMES);
        presetsSpinner.setAdapter(presetAdapter);
        presetsSpinner.setSelection(selectedPreset);
        speedValue=text("낭독 속도",14,true);body.addView(speedValue,lp(-1,-2,16));
        speedBar = new SeekBar(this);speedBar.setMax(13);
        speedBar.setProgress(Math.max(0,Math.min(13,Math.round((AppPrefs.speed(this)-0.65f)/0.05f))));
        body.addView(speedBar,lp(-1,-2,2));
        pitchValue=text("음높이",14,true);body.addView(pitchValue,lp(-1,-2,10));
        pitchBar = new SeekBar(this);pitchBar.setMax(8);
        pitchBar.setProgress(Math.max(0,Math.min(8,Math.round((AppPrefs.pitch(this)-0.80f)/0.05f))));
        body.addView(pitchBar,lp(-1,-2,2));
        SeekBar.OnSeekBarChangeListener change=new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar s,int progress,boolean user){
                speedValue.setText(String.format(Locale.KOREA,"낭독 속도 %.2f×",rate()));
                pitchValue.setText(String.format(Locale.KOREA,"음높이 %.2f",pitch()));
                if(user){
                    selectedPreset=4;
                    presetsSpinner.setSelection(4);
                    if(ready)persistVoiceSettings();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar s){}
            @Override public void onStopTrackingTouch(SeekBar s){}
        };
        speedBar.setOnSeekBarChangeListener(change);pitchBar.setOnSeekBarChangeListener(change);
        speedValue.setText(String.format(Locale.KOREA,"낭독 속도 %.2f×",rate()));
        pitchValue.setText(String.format(Locale.KOREA,"음높이 %.2f",pitch()));
        presetsSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){
                if(position!=selectedPreset){
                    selectedPreset=position;
                    if(position>=0 && position<4){
                        speedBar.setProgress(Math.round((PRESET_RATES[position]-0.65f)/0.05f));
                        pitchBar.setProgress(Math.round((PRESET_PITCHES[position]-0.80f)/0.05f));
                    }
                    if(ready)persistVoiceSettings();
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
        });
        body.addView(text("02  뉴스 원고",17,true),lp(-1,-2,21));
        editor = new EditText(this);editor.setTextSize(16);editor.setTextColor(DARK);editor.setGravity(Gravity.TOP);editor.setPadding(dp(this,14),dp(this,14),dp(this,14),dp(this,14));
        editor.setHint("여기에 한국어 뉴스 원고를 붙여 넣으세요.");editor.setMinLines(8);editor.setBackgroundResource(R.drawable.bg_editor);
        editor.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        body.addView(editor,lp(-1,-2,10));
        Button sample=btn("예시 뉴스 넣기",0xffe7f2f3,MINT);body.addView(sample,lp(-1,46,10));
        sample.setOnClickListener(v->editor.setText("안녕하십니까. 오늘의 주요 뉴스를 전해드리겠습니다.\n첫 번째 소식입니다. 주요 경제 동향과 기업 투자 현황을 살펴보겠습니다.\n이상으로 뉴스를 마치겠습니다. 감사합니다."));
        body.addView(text("03  재생 · 저장",17,true),lp(-1,-2,25));
        status=text("음성 엔진을 준비하고 있습니다.",13,false);status.setTextColor(0xff61778e);body.addView(status,lp(-1,-2,8));
        LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.HORIZONTAL);body.addView(controls,lp(-1,53,15));
        Button preview=btn("미리 듣기",0xffe3eff2,DARK);controls.addView(preview,new LinearLayout.LayoutParams(0,-1,1));
        Button stop=btn("정지",0xffe3eff2,DARK);LinearLayout.LayoutParams stopP=new LinearLayout.LayoutParams(0,-1,1);stopP.leftMargin=dp(this,6);controls.addView(stop,stopP);
        Button play=btn("읽기 시작",0xff63ddbd,0xff0b2936);LinearLayout.LayoutParams playP=new LinearLayout.LayoutParams(0,-1,1);playP.leftMargin=dp(this,6);controls.addView(play,playP);
        Button save=btn("긴 원고 WAV 자동 생성 및 다운로드 저장",MINT,Color.WHITE);body.addView(save,lp(-1,55,12));
        TextView foot=text("엔진·목소리·낭독 스타일을 저장하고 WAV 자동 생성에도 동일하게 적용합니다. 여성 음성 여부와 낭독 품질은 미리 듣기로 확인하세요. 긴 원고는 자동 분할·결합합니다.",12,false);
        foot.setTextColor(0xff61778e);body.addView(foot,lp(-1,-2,16));
        preview.setOnClickListener(v->speakNow("안녕하십니까. 오늘의 주요 뉴스를 전해드리겠습니다.",true));
        play.setOnClickListener(v->speakNow(editor.getText().toString(),false));
        stop.setOnClickListener(v->{taskToken++;if(tts!=null)tts.stop();updateStatus("낭독 정지됨");});
        save.setOnClickListener(v->queueManualSave());
        buildAutomationControls(body);
        setContentView(sc);
        try {
            currentEngine=AppPrefs.enginePackage(this);
            tts = new TextToSpeech(this, this, currentEngine.isEmpty()?null:currentEngine);
        } catch (RuntimeException e) {
            updateStatus("TTS 시작 오류: " + e.getClass().getSimpleName());
        }
    }
    private float rate() { return 0.65f+speedBar.getProgress()*0.05f; }
    private float pitch() { return 0.80f+pitchBar.getProgress()*0.05f; }

    @Override public void onInit(int code) {
        // Some devices report TTS initialization asynchronously or lack a Korean voice.
        main.post(() -> finishInitialization(code));
    }
    private void finishInitialization(int code) {
        if(isFinishing() || isDestroyed()) return;
        if(code != TextToSpeech.SUCCESS || tts == null) {
            updateStatus("TTS 엔진을 초기화할 수 없습니다. 음성 설정을 확인하세요.");
            return;
        }
        try {
            int lang = tts.setLanguage(Locale.KOREAN);
            if(lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                updateStatus("한국어 음성 데이터가 없습니다. 삼성 TTS 한국어 음성을 설치하세요.");
                return;
            }
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) {
                    updateStatus(utteranceId.startsWith("save_") ? "WAV 음성을 생성하는 중..." : "뉴스를 낭독하고 있습니다.");
                }
                @Override public void onDone(String utteranceId) {
                    if(utteranceId.startsWith("save_")) updateStatus("WAV 구간 생성 완료");
                    else if(utteranceId.equals("play_"+taskToken+"_"+lastQueuedIndex)) updateStatus("낭독 완료");
                }
                @Override public void onError(String utteranceId) {
                    updateStatus("음성 합성 오류. 다른 한국어 음성을 선택해 주세요.");
                }
            });
            String active=tts.getCurrentEngine();
            if(active==null||active.isEmpty()){
                updateStatus("현재 TTS 엔진을 확인하지 못했습니다.");
                return;
            }
            if(!currentEngine.isEmpty()&&!currentEngine.equals(active)){
                updateStatus("선택한 TTS 엔진이 실행되지 않았습니다. 설치 여부를 확인하세요.");
                return;
            }
            currentEngine=active;
            if(!engineUiReady){
                installedEngines.clear();
                try{if(tts.getEngines()!=null)installedEngines.addAll(tts.getEngines());}
                catch(RuntimeException ignored){}
                List<String> descriptions=new ArrayList<>();
                int which=-1;
                for(int i=0;i<installedEngines.size();i++){
                    TextToSpeech.EngineInfo e=installedEngines.get(i);
                    descriptions.add(String.valueOf(e.label)+" · "+e.name);
                    if(e.name.equals(currentEngine))which=i;
                }
                if(installedEngines.isEmpty()){
                    descriptions.add("기본 TTS 엔진 · "+currentEngine);
                }
                ArrayAdapter<String> enginesAdapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,descriptions);
                enginesSpinner.setAdapter(enginesAdapter);
                if(which>=0)enginesSpinner.setSelection(which);
                engineUiReady=true;
                enginesSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
                    @Override public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){
                        if(!engineUiReady||position<0||position>=installedEngines.size())return;
                        String packageName=installedEngines.get(position).name;
                        if(!packageName.equals(currentEngine))restartWithEngine(packageName);
                    }
                    @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
                });
            }
            List<Voice> voices = new ArrayList<>();
            try {
                if(tts.getVoices() != null) {
                    for(Voice v : tts.getVoices())
                        if(v != null && v.getLocale() != null && "ko".equals(v.getLocale().getLanguage())) voices.add(v);
                }
            } catch (RuntimeException ignored) {
                // Some system TTS engines omit a usable list; default Korean voice remains available.
            }
            voices.sort(Comparator.comparingInt((Voice v)->scoreVoice(v)).reversed());
            koreanVoices.clear();
            koreanVoices.addAll(voices);
            List<String> names = new ArrayList<>();
            for(Voice v : koreanVoices) names.add(v.getName() + " · " + v.getLocale().toLanguageTag() +
                (v.isNetworkConnectionRequired()?" · 인터넷 음성":" · 오프라인 음성"));
            if(names.isEmpty()) names.add("기본 한국어 목소리");
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names);
            voicesSpinner.setAdapter(adapter);
            voicesSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                    if(pos < koreanVoices.size() && tts != null) {
                        try {
                            if(tts.setVoice(koreanVoices.get(pos))==TextToSpeech.SUCCESS&&ready)persistVoiceSettings();
                        } catch(RuntimeException ignored) {}
                    }
                }
                @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });
            String savedVoice = AppPrefs.voiceName(this);
            for(int v=0;v<koreanVoices.size();v++){
                if(savedVoice.equals(koreanVoices.get(v).getName())) {voicesSpinner.setSelection(v);break;}
            }
            ready = true;
            updateStatus("준비 완료 · TTS 엔진 "+currentEngine+" · 한국어 목소리 "+koreanVoices.size()+"개");
        } catch(RuntimeException e) {
            ready = false;
            updateStatus("TTS 초기화 오류: " + e.getClass().getSimpleName());
        }
    }
    private void restartWithEngine(String packageName){
        try{
            ready=false;
            taskToken++;
            updateStatus("TTS 엔진을 전환하는 중: "+packageName);
            if(tts!=null){tts.stop();tts.shutdown();}
            currentEngine=packageName;
            AppPrefs.get(this).edit().putString("engine",packageName).remove("voice").apply();
            koreanVoices.clear();
            voicesSpinner.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,
                new String[]{"새 엔진에서 한국어 목소리를 확인하는 중..."}));
            tts=new TextToSpeech(this,this,packageName);
        }catch(RuntimeException e){
            ready=false;
            updateStatus("TTS 엔진 전환 실패: "+e.getClass().getSimpleName());
        }
    }
    private int scoreVoice(Voice v){
        String n=v.getName().toLowerCase(Locale.ROOT);int s=0;
        if(n.matches(".*(female|woman|yuna|sunhi|seoyeon|heami|sora|jiyeon|suji|여성|서연|유나|선희).*"))s+=10;
        if(n.matches(".*(male|남성).*"))s-=20;
        if(!v.isNetworkConnectionRequired())s+=3;
        return s;
    }
    private void configure() {
        tts.setLanguage(Locale.KOREAN);
        int selected=voicesSpinner.getSelectedItemPosition();
        if(selected>=0&&selected<koreanVoices.size())tts.setVoice(koreanVoices.get(selected));
        tts.setSpeechRate(rate());tts.setPitch(pitch());
    }
    private List<String> chunks(String text){
        List<String> result=new ArrayList<>();int max=Math.min(1000,TextToSpeech.getMaxSpeechInputLength()-100);
        String remaining=text.trim();
        while(remaining.length()>0){
            if(remaining.length()<=max){result.add(remaining);break;}
            int limit=Math.min(max,remaining.length());int cut=-1;
            for(int i=limit-1;i>=limit/2;i--){char c=remaining.charAt(i);if(c==' '||c=='\n'||c=='.'||c=='!'||c=='?'){cut=i+1;break;}}
            if(cut<1)cut=limit;
            result.add(remaining.substring(0,cut).trim());remaining=remaining.substring(cut).trim();
        }return result;
    }
    private void speakNow(String src,boolean preview){
        if(!ready){updateStatus("TTS 엔진이 아직 준비되지 않았습니다.");return;}
        if(src.trim().isEmpty()){updateStatus("원고를 입력하세요.");return;}
        try {
            taskToken++;
            tts.stop();
            configure();
            persistVoiceSettings();
            List<String> segments = chunks(src);
            lastQueuedIndex = segments.size()-1;
            for(int i=0;i<segments.size();i++) {
                int code = tts.speak(segments.get(i), i==0 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD, new Bundle(), "play_"+taskToken+"_"+i);
                if(code==TextToSpeech.ERROR) {updateStatus("낭독 명령을 실행하지 못했습니다.");return;}
            }
            updateStatus(preview ? "목소리 미리 듣기 중..." : "총 "+segments.size()+"개 구간으로 나누어 낭독합니다.");
        } catch(RuntimeException e) {
            updateStatus("낭독 실행 오류: " + e.getClass().getSimpleName());
        }
    }
    private void persistVoiceSettings(){
        int selected=voicesSpinner.getSelectedItemPosition();
        String voice=(selected>=0 && selected<koreanVoices.size())?koreanVoices.get(selected).getName():"";
        AppPrefs.get(this).edit().putString("engine",currentEngine).putString("voice",voice)
            .putFloat("speed",rate()).putFloat("pitch",pitch()).putInt("preset",selectedPreset).apply();
    }
    private void queueManualSave(){
        if(!ready){updateStatus("한국어 TTS 엔진이 준비되지 않았습니다.");return;}
        String src=editor.getText().toString().trim();
        if(src.isEmpty()){updateStatus("저장할 뉴스 원고를 입력하세요.");return;}
        if(src.length()>60000){updateStatus("원고는 60,000자 이내로 입력하세요.");return;}
        try {
            persistVoiceSettings();
            File inbox=new File(getFilesDir(),"inbox");
            if(!inbox.exists()&&!inbox.mkdirs())throw new Exception("inbox 저장 실패");
            String name="news_"+UUID.randomUUID().toString().replace("-","")+".txt";
            try(FileOutputStream out=new FileOutputStream(new File(inbox,name))){out.write(src.getBytes(StandardCharsets.UTF_8));}
            Data data=new Data.Builder().putString(NewsVoiceWorker.INPUT_LOCAL_FILE,name).build();
            OneTimeWorkRequest task=new OneTimeWorkRequest.Builder(NewsVoiceWorker.class).setInputData(data).build();
            WorkManager.getInstance(this).enqueueUniqueWork("newsvoice_manual",ExistingWorkPolicy.APPEND_OR_REPLACE,task);
            updateStatus("WAV 생성 예약됨. 다운로드/NewsVoiceWAV 폴더를 확인하세요.");
        }catch(Exception e){updateStatus("수동 저장 요청 실패: "+e.getMessage());}
    }
    private void buildAutomationControls(LinearLayout body){
        body.addView(text("04  자동 뉴스 수신 · WAV 변환",17,true),lp(-1,-2,28));
        TextView note=text("서버 연결 전에는 수동 WAV만 사용하세요. 자동 수신은 HTTPS와 서버 인증키가 필요합니다. 15분 단위 확인은 정각을 보장하지 않습니다.",13,false);
        note.setTextColor(0xff61778e);body.addView(note,lp(-1,-2,7));
        android.content.SharedPreferences p=AppPrefs.get(this);
        serverEdit=new EditText(this);serverEdit.setSingleLine(true);serverEdit.setTextSize(14);
        serverEdit.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        serverEdit.setHint("https://서버주소.example");serverEdit.setText(p.getString("server",""));
        body.addView(serverEdit,lp(-1,52,11));
        tokenEdit=new EditText(this);tokenEdit.setSingleLine(true);tokenEdit.setTextSize(14);
        tokenEdit.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        tokenEdit.setHint("접근 인증키 (12자 이상)");tokenEdit.setText(p.getString("token",""));
        body.addView(tokenEdit,lp(-1,52,9));
        automaticCheck=new CheckBox(this);automaticCheck.setText("자동 뉴스 수신 사용 (약 15분 간격)");automaticCheck.setTextColor(DARK);
        automaticCheck.setChecked(p.getBoolean("auto",false));body.addView(automaticCheck,lp(-1,-2,6));
        Button settings=btn("자동 수신 설정 저장",MINT,Color.WHITE);body.addView(settings,lp(-1,50,10));
        settings.setOnClickListener(v->saveAutoSettings());
        Button sync=btn("뉴스 원고 지금 확인",0xffe7f2f3,MINT);body.addView(sync,lp(-1,50,8));
        sync.setOnClickListener(v->{
            if(!saveAutoSettings())return;
            if(!AppPrefs.get(this).getBoolean("auto",false)){updateStatus("자동 수신이 꺼져 있습니다. 활성화 후 재시도하세요.");return;}
            WorkManager.getInstance(this).enqueueUniqueWork("newsvoice_now",ExistingWorkPolicy.KEEP,
                new OneTimeWorkRequest.Builder(NewsVoiceWorker.class).build());
            updateStatus("서버 새 원고 확인 작업을 요청했습니다.");
        });
        Button refresh=btn("자동 작업 상태 새로고침",0xffe7f2f3,DARK);body.addView(refresh,lp(-1,48,8));
        refresh.setOnClickListener(v->updateStatus(AppPrefs.get(this).getString("status","아직 처리 내역 없음")));
        TextView caution=text("자동 모드에서는 WAV 파일을 갤럭시 다운로드 폴더에 저장하고 HTTPS 서버로 전송합니다. 원고와 음성을 전송하므로 신뢰할 수 있는 본인 서버만 등록하세요.",12,false);
        caution.setTextColor(0xff61778e);body.addView(caution,lp(-1,-2,12));
    }
    private boolean saveAutoSettings(){
        String base=serverEdit.getText().toString().trim().replaceAll("/+$","");
        String token=tokenEdit.getText().toString().trim();
        boolean enabled=automaticCheck.isChecked();
        if(enabled && (!base.startsWith("https://") || base.contains("?") || base.contains("#") || token.length()<12)){
            updateStatus("자동 수신은 HTTPS 주소와 12자 이상의 인증키가 필요합니다.");
            return false;
        }
        persistVoiceSettings();
        AppPrefs.get(this).edit().putString("server",base).putString("token",token).putBoolean("auto",enabled).apply();
        WorkManager wm=WorkManager.getInstance(this);
        if(enabled){
            Constraints c=new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
            PeriodicWorkRequest job=new PeriodicWorkRequest.Builder(NewsVoiceWorker.class,15,TimeUnit.MINUTES)
                    .setConstraints(c).build();
            wm.enqueueUniquePeriodicWork(NewsVoiceWorker.UNIQUE_SYNC,ExistingPeriodicWorkPolicy.UPDATE,job);
            updateStatus("자동 수신 저장됨 · 약 15분 단위 확인");
        }else{
            wm.cancelUniqueWork(NewsVoiceWorker.UNIQUE_SYNC);
            updateStatus("자동 수신 꺼짐 · 수동 WAV 생성은 계속 이용 가능");
        }
        return true;
    }
    @Override public void onDestroy(){
        taskToken++;
        if(tts!=null){
            try { tts.stop(); tts.shutdown(); } catch(RuntimeException ignored) {}
        }
        super.onDestroy();
    }
}
