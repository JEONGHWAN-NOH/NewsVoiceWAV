package com.newsvoice.tts;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.SharedPreferences;
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
import java.io.IOException;
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
    private static final int OPENING_PICKER=5110;
    private static final int SCRIPT_PICKER=5111;
    private static final int PORTRAIT_PICKER_OPENING=5121, PORTRAIT_PICKER_ANCHOR=5122, PORTRAIT_PICKER_CLOSING=5123;
    private TextView openingAStatus;
    private static final int MINT = 0xff157e71;
    private TextToSpeech tts;
    private boolean ready = false;
    private EditText editor;
    private Spinner voicesSpinner, enginesSpinner, presetsSpinner;
    private TextView status, jobStatus, etaText, speedValue, pitchValue;
    private android.widget.ProgressBar renderProgress;
    private SharedPreferences.OnSharedPreferenceChangeListener jobListener;
    private SeekBar speedBar, pitchBar;
    private static final String SAMSUNG_PACKAGE="com.samsung.SMT";
    private final List<Voice> koreanVoices = new ArrayList<>();
    private TextToSpeech samsungTestTts;
    private TextView samsungTestStatus;
    private Button samsungPreviewButton, samsungWavButton, samsungApplyButton;
    private boolean samsungWavVerified=false;
    private static final String SAMSUNG_SAMPLE="안녕하십니까. 뉴스보이스 삼성 음성 연결 시험입니다. 이 목소리가 갤럭시 글자 읽어주기의 삼성 목소리와 같은지 들어보세요.";
    private final List<TextToSpeech.EngineInfo> installedEngines = new ArrayList<>();
    private static final String[] PRESET_NAMES = {"표준형 뉴스 앵커", "차분형 뉴스 앵커", "명료형 뉴스 앵커", "부드러운형 뉴스 앵커", "직접 조절"};
    private static final float[] PRESET_RATES = {0.95f, 0.90f, 1.00f, 0.90f};
    private static final float[] PRESET_PITCHES = {1.00f, 0.95f, 1.05f, 1.05f};
    private String currentEngine = "";
    private boolean engineUiReady = false;
    private int selectedPreset = 0;
    private EditText serverEdit, tokenEdit;
    private CheckBox automaticCheck, videoCheck;
    private EditText anchorEdit;
    private long taskToken = 0;
    private int lastQueuedIndex = -1;
    private List<String> playbackSegments = new ArrayList<>();
    private int playbackIndex = -1;
    private long playbackToken = -1;
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
    private void refreshRenderProgress(){
        android.content.SharedPreferences p=AppPrefs.get(this);
        int percent=p.getInt("render_percent",0);
        boolean active=p.getBoolean("render_active",false);
        String stage=p.getString("render_stage",p.getString("status","작업 기록 없음"));
        if(renderProgress!=null)renderProgress.setProgress(percent);
        if(jobStatus!=null)jobStatus.setText((active?"영상 제작 진행 중: ":"제작 상태: ")+stage);
        if(etaText!=null){
            long ms=p.getLong("render_eta_ms",-1);
            String remaining=active?(ms<0?"남은 시간 계산 중":
                String.format(Locale.KOREA,"예상 남은 시간: 약 %02d분 %02d초",ms/60000,(ms/1000)%60)):
                (percent==100?"제작 완료 · 100%":"대기 중");
            etaText.setText("제작 진행률 "+percent+"%    |    "+remaining);
        }
    }
    private void updateStatus(String message) { main.post(() -> { if(status != null) status.setText(message); }); }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xfff7fbff);getWindow().setNavigationBarColor(0xfff7fbff);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView sc = new ScrollView(this);sc.setFillViewport(true);sc.setBackgroundColor(0xfff7fbff);
        LinearLayout body = new LinearLayout(this);body.setPadding(dp(this,20),dp(this,27),dp(this,20),dp(this,30));body.setOrientation(LinearLayout.VERTICAL);sc.addView(body);
        TextView brand=text("●  "+getString(R.string.app_display_name),19,true);brand.setTextColor(MINT);body.addView(brand);
        TextView headline=text("한국어 뉴스 음성 자동 생성",24,true);body.addView(headline,lp(-1,-2,14));
        TextView sub=text("원고를 입력하고 한국어 음성을 선택해 낭독하세요.",13,false);sub.setTextColor(0xff61778e);body.addView(sub,lp(-1,-2,5));
        body.addView(text("01  TTS 엔진과 한국어 목소리 · 미리 듣기",17,true),lp(-1,-2,25));
        TextView enginesHelp=text("설치된 삼성·구글 등 TTS 엔진 중 선택합니다. 표시되는 엔진과 음성 개수는 휴대폰마다 다릅니다.",12,false);
        enginesHelp.setTextColor(0xff61778e);body.addView(enginesHelp,lp(-1,-2,6));
        enginesSpinner = new Spinner(this); enginesSpinner.setBackground(rounded(0xffe7f1f6,dp(this,12)));
        body.addView(enginesSpinner,lp(-1,52,9));
        body.addView(text("삼성 TTS 연동 진단 (v5.7)",16,true),lp(-1,-2,15));
        TextView samsungHint=text("갤럭시 설정에서 재생되어도 다른 앱에 삼성 TTS가 공개되지 않을 수 있습니다. 진단 후 시험 재생과 WAV 저장으로 확인하세요.",12,false);
        samsungHint.setTextColor(0xff61778e);body.addView(samsungHint,lp(-1,-2,6));
        samsungTestStatus=text("삼성 엔진 상태: 진단하지 않음",13,true);
        samsungTestStatus.setTextColor(MINT);body.addView(samsungTestStatus,lp(-1,-2,8));
        Button samsungScan=btn("삼성 TTS 접근 진단",0xffe3eff2,DARK);
        body.addView(samsungScan,lp(-1,49,8));
        samsungScan.setOnClickListener(v->diagnoseSamsung());
        samsungPreviewButton=btn("삼성 TTS 시험 재생",0xffe3eff2,DARK);
        samsungPreviewButton.setEnabled(false);
        body.addView(samsungPreviewButton,lp(-1,49,7));
        samsungPreviewButton.setOnClickListener(v->previewSamsungOnly());
        samsungWavButton=btn("삼성 TTS WAV 시험 저장",0xffe3eff2,DARK);
        samsungWavButton.setEnabled(false);
        body.addView(samsungWavButton,lp(-1,49,7));
        samsungWavButton.setOnClickListener(v->saveSamsungWavTest());
        samsungApplyButton=btn("삼성 엔진을 뉴스 제작에 사용 요청",0xffe3eff2,DARK);
        samsungApplyButton.setEnabled(false);
        body.addView(samsungApplyButton,lp(-1,49,8));
        samsungApplyButton.setOnClickListener(v->{
            if(!samsungWavVerified)return;
            AppPrefs.get(this).edit().putString("engine",SAMSUNG_PACKAGE).remove("voice").apply();
            restartWithEngine(SAMSUNG_PACKAGE);
            samsungTestStatus.setText("삼성 엔진을 요청하도록 저장했습니다. 실제 선택 여부는 음색과 WAV 재생으로 확인하세요.");
        });
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
        body.addView(text("02  뉴스 원고 · 대본만 입력하면 자동 제작",17,true),lp(-1,-2,21));
        editor = new EditText(this);editor.setTextSize(16);editor.setTextColor(DARK);editor.setGravity(Gravity.TOP);editor.setPadding(dp(this,14),dp(this,14),dp(this,14),dp(this,14));
        editor.setHint("아나운서 대본 전체를 붙여 넣으세요. 첫 번째 소식입니다. 등 순서가 있으면 자동으로 뉴스를 분리합니다.");editor.setMinLines(8);editor.setBackgroundResource(R.drawable.bg_editor);
        editor.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        body.addView(editor,lp(-1,-2,10));
        Button importScript=btn("휴대폰에서 대본 TXT 파일 불러오기",0xffffe78b,DARK);
        body.addView(importScript,lp(-1,50,8));
        importScript.setOnClickListener(v->{
            Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            pick.setType("text/*");
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(pick,SCRIPT_PICKER);
        });
        body.addView(text("마지막 코너 · 앵커 한마디",15,true),lp(-1,-2,14));
        anchorEdit=new EditText(this);
        anchorEdit.setHint("비워 두면 정중한 기본 멘트가 자동 삽입됩니다. 필요하면 오늘 뉴스에 맞는 앵커 멘트를 작성하세요.");
        anchorEdit.setText(AppPrefs.anchorComment(this));
        anchorEdit.setTextSize(14);anchorEdit.setTextColor(DARK);
        anchorEdit.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        anchorEdit.setMinLines(2);anchorEdit.setMaxLines(5);
        anchorEdit.setBackgroundResource(R.drawable.bg_editor);
        anchorEdit.setPadding(dp(this,12),dp(this,12),dp(this,12),dp(this,12));
        body.addView(anchorEdit,lp(-1,-2,8));
        TextView note=text("A안: 아나운서 대본만 붙여 넣으면 '첫 번째 소식입니다.' 같은 순서를 분석해 뉴스별로 자동 분리합니다. 기존 '제목 + 본문 + ===' 형식도 유지됩니다. 상단 자료 이미지 검색과 하단 전체 뉴스 진행률은 MP4 생성 시 자동 적용되며, 사진을 찾지 못하면 중립 이미지를 사용합니다.",12,false);
        note.setTextColor(0xff61778e);body.addView(note,lp(-1,-2,9));
        Button reviewButton=btn("아나운서 대본 검수 · 정중한 방송체 정리",0xffe7f2f3,MINT);
        body.addView(reviewButton,lp(-1,52,7));
        TextView reviewHint=text("쉼표·마침표·일부 명백한 종결어미만 자동 보정합니다. 뜻이 불분명한 반말·하대 표현은 검토 후 수정하셔야 합니다.",12,false);
        reviewHint.setTextColor(0xff61778e);body.addView(reviewHint,lp(-1,-2,7));
        reviewButton.setOnClickListener(v->reviewEditorScript());
        Button sample=btn("예시 뉴스 넣기",0xffe7f2f3,MINT);body.addView(sample,lp(-1,46,10));
        sample.setOnClickListener(v->editor.setText("경제 뉴스 1\n필리핀 정부는 오늘 산업 투자와 고용 관련 계획을 발표했습니다. 구체적인 집행 일정은 추가 발표를 통해 확인해야 합니다.\n===\n사회 뉴스 2\n교육부는 학교 지원 현황을 점검하고 있다고 밝혔습니다. 후속 일정은 공식 발표를 통해 확인하겠습니다."));
        body.addView(text("03  활기찬 오프닝 음악 · MP4 자동 적용",17,true),lp(-1,-2,23));
        TextView openingHelp=text("새롭게 제작한 밝고 활기찬 6초 오프닝 음악이 기본 설치되며 MP4를 제작할 때마다 자동으로 재생됩니다. 직접 준비한 다른 오프닝 WAV가 있으면 아래 버튼으로 교체할 수 있습니다.",12,false);
        openingHelp.setTextColor(0xff61778e);body.addView(openingHelp,lp(-1,-2,7));
        String openingMessage;
        try{
            OpeningMusicA.ensureInstalled(this);
            openingMessage=AppPrefs.get(this).getBoolean("opening_a_builtin",false)?
                "활기찬 6초 오프닝 등록 완료 · MP4마다 자동 적용":
                "사용자 원본 A안 등록 완료 · MP4에 자동 적용";
        }catch(IOException error){
            openingMessage="음악 준비 오류 · "+error.getMessage();
        }
        openingAStatus=text(openingMessage,13,true);
        body.addView(openingAStatus,lp(-1,-2,7));
        Button chooseOpening=btn("원본 A안 WAV로 교체 (선택)",0xffe7f2f3,MINT);
        body.addView(chooseOpening,lp(-1,51,9));
        chooseOpening.setOnClickListener(v->{
            Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            pick.setType("audio/*");
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(pick,OPENING_PICKER);
        });
        body.addView(text("아나운서 이미지 · 갤러리 등록",17,true),lp(-1,-2,22));
        LinearLayout portraitButtons=new LinearLayout(this);
        portraitButtons.setOrientation(LinearLayout.HORIZONTAL);
        body.addView(portraitButtons,lp(-1,49,8));
        String[] portraitNames={"1 시작 인사","2 앵커 한마디","3 마감 인사"};
        int[] portraitCodes={PORTRAIT_PICKER_OPENING,PORTRAIT_PICKER_ANCHOR,PORTRAIT_PICKER_CLOSING};
        for(int k=0;k<3;k++){
            final int request=portraitCodes[k];
            Button pick=btn(portraitNames[k],0xffdfeef4,DARK);
            LinearLayout.LayoutParams param=new LinearLayout.LayoutParams(0,-1,1);
            if(k>0)param.leftMargin=dp(this,5);
            portraitButtons.addView(pick,param);
            pick.setOnClickListener(v->{
                Intent gallery=new Intent(Intent.ACTION_GET_CONTENT);
                gallery.setType("image/*");
                gallery.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(gallery,request);
            });
        }
        body.addView(text("04  재생 · 저장",17,true),lp(-1,-2,25));
        status=text("음성 엔진을 준비하고 있습니다.",13,false);status.setTextColor(0xff61778e);body.addView(status,lp(-1,-2,8));
        jobStatus=text("제작 상태: "+AppPrefs.get(this).getString("status","작업 기록 없음"),14,true);
        jobStatus.setTextColor(0xffe01616);
        jobStatus.setBackground(rounded(0xffffeded,dp(this,12)));
        jobStatus.setPadding(dp(this,12),dp(this,13),dp(this,12),dp(this,13));
        body.addView(jobStatus,lp(-1,-2,10));
        renderProgress=new android.widget.ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        renderProgress.setMax(100);renderProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(0xff12a45a));
        body.addView(renderProgress,lp(-1,15,9));
        etaText=text("남은 시간 계산 중",14,true);
        etaText.setTextColor(0xffe01616);body.addView(etaText,lp(-1,-2,8));
        refreshRenderProgress();
        LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.HORIZONTAL);body.addView(controls,lp(-1,53,15));
        Button preview=btn("미리 듣기",0xffe3eff2,DARK);controls.addView(preview,new LinearLayout.LayoutParams(0,-1,1));
        Button stop=btn("정지",0xffe3eff2,DARK);LinearLayout.LayoutParams stopP=new LinearLayout.LayoutParams(0,-1,1);stopP.leftMargin=dp(this,6);controls.addView(stop,stopP);
        Button play=btn("읽기 시작",0xff63ddbd,0xff0b2936);LinearLayout.LayoutParams playP=new LinearLayout.LayoutParams(0,-1,1);playP.leftMargin=dp(this,6);controls.addView(play,playP);
        videoCheck=new CheckBox(this);
        videoCheck.setText("뉴스 카드 MP4까지 자동 제작 (WAV + MP4)");
        videoCheck.setTextColor(DARK);
        videoCheck.setChecked(AppPrefs.videoEnabled(this));
        body.addView(videoCheck,lp(-1,-2,12));
        TextView formatHelp=text("v5.30 날짜별 자동 저장·이미지·진행률: 상단 노란 뉴스 번호 배지를 제거하고 관련 자료 이미지(검색 실패 시 중립 이미지)를 표시합니다. 뉴스 전체 진행률은 실제 영상 재생시간에 맞춰 증가합니다. 본문 56px·줄 간격 84px, 30fps, 중앙 추종 가변 스크롤, 음성 우선 0.7초(700ms), 6초 표지·JPG·오프닝 음악·카카오톡 공유를 유지합니다. 음성·영상·표지·자료출처 파일은 필리핀 현지 날짜별 하위 폴더에 같은 회차명과 제작시각으로 저장됩니다.",12,false);
        formatHelp.setTextColor(0xff61778e);body.addView(formatHelp,lp(-1,-2,7));
        Button save=btn(videoCheck.isChecked()?"WAV + MP4 자동 생성 및 다운로드 저장":"WAV 음성 자동 생성 및 다운로드 저장",MINT,Color.WHITE);
        body.addView(save,lp(-1,55,12));
        Button lastVideo=btn("최근 저장된 MP4 바로 열기",0xffe7f2f3,DARK);
        body.addView(lastVideo,lp(-1,50,9));
        lastVideo.setOnClickListener(v->openLastVideo());
        body.addView(text("05  카카오톡 공유 · 표지 먼저",17,true),lp(-1,-2,18));
        TextView shareHelp=text("① 표지를 먼저 사진으로 공유하면 채팅방에 뉴스 카드가 표시됩니다. ② 이어서 MP4를 공유하세요. MP4 자체의 미리보기 선택은 카카오톡이 결정하므로 100% 고정은 보장되지 않습니다.",12,false);
        shareHelp.setTextColor(0xff61778e);body.addView(shareHelp,lp(-1,-2,7));
        Button shareCover=btn("① 카카오톡용 뉴스 표지 JPG 공유",0xffffe78b,DARK);
        body.addView(shareCover,lp(-1,49,8));
        shareCover.setOnClickListener(v->shareLast(true));
        Button shareVideo=btn("② 뉴스 영상 MP4 공유",0xffd8edf6,DARK);
        body.addView(shareVideo,lp(-1,49,8));
        shareVideo.setOnClickListener(v->shareLast(false));
        videoCheck.setOnCheckedChangeListener((button,isChecked)->{
            AppPrefs.get(this).edit().putBoolean("video",isChecked).apply();
            save.setText(isChecked?"WAV + MP4 자동 생성 및 다운로드 저장":"WAV 음성 자동 생성 및 다운로드 저장");
        });
        TextView foot=text("엔진·목소리·낭독 스타일을 저장하고 WAV 자동 생성에도 동일하게 적용합니다. 여성 음성 여부와 낭독 품질은 미리 듣기로 확인하세요. 긴 원고는 자동 분할·결합합니다.",12,false);
        foot.setTextColor(0xff61778e);body.addView(foot,lp(-1,-2,16));
        preview.setOnClickListener(v->{
            String script=editor.getText().toString().trim();
            if(script.isEmpty()){updateStatus("미리 들을 뉴스 원고를 입력하세요.");return;}
            try{
                List<NewsStory> sections=NewsBroadcastSegments.build(
                    NewsAutoScript.adapt(script),anchorEdit.getText().toString());
                speakNow(NewsBroadcastSegments.audioScript(sections),true);
            }catch(IllegalArgumentException e){
                updateStatus("전체 대본 미리 듣기 오류: "+e.getMessage());
            }
        });
        play.setOnClickListener(v->{
            String checked=reviewEditorScript();
            if(checked==null)return;
            try{
                List<NewsStory> sections=NewsBroadcastSegments.build(checked,anchorEdit.getText().toString());
                speakNow(NewsBroadcastSegments.audioScript(sections),false);
            }catch(IllegalArgumentException e){updateStatus("방송 멘트 검수: "+e.getMessage());}
        });
        stop.setOnClickListener(v->{taskToken++;playbackSegments.clear();playbackIndex=-1;playbackToken=-1;if(tts!=null)tts.stop();updateStatus("낭독 정지됨");});
        save.setOnClickListener(v->queueManualSave());
        buildAutomationControls(body);
        setContentView(sc);
        jobListener=(prefs,key)->{
            if("status".equals(key)||"render_percent".equals(key)||"render_eta_ms".equals(key)||"render_stage".equals(key)||"render_active".equals(key))
                main.post(this::refreshRenderProgress);
        };
        AppPrefs.get(this).registerOnSharedPreferenceChangeListener(jobListener);
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
                    else if(utteranceId.startsWith("play_")) main.post(() -> advancePlayback(utteranceId));
                }
                @Override public void onError(String utteranceId) {
                    main.post(() -> {
                        if(utteranceId.startsWith("play_")) {
                            if(!isCurrentPlayback(utteranceId))return;
                            playbackSegments.clear();
                            playbackIndex=-1;
                            playbackToken=-1;
                            updateStatus("음성 낭독 오류: "+utteranceId);
                        }else updateStatus("음성 합성 오류. 다른 한국어 음성을 선택해 주세요.");
                    });
                }
            });
            String active=currentEngine.isEmpty()?tts.getDefaultEngine():currentEngine;
            if(active==null||active.isEmpty()){
                updateStatus("TTS 엔진 정보를 확인하지 못했습니다.");
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
    private void diagnoseSamsung(){
        samsungWavVerified=false;
        samsungPreviewButton.setEnabled(false);samsungWavButton.setEnabled(false);
        samsungApplyButton.setEnabled(false);
        if(samsungTestTts!=null){samsungTestTts.stop();samsungTestTts.shutdown();samsungTestTts=null;}
        boolean installed=false,exposed=false;
        try{getPackageManager().getPackageInfo(SAMSUNG_PACKAGE,0);installed=true;}
        catch(android.content.pm.PackageManager.NameNotFoundException ignored){}
        if(tts!=null){
            try{
                for(TextToSpeech.EngineInfo e:tts.getEngines())
                    if(SAMSUNG_PACKAGE.equals(e.name)){exposed=true;break;}
            }catch(RuntimeException ignored){}
        }
        if(!installed){
            samsungTestStatus.setText("삼성 TTS 패키지를 외부 앱에서 확인하지 못했습니다. 삼성 설정에서는 재생될 수 있습니다.");
            return;
        }
        if(!exposed){
            samsungTestStatus.setText("삼성 TTS 설치 확인. 그러나 외부 앱의 TTS 엔진 목록에 표시되지 않습니다. One UI 접근 제한 가능성: 삼성 음성만 재생하는 기능은 사용할 수 없습니다.");
            return;
        }
        samsungTestStatus.setText("삼성 엔진 목록 발견. 한국어 연결을 시험합니다...");
        try{
            samsungTestTts=new TextToSpeech(this,code->main.post(()->{
                if(samsungTestTts==null||isFinishing()||isDestroyed())return;
                if(code!=TextToSpeech.SUCCESS){
                    samsungTestStatus.setText("삼성 엔진 초기화 실패: 외부 앱 접근이 제한됐을 수 있습니다.");
                    return;
                }
                int language=samsungTestTts.setLanguage(Locale.KOREAN);
                if(language==TextToSpeech.LANG_MISSING_DATA||language==TextToSpeech.LANG_NOT_SUPPORTED){
                    samsungTestStatus.setText("삼성 엔진 요청은 성공했으나 한국어 TTS를 사용할 수 없습니다.");
                    return;
                }
                Voice selected=null;
                try{
                    if(samsungTestTts.getVoices()!=null)
                        for(Voice v:samsungTestTts.getVoices())
                            if(v!=null&&v.getLocale()!=null&&"ko".equals(v.getLocale().getLanguage())){
                                selected=v;break;
                            }
                    if(selected!=null)samsungTestTts.setVoice(selected);
                }catch(RuntimeException ignored){}
                String voiceName=selected==null?"기본 한국어":selected.getName();
                samsungTestStatus.setText("삼성 패키지를 지정한 TTS 초기화 성공 (목소리: "+voiceName+"). 실제 삼성 엔진 사용 여부는 음색을 비교해야 합니다.");
                samsungPreviewButton.setEnabled(true);
                samsungWavButton.setEnabled(true);
            }),SAMSUNG_PACKAGE);
        }catch(RuntimeException ex){
            samsungTestStatus.setText("삼성 TTS 접근 오류: "+ex.getClass().getSimpleName());
        }
    }
    private void previewSamsungOnly(){
        if(samsungTestTts==null)return;
        int result=samsungTestTts.speak(SAMSUNG_SAMPLE,TextToSpeech.QUEUE_FLUSH,new Bundle(),"samsung_preview");
        if(result!=TextToSpeech.SUCCESS){
            samsungTestStatus.setText("삼성 패키지 요청 음성 재생이 거절됐습니다.");
        }else{
            samsungTestStatus.setText("시험 낭독 요청 완료. 갤럭시 설정에서 들은 목소리와 동일한지 직접 비교해 주세요. 다른 엔진으로 대체됐을 가능성은 배제할 수 없습니다.");
        }
    }
    private void saveSamsungWavTest(){
        if(samsungTestTts==null)return;
        String stamp=String.valueOf(System.currentTimeMillis());
        File output=new File(getCacheDir(),"samsung_test_"+stamp+".wav");
        samsungWavButton.setEnabled(false);samsungApplyButton.setEnabled(false);samsungWavVerified=false;
        samsungTestStatus.setText("삼성 TTS 패키지를 지정한 WAV 합성 시험 중...");
        samsungTestTts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
            @Override public void onStart(String id){}
            @Override public void onDone(String id){
                if(!"samsung_file".equals(id))return;
                new Thread(()->{
                    try{
                        WavTools.inspect(output);
                        DownloadsPublisher.publish(MainActivity.this,output,"newsvoice_samsung_test_"+stamp+".wav");
                        main.post(()->{
                            samsungWavVerified=true;
                            samsungWavButton.setEnabled(true);
                            samsungApplyButton.setEnabled(true);
                            samsungTestStatus.setText("WAV 저장 성공: 다운로드/1. 필리핀뉴스/날짜별 폴더/newsvoice_samsung_test_"+stamp+".wav. 파일을 재생해 실제 삼성 목소리와 동일한지 확인한 후 적용하세요.");
                        });
                    }catch(Exception e){
                        main.post(()->{
                            samsungWavButton.setEnabled(true);
                            samsungTestStatus.setText("WAV 시험 실패: "+e.getClass().getSimpleName()+" - "+e.getMessage());
                        });
                    }finally{output.delete();}
                }).start();
            }
            @Override public void onError(String id){
                main.post(()->{
                    samsungWavButton.setEnabled(true);
                    samsungTestStatus.setText("삼성 패키지 요청으로 WAV 합성에 실패했습니다.");
                });
            }
            @Override public void onError(String id,int errorCode){onError(id);}
        });
        int accepted=samsungTestTts.synthesizeToFile(SAMSUNG_SAMPLE,new Bundle(),output,"samsung_file");
        if(accepted!=TextToSpeech.SUCCESS){
            samsungWavButton.setEnabled(true);
            samsungTestStatus.setText("삼성 패키지를 지정한 WAV 생성 명령이 거절됐습니다.");
        }
    }
    private void restartWithEngine(String packageName){
        try{
            ready=false;
            taskToken++;
            updateStatus("TTS 엔진을 전환하는 중: "+packageName);
            if(tts!=null){tts.stop();tts.shutdown();}
            currentEngine=packageName;
            engineUiReady=false;
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
    private boolean isCurrentPlayback(String utteranceId){
        return playbackIndex>=0 && playbackToken==taskToken &&
            utteranceId.equals("play_"+playbackToken+"_"+playbackIndex);
    }
    private void advancePlayback(String utteranceId){
        if(!isCurrentPlayback(utteranceId))return;
        if(playbackIndex+1>=playbackSegments.size()){
            playbackSegments.clear();
            playbackIndex=-1;
            playbackToken=-1;
            updateStatus("낭독 완료");
        }else{
            playbackIndex++;
            speakPlaybackChunk();
        }
    }
    private void speakPlaybackChunk(){
        if(playbackToken!=taskToken||playbackIndex<0||playbackIndex>=playbackSegments.size())return;
        int result;
        try{
            result=tts.speak(playbackSegments.get(playbackIndex),TextToSpeech.QUEUE_ADD,
                new Bundle(),"play_"+playbackToken+"_"+playbackIndex);
        }catch(RuntimeException ex){
            playbackSegments.clear();
            playbackIndex=-1;
            playbackToken=-1;
            updateStatus("낭독 구간 처리 오류: "+ex.getClass().getSimpleName());
            return;
        }
        if(result!=TextToSpeech.SUCCESS){
            int failed=playbackIndex+1;
            playbackSegments.clear();
            playbackIndex=-1;
            playbackToken=-1;
            updateStatus("낭독 "+failed+"번째 구간을 실행하지 못했습니다.");
        }else updateStatus("뉴스 낭독 중: "+(playbackIndex+1)+"/"+playbackSegments.size()+" 구간");
    }
    private void speakNow(String src,boolean preview){
        if(!ready){updateStatus("TTS 엔진이 아직 준비되지 않았습니다.");return;}
        if(src.trim().isEmpty()){updateStatus("원고를 입력하세요.");return;}
        try {
            taskToken++;
            playbackSegments.clear();
            playbackIndex=-1;
            playbackToken=-1;
            tts.stop();
            configure();
            persistVoiceSettings();
            // Only one chunk is queued at a time to avoid overwhelming TTS engines.
            playbackSegments=chunks(src);
            if(playbackSegments.isEmpty()){updateStatus("읽을 원고가 없습니다.");return;}
            playbackToken=taskToken;
            playbackIndex=0;
            lastQueuedIndex=playbackSegments.size()-1;
            speakPlaybackChunk();
        }catch(RuntimeException e){
            playbackSegments.clear();
            playbackIndex=-1;
            playbackToken=-1;
            updateStatus("낭독 실행 오류: "+e.getClass().getSimpleName());
        }
    }
    private void persistVoiceSettings(){
        int selected=voicesSpinner.getSelectedItemPosition();
        String voice=(selected>=0 && selected<koreanVoices.size())?koreanVoices.get(selected).getName():"";
        AppPrefs.get(this).edit().putString("engine",currentEngine).putString("voice",voice)
            .putFloat("speed",rate()).putFloat("pitch",pitch()).putInt("preset",selectedPreset).apply();
    }
    /** Native chooser: share JPG first so a cover message is visible in KakaoTalk. */
    private void shareLast(boolean cover){
        String key=cover?"last_cover_uri":"last_mp4_uri";
        String value=AppPrefs.get(this).getString(key,"");
        if(value.isEmpty()){
            updateStatus(cover?"먼저 MP4를 제작해 표지 JPG를 저장하세요.":
                "먼저 뉴스 MP4를 제작하고 저장하세요.");
            return;
        }
        try{
            Intent send=new Intent(Intent.ACTION_SEND);
            send.setType(cover?"image/jpeg":"video/mp4");
            send.putExtra(Intent.EXTRA_STREAM,Uri.parse(value));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send,
                cover?"표지 사진 공유 · 카카오톡 선택":"뉴스 동영상 공유 · 카카오톡 선택"));
        }catch(ActivityNotFoundException | SecurityException ex){
            updateStatus("공유할 앱이 없거나 접근 권한이 없습니다: "+ex.getClass().getSimpleName());
        }
    }
    private void openLastVideo(){
        String value=AppPrefs.get(this).getString("last_mp4_uri","");
        if(value.isEmpty()){
            updateStatus("저장된 MP4 기록이 없습니다. 아래 제작 상태를 확인하세요.");
            return;
        }
        try {
            Intent view=new Intent(Intent.ACTION_VIEW);
            view.setDataAndType(Uri.parse(value),"video/mp4");
            view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(view);
        }catch(ActivityNotFoundException | SecurityException ex){
            updateStatus("MP4 열기 오류: "+ex.getClass().getSimpleName()+
                ". 내 파일 → 다운로드 → 1. 필리핀뉴스 → 날짜별 폴더에서 확인하세요.");
        }
    }
    private String reviewEditorScript(){
        BroadcastScriptReview.Result result=BroadcastScriptReview.review(editor.getText().toString());
        if(!result.reviewed.equals(editor.getText().toString().trim())){
            editor.setText(result.reviewed);
            editor.setSelection(editor.length());
        }
        updateStatus(result.summary());
        return result.approved()?result.reviewed:null;
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode>=PORTRAIT_PICKER_OPENING&&requestCode<=PORTRAIT_PICKER_CLOSING){
            if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;
            String slot=requestCode==PORTRAIT_PICKER_OPENING?"opening":
                requestCode==PORTRAIT_PICKER_ANCHOR?"anchor":"closing";
            try(InputStream in=getContentResolver().openInputStream(data.getData())){
                if(in==null)throw new IOException("갤러리 사진을 읽을 수 없습니다.");
                android.graphics.BitmapFactory.Options opt=new android.graphics.BitmapFactory.Options();
                opt.inSampleSize=2;
                android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeStream(in,null,opt);
                if(bitmap==null)throw new IOException("지원되지 않는 사진 형식입니다.");
                File dest=new File(getFilesDir(),"portrait_"+slot+".jpg");
                try(FileOutputStream out=new FileOutputStream(dest)){
                    if(!bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,87,out))
                        throw new IOException("사진 저장 실패");
                }finally{bitmap.recycle();}
                updateStatus("아나운서 사진 등록 완료: "+slot+" · 다음 MP4부터 적용");
            }catch(Exception ex){updateStatus("아나운서 이미지 오류: "+ex.getMessage());}
            return;
        }
        if(requestCode==SCRIPT_PICKER){
            if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;
            try(InputStream input=getContentResolver().openInputStream(data.getData())){
                if(input==null)throw new IOException("선택한 대본 파일을 읽을 수 없습니다.");
                java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
                byte[] buf=new byte[8192];int n;
                while((n=input.read(buf))>=0){
                    if(bytes.size()+n>240000)throw new IOException("240KB 이하의 대본을 선택하세요.");
                    bytes.write(buf,0,n);
                }
                String script=new String(bytes.toByteArray(),StandardCharsets.UTF_8)
                    .replace("\uFEFF","").trim();
                if(script.isEmpty()||script.length()>60000)
                    throw new IOException("비어 있거나 너무 긴 대본입니다.");
                editor.setText(script);editor.setSelection(editor.length());
                updateStatus("대본 불러오기 완료 · 뉴스 자동 분리 및 제작 버튼을 누르세요.");
            }catch(Exception ex){updateStatus("대본 불러오기 오류: "+ex.getMessage());}
            return;
        }
        if(requestCode!=OPENING_PICKER||resultCode!=RESULT_OK||data==null||
           data.getData()==null)return;
        File target=new File(getFilesDir(),"newsvoice_opening_A.wav");
        File candidate=new File(getFilesDir(),"newsvoice_opening_candidate.wav");
        try(InputStream in=getContentResolver().openInputStream(data.getData());
            FileOutputStream out=new FileOutputStream(candidate)){
            if(in==null)throw new IOException("선택한 음악 파일을 읽을 수 없습니다.");
            byte[] buf=new byte[16384];int n;long bytes=0;
            while((n=in.read(buf))!=-1){
                bytes+=n;if(bytes>6L*1024*1024)
                    throw new IOException("6MB 이하의 WAV 파일을 선택해 주세요.");
                out.write(buf,0,n);
            }
            WavTools.Piece wav=WavTools.inspect(candidate);
            int format=(wav.format[0]&255)|((wav.format[1]&255)<<8);
            int bits=(wav.format[14]&255)|((wav.format[15]&255)<<8);
            int channels=(wav.format[2]&255)|((wav.format[3]&255)<<8);
            int hz=(wav.format[4]&255)|((wav.format[5]&255)<<8)|
                ((wav.format[6]&255)<<16)|((wav.format[7]&255)<<24);
            double length=(double)wav.dataLength/(hz*channels*2.);
            if(format!=1||bits!=16||channels<1||channels>2||
               hz<8000||hz>96000||length<5.5||length>6.3)
                throw new IOException("5.5~6.3초 PCM 16-bit WAV 음원만 사용할 수 있습니다.");
            java.nio.file.Files.move(candidate.toPath(),target.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            AppPrefs.get(this).edit().putBoolean("opening_a_builtin",false).apply();
            openingAStatus.setText("사용자 A안 원본 등록 완료 · "+String.format(Locale.KOREA,"%.1f",length)+"초 · 자동 적용");
            updateStatus("오프닝 음악이 등록됐습니다. 앞으로 MP4 첫 6초에 사용합니다.");
        }catch(Exception ex){
            openingAStatus.setText("음원 등록 실패 · 올바른 A안 WAV 파일을 확인하세요.");
            updateStatus("오프닝 WAV 오류: "+ex.getMessage());
        }finally{candidate.delete();}
    }
    private void queueManualSave(){
        if(!ready){updateStatus("한국어 음성 엔진이 준비되지 않았습니다.");return;}
        String text=reviewEditorScript();
        if(text==null)return;
        try {
            String comment=anchorEdit.getText().toString().trim();
            NewsBroadcastSegments.build(text,comment);
            AppPrefs.get(this).edit().putString("anchor_comment",comment).apply();
            persistVoiceSettings();
            File inbox=new File(getFilesDir(),"inbox");
            if(!inbox.exists()&&!inbox.mkdirs())throw new IOException("원고 저장 폴더 오류");
            String filename="news_"+UUID.randomUUID().toString().replace("-","")+".txt";
            try(FileOutputStream stream=new FileOutputStream(new File(inbox,filename))){
                stream.write(text.getBytes(StandardCharsets.UTF_8));
            }
            Intent intent=new Intent(this,ManualRenderService.class);
            intent.putExtra(ManualRenderService.EXTRA_FILE,filename);
            AppPrefs.status(this,"사용자 요청 · 영상 제작 서비스 실행 중");
            startForegroundService(intent);
            updateStatus("영상 제작을 시작했습니다. 제작 상태와 알림에서 진행 과정을 확인하세요.");
        }catch(Exception e){
            AppPrefs.status(this,"영상 제작 시작 실패: "+e.getClass().getSimpleName()+" - "+e.getMessage());
            updateStatus("영상 제작 시작 실패: "+e.getMessage());
        }
    }
    private void buildAutomationControls(LinearLayout body){
        body.addView(text("04  자동 뉴스 수신 · WAV 변환",17,true),lp(-1,-2,28));
        TextView note=text("서버 연결 전에는 직접 대본을 입력해 WAV + MP4를 제작하세요. 자동 뉴스 수신에는 별도의 HTTPS 대본 서버와 인증키가 필요합니다. 15분 단위 확인은 정각을 보장하지 않습니다.",13,false);
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
        TextView caution=text("자동 모드는 검증된 원고를 받아 WAV와 MP4를 다운로드/1. 필리핀뉴스/날짜별 폴더에 저장합니다. MP4에는 등록된 A 타입 오프닝이 자동 적용됩니다. WAV만 설정한 서버로 전송합니다.",12,false);
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
        if(jobListener!=null)AppPrefs.get(this).unregisterOnSharedPreferenceChangeListener(jobListener);
        if(tts!=null){
            try { tts.stop(); tts.shutdown(); } catch(RuntimeException ignored) {}
        }
        if(samsungTestTts!=null){
            try{samsungTestTts.stop();samsungTestTts.shutdown();}catch(RuntimeException ignored){}
        }
        super.onDestroy();
    }
}
