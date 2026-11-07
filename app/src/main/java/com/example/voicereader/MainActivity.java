package com.example.voicereader;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.media.AudioAttributes;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.*;
import android.widget.*;
import android.database.Cursor;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
    static final int PICK_TREE=101, PICK_FILE=102, REQ_NOTIFY=103;
    TextView status, selected, engineInfo;
    EditText interval, webUrl;
    CheckBox monitorLocalBox, monitorWebBox, networkVoiceBox;
    String confirmedWebUrl="";
    Spinner voice, rate;
    SeekBar gainBar;
    TextView gainLabel;
    Uri treeUri, fileUri;
    TextToSpeech tts;
    boolean ttsReady=false;
    final ArrayList<Voice> availableVoices=new ArrayList<>();
    final ArrayList<String> voiceLabels=new ArrayList<>();
    final String[] rates={"0.5倍","0.75倍","1.0倍","1.25倍","1.5倍"};
    final float[] speedValues={0.5f,0.75f,1.0f,1.25f,1.5f};

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        treeUri=readUri("tree"); fileUri=readUri("file");
        buildUi(); refreshSelection();
        confirmedWebUrl=getPreferences(0).getString("web_url","");
        webUrl.setText(confirmedWebUrl);
        requestNotificationPermission(); initTTS();
    }

    void buildUi() {
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(1); root.setPadding(22,20,22,24); scroll.addView(root);
        TextView title=new TextView(this); title.setText("文件智能朗读器");
        title.setTextSize(25); title.setPadding(0,0,0,14); root.addView(title);
        selected=new TextView(this); selected.setTextSize(14); root.addView(selected);
        addButton(root,"选择监控文件夹",v->chooseTree());
        addButton(root,"选择单个 TXT / CSV / LOG 文件",v->chooseFile());
        addButton(root,"朗读所选文件最后一行",v->readSelected());
        addButton(root,"朗读最新文件最后一行",v->readLatest());

        TextView webLabel=new TextView(this);
        webLabel.setText("网页监控地址（纯文本直链或普通网页）"); root.addView(webLabel);
        webUrl=new EditText(this); webUrl.setSingleLine(true);
        webUrl.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        webUrl.setHint("https://example.com/log.txt 或 https://example.com/page");
        root.addView(webUrl);
        addButton(root,"确定 / 保存网页地址",v->confirmWebUrl());
        TextView sourceLabel=new TextView(this); sourceLabel.setText("选择要监控的数据来源（可同时勾选）"); root.addView(sourceLabel);
        monitorLocalBox=new CheckBox(this); monitorLocalBox.setText("监控本机文件夹最新 TXT / CSV / LOG");
        monitorLocalBox.setChecked(treeUri!=null); root.addView(monitorLocalBox);
        monitorWebBox=new CheckBox(this); monitorWebBox.setText("监控已确定的网页地址");
        monitorWebBox.setChecked(!confirmedWebUrl.isEmpty()); root.addView(monitorWebBox);

        engineInfo=new TextView(this); engineInfo.setPadding(0,12,0,8);
        engineInfo.setText("语音引擎：正在检测"); root.addView(engineInfo);
        networkVoiceBox=new CheckBox(this);
        networkVoiceBox.setText("联网语音模式（优先选择需要网络的中文语音包）");
        networkVoiceBox.setChecked(getPreferences(0).getBoolean("network_voice",false));
        networkVoiceBox.setOnCheckedChangeListener((button,checked)->{
            getPreferences(0).edit().putBoolean("network_voice",checked).apply();
            if(ttsReady) applyNetworkVoice();
        });
        root.addView(networkVoiceBox);
        addButton(root,"测试中文语音",v->testSpeech());

        TextView l1=new TextView(this); l1.setText("本机语音音色"); root.addView(l1);
        voice=new Spinner(this);
        voice.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"系统默认（初始化中）"}));
        root.addView(voice);

        TextView l2=new TextView(this); l2.setText("朗读速度"); root.addView(l2);
        rate=new Spinner(this);
        rate.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,rates));
        rate.setSelection(2); root.addView(rate);

        TextView gainTitle=new TextView(this); gainTitle.setText("朗读增强音量（50%～300%，默认200%）"); root.addView(gainTitle);
        gainLabel=new TextView(this); root.addView(gainLabel);
        gainBar=new SeekBar(this); gainBar.setMax(250); gainBar.setProgress(150); root.addView(gainBar);
        gainLabel.setText("软件增益：200%");
        gainBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar,int progress,boolean fromUser) {
                gainLabel.setText("软件增益："+(50+progress)+"%"+(50+progress>=250?"（高增益可能失真）":""));
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });

        TextView l3=new TextView(this); l3.setText("自动检查间隔（秒）"); root.addView(l3);
        interval=new EditText(this); interval.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        interval.setText("10"); root.addView(interval);

        addButton(root,"开始自动监控并朗读",v->startMonitor());
        addButton(root,"暂停朗读",v->command(ReaderService.PAUSE));
        addButton(root,"继续朗读",v->command(ReaderService.RESUME));
        addButton(root,"停止朗读",v->command(ReaderService.STOP));
        status=new TextView(this); status.setText("正在准备"); status.setTextSize(16);
        status.setPadding(0,18,0,0); root.addView(status);

        TextView note=new TextView(this);
        note.setText("说明：本机目录和网页地址可同时监控。网页支持 TXT/CSV/LOG 纯文本直链，以及可直接请求的静态 HTML 网页。需要登录、Cookie、验证码或 JavaScript 动态加载的页面暂不支持。后台运行时通知栏会显示监控状态。");
        root.addView(note); setContentView(scroll);
    }

    void confirmWebUrl() {
        String url=webUrl.getText().toString().trim();
        if(!url.isEmpty() && !(url.startsWith("http://")||url.startsWith("https://"))) {
            status.setText("网址必须以 http:// 或 https:// 开头"); return;
        }
        confirmedWebUrl=url;
        getPreferences(0).edit().putString("web_url",confirmedWebUrl).apply();
        monitorWebBox.setChecked(!confirmedWebUrl.isEmpty());
        status.setText(confirmedWebUrl.isEmpty()?"网页地址已清空":"网页地址已确定并保存："+confirmedWebUrl);
    }

    void addButton(LinearLayout root,String text,android.view.View.OnClickListener listener) {
        Button b=new Button(this); b.setText(text); root.addView(b); b.setOnClickListener(listener);
    }

    void initTTS() {
        status.setText("正在初始化系统语音引擎……");
        tts=new TextToSpeech(this,result->{
            if(result!=TextToSpeech.SUCCESS) {
                ttsReady=false; engineInfo.setText("语音引擎：初始化失败");
                status.setText("TTS 初始化失败，请检查系统语音引擎"); return;
            }
            ttsReady=true;
            try {
                tts.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
                int lang=tts.setLanguage(Locale.forLanguageTag("zh-CN"));
                String engine=tts.getDefaultEngine();
                engineInfo.setText("语音引擎："+(engine==null?"系统默认引擎":engine));
                loadChineseVoices();
                if(networkVoiceBox.isChecked()) applyNetworkVoice();
                status.setText((lang==TextToSpeech.LANG_MISSING_DATA || lang==TextToSpeech.LANG_NOT_SUPPORTED)
                    ?"中文语音数据不可用，请检查系统TTS":"中文语音已就绪");
                tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                    @Override public void onStart(String id) { runOnUiThread(()->status.setText("正在朗读……")); }
                    @Override public void onDone(String id) { runOnUiThread(()->status.setText("朗读完成")); }
                    @Override public void onError(String id) { runOnUiThread(()->status.setText("TTS 播放失败")); }
                    @Override public void onError(String id,int code) { runOnUiThread(()->status.setText("TTS 错误："+code)); }
                });
            } catch(Exception e) { status.setText("TTS 初始化异常："+e.getMessage()); }
        });
    }

    void loadChineseVoices() {
        availableVoices.clear(); voiceLabels.clear(); voiceLabels.add("系统默认");
        try {
            Set<Voice> all=tts.getVoices();
            if(all!=null) for(Voice v:all) {
                Locale l=v.getLocale();
                if(l!=null && "zh".equalsIgnoreCase(l.getLanguage())) {
                    availableVoices.add(v);
                    voiceLabels.add((v.isNetworkConnectionRequired()?"[联网] ":"[离线] ")+l.getDisplayName()+" · "+v.getName());
                }
            }
        } catch(Exception ignored) {}
        voice.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,voiceLabels));
    }

    void applySelectedVoice() {
        if(tts==null) return;
        if(networkVoiceBox!=null && networkVoiceBox.isChecked()) { applyNetworkVoice(); return; }
        int p=voice.getSelectedItemPosition();
        try {
            if(p>0 && p-1<availableVoices.size()) tts.setVoice(availableVoices.get(p-1));
            else tts.setLanguage(Locale.forLanguageTag("zh-CN"));
        } catch(Exception ignored) {}
    }

    boolean applyNetworkVoice() {
        if(tts==null || Build.VERSION.SDK_INT<21) {
            if(status!=null) status.setText("当前系统不支持联网语音选择");
            return false;
        }
        try {
            Voice best=null;
            Set<Voice> all=tts.getVoices();
            if(all!=null) for(Voice v:all) {
                Locale l=v.getLocale();
                if(l!=null && "zh".equalsIgnoreCase(l.getLanguage()) && v.isNetworkConnectionRequired()) {
                    if(best==null || v.getQuality()>best.getQuality()) best=v;
                }
            }
            if(best==null) {
                if(status!=null) status.setText("没有找到联网中文语音包。请先在系统 TTS 设置中下载/启用联网中文语音");
                return false;
            }
            int r=tts.setVoice(best);
            if(status!=null) status.setText(r==TextToSpeech.SUCCESS?"已选择联网语音："+best.getName():"联网语音选择失败："+r);
            return r==TextToSpeech.SUCCESS;
        } catch(Exception e) {
            if(status!=null) status.setText("联网语音选择异常："+e.getMessage());
            return false;
        }
    }

    void testSpeech() {
        if(tts==null || !ttsReady) { status.setText("语音引擎尚未初始化"); return; }
        try {
            tts.stop();
            int lang=tts.setLanguage(Locale.forLanguageTag("zh-CN"));
            if(lang==TextToSpeech.LANG_MISSING_DATA || lang==TextToSpeech.LANG_NOT_SUPPORTED) {
                status.setText("中文语音不可用，请在系统TTS设置中安装中文语音数据"); return;
            }
            if(networkVoiceBox.isChecked() && !applyNetworkVoice()) return;
            tts.setSpeechRate(1f);
            int r=tts.speak("你好，现在正在测试中文语音，一二三四五。",TextToSpeech.QUEUE_FLUSH,null,"test_"+System.currentTimeMillis());
            status.setText(r==TextToSpeech.ERROR?"中文语音提交失败":"已提交测试，请听声音");
        } catch(Exception e) { status.setText("测试异常："+e.getMessage()); }
    }

    void speakText(String text) {
        if(tts==null || !ttsReady) { status.setText("语音引擎尚未初始化"); return; }
        try {
            applySelectedVoice();
            int p=rate.getSelectedItemPosition(); if(p<0 || p>=speedValues.length) p=2;
            tts.setSpeechRate(speedValues[p]);
            int r=tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"manual_"+System.currentTimeMillis());
            status.setText(r==TextToSpeech.ERROR?"TTS 拒绝播放":"已提交语音播放");
        } catch(Exception e) { status.setText("播放异常："+e.getMessage()); }
    }

    void chooseTree() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i,PICK_TREE);
    }
    void chooseFile() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"text/plain","text/csv","application/octet-stream"});
        i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i,PICK_FILE);
    }

    @Override protected void onActivityResult(int req,int res,Intent data) {
        super.onActivityResult(req,res,data);
        if(res!=RESULT_OK || data==null || data.getData()==null) return;
        Uri u=data.getData();
        try {
            int f=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(u,f);
        } catch(Exception ignored) {}
        if(req==PICK_TREE) { treeUri=u; saveUri("tree",u); if(monitorLocalBox!=null) monitorLocalBox.setChecked(true); refreshSelection(); status.setText("监控文件夹授权成功"); }
        else if(req==PICK_FILE) { fileUri=u; saveUri("file",u); refreshSelection(); status.setText("文件已选择"); }
    }

    void refreshSelection() {
        selected.setText("监控目录："+(treeUri==null?"未选择":treeDisplayName(treeUri))+
            "
指定文件："+(fileUri==null?"未选择":displayName(fileUri)));
    }
    String treeDisplayName(Uri tree) {
        try {
            String id=DocumentsContract.getTreeDocumentId(tree);
            Uri doc=DocumentsContract.buildDocumentUriUsingTree(tree,id);
            try(Cursor c=getContentResolver().query(doc,new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)) {
                if(c!=null && c.moveToFirst()) return c.getString(0);
            }
        } catch(Exception ignored) {}
        return "已选择目录";
    }
    String displayName(Uri u) {
        try(Cursor c=getContentResolver().query(u,null,null,null,null)) {
            if(c!=null && c.moveToFirst()) { int n=c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if(n>=0) return c.getString(n); }
        } catch(Exception ignored) {}
        return "未知文件";
    }
    void saveUri(String k,Uri u) { getSharedPreferences("reader",0).edit().putString(k,u.toString()).apply(); }
    Uri readUri(String k) { String s=getSharedPreferences("reader",0).getString(k,null); return s==null?null:Uri.parse(s); }

    void readSelected() {
        if(fileUri==null) { status.setText("请先选择一个文件"); return; }
        readUriLastLine(fileUri);
    }
    void readLatest() {
        if(treeUri==null) { status.setText("请先选择监控文件夹"); return; }
        try {
            Uri u=latestTextFile(treeUri);
            if(u==null) { status.setText("目录中没有 TXT/CSV/LOG 文件"); return; }
            readUriLastLine(u);
        } catch(Exception e) { status.setText("读取目录失败："+e.getMessage()); }
    }
    Uri latestTextFile(Uri tree) throws Exception {
        String id=DocumentsContract.getTreeDocumentId(tree);
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,id);
        Uri best=null; long bestTime=-1;
        try(Cursor c=getContentResolver().query(children,new String[]{
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED},null,null,null)) {
            if(c==null) return null;
            while(c.moveToNext()) {
                String n=c.getString(1); if(n==null) continue;
                String low=n.toLowerCase(Locale.ROOT);
                if(!(low.endsWith(".txt")||low.endsWith(".csv")||low.endsWith(".log"))) continue;
                long t=c.getLong(2);
                if(t>bestTime) { bestTime=t; best=DocumentsContract.buildDocumentUriUsingTree(tree,c.getString(0)); }
            }
        }
        return best;
    }
    String lastLine(Uri u) throws Exception {
        String last="";
        try(BufferedReader r=new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(u),"UTF-8"))) {
            String line; while((line=r.readLine())!=null) if(!line.trim().isEmpty()) last=line.trim();
        }
        return last;
    }
    void readUriLastLine(Uri u) {
        try { String s=lastLine(u); if(s.isEmpty()) status.setText("文件最后一行为空"); else speakText(s); }
        catch(Exception e) { status.setText("读取失败："+e.getMessage()); }
    }

    void startMonitor() {
        boolean useLocal=monitorLocalBox.isChecked();
        boolean useWeb=monitorWebBox.isChecked();
        String url=confirmedWebUrl;
        if(useLocal && treeUri==null) { status.setText("已勾选本机监控，但还没有选择文件夹"); return; }
        if(useWeb && url.isEmpty()) { status.setText("请先输入网页地址并点击“确定 / 保存网页地址”"); return; }
        if(!useLocal && !useWeb) { status.setText("请至少勾选一种监控来源"); return; }
        int sec=10; try { sec=Math.max(3,Integer.parseInt(interval.getText().toString().trim())); } catch(Exception ignored) {}
        int p=rate.getSelectedItemPosition(); if(p<0||p>=speedValues.length) p=2;
        Intent i=new Intent(this,ReaderService.class); i.setAction(ReaderService.START);
        if(useLocal && treeUri!=null) i.putExtra("tree",treeUri.toString());
        i.putExtra("monitor_local",useLocal); i.putExtra("monitor_web",useWeb);
        i.putExtra("url",useWeb?url:""); i.putExtra("interval",sec); i.putExtra("speed",speedValues[p]);
        i.putExtra("gain",(50+gainBar.getProgress())/100f);
        i.putExtra("network_voice",networkVoiceBox.isChecked());
        if(Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i);
        status.setText("后台监控已启动："+sec+"秒");
    }
    void command(String action) {
        Intent i=new Intent(this,ReaderService.class); i.setAction(action);
        if(Build.VERSION.SDK_INT>=26 && !ReaderService.STOP.equals(action)) startForegroundService(i);
        else startService(i);
    }
    void requestNotificationPermission() {
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},REQ_NOTIFY);
    }
    @Override protected void onDestroy() {
        if(tts!=null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
}

