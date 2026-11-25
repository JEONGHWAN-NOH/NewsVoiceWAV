package com.newsvoice.radio;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.speech.tts.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public class MainActivity extends Activity {
    private RadioService radio; private TextToSpeech tts; private boolean ttsReady,reading,txtMode;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private List<Captions.Cue> cues=new ArrayList<>(); private List<String> script=new ArrayList<>();
    private final List<TextView> rows=new ArrayList<>();
    private ScrollView scroll; private LinearLayout body; private SeekBar seek; private TextView status; private Button play,read;
    private File audio,subtitle,txt; private int last=-2,viewport=0,spoken=0,generation=0; private boolean dragging; private float font=24,rate=.95f,pitch=1f;
    private final ServiceConnection connection=new ServiceConnection(){public void onServiceConnected(ComponentName n,IBinder b){radio=((RadioService.LocalBinder)b).get();try{if(audio.isFile()&&!radio.ready())radio.load(audio);if(txtMode)estimate();}catch(Exception e){error(e);}}public void onServiceDisconnected(ComponentName n){radio=null;}};
    private final Runnable tick=new Runnable(){public void run(){if(!reading&&radio!=null&&radio.ready()&&!cues.isEmpty()&&(!txtMode||!script.isEmpty())){int p=radio.position(),d=radio.duration();if(!dragging){seek.setMax(Math.max(1,d));seek.setProgress(p);}status.setText(clock(p)+" / "+clock(d)+(txtMode?" · 추정 자막":""));play.setText(radio.playing()?"일시정지":"MP3 재생");int i=Captions.index(cues,p-700L);if(radio.playing()||p>0)focus(i);}handler.postDelayed(this,100);}};
    @Override public void onCreate(Bundle b){super.onCreate(b);audio=new File(getFilesDir(),"broadcast.mp3");subtitle=new File(getFilesDir(),"broadcast.srt");txt=new File(getFilesDir(),"script.txt");
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(Color.BLACK);root.setPadding(12,8,12,8);setContentView(root);
        LinearLayout tools=new LinearLayout(this);root.addView(tools);button(tools,"TXT 열기",()->pick(5,"*/*"));button(tools,"ZIP 열기",()->pick(1,"*/*"));button(tools,"MP3",()->pick(2,"*/*"));button(tools,"SRT",()->pick(3,"*/*"));
        LinearLayout extras=new LinearLayout(this);root.addView(extras);button(extras,"대본 붙여넣기",()->paste());button(extras,"저장/공유",()->save());button(extras,"음성 선택",()->voices());
        scroll=new ScrollView(this);scroll.setFillViewport(true);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));body=new LinearLayout(this);body.setOrientation(1);scroll.addView(body);scroll.addOnLayoutChangeListener((v,l,t,r,bottom,ol,ot,or,ob)->{int h=bottom-t;if(h>0&&h!=viewport){viewport=h;body.setPadding(0,h/3,0,h*2/3);last=-2;}});
        status=new TextView(this);status.setTextColor(Color.WHITE);status.setGravity(Gravity.CENTER);root.addView(status);seek=new SeekBar(this);root.addView(seek);seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){dragging=true;}public void onStopTrackingTouch(SeekBar s){if(!reading&&radio!=null&&radio.ready())radio.seek(s.getProgress());dragging=false;last=-2;}public void onProgressChanged(SeekBar s,int p,boolean u){}});
        LinearLayout controls=new LinearLayout(this);root.addView(controls);read=button(controls,"대본 읽기",()->toggleRead());play=button(controls,"MP3 재생",()->{if(reading)stopRead(false);if(radio!=null&&radio.ready()&&!cues.isEmpty())radio.toggle();else message("MP3와 SRT 또는 TXT를 불러오세요.");});button(controls,"정지",()->{stopRead(true);if(radio!=null&&radio.ready()){if(radio.playing())radio.toggle();radio.seek(0);}});
        LinearLayout settings=new LinearLayout(this);root.addView(settings);button(settings,"속도",()->chooseSpeed());button(settings,"글자 -",()->resize(-2));button(settings,"글자 +",()->resize(2));
        bindService(new Intent(this,RadioService.class),connection,BIND_AUTO_CREATE);
        tts=new TextToSpeech(this,result->{if(result!=TextToSpeech.SUCCESS){message("휴대전화 음성 엔진을 확인하세요.");return;}int lang=tts.setLanguage(Locale.KOREA);ttsReady=lang>=0;tts.setSpeechRate(rate);tts.setPitch(pitch);tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){public void onStart(String id){handler.post(()->{if(valid(id)){focus(spoken);status.setText("낭독 중 · "+(spoken+1)+" / "+script.size());seek.setMax(Math.max(1,script.size()));seek.setProgress(spoken+1);}});}public void onDone(String id){handler.post(()->{if(valid(id)){spoken++;speakNext();}});}public void onError(String id){handler.post(()->{if(valid(id)){stopRead(false);message("음성 엔진 재생 오류");}});}});});
        try{if(txt.isFile()){txtMode=true;script=TxtScript.split(TxtScript.decode(Files.readAllBytes(txt.toPath())));show(script);}else if(subtitle.isFile())readCues();else status.setText("TXT 또는 방송 ZIP을 열어 주세요");}catch(Exception e){error(e);}handler.post(tick);
    }
    private boolean valid(String id){return reading&&id.equals(generation+":"+spoken);}
    private void toggleRead(){if(reading){stopRead(false);return;}if(script.isEmpty()){message("TXT 대본을 먼저 열어 주세요.");return;}if(!ttsReady){message("한국어 음성 엔진이 준비되지 않았습니다.");return;}if(radio!=null&&radio.playing())radio.toggle();if(spoken>=script.size())spoken=0;reading=true;generation++;read.setText("일시정지");speakNext();}
    private void speakNext(){if(!reading)return;if(spoken>=script.size()){stopRead(true);status.setText("낭독 완료");return;}if(tts.speak(script.get(spoken),TextToSpeech.QUEUE_FLUSH,null,generation+":"+spoken)==TextToSpeech.ERROR){stopRead(false);message("한국어 음성 재생 실패");}}
    private void stopRead(boolean reset){generation++;reading=false;if(tts!=null)tts.stop();if(reset)spoken=0;if(read!=null)read.setText("대본 읽기");}
    private void voices(){if(!ttsReady){message("한국어 음성 엔진 초기화가 필요합니다.");return;}List<Voice> list=new ArrayList<>();if(tts.getVoices()!=null)for(Voice v:tts.getVoices())if("ko".equals(v.getLocale().getLanguage())&&!v.isNetworkConnectionRequired())list.add(v);list.sort(Comparator.comparing(Voice::getName));if(list.isEmpty()){message("다운로드된 한국어 오프라인 음성이 없습니다.");return;}String[] names=new String[list.size()];for(int i=0;i<names.length;i++)names[i]=list.get(i).getName();new AlertDialog.Builder(this).setTitle("한국어 오프라인 음성").setItems(names,(d,i)->{tts.setVoice(list.get(i));}).show();}
    private void paste(){EditText box=new EditText(this);box.setMinLines(5);box.setGravity(Gravity.TOP);new AlertDialog.Builder(this).setTitle("뉴스 대본 붙여넣기").setView(box).setPositiveButton("열기",(d,w)->{try{loadText(box.getText().toString());}catch(Exception e){error(e);}}).setNegativeButton("취소",null).show();}
    private void loadText(String content)throws IOException{List<String> next=TxtScript.split(content);stopRead(true);if(radio!=null&&radio.playing())radio.toggle();Files.write(txt.toPath(),content.getBytes(StandardCharsets.UTF_8));script=next;txtMode=true;cues=new ArrayList<>();show(script);status.setText("TXT 열기 완료 · "+script.size()+"문장");last=-2;scroll.scrollTo(0,0);estimate();}
    private void estimate(){if(txtMode&&radio!=null&&radio.ready()&&!script.isEmpty())cues=TxtScript.estimate(script,radio.duration());}
    private void focus(int i){if(i>=0&&i<rows.size()&&i!=last){last=i;scroll.smoothScrollTo(0,Math.max(0,rows.get(i).getTop()-viewport/3));}}
    private void show(List<String> lines){body.removeAllViews();rows.clear();for(String line:lines){TextView row=new TextView(this);row.setText(line);row.setTextColor(Color.WHITE);row.setTextSize(font);row.setLineSpacing(8,1.15f);row.setPadding(0,8,0,16);body.addView(row,new LinearLayout.LayoutParams(-1,-2));rows.add(row);}last=-2;}
    private Button button(LinearLayout parent,String title,Runnable r){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(12);parent.addView(b,new LinearLayout.LayoutParams(0,-2,1));b.setOnClickListener(v->r.run());return b;}
    private void pick(int code,String mime){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(mime).addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,code);}
    private void save(){if(txtMode&&!script.isEmpty()&&(radio==null||!radio.ready())){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"NewsVoice-script.txt");startActivityForResult(i,6);return;}if(!audio.isFile()||cues.isEmpty()){message("MP3와 대본 또는 SRT가 필요합니다.");return;}Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"NewsVoice-Radio.zip");startActivityForResult(i,4);}
    private void copy(InputStream in,OutputStream out,long limit)throws IOException{if(in==null||out==null)throw new IOException("파일을 열 수 없습니다");byte[] b=new byte[8192];long total=0;int n;while((n=in.read(b))!=-1){total+=n;if(total>limit)throw new IOException("파일이 너무 큽니다");out.write(b,0,n);}}
    private void readCues()throws IOException{cues=Captions.parse(TxtScript.decode(Files.readAllBytes(subtitle.toPath())));script=new ArrayList<>();for(Captions.Cue cue:cues)script.add(cue.text());txtMode=false;Files.deleteIfExists(txt.toPath());show(script);spoken=0;}
    private void resize(int delta){font=Math.max(16,Math.min(40,font+delta));for(TextView row:rows)row.setTextSize(font);last=-2;}
    private void chooseSpeed(){String[] a={"0.75x","0.95x","1.0x","1.25x","1.5x"};float[] values={.75f,.95f,1f,1.25f,1.5f};new AlertDialog.Builder(this).setItems(a,(d,i)->{rate=values[i];if(ttsReady)tts.setSpeechRate(rate);if(radio!=null&&radio.ready())radio.speed(rate);}).show();}
    private void share(Uri uri,String type){new AlertDialog.Builder(this).setMessage("저장 완료").setPositiveButton("공유",(d,w)->{Intent share=new Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(share,"방송 공유"));}).setNegativeButton("닫기",null).show();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();try{
        if(request==6){try(OutputStream out=getContentResolver().openOutputStream(uri)){Files.copy(txt.toPath(),out);}share(uri,"text/plain");return;}
        if(request==4){try(ZipOutputStream zip=new ZipOutputStream(getContentResolver().openOutputStream(uri))){zip.putNextEntry(new ZipEntry("broadcast.mp3"));Files.copy(audio.toPath(),zip);zip.closeEntry();zip.putNextEntry(new ZipEntry("broadcast.srt"));zip.write(TxtScript.toSrt(cues).getBytes(StandardCharsets.UTF_8));zip.closeEntry();}share(uri,"application/zip");return;}
        stopRead(true);if(request==5){ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=getContentResolver().openInputStream(uri)){copy(in,out,2_000_000);}loadText(TxtScript.decode(out.toByteArray()));return;}
        if(request==1){File a=new File(getCacheDir(),"import.mp3"),s=new File(getCacheDir(),"import.srt"),t=new File(getCacheDir(),"import.txt");boolean hasA=false,hasS=false,hasT=false;try(ZipInputStream zip=new ZipInputStream(getContentResolver().openInputStream(uri))){ZipEntry e;while((e=zip.getNextEntry())!=null){String name=e.getName().toLowerCase(Locale.ROOT);if(!e.isDirectory()){File target=null;long limit=2_000_000;if(name.endsWith(".mp3")){target=a;limit=100_000_000;hasA=true;}else if(name.endsWith(".srt")){target=s;hasS=true;}else if(name.endsWith(".txt")){target=t;hasT=true;}if(target!=null)try(OutputStream out=new FileOutputStream(target)){copy(zip,out,limit);}}zip.closeEntry();}}if(!hasA&&!hasT)throw new IOException("ZIP에 MP3 또는 TXT가 없습니다");if(hasA){Files.copy(a.toPath(),audio.toPath(),StandardCopyOption.REPLACE_EXISTING);if(radio!=null)radio.load(audio);}if(hasS){Captions.parse(TxtScript.decode(Files.readAllBytes(s.toPath())));Files.copy(s.toPath(),subtitle.toPath(),StandardCopyOption.REPLACE_EXISTING);readCues();}else if(hasT)loadText(TxtScript.decode(Files.readAllBytes(t.toPath())));else throw new IOException("ZIP에 SRT 또는 TXT가 없습니다");}
        else{File temp=new File(getCacheDir(),"single-import");try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(temp)){copy(in,out,request==2?100_000_000:2_000_000);}if(request==3){Captions.parse(TxtScript.decode(Files.readAllBytes(temp.toPath())));Files.copy(temp.toPath(),subtitle.toPath(),StandardCopyOption.REPLACE_EXISTING);readCues();}else if(request==2){Files.copy(temp.toPath(),audio.toPath(),StandardCopyOption.REPLACE_EXISTING);if(radio!=null)radio.load(audio);estimate();}}scroll.scrollTo(0,0);last=-2;
    }catch(Exception e){error(e);}}
    private void error(Exception e){new AlertDialog.Builder(this).setMessage("파일 오류: "+e.getMessage()).setPositiveButton("확인",null).show();}
    private void message(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private String clock(int ms){return String.format(Locale.ROOT,"%02d:%02d",ms/60000,(ms/1000)%60);}
    @Override protected void onDestroy(){handler.removeCallbacks(tick);stopRead(false);if(tts!=null)tts.shutdown();unbindService(connection);super.onDestroy();}
}
