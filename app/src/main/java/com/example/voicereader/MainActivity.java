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
import android.text.InputType;

import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {

    static final int PICK_TREE=101, PICK_FILE=102, REQ_NOTIFY=103;

    TextView status,selected,engineInfo,gainLabel;
    EditText interval,webUrl,serverUrl,apiKey;
    CheckBox monitorLocalBox,monitorWebBox;
    Spinner voice,rate,mode,edgeVoice;
    SeekBar gainBar;

    String confirmedWebUrl="";
    Uri treeUri,fileUri;

    TextToSpeech tts;
    boolean ttsReady=false;
    boolean keyVerified=false;

    final ArrayList<Voice> availableVoices=new ArrayList<>();
    final ArrayList<String> voiceLabels=new ArrayList<>();

    final String[] rates={
        "0.5倍","0.75倍","1.0倍","1.25倍","1.5倍"
    };

    final float[] speedValues={
        0.5f,0.75f,1.0f,1.25f,1.5f
    };

    final String[] edgeVoiceNames={
        "晓晓（女声）",
        "云扬（男声）",
        "晓伊（女声）",
        "云希（男声）"
    };

    final String[] edgeVoiceIds={
        "zh-CN-XiaoxiaoNeural",
        "zh-CN-YunyangNeural",
        "zh-CN-XiaoyiNeural",
        "zh-CN-YunxiNeural"
    };

    final ExecutorService netExecutor=
            Executors.newSingleThreadExecutor();

    SharedPreferences prefs;

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);

        prefs=getSharedPreferences("reader",0);

        // 旧版本的模式编号：1 代表 Edge 在线；新版编号改为 2。
        if(!prefs.getBoolean("mode_v2_migrated",false)) {
            int oldMode=prefs.getInt("mode",0);
            SharedPreferences.Editor edit=prefs.edit().putBoolean("mode_v2_migrated",true);
            if(oldMode==1) edit.putInt("mode",2);
            edit.apply();
        }

        treeUri=readUri("tree");
        fileUri=readUri("file");

        buildUi();

        refreshSelection();

        confirmedWebUrl=prefs.getString("web_url","");

        webUrl.setText(confirmedWebUrl);

        serverUrl.setText(
                prefs.getString(
                        "server_url",
                        "http://170.106.188.189:6363"
                )
        );

        apiKey.setText(
                prefs.getString("api_key","")
        );

        keyVerified=prefs.getBoolean("key_verified",false);

        requestNotificationPermission();

        initTTS();

        updateKeyStatus();
    }

    void buildUi() {

        ScrollView scroll=new ScrollView(this);

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(1);
        root.setPadding(22,20,22,24);

        scroll.addView(root);

        TextView title=new TextView(this);
        title.setText("文件智能朗读器");
        title.setTextSize(25);
        title.setPadding(0,0,0,14);
        root.addView(title);

        selected=new TextView(this);
        selected.setTextSize(14);
        root.addView(selected);

        addButton(root,"选择监控文件夹",v->chooseTree());
        addButton(root,"选择单个 TXT / CSV / LOG 文件",v->chooseFile());
        addButton(root,"朗读所选文件最后一行",v->readSelected());
        addButton(root,"朗读最新文件最后一行",v->readLatest());

        addDivider(root);

        TextView webLabel=new TextView(this);
        webLabel.setText("网页监控地址");
        root.addView(webLabel);

        webUrl=new EditText(this);
        webUrl.setSingleLine(true);
        webUrl.setInputType(
                InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_VARIATION_URI
        );
        webUrl.setHint("https://example.com/log.txt");
        root.addView(webUrl);

        addButton(root,"确定 / 保存网页地址",v->confirmWebUrl());

        monitorLocalBox=new CheckBox(this);
        monitorLocalBox.setText("监控本机文件夹最新 TXT / CSV / LOG");
        monitorLocalBox.setChecked(treeUri!=null);
        root.addView(monitorLocalBox);

        monitorWebBox=new CheckBox(this);
        monitorWebBox.setText("监控已确定的网页地址");
        root.addView(monitorWebBox);

        addDivider(root);

        TextView serverTitle=new TextView(this);
        serverTitle.setText("Edge TTS 在线语音服务器");
        serverTitle.setTextSize(19);
        root.addView(serverTitle);

        TextView serverLabel=new TextView(this);
        serverLabel.setText("服务器地址");
        root.addView(serverLabel);

        serverUrl=new EditText(this);
        serverUrl.setSingleLine(true);
        serverUrl.setInputType(
                InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_VARIATION_URI
        );
        serverUrl.setHint("http://服务器IP:6363");
        root.addView(serverUrl);

        TextView keyLabel=new TextView(this);
        keyLabel.setText("API 密钥");
        root.addView(keyLabel);

        apiKey=new EditText(this);
        apiKey.setSingleLine(true);
        apiKey.setInputType(
                InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_VARIATION_PASSWORD
        );
        apiKey.setHint("输入服务器 API 密钥");
        root.addView(apiKey);

        addButton(root,"验证并保存服务器密钥",v->verifyServerKey());

        addButton(root,"清除已保存的密钥",v->clearKey());

        engineInfo=new TextView(this);
        engineInfo.setPadding(0,10,0,8);
        engineInfo.setText("服务器状态：尚未验证");
        root.addView(engineInfo);

        addDivider(root);

        TextView modeLabel=new TextView(this);
        modeLabel.setText("朗读模式");
        root.addView(modeLabel);

        mode=new Spinner(this);

        mode.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{
                        "本机离线语音（无需网络）",
                        "本机联网语音（使用手机 TTS）",
                        "Edge TTS 在线语音（使用服务器）"
                }
        ));

        mode.setSelection(
                prefs.getInt("mode",0)
        );

        root.addView(mode);

        TextView edgeLabel=new TextView(this);
        edgeLabel.setText("Edge TTS 在线音色");
        root.addView(edgeLabel);

        edgeVoice=new Spinner(this);

        edgeVoice.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                edgeVoiceNames
        ));

        edgeVoice.setSelection(
                prefs.getInt("edge_voice_index",0)
        );

        root.addView(edgeVoice);

        TextView localLabel=new TextView(this);
        localLabel.setText("本机中文语音音色");
        root.addView(localLabel);

        voice=new Spinner(this);

        voice.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"系统默认（初始化中）"}
        ));

        root.addView(voice);

        addButton(root,"测试当前选择的语音",v->testSpeech());

        addDivider(root);

        TextView rateLabel=new TextView(this);
        rateLabel.setText("朗读速度");
        root.addView(rateLabel);

        rate=new Spinner(this);

        rate.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                rates
        ));

        rate.setSelection(2);
        root.addView(rate);

        TextView gainTitle=new TextView(this);
        gainTitle.setText("朗读增强音量（50%～300%，默认200%）");
        root.addView(gainTitle);

        gainLabel=new TextView(this);
        root.addView(gainLabel);

        gainBar=new SeekBar(this);
        gainBar.setMax(250);
        gainBar.setProgress(150);
        root.addView(gainBar);

        gainLabel.setText("软件增益：200%");

        gainBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(
                    SeekBar bar,int progress,boolean fromUser
            ) {
                int value=50+progress;

                gainLabel.setText(
                        "软件增益："+value+"%" +
                        (value>=250?"（高增益可能失真）":"")
                );
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {}

            @Override
            public void onStopTrackingTouch(SeekBar bar) {}
        });

        TextView intervalLabel=new TextView(this);
        intervalLabel.setText("自动检查间隔（秒）");
        root.addView(intervalLabel);

        interval=new EditText(this);
        interval.setInputType(InputType.TYPE_CLASS_NUMBER);
        interval.setText("10");
        root.addView(interval);

        addDivider(root);

        addButton(root,"开始自动监控并朗读",v->startMonitor());
        addButton(root,"暂停朗读",v->command(ReaderService.PAUSE));
        addButton(root,"继续朗读",v->command(ReaderService.RESUME));
        addButton(root,"停止朗读",v->command(ReaderService.STOP));

        status=new TextView(this);
        status.setText("正在准备");
        status.setTextSize(16);
        status.setPadding(0,18,0,0);
        root.addView(status);

        TextView note=new TextView(this);

        note.setText(
                "说明：本机目录和网页地址可以同时监控。" +
                "Edge TTS 需要先验证服务器密钥。" +
                "本机离线语音无需网络；本机联网语音使用手机 TTS 引擎的联网音色；Edge TTS 使用已验证的服务器。" +
                "本机监控和网页监控可同时开启，三种朗读模式可随时切换。" +
                "网页暂不支持需要登录、Cookie、验证码或 JavaScript 动态加载的页面。"
        );

        root.addView(note);

        setContentView(scroll);

        mode.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {

            @Override
            public void onItemSelected(
                    AdapterView<?> parent,View view,int position,long id
            ) {
                prefs.edit().putInt("mode",position).apply();

                if(position==2 && !keyVerified) {
                    status.setText("请先验证 Edge TTS 服务器密钥");
                } else if(position==1) {
                    status.setText("已选择本机联网语音；需要手机 TTS 引擎提供联网中文音色");
                } else if(position==0) {
                    status.setText("已选择本机离线语音；无需网络");
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        edgeVoice.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {

            @Override
            public void onItemSelected(
                    AdapterView<?> parent,View view,int position,long id
            ) {
                prefs.edit()
                        .putInt("edge_voice_index",position)
                        .apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    void addDivider(LinearLayout root) {
        View line=new View(this);
        line.setBackgroundColor(0xFFBBBBBB);

        LinearLayout.LayoutParams p=
                new LinearLayout.LayoutParams(
                        -1,1
                );

        p.setMargins(0,18,0,18);
        root.addView(line,p);
    }

    void addButton(
            LinearLayout root,
            String text,
            View.OnClickListener listener
    ) {
        Button b=new Button(this);
        b.setText(text);
        root.addView(b);
        b.setOnClickListener(listener);
    }

    void updateKeyStatus() {
        if(engineInfo==null) return;

        engineInfo.setText(
                keyVerified
                ?"服务器状态：密钥验证成功"
                :"服务器状态：尚未验证"
        );
    }

    void verifyServerKey() {

        String base=serverUrl.getText().toString().trim();
        String key=apiKey.getText().toString().trim();

        if(!(base.startsWith("http://") ||
             base.startsWith("https://"))) {

            status.setText("服务器地址必须以 http:// 或 https:// 开头");
            return;
        }

        if(key.isEmpty()) {
            status.setText("请先输入 API 密钥");
            return;
        }

        status.setText("正在连接服务器验证密钥……");

        netExecutor.execute(()->{

            HttpURLConnection conn=null;

            try {

                String endpoint=base.replaceAll("/+$","")+
                        "/auth/check";

                conn=(HttpURLConnection)new URL(endpoint)
                        .openConnection();

                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                conn.setRequestProperty(
                        "Authorization","Bearer "+key
                );

                int code=conn.getResponseCode();

                InputStream in=code>=200 && code<300
                        ?conn.getInputStream()
                        :conn.getErrorStream();

                StringBuilder response=new StringBuilder();

                if(in!=null) {
                    try(BufferedReader r=new BufferedReader(
                            new InputStreamReader(in,"UTF-8")
                    )) {
                        String line;
                        while((line=r.readLine())!=null) {
                            response.append(line);
                        }
                    }
                }

                JSONObject json=new JSONObject(
                        response.toString()
                );

                boolean valid=
                        code==200 &&
                        json.optBoolean("valid",false);

                runOnUiThread(()->{

                    if(valid) {

                        keyVerified=true;

                        prefs.edit()
                                .putString("server_url",base)
                                .putString("api_key",key)
                                .putBoolean("key_verified",true)
                                .apply();

                        updateKeyStatus();

                        status.setText(
                                "验证成功，Edge TTS 在线语音已解锁"
                        );

                    } else {

                        keyVerified=false;

                        prefs.edit()
                                .putBoolean("key_verified",false)
                                .apply();

                        updateKeyStatus();

                        status.setText(
                                "密钥验证失败，HTTP "+code
                        );
                    }
                });

            } catch(Exception e) {

                runOnUiThread(()->{
                    keyVerified=false;

                    prefs.edit()
                            .putBoolean("key_verified",false)
                            .apply();

                    updateKeyStatus();

                    status.setText(
                            "连接验证失败："+e.getMessage()
                    );
                });

            } finally {

                if(conn!=null) conn.disconnect();
            }
        });
    }

    void clearKey() {

        keyVerified=false;

        apiKey.setText("");

        prefs.edit()
                .remove("api_key")
                .putBoolean("key_verified",false)
                .apply();

        updateKeyStatus();

        status.setText("已清除密钥，需要重新验证才能使用在线语音");
    }

    String selectedServer() {
        return serverUrl.getText().toString().trim();
    }

    String selectedKey() {
        return apiKey.getText().toString().trim();
    }

    int selectedRatePosition() {
        int p=rate.getSelectedItemPosition();

        if(p<0 || p>=speedValues.length) return 2;

        return p;
    }

    String selectedEdgeVoice() {
        int p=edgeVoice.getSelectedItemPosition();

        if(p<0 || p>=edgeVoiceIds.length) p=0;

        return edgeVoiceIds[p];
    }

    boolean isEdgeMode() {
        return mode.getSelectedItemPosition()==2;
    }

    boolean isNetworkLocalMode() {
        return mode.getSelectedItemPosition()==1;
    }

    String selectedLocalVoiceName() {
        int p=voice==null?0:voice.getSelectedItemPosition();
        if(p>0 && p-1<availableVoices.size()) {
            return availableVoices.get(p-1).getName();
        }
        return "";
    }

    void confirmWebUrl() {

        String url=webUrl.getText().toString().trim();

        if(!url.isEmpty() &&
                !(url.startsWith("http://") ||
                  url.startsWith("https://"))) {

            status.setText("网址必须以 http:// 或 https:// 开头");
            return;
        }

        confirmedWebUrl=url;

        prefs.edit()
                .putString("web_url",confirmedWebUrl)
                .apply();

        monitorWebBox.setChecked(!confirmedWebUrl.isEmpty());

        status.setText(
                confirmedWebUrl.isEmpty()
                ?"网页地址已清空"
                :"网页地址已确定并保存"
        );
    }

    void initTTS() {

        status.setText("正在初始化系统语音引擎……");

        tts=new TextToSpeech(this,result->{

            if(result!=TextToSpeech.SUCCESS) {

                ttsReady=false;

                engineInfo.setText("本机语音引擎：初始化失败");

                status.setText("本机 TTS 初始化失败");

                return;
            }

            ttsReady=true;

            try {

                tts.setAudioAttributes(
                        new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                );

                tts.setLanguage(Locale.forLanguageTag("zh-CN"));

                String engine=tts.getDefaultEngine();

                engineInfo.setText(
                        "本机语音引擎："+(engine==null?"系统默认":engine)
                );

                loadChineseVoices();

                status.setText("本机中文语音已就绪");

            } catch(Exception e) {
                status.setText("TTS 初始化异常："+e.getMessage());
            }
        });
    }

    void loadChineseVoices() {

        availableVoices.clear();
        voiceLabels.clear();

        voiceLabels.add("系统默认");

        try {

            Set<Voice> all=tts.getVoices();

            if(all!=null) {

                for(Voice v:all) {

                    Locale l=v.getLocale();

                    if(l!=null &&
                            "zh".equalsIgnoreCase(l.getLanguage())) {

                        availableVoices.add(v);

                        voiceLabels.add(
                                (v.isNetworkConnectionRequired()
                                ?"[联网] ":"[离线] ")+
                                l.getDisplayName()+" · "+v.getName()
                        );
                    }
                }
            }

        } catch(Exception ignored) {}

        voice.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                voiceLabels
        ));
    }

    void applySelectedVoice() {

        if(tts==null) return;

        int p=voice.getSelectedItemPosition();

        try {
            boolean wantNetwork=isNetworkLocalMode();
            Voice chosen=null;

            if(p>0 && p-1<availableVoices.size()) {
                Voice selected=availableVoices.get(p-1);
                if(selected.isNetworkConnectionRequired()==wantNetwork) chosen=selected;
            }

            if(chosen==null) {
                for(Voice candidate:availableVoices) {
                    if(candidate.isNetworkConnectionRequired()==wantNetwork &&
                            (chosen==null || candidate.getQuality()>chosen.getQuality())) {
                        chosen=candidate;
                    }
                }
            }

            if(chosen!=null) tts.setVoice(chosen);
            else tts.setLanguage(Locale.forLanguageTag("zh-CN"));

        } catch(Exception ignored) {}
    }

    void testSpeech() {
        String sample="你好，现在正在测试当前选择的中文语音，一二三四五。";

        if(isEdgeMode() && !keyVerified) {
            status.setText("请先验证服务器密钥");
            return;
        }

        // 测试也走后台服务，确保测试路径与自动监控的实际朗读路径一致。
        speakThroughService(sample);
    }

    void speakText(String text) {

        if(isEdgeMode()) {

            if(!keyVerified) {
                status.setText("请先验证服务器密钥");
                return;
            }

            speakThroughService(text);
            return;
        }

        if(tts==null || !ttsReady) {
            status.setText("本机语音引擎尚未初始化");
            return;
        }

        try {

            applySelectedVoice();

            tts.setSpeechRate(
                    speedValues[selectedRatePosition()]
            );

            int r=tts.speak(
                    text,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "manual_"+System.currentTimeMillis()
            );

            status.setText(
                    r==TextToSpeech.ERROR
                    ?"本机 TTS 拒绝播放"
                    :"已提交本机语音播放"
            );

        } catch(Exception e) {
            status.setText("播放异常："+e.getMessage());
        }
    }

    void speakThroughService(String text) {

        Intent i=new Intent(this,ReaderService.class);

        i.setAction(ReaderService.SPEAK);

        i.putExtra("text",text);

        putSpeechSettings(i);

        startReaderService(i);

        status.setText(isEdgeMode()?"已提交 Edge TTS 服务器在线朗读":
                (isNetworkLocalMode()?"已提交手机本机联网 TTS 朗读":"已提交手机本机离线 TTS 朗读"));
    }

    void putSpeechSettings(Intent i) {

        i.putExtra("server_url",selectedServer());
        i.putExtra("api_key",selectedKey());

        i.putExtra("edge_mode",isEdgeMode());
        i.putExtra("network_voice",isNetworkLocalMode());
        i.putExtra("local_voice_name",selectedLocalVoiceName());
        i.putExtra("edge_voice",selectedEdgeVoice());

        i.putExtra(
                "speed",
                speedValues[selectedRatePosition()]
        );

        i.putExtra(
                "gain",
                (50+gainBar.getProgress())/100f
        );
    }

    void startReaderService(Intent i) {

        if(Build.VERSION.SDK_INT>=26) {
            startForegroundService(i);
        } else {
            startService(i);
        }
    }

    void chooseTree() {

        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);

        i.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        );

        startActivityForResult(i,PICK_TREE);
    }

    void chooseFile() {

        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);

        i.setType("*/*");

        i.putExtra(
                Intent.EXTRA_MIME_TYPES,
                new String[]{
                        "text/plain",
                        "text/csv",
                        "application/octet-stream"
                }
        );

        i.addCategory(Intent.CATEGORY_OPENABLE);

        startActivityForResult(i,PICK_FILE);
    }

    @Override
    protected void onActivityResult(int req,int res,Intent data) {

        super.onActivityResult(req,res,data);

        if(res!=RESULT_OK || data==null || data.getData()==null) return;

        Uri u=data.getData();

        try {

            int f=data.getFlags() &
                    (Intent.FLAG_GRANT_READ_URI_PERMISSION |
                     Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            getContentResolver().takePersistableUriPermission(u,f);

        } catch(Exception ignored) {}

        if(req==PICK_TREE) {

            treeUri=u;

            saveUri("tree",u);

            if(monitorLocalBox!=null) {
                monitorLocalBox.setChecked(true);
            }

            refreshSelection();

            status.setText("监控文件夹授权成功");

        } else if(req==PICK_FILE) {

            fileUri=u;

            saveUri("file",u);

            refreshSelection();

            status.setText("文件已选择");
        }
    }

    void refreshSelection() {

        selected.setText(
                "监控目录："+(treeUri==null
                        ?"未选择":treeDisplayName(treeUri))+
                "\n指定文件："+(fileUri==null
                        ?"未选择":displayName(fileUri))
        );
    }

    String treeDisplayName(Uri tree) {

        try {

            String id=DocumentsContract.getTreeDocumentId(tree);

            Uri doc=DocumentsContract.buildDocumentUriUsingTree(tree,id);

            try(Cursor c=getContentResolver().query(
                    doc,
                    new String[]{
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME
                    },
                    null,null,null
            )) {

                if(c!=null && c.moveToFirst()) return c.getString(0);
            }

        } catch(Exception ignored) {}

        return "已选择目录";
    }

    String displayName(Uri u) {

        try(Cursor c=getContentResolver().query(
                u,null,null,null,null
        )) {

            if(c!=null && c.moveToFirst()) {

                int n=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);

                if(n>=0) return c.getString(n);
            }

        } catch(Exception ignored) {}

        return "未知文件";
    }

    void saveUri(String k,Uri u) {
        prefs.edit().putString(k,u.toString()).apply();
    }

    Uri readUri(String k) {

        String s=prefs==null
                ?getSharedPreferences("reader",0).getString(k,null)
                :prefs.getString(k,null);

        return s==null?null:Uri.parse(s);
    }

    void readSelected() {

        if(fileUri==null) {
            status.setText("请先选择一个文件");
            return;
        }

        readUriLastLine(fileUri);
    }

    void readLatest() {

        if(treeUri==null) {
            status.setText("请先选择监控文件夹");
            return;
        }

        try {

            Uri u=latestTextFile(treeUri);

            if(u==null) {
                status.setText("目录中没有 TXT/CSV/LOG 文件");
                return;
            }

            readUriLastLine(u);

        } catch(Exception e) {
            status.setText("读取目录失败："+e.getMessage());
        }
    }

    Uri latestTextFile(Uri tree) throws Exception {

        String id=DocumentsContract.getTreeDocumentId(tree);

        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,id);

        Uri best=null;
        long bestTime=-1;

        try(Cursor c=getContentResolver().query(
                children,
                new String[]{
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED
                },
                null,null,null
        )) {

            if(c==null) return null;

            while(c.moveToNext()) {

                String n=c.getString(1);

                if(n==null) continue;

                String low=n.toLowerCase(Locale.ROOT);

                if(!(low.endsWith(".txt") ||
                     low.endsWith(".csv") ||
                     low.endsWith(".log"))) continue;

                long t=c.getLong(2);

                if(t>bestTime) {

                    bestTime=t;

                    best=DocumentsContract.buildDocumentUriUsingTree(
                            tree,c.getString(0)
                    );
                }
            }
        }

        return best;
    }

    String lastLine(Uri u) throws Exception {

        String last="";

        InputStream in=getContentResolver().openInputStream(u);

        if(in==null) return "";

        try(BufferedReader r=new BufferedReader(
                new InputStreamReader(in,"UTF-8")
        )) {

            String line;

            while((line=r.readLine())!=null) {
                if(!line.trim().isEmpty()) last=line.trim();
            }
        }

        return last;
    }

    void readUriLastLine(Uri u) {

        try {

            String s=lastLine(u);

            if(s.isEmpty()) {
                status.setText("文件最后一行为空");
            } else {
                speakText(s);
            }

        } catch(Exception e) {
            status.setText("读取失败："+e.getMessage());
        }
    }

    void startMonitor() {

        boolean useLocal=monitorLocalBox.isChecked();
        boolean useWeb=monitorWebBox.isChecked();

        String url=confirmedWebUrl;

        if(useLocal && treeUri==null) {
            status.setText("已勾选本机监控，但还没有选择文件夹");
            return;
        }

        if(useWeb && url.isEmpty()) {
            status.setText("请先输入网页地址并保存");
            return;
        }

        if(!useLocal && !useWeb) {
            status.setText("请至少勾选一种监控来源");
            return;
        }

        if(isEdgeMode() && !keyVerified) {
            status.setText("Edge TTS 尚未验证服务器密钥");
            return;
        }

        int sec=10;

        try {
            sec=Math.max(
                    3,
                    Integer.parseInt(interval.getText().toString().trim())
            );
        } catch(Exception ignored) {}

        Intent i=new Intent(this,ReaderService.class);

        i.setAction(ReaderService.START);

        if(useLocal && treeUri!=null) {
            i.putExtra("tree",treeUri.toString());
        }

        i.putExtra("monitor_local",useLocal);
        i.putExtra("monitor_web",useWeb);
        i.putExtra("url",useWeb?url:"");
        i.putExtra("interval",sec);

        putSpeechSettings(i);

        startReaderService(i);

        status.setText("后台监控已启动："+sec+"秒");
    }

    void command(String action) {

        Intent i=new Intent(this,ReaderService.class);

        i.setAction(action);

        startService(i);
    }

    void requestNotificationPermission() {

        if(Build.VERSION.SDK_INT>=33 &&
                checkSelfPermission(
                        "android.permission.POST_NOTIFICATIONS"
                )!=PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{
                            "android.permission.POST_NOTIFICATIONS"
                    },
                    REQ_NOTIFY
            );
        }
    }

    @Override
    protected void onDestroy() {

        if(tts!=null) {
            tts.stop();
            tts.shutdown();
        }

        netExecutor.shutdownNow();

        super.onDestroy();
    }
}


