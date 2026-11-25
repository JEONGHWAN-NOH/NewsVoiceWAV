package com.newsvoice.radio;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public class MainActivity extends Activity {
    private RadioService radio;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private List<Captions.Cue> cues=new ArrayList<>();
    private final List<TextView> rows=new ArrayList<>();
    private ScrollView scroll; private LinearLayout body; private SeekBar seek; private TextView status; private Button play;
    private File audio,subtitle; private int last=-2; private boolean dragging; private float font=24; private int viewport=0;
    private final ServiceConnection connection=new ServiceConnection(){public void onServiceConnected(ComponentName n,IBinder b){radio=((RadioService.LocalBinder)b).get();try{if(audio.isFile()&&!radio.ready())radio.load(audio);}catch(Exception e){error(e);}}public void onServiceDisconnected(ComponentName n){radio=null;}};
    private final Runnable tick=new Runnable(){public void run(){if(radio!=null&&radio.ready()){int p=radio.position(),d=radio.duration();if(!dragging){seek.setMax(Math.max(1,d));seek.setProgress(p);}status.setText(clock(p)+" / "+clock(d));play.setText(radio.playing()?"�Ͻ�����":"���");int i=Captions.index(cues,p-700L);if(i!=last&&i>=0&&i<rows.size()){last=i;scroll.smoothScrollTo(0,Math.max(0,rows.get(i).getTop()-viewport/3));}}handler.postDelayed(this,100);}};
    @Override public void onCreate(Bundle b){super.onCreate(b);audio=new File(getFilesDir(),"broadcast.mp3");subtitle=new File(getFilesDir(),"broadcast.srt");
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(Color.BLACK);root.setPadding(16,8,16,8);setContentView(root);
        LinearLayout tools=new LinearLayout(this);root.addView(tools);button(tools,"ZIP ����",()->pick(1,"*/*"));button(tools,"MP3",()->pick(2,"audio/*"));button(tools,"SRT",()->pick(3,"*/*"));button(tools,"����",()->save());
        scroll=new ScrollView(this);scroll.setFillViewport(true);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));body=new LinearLayout(this);body.setOrientation(1);scroll.addView(body);scroll.addOnLayoutChangeListener((v,l,t,r,bottom,ol,ot,or,ob)->{int h=bottom-t;if(h>0&&h!=viewport){viewport=h;body.setPadding(0,h/3,0,h*2/3);last=-2;}});
        status=new TextView(this);status.setTextColor(Color.WHITE);status.setGravity(Gravity.CENTER);root.addView(status);seek=new SeekBar(this);root.addView(seek);seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){dragging=true;}public void onStopTrackingTouch(SeekBar s){if(radio!=null)radio.seek(s.getProgress());dragging=false;last=-2;}public void onProgressChanged(SeekBar s,int p,boolean u){}});
        LinearLayout controls=new LinearLayout(this);root.addView(controls);play=button(controls,"���",()->{if(radio!=null&&radio.ready()&&!cues.isEmpty())radio.toggle();else Toast.makeText(this,"MP3�� SRT�� �ҷ�������",Toast.LENGTH_SHORT).show();});button(controls,"�ӵ�",()->chooseSpeed());button(controls,"���� -",()->resize(-2));button(controls,"���� +",()->resize(2));
        bindService(new Intent(this,RadioService.class),connection,BIND_AUTO_CREATE);try{if(subtitle.isFile())readCues();}catch(Exception e){error(e);}handler.post(tick);
    }
    private Button button(LinearLayout parent,String title,Runnable r){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(12);parent.addView(b,new LinearLayout.LayoutParams(0,-2,1));b.setOnClickListener(v->r.run());return b;}
    private void pick(int code,String mime){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(mime).addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,code);}
    private void save(){if(!audio.isFile()||!subtitle.isFile()){Toast.makeText(this,"MP3 �� SRT �ʿ�",Toast.LENGTH_SHORT).show();return;}Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"NewsVoice-Radio.zip");startActivityForResult(i,4);}
    private void copy(InputStream in,OutputStream out,long limit)throws IOException{byte[] b=new byte[8192];long total=0;int n;while((n=in.read(b))!=-1){total+=n;if(total>limit)throw new IOException("File too large");out.write(b,0,n);}}
    private void readCues()throws IOException{cues=Captions.parse(new String(Files.readAllBytes(subtitle.toPath()),StandardCharsets.UTF_8));body.removeAllViews();rows.clear();for(Captions.Cue cue:cues){TextView row=new TextView(this);row.setText(cue.text());row.setTextColor(Color.WHITE);row.setTextSize(font);row.setLineSpacing(8,1.15f);row.setPadding(0,8,0,16);body.addView(row,new LinearLayout.LayoutParams(-1,-2));rows.add(row);}last=-2;}
    private void resize(int delta){font=Math.max(16,Math.min(40,font+delta));for(TextView row:rows)row.setTextSize(font);last=-2;}
    private void chooseSpeed(){String[] a={"0.75x","1.0x","1.25x","1.5x"};float[] values={.75f,1f,1.25f,1.5f};new AlertDialog.Builder(this).setItems(a,(d,i)->{if(radio!=null)radio.speed(values[i]);}).show();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();try{
        if(request==4){try(ZipOutputStream zip=new ZipOutputStream(getContentResolver().openOutputStream(uri))){for(File f:new File[]{audio,subtitle}){zip.putNextEntry(new ZipEntry(f.getName()));Files.copy(f.toPath(),zip);zip.closeEntry();}}new AlertDialog.Builder(this).setMessage("��� ���� �Ϸ�").setPositiveButton("����",(d,w)->{Intent share=new Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(share,"��� ����"));}).setNegativeButton("�ݱ�",null).show();return;}
        if(request==1){File a=new File(getCacheDir(),"import.mp3"),s=new File(getCacheDir(),"import.srt");boolean hasA=false,hasS=false;try(ZipInputStream zip=new ZipInputStream(getContentResolver().openInputStream(uri))){ZipEntry e;while((e=zip.getNextEntry())!=null){String name=e.getName().toLowerCase(Locale.ROOT);if(!e.isDirectory()&&(name.endsWith(".mp3")||name.endsWith(".srt"))){boolean isAudio=name.endsWith(".mp3");if(isAudio?hasA:hasS)throw new IOException("Duplicate media");try(OutputStream out=new FileOutputStream(isAudio?a:s)){copy(zip,out,isAudio?100_000_000:2_000_000);}if(isAudio)hasA=true;else hasS=true;}zip.closeEntry();}}if(!hasA||!hasS)throw new IOException("MP3 + SRT required");Captions.parse(new String(Files.readAllBytes(s.toPath()),StandardCharsets.UTF_8));Files.copy(a.toPath(),audio.toPath(),StandardCopyOption.REPLACE_EXISTING);Files.copy(s.toPath(),subtitle.toPath(),StandardCopyOption.REPLACE_EXISTING);}
        else{File target=request==2?audio:subtitle;File temp=new File(getCacheDir(),"single-import");try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(temp)){copy(in,out,request==2?100_000_000:2_000_000);}if(request==3)Captions.parse(new String(Files.readAllBytes(temp.toPath()),StandardCharsets.UTF_8));Files.copy(temp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);}
        if(subtitle.isFile())readCues();if(audio.isFile()&&radio!=null&&request!=3)radio.load(audio);scroll.scrollTo(0,0);last=-2;
    }catch(Exception e){error(e);}}
    private void error(Exception e){new AlertDialog.Builder(this).setMessage("���� ����: "+e.getMessage()).setPositiveButton("Ȯ��",null).show();}
    private String clock(int ms){return String.format(Locale.ROOT,"%02d:%02d",ms/60000,(ms/1000)%60);}
    @Override protected void onDestroy(){handler.removeCallbacks(tick);unbindService(connection);super.onDestroy();}
}
