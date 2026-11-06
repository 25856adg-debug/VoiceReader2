package com.example.voicereader;

import android.app.*;
import android.os.*;
import android.content.*;
import android.net.Uri;
import android.speech.tts.TextToSpeech;
import android.view.*;
import android.widget.*;
import android.database.Cursor;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
    static final int PICK_TREE=101, PICK_FILE=102;
    TextView status, selected;
    EditText interval;
    Spinner voice, rate;
    Uri treeUri, fileUri;
    TextToSpeech tts;
    boolean ttsReady=false;
    Handler handler=new Handler(Looper.getMainLooper());
    Runnable pollTask;
    String[] voices={"系统默认","中文女声（如可用）","中文男声（如可用）"};
    String[] rates={"0.5倍","0.75倍","1.0倍","1.25倍","1.5倍"};

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        treeUri = readUri("tree");
        fileUri = readUri("file");
        buildUi();
        tts = new TextToSpeech(this, result -> {
            ttsReady = result == TextToSpeech.SUCCESS;
            if (ttsReady) {
                int lang=tts.setLanguage(Locale.SIMPLIFIED_CHINESE);
                status.setText(lang==TextToSpeech.LANG_MISSING_DATA || lang==TextToSpeech.LANG_NOT_SUPPORTED
                    ? "本机中文语音数据不可用，请安装中文 TTS 语音包" : "本机语音已就绪");
            } else status.setText("本机语音初始化失败");
        });
    }

    void buildUi() {
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this); root.setOrientation(1); root.setPadding(22,20,22,24);
        scroll.addView(root);
        TextView title=new TextView(this); title.setText("文件智能朗读器"); title.setTextSize(25); title.setPadding(0,0,0,14); root.addView(title);
        selected=new TextView(this); selected.setText(treeUri==null?"尚未选择监控文件夹":"已选择监控文件夹"); root.addView(selected);
        addButton(root,"选择监控文件夹",v->chooseTree());
        addButton(root,"选择单个 TXT / CSV / LOG 文件",v->chooseFile());
        addButton(root,"朗读所选文件最后一行",v->readSelected());
        addButton(root,"朗读最新文件最后一行",v->readLatest());
        TextView l1=new TextView(this); l1.setText("本机语音音色"); root.addView(l1);
        voice=new Spinner(this); voice.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,voices)); root.addView(voice);
        TextView l2=new TextView(this); l2.setText("朗读速度"); root.addView(l2);
        rate=new Spinner(this); rate.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,rates)); rate.setSelection(2); root.addView(rate);
        TextView l3=new TextView(this); l3.setText("自动检查间隔（秒）"); root.addView(l3);
        interval=new EditText(this); interval.setInputType(2); interval.setText("10"); root.addView(interval);
        addButton(root,"开始自动监控并朗读",v->startMonitor());
        addButton(root,"暂停朗读",v->command(ReaderService.PAUSE));
        addButton(root,"继续朗读",v->command(ReaderService.RESUME));
        addButton(root,"停止朗读",v->stopMonitor());
        status=new TextView(this); status.setText("准备就绪"); status.setTextSize(16); status.setPadding(0,18,0,0); root.addView(status);
        TextView note=new TextView(this);
        note.setText("\n说明：本机引擎使用 Android 系统 TTS。Edge TTS 需要配置可访问的 Edge TTS 服务地址，后续接入。首次使用请授予文件夹访问权限。");
        root.addView(note);
        setContentView(scroll);
    }
    void addButton(LinearLayout root,String text,android.view.View.OnClickListener l) {
        Button b=new Button(this); b.setText(text); root.addView(b); b.setOnClickListener(l);
    }
    void chooseTree() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i,PICK_TREE);
    }
    void chooseFile() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"text/plain","text/csv","application/octet-stream"});
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i,PICK_FILE);
    }
    @Override protected void onActivityResult(int req,int res,Intent data) {
        super.onActivityResult(req,res,data);
        if(res!=RESULT_OK||data==null)return;
        Uri u=data.getData();
        if(req==PICK_TREE) {
            int flags=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(u,flags);
            treeUri=u; saveUri("tree",u); selected.setText("监控目录："+u);
        } else if(req==PICK_FILE) {
            fileUri=u; saveUri("file",u); selected.setText("指定文件："+displayName(u));
        }
    }
    void saveUri(String key,Uri u){getSharedPreferences("reader",0).edit().putString(key,u.toString()).apply();}
    Uri readUri(String key){String s=getSharedPreferences("reader",0).getString(key,null);return s==null?null:Uri.parse(s);}
    String displayName(Uri u) {
        try(Cursor c=getContentResolver().query(u,null,null,null,null)){
            if(c!=null&&c.moveToFirst()){int n=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(n>=0)return c.getString(n);}
        }catch(Exception ignored){}
        return u.getLastPathSegment();
    }
    void readSelected() {
        if(fileUri==null){status.setText("请先选择一个文件");return;}
        readUriLastLine(fileUri);
    }
    void readLatest() {
        if(treeUri==null){status.setText("请先选择监控文件夹");return;}
        try {
            Uri child=latestTextFile(treeUri);
            if(child==null){status.setText("目录中没有 TXT / CSV / LOG 文件");return;}
            status.setText("最新文件："+displayName(child));
            readUriLastLine(child);
        } catch(Exception e){status.setText("读取目录失败："+e.getMessage());}
    }
    Uri latestTextFile(Uri tree) throws Exception {
        String docId=DocumentsContract.getTreeDocumentId(tree);
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,docId);
        Uri best=null; long bestTime=-1;
        try(Cursor c=getContentResolver().query(children,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_LAST_MODIFIED,DocumentsContract.Document.COLUMN_MIME_TYPE},null,null,null)){
            if(c==null)return null;
            while(c.moveToNext()){
                String name=c.getString(1); if(name==null)continue;
                String low=name.toLowerCase(Locale.ROOT);
                if(!(low.endsWith(".txt")||low.endsWith(".csv")||low.endsWith(".log")))continue;
                long tm=c.getLong(2);
                if(tm>bestTime){bestTime=tm;best=DocumentsContract.buildDocumentUriUsingTree(tree,c.getString(0));}
            }
        }
        return best;
    }
    String lastLine(Uri u) throws Exception {
        StringBuilder all=new StringBuilder();
        try(BufferedReader r=new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(u),"UTF-8"))){
            String line; while((line=r.readLine())!=null) all.append(line).append('\n');
        }
        String[] lines=all.toString().split("\\r?\\n");
        for(int i=lines.length-1;i>=0;i--)if(!lines[i].trim().isEmpty())return lines[i].trim();
        return "";
    }
    void readUriLastLine(Uri u) {
        try {
            String s=lastLine(u);
            if(s.isEmpty()){status.setText("文件最后一行为空");return;}
            if(ttsReady) {
                tts.setSpeechRate(new float[]{0.5f,0.75f,1f,1.25f,1.5f}[rate.getSelectedItemPosition()]);
                tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,"reader_line");
                status.setText("正在朗读："+s);
            } else status.setText("语音尚未就绪："+s);
        }catch(Exception e){status.setText("读取失败："+e.getMessage());}
    }
    void startMonitor() {
        if(treeUri==null){status.setText("请先选择监控文件夹");return;}
        int sec=10;try{sec=Math.max(3,Integer.parseInt(interval.getText().toString()));}catch(Exception ignored){}
        Intent i=new Intent(this,ReaderService.class); i.setAction(ReaderService.START); i.putExtra("tree",treeUri.toString());i.putExtra("interval",sec);
        if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
        status.setText("自动监控已启动（"+sec+"秒）");
    }
    void command(String action){Intent i=new Intent(this,ReaderService.class);i.setAction(action);startService(i);}
    void stopMonitor(){command(ReaderService.STOP);if(tts!=null)tts.stop();status.setText("已停止");}
    @Override protected void onDestroy(){if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}
}

