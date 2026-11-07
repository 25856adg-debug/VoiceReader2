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

    static final int PICK_TREE=101;
    static final int PICK_FILE=102;
    static final int REQ_NOTIFY=103;
    static final int PICK_GOOGLE_JSON=104;

    static final int PLATFORM_LOCAL=0;
    static final int PLATFORM_EDGE=1;
    static final int PLATFORM_AZURE=2;
    static final int PLATFORM_GOOGLE=3;
    static final int PLATFORM_POLLY=4;
    static final int PLATFORM_ELEVEN=5;

    TextView status,selected,engineInfo,gainLabel;
    TextView platformInfo,googleJsonInfo;

    EditText interval,webUrl;
    EditText serverUrl,apiKey;

    EditText azureKey,azureRegion,azureVoice;
    EditText googleProject;
    EditText awsAccess,awsSecret,awsRegion,pollyVoice,pollyEngine;
    EditText elevenKey,elevenVoiceId,elevenModel;

    CheckBox monitorLocalBox,monitorWebBox;

    Spinner voice,rate,platform,edgeVoice;

    SeekBar gainBar;

    LinearLayout platformConfig;

    String confirmedWebUrl="";

    Uri treeUri,fileUri;

    TextToSpeech tts;

    boolean ttsReady=false;
    boolean keyVerified=false;

    boolean azureVerified=false;
    boolean googleVerified=false;
    boolean pollyVerified=false;
    boolean elevenVerified=false;

    String googleJsonPath="";
    String googleClientEmail="";
    String googlePrivateKey="";
    String googleTokenUri="https://oauth2.googleapis.com/token";

    final ArrayList<Voice> availableVoices=new ArrayList<>();
    final ArrayList<String> voiceLabels=new ArrayList<>();

    final String[] rates={
            "0.5倍",
            "0.75倍",
            "1.0倍",
            "1.25倍",
            "1.5倍"
    };

    final float[] speedValues={
            0.5f,0.75f,1.0f,1.25f,1.5f
    };

    final String[] platforms={
            "Android 本机 TTS",
            "Edge TTS（自己的服务器）",
            "Microsoft Azure Speech",
            "Google Cloud TTS",
            "Amazon Polly",
            "ElevenLabs"
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

    int currentPlatform=PLATFORM_LOCAL;

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);

        prefs=getSharedPreferences("reader",0);

        treeUri=readUri("tree");
        fileUri=readUri("file");

        confirmedWebUrl=prefs.getString("web_url","");

        buildUi();

        refreshSelection();

        webUrl.setText(confirmedWebUrl);

        loadAllSettings();

        requestNotificationPermission();

        initTTS();

        updatePlatformStatus();
    }

    // ================= 主界面 =================

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

        // ================= 网页监控 =================

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
        monitorLocalBox.setText(
                "监控本机文件夹最新 TXT / CSV / LOG"
        );
        monitorLocalBox.setChecked(treeUri!=null);
        root.addView(monitorLocalBox);

        monitorWebBox=new CheckBox(this);
        monitorWebBox.setText("监控已确定的网页地址");
        root.addView(monitorWebBox);

        addDivider(root);

        // ================= 平台选择 =================

        TextView platformTitle=new TextView(this);
        platformTitle.setText("语音合成平台");
        platformTitle.setTextSize(20);
        root.addView(platformTitle);

        platform=new Spinner(this);

        platform.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                platforms
        ));

        platform.setSelection(
                prefs.getInt("platform",PLATFORM_LOCAL)
        );

        root.addView(platform);

        platformInfo=new TextView(this);
        platformInfo.setPadding(0,8,0,8);
        platformInfo.setText("平台配置状态");
        root.addView(platformInfo);

        platformConfig=new LinearLayout(this);
        platformConfig.setOrientation(1);
        root.addView(platformConfig);

        platform.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {

            @Override
            public void onItemSelected(
                    AdapterView<?> parent,
                    View view,
                    int position,
                    long id
            ) {

                currentPlatform=position;

                prefs.edit()
                        .putInt("platform",position)
                        .apply();

                showPlatformConfig(position);

                updatePlatformStatus();

                status.setText(
                        "已选择："+platforms[position]+
                        "。设置将在下次启动朗读任务时生效"
                );
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        addDivider(root);

        // ================= 本机 TTS 音色 =================

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

        // ================= Edge 音色 =================

        edgeVoice=new Spinner(this);

        edgeVoice.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                edgeVoiceNames
        ));

        edgeVoice.setSelection(
                prefs.getInt("edge_voice_index",0)
        );

        // ================= 语速 =================

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

        rate.setSelection(
                prefs.getInt("rate",2)
        );

        root.addView(rate);

        rate.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {

            @Override
            public void onItemSelected(
                    AdapterView<?> parent,
                    View view,
                    int position,
                    long id
            ) {
                prefs.edit().putInt("rate",position).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // ================= 增益 =================

        TextView gainTitle=new TextView(this);
        gainTitle.setText(
                "朗读增强音量（50%～300%，默认200%）"
        );
        root.addView(gainTitle);

        gainLabel=new TextView(this);
        root.addView(gainLabel);

        gainBar=new SeekBar(this);
        gainBar.setMax(250);

        gainBar.setProgress(
                prefs.getInt("gain_progress",150)
        );

        root.addView(gainBar);

        updateGainLabel();

        gainBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(
                    SeekBar bar,
                    int progress,
                    boolean fromUser
            ) {
                updateGainLabel();

                if(fromUser) {
                    prefs.edit()
                            .putInt("gain_progress",progress)
                            .apply();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {}

            @Override
            public void onStopTrackingTouch(SeekBar bar) {}
        });

        // ================= 检查间隔 =================

        TextView intervalLabel=new TextView(this);
        intervalLabel.setText("自动检查间隔（秒）");
        root.addView(intervalLabel);

        interval=new EditText(this);
        interval.setInputType(InputType.TYPE_CLASS_NUMBER);
        interval.setText(
                String.valueOf(prefs.getInt("interval",10))
        );
        root.addView(interval);

        addDivider(root);

        // ================= 控制按钮 =================

        addButton(root,"测试当前选择的语音",v->testSpeech());

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

        note.setPadding(0,14,0,0);

        note.setText(
                "说明：本机目录和网页地址可以同时监控。\n"+
                "六个平台分别保存配置。\n"+
                "切换平台不会改变正在运行的任务，"+
                "下次重新启动任务时才会使用新平台。\n"+
                "在线语音需要网络和对应平台的有效凭据。\n"+
                "网页暂不支持需要登录、Cookie、验证码"+
                "或 JavaScript 动态加载的页面。"
        );

        root.addView(note);

        setContentView(scroll);

        edgeVoice.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {

            @Override
            public void onItemSelected(
                    AdapterView<?> parent,
                    View view,
                    int position,
                    long id
            ) {
                prefs.edit()
                        .putInt("edge_voice_index",position)
                        .apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    void updateGainLabel() {

        if(gainLabel==null || gainBar==null) return;

        int value=50+gainBar.getProgress();

        gainLabel.setText(
                "软件增益："+value+"%"+
                (value>=250?"（高增益可能失真）":"")
        );
    }

    void addDivider(LinearLayout root) {

        View line=new View(this);

        line.setBackgroundColor(0xFFBBBBBB);

        LinearLayout.LayoutParams p=
                new LinearLayout.LayoutParams(-1,1);

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
    
    // ================= 平台配置界面 =================

    EditText addConfigEdit(
            String label,
            String hint,
            String value,
            boolean password
    ) {

        TextView tv=new TextView(this);
        tv.setText(label);
        platformConfig.addView(tv);

        EditText edit=new EditText(this);
        edit.setSingleLine(true);
        edit.setText(value);
        edit.setHint(hint);

        if(password) {
            edit.setInputType(
                    InputType.TYPE_CLASS_TEXT |
                    InputType.TYPE_TEXT_VARIATION_PASSWORD
            );
        } else {
            edit.setInputType(InputType.TYPE_CLASS_TEXT);
        }

        platformConfig.addView(edit);

        return edit;
    }

    void showPlatformConfig(int p) {

        platformConfig.removeAllViews();

        serverUrl=null;
        apiKey=null;

        azureKey=null;
        azureRegion=null;
        azureVoice=null;

        googleProject=null;

        awsAccess=null;
        awsSecret=null;
        awsRegion=null;
        pollyVoice=null;
        pollyEngine=null;

        elevenKey=null;
        elevenVoiceId=null;
        elevenModel=null;

        if(p==PLATFORM_LOCAL) {

            addConfigText(
                    "Android 本机 TTS 使用系统安装的语音引擎。"
            );

            return;
        }

        if(p==PLATFORM_EDGE) {

            addConfigText("Edge TTS 自建服务器配置");

            serverUrl=addConfigEdit(
                    "服务器地址",
                    "http://服务器IP:6363",
                    prefs.getString(
                            "server_url",
                            "http://170.106.188.189:6363"
                    ),
                    false
            );

            apiKey=addConfigEdit(
                    "API 密钥",
                    "输入服务器 API 密钥",
                    prefs.getString("api_key",""),
                    true
            );

            addButton(
                    platformConfig,
                    "验证并保存 Edge 服务器",
                    v->verifyServerKey()
            );

            addButton(
                    platformConfig,
                    "清除 Edge 密钥",
                    v->clearKey()
            );

            TextView edgeVoiceLabel=new TextView(this);
            edgeVoiceLabel.setText("Edge 在线音色");
            platformConfig.addView(edgeVoiceLabel);
            platformConfig.addView(edgeVoice);

            return;
        }

        if(p==PLATFORM_AZURE) {

            addConfigText("Microsoft Azure Speech 配置");

            azureKey=addConfigEdit(
                    "Speech Key",
                    "Azure Speech Key",
                    prefs.getString("azure_key",""),
                    true
            );

            azureRegion=addConfigEdit(
                    "Region",
                    "例如 eastasia、eastus",
                    prefs.getString("azure_region",""),
                    false
            );

            azureVoice=addConfigEdit(
                    "中文音色",
                    "zh-CN-XiaoxiaoNeural",
                    prefs.getString(
                            "azure_voice",
                            "zh-CN-XiaoxiaoNeural"
                    ),
                    false
            );

            addButton(
                    platformConfig,
                    "验证 Azure 配置",
                    v->verifyAzure()
            );

            return;
        }

        if(p==PLATFORM_GOOGLE) {

            addConfigText("Google Cloud Text-to-Speech 配置");

            googleProject=addConfigEdit(
                    "Google Cloud Project ID",
                    "项目 ID",
                    prefs.getString("google_project_id",""),
                    false
            );

            googleJsonInfo=new TextView(this);
            googleJsonInfo.setPadding(0,10,0,10);

            googleJsonInfo.setText(
                    googleJsonPath.isEmpty()
                    ?"尚未导入服务账号 JSON"
                    :"已导入服务账号 JSON"
            );

            platformConfig.addView(googleJsonInfo);

            addButton(
                    platformConfig,
                    "选择 Google 服务账号 JSON",
                    v->chooseGoogleJson()
            );

            addButton(
                    platformConfig,
                    "验证 Google Cloud TTS",
                    v->verifyGoogle()
            );

            return;
        }

        if(p==PLATFORM_POLLY) {

            addConfigText("Amazon Polly 配置");

            awsAccess=addConfigEdit(
                    "AWS Access Key ID",
                    "Access Key ID",
                    prefs.getString("aws_access_key",""),
                    false
            );

            awsSecret=addConfigEdit(
                    "AWS Secret Access Key",
                    "Secret Access Key",
                    prefs.getString("aws_secret_key",""),
                    true
            );

            awsRegion=addConfigEdit(
                    "AWS Region",
                    "例如 us-east-1、ap-southeast-1",
                    prefs.getString("aws_region",""),
                    false
            );

            pollyVoice=addConfigEdit(
                    "Polly Voice ID",
                    "Zhiyu",
                    prefs.getString("polly_voice","Zhiyu"),
                    false
            );

            pollyEngine=addConfigEdit(
                    "Engine",
                    "standard 或 neural",
                    prefs.getString("polly_engine","standard"),
                    false
            );

            addButton(
                    platformConfig,
                    "检查 Polly 配置",
                    v->verifyPolly()
            );

            addConfigText(
                    "注意：AWS 按账户、区域和语音类型计费，"+
                    "请先确认账号权限及价格。"
            );

            return;
        }

        if(p==PLATFORM_ELEVEN) {

            addConfigText("ElevenLabs 配置");

            elevenKey=addConfigEdit(
                    "API Key",
                    "ElevenLabs API Key",
                    prefs.getString("eleven_key",""),
                    true
            );

            elevenVoiceId=addConfigEdit(
                    "Voice ID",
                    "输入 Voice ID",
                    prefs.getString("eleven_voice_id",""),
                    false
            );

            elevenModel=addConfigEdit(
                    "Model ID",
                    "eleven_multilingual_v2",
                    prefs.getString(
                            "eleven_model",
                            "eleven_multilingual_v2"
                    ),
                    false
            );

            addButton(
                    platformConfig,
                    "检查 ElevenLabs 配置",
                    v->verifyEleven()
            );

            return;
        }
    }

    void addConfigText(String message) {

        TextView tv=new TextView(this);

        tv.setText(message);
        tv.setPadding(0,8,0,8);

        platformConfig.addView(tv);
    }

    // ================= 保存所有平台配置 =================

    void saveAllSettings() {

        SharedPreferences.Editor e=prefs.edit();

        if(serverUrl!=null) {
            e.putString(
                    "server_url",
                    serverUrl.getText().toString().trim()
            );
        }

        if(apiKey!=null) {
            e.putString(
                    "api_key",
                    apiKey.getText().toString().trim()
            );
        }

        if(azureKey!=null) {
            e.putString(
                    "azure_key",
                    azureKey.getText().toString().trim()
            );
        }

        if(azureRegion!=null) {
            e.putString(
                    "azure_region",
                    azureRegion.getText().toString().trim()
            );
        }

        if(azureVoice!=null) {
            e.putString(
                    "azure_voice",
                    azureVoice.getText().toString().trim()
            );
        }

        if(googleProject!=null) {
            e.putString(
                    "google_project_id",
                    googleProject.getText().toString().trim()
            );
        }

        if(awsAccess!=null) {
            e.putString(
                    "aws_access_key",
                    awsAccess.getText().toString().trim()
            );
        }

        if(awsSecret!=null) {
            e.putString(
                    "aws_secret_key",
                    awsSecret.getText().toString().trim()
            );
        }

        if(awsRegion!=null) {
            e.putString(
                    "aws_region",
                    awsRegion.getText().toString().trim()
            );
        }

        if(pollyVoice!=null) {
            e.putString(
                    "polly_voice",
                    pollyVoice.getText().toString().trim()
            );
        }

        if(pollyEngine!=null) {
            e.putString(
                    "polly_engine",
                    pollyEngine.getText().toString().trim()
            );
        }

        if(elevenKey!=null) {
            e.putString(
                    "eleven_key",
                    elevenKey.getText().toString().trim()
            );
        }

        if(elevenVoiceId!=null) {
            e.putString(
                    "eleven_voice_id",
                    elevenVoiceId.getText().toString().trim()
            );
        }

        if(elevenModel!=null) {
            e.putString(
                    "eleven_model",
                    elevenModel.getText().toString().trim()
            );
        }

        e.putString("google_json_path",googleJsonPath);

        e.apply();
    }

    void loadAllSettings() {

        googleJsonPath=prefs.getString(
                "google_json_path",
                ""
        );

        googleClientEmail=prefs.getString(
                "google_client_email",
                ""
        );

        googlePrivateKey=prefs.getString(
                "google_private_key",
                ""
        );

        googleTokenUri=prefs.getString(
                "google_token_uri",
                "https://oauth2.googleapis.com/token"
        );

        keyVerified=prefs.getBoolean(
                "key_verified",
                false
        );

        azureVerified=prefs.getBoolean(
                "azure_verified",
                false
        );

        googleVerified=prefs.getBoolean(
                "google_verified",
                false
        );

        pollyVerified=prefs.getBoolean(
                "polly_verified",
                false
        );

        elevenVerified=prefs.getBoolean(
                "eleven_verified",
                false
        );

        if(platform!=null) {
            int p=prefs.getInt("platform",PLATFORM_LOCAL);

            if(p<0 || p>=platforms.length) p=PLATFORM_LOCAL;

            platform.setSelection(p);
        }
    }

    // ================= Google JSON 导入 =================

    void chooseGoogleJson() {

        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);

        i.setType("application/json");

        i.addCategory(Intent.CATEGORY_OPENABLE);

        i.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        );

        startActivityForResult(i,PICK_GOOGLE_JSON);
    }

    void importGoogleJson(Uri uri) {

        status.setText("正在读取 Google 服务账号文件……");

        netExecutor.execute(()->{

            try {

                StringBuilder content=new StringBuilder();

                try(InputStream in=getContentResolver()
                        .openInputStream(uri);
                    BufferedReader reader=new BufferedReader(
                            new InputStreamReader(in,"UTF-8")
                    )) {

                    if(reader==null) {
                        throw new IOException("无法打开 JSON 文件");
                    }

                    String line;

                    while((line=reader.readLine())!=null) {

                        content.append(line).append('\n');

                        if(content.length()>1024*1024) {
                            throw new IOException(
                                    "JSON 文件超过 1MB"
                            );
                        }
                    }
                }

                JSONObject json=new JSONObject(content.toString());

                String type=json.optString("type","");

                if(!"service_account".equals(type)) {
                    throw new IOException(
                            "文件不是 Google service_account 类型"
                    );
                }

                String email=json.optString("client_email","");
                String privateKey=json.optString("private_key","");
                String tokenUri=json.optString(
                        "token_uri",
                        "https://oauth2.googleapis.com/token"
                );

                String projectId=json.optString("project_id","");

                if(email.isEmpty() ||
                        privateKey.isEmpty() ||
                        tokenUri.isEmpty()) {

                    throw new IOException(
                            "JSON 缺少 client_email、private_key 或 token_uri"
                    );
                }

                if(!tokenUri.startsWith("https://")) {
                    throw new IOException(
                            "Google token_uri 必须使用 HTTPS"
                    );
                }

                String finalEmail=email;
                String finalPrivateKey=privateKey;
                String finalTokenUri=tokenUri;
                String finalProject=projectId;

                runOnUiThread(()->{

                    googleClientEmail=finalEmail;
                    googlePrivateKey=finalPrivateKey;
                    googleTokenUri=finalTokenUri;

                    if(googleProject!=null &&
                            finalProject.length()>0) {

                        googleProject.setText(finalProject);
                    }

                    googleJsonPath="已导入";

                    prefs.edit()
                            .putString(
                                    "google_client_email",
                                    googleClientEmail
                            )
                            .putString(
                                    "google_private_key",
                                    googlePrivateKey
                            )
                            .putString(
                                    "google_token_uri",
                                    googleTokenUri
                            )
                            .putString(
                                    "google_json_path",
                                    googleJsonPath
                            )
                            .putString(
                                    "google_project_id",
                                    finalProject
                            )
                            .putBoolean(
                                    "google_verified",
                                    false
                            )
                            .apply();

                    googleVerified=false;

                    if(googleJsonInfo!=null) {
                        googleJsonInfo.setText(
                                "已导入："+finalEmail
                        );
                    }

                    status.setText(
                            "Google 服务账号 JSON 导入成功"
                    );

                    updatePlatformStatus();
                });

            } catch(Exception e) {

                runOnUiThread(()->{
                    status.setText(
                            "导入失败："+e.getMessage()
                    );
                });
            }
        });
    }
    
    // ================= 平台状态 =================

    void updatePlatformStatus() {

        if(platformInfo==null) return;

        String message;

        switch(currentPlatform) {

            case PLATFORM_LOCAL:
                message=ttsReady
                        ?"本机 TTS：已初始化"
                        :"本机 TTS：正在初始化或不可用";
                break;

            case PLATFORM_EDGE:
                message=keyVerified
                        ?"Edge：服务器密钥已验证"
                        :"Edge：尚未验证服务器密钥";
                break;

            case PLATFORM_AZURE:
                message=azureVerified
                        ?"Azure：配置已检查"
                        :"Azure：尚未检查配置";
                break;

            case PLATFORM_GOOGLE:
                message=googleVerified
                        ?"Google：服务账号配置已导入并检查"
                        :"Google：尚未检查配置";
                break;

            case PLATFORM_POLLY:
                message=pollyVerified
                        ?"Polly：配置已检查"
                        :"Polly：尚未检查配置";
                break;

            case PLATFORM_ELEVEN:
                message=elevenVerified
                        ?"ElevenLabs：配置已检查"
                        :"ElevenLabs：尚未检查配置";
                break;

            default:
                message="未知平台";
        }

        platformInfo.setText(message);
    }

    // ================= Azure 配置检查 =================

    void verifyAzure() {

        saveAllSettings();

        String key=azureKey==null
                ?""
                :azureKey.getText().toString().trim();

        String region=azureRegion==null
                ?""
                :azureRegion.getText().toString().trim();

        String voiceName=azureVoice==null
                ?""
                :azureVoice.getText().toString().trim();

        if(key.isEmpty() || region.isEmpty()) {

            status.setText("请填写 Azure Speech Key 和 Region");
            return;
        }

        if(voiceName.isEmpty()) {
            status.setText("请填写 Azure 中文音色名称");
            return;
        }

        azureVerified=true;

        prefs.edit()
                .putBoolean("azure_verified",true)
                .apply();

        updatePlatformStatus();

        status.setText(
                "Azure 配置格式已检查。点击“测试当前选择的语音”进行实际合成验证。"
        );
    }

    // ================= Google 配置检查 =================

    void verifyGoogle() {

        saveAllSettings();

        String project=googleProject==null
                ?prefs.getString("google_project_id","")
                :googleProject.getText().toString().trim();

        if(googleClientEmail.isEmpty() ||
                googlePrivateKey.isEmpty()) {

            status.setText(
                    "请先导入 Google 服务账号 JSON"
            );
            return;
        }

        if(project.isEmpty()) {
            status.setText("请填写 Google Cloud Project ID");
            return;
        }

        if(!googleClientEmail.contains("@")) {
            status.setText("Google client_email 格式不正确");
            return;
        }

        try {

            // 先检查私钥格式及可解析性
            String key=googlePrivateKey
                    .replace("-----BEGIN PRIVATE KEY-----","")
                    .replace("-----END PRIVATE KEY-----","")
                    .replaceAll("\\s","");

            byte[] der=android.util.Base64.decode(
                    key,
                    android.util.Base64.DEFAULT
            );

            java.security.KeyFactory factory=
                    java.security.KeyFactory.getInstance("RSA");

            factory.generatePrivate(
                    new java.security.spec.PKCS8EncodedKeySpec(der)
            );

            googleVerified=true;

            prefs.edit()
                    .putBoolean("google_verified",true)
                    .putString("google_project_id",project)
                    .apply();

            updatePlatformStatus();

            status.setText(
                    "Google JSON 格式和 RSA 私钥检查通过。点击语音测试进行实际 API 验证。"
            );

        } catch(Exception e) {

            googleVerified=false;

            prefs.edit()
                    .putBoolean("google_verified",false)
                    .apply();

            updatePlatformStatus();

            status.setText(
                    "Google 私钥解析失败："+e.getMessage()
            );
        }
    }

    // ================= Polly 配置检查 =================

    void verifyPolly() {

        saveAllSettings();

        String access=awsAccess==null
                ?""
                :awsAccess.getText().toString().trim();

        String secret=awsSecret==null
                ?""
                :awsSecret.getText().toString().trim();

        String region=awsRegion==null
                ?""
                :awsRegion.getText().toString().trim();

        String voiceName=pollyVoice==null
                ?""
                :pollyVoice.getText().toString().trim();

        String engine=pollyEngine==null
                ?""
                :pollyEngine.getText().toString().trim();

        if(access.isEmpty() ||
                secret.isEmpty() ||
                region.isEmpty() ||
                voiceName.isEmpty()) {

            status.setText("请填写 Polly 的全部必要参数");
            return;
        }

        if(!engine.equals("standard") &&
                !engine.equals("neural") &&
                !engine.equals("long-form") &&
                !engine.equals("generative")) {

            status.setText(
                    "Engine 应为 standard、neural、long-form 或 generative"
            );
            return;
        }

        pollyVerified=true;

        prefs.edit()
                .putBoolean("polly_verified",true)
                .apply();

        updatePlatformStatus();

        status.setText(
                "Polly 配置格式已检查。点击语音测试进行实际 AWS 请求验证。"
        );
    }

    // ================= ElevenLabs 配置检查 =================

    void verifyEleven() {

        saveAllSettings();

        String key=elevenKey==null
                ?""
                :elevenKey.getText().toString().trim();

        String id=elevenVoiceId==null
                ?""
                :elevenVoiceId.getText().toString().trim();

        String model=elevenModel==null
                ?""
                :elevenModel.getText().toString().trim();

        if(key.isEmpty() || id.isEmpty()) {
            status.setText("请填写 ElevenLabs API Key 和 Voice ID");
            return;
        }

        if(model.isEmpty()) {
            status.setText("请填写 ElevenLabs Model ID");
            return;
        }

        elevenVerified=true;

        prefs.edit()
                .putBoolean("eleven_verified",true)
                .apply();

        updatePlatformStatus();

        status.setText(
                "ElevenLabs 配置格式已检查。点击语音测试进行实际 API 请求验证。"
        );
    }

    // ================= 当前平台参数保存 =================

    void saveCurrentPlatformSettings() {

        saveAllSettings();

        if(platform!=null) {
            int p=platform.getSelectedItemPosition();

            if(p>=0 && p<platforms.length) {
                prefs.edit().putInt("platform",p).apply();
            }
        }

        if(rate!=null) {
            prefs.edit()
                    .putInt("rate",selectedRatePosition())
                    .apply();
        }

        if(gainBar!=null) {
            prefs.edit()
                    .putInt("gain_progress",gainBar.getProgress())
                    .apply();
        }
    }

    // ================= 生成 Service 参数 =================

    void putSpeechSettings(Intent i) {

        saveCurrentPlatformSettings();

        int p=platform.getSelectedItemPosition();

        if(p<0 || p>=platforms.length) {
            p=PLATFORM_LOCAL;
        }

        i.putExtra("platform",p);

        // Edge
        i.putExtra(
                "server_url",
                prefs.getString("server_url","")
        );

        i.putExtra(
                "api_key",
                prefs.getString("api_key","")
        );

        i.putExtra(
                "edge_voice",
                edgeVoiceIds[
                        Math.max(
                                0,
                                Math.min(
                                        edgeVoice.getSelectedItemPosition(),
                                        edgeVoiceIds.length-1
                                )
                        )
                ]
        );

        // Azure
        i.putExtra(
                "azure_key",
                prefs.getString("azure_key","")
        );

        i.putExtra(
                "azure_region",
                prefs.getString("azure_region","")
        );

        i.putExtra(
                "azure_voice",
                prefs.getString(
                        "azure_voice",
                        "zh-CN-XiaoxiaoNeural"
                )
        );

        // Google
        i.putExtra(
                "google_json_path",
                googleJsonPath
        );

        i.putExtra(
                "google_project_id",
                prefs.getString("google_project_id","")
        );

        i.putExtra(
                "google_client_email",
                googleClientEmail
        );

        i.putExtra(
                "google_private_key",
                googlePrivateKey
        );

        i.putExtra(
                "google_token_uri",
                googleTokenUri
        );

        // Amazon Polly
        i.putExtra(
                "aws_access_key",
                prefs.getString("aws_access_key","")
        );

        i.putExtra(
                "aws_secret_key",
                prefs.getString("aws_secret_key","")
        );

        i.putExtra(
                "aws_region",
                prefs.getString("aws_region","")
        );

        i.putExtra(
                "polly_voice",
                prefs.getString("polly_voice","Zhiyu")
        );

        i.putExtra(
                "polly_engine",
                prefs.getString("polly_engine","standard")
        );

        // ElevenLabs
        i.putExtra(
                "eleven_key",
                prefs.getString("eleven_key","")
        );

        i.putExtra(
                "eleven_voice_id",
                prefs.getString("eleven_voice_id","")
        );

        i.putExtra(
                "eleven_model",
                prefs.getString(
                        "eleven_model",
                        "eleven_multilingual_v2"
                )
        );

        // 通用参数
        i.putExtra(
                "speed",
                speedValues[selectedRatePosition()]
        );

        i.putExtra(
                "gain",
                (50+gainBar.getProgress())/100f
        );
    }

    // ================= 当前平台是否允许启动 =================

    boolean checkPlatformReady() {

        int p=platform.getSelectedItemPosition();

        saveCurrentPlatformSettings();

        switch(p) {

            case PLATFORM_LOCAL:

                if(!ttsReady) {
                    status.setText("本机 TTS 尚未准备好");
                    return false;
                }

                return true;

            case PLATFORM_EDGE:

                if(!keyVerified) {
                    status.setText("请先验证 Edge 服务器密钥");
                    return false;
                }

                return true;

            case PLATFORM_AZURE:

                if(!azureVerified) {
                    status.setText("请先检查 Azure 配置");
                    return false;
                }

                return true;

            case PLATFORM_GOOGLE:

                if(!googleVerified) {
                    status.setText("请先导入并检查 Google JSON");
                    return false;
                }

                return true;

            case PLATFORM_POLLY:

                if(!pollyVerified) {
                    status.setText("请先检查 Polly 配置");
                    return false;
                }

                return true;

            case PLATFORM_ELEVEN:

                if(!elevenVerified) {
                    status.setText("请先检查 ElevenLabs 配置");
                    return false;
                }

                return true;
        }

        status.setText("未知语音平台");
        return false;
    }
    
    // ================= Edge 服务器验证 =================

    void verifyServerKey() {

        saveAllSettings();

        String url=serverUrl.getText().toString().trim();
        String key=apiKey.getText().toString().trim();

        if(url.isEmpty() || key.isEmpty()) {
            status.setText("请填写服务器地址和 API Key");
            return;
        }

        new Thread(() -> {

            HttpURLConnection conn=null;

            try {

                String base=url.replaceAll("/+$","");

                URL target=new URL(base+"/auth/check");

                conn=(HttpURLConnection)target.openConnection();

                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                conn.setRequestProperty(
                        "Authorization",
                        "Bearer "+key
                );

                int code=conn.getResponseCode();

                InputStream input=code>=200 && code<300
                        ?conn.getInputStream()
                        :conn.getErrorStream();

                StringBuilder result=new StringBuilder();

                if(input!=null) {

                    BufferedReader reader=new BufferedReader(
                            new InputStreamReader(
                                    input,
                                    StandardCharsets.UTF_8
                            )
                    );

                    String line;

                    while((line=reader.readLine())!=null) {
                        result.append(line);
                    }

                    reader.close();
                }

                JSONObject json=new JSONObject(result.toString());

                boolean valid=code>=200 &&
                        code<300 &&
                        json.optBoolean("valid",false);

                runOnUiThread(() -> {

                    keyVerified=valid;

                    prefs.edit()
                            .putBoolean("edge_verified",valid)
                            .apply();

                    updatePlatformStatus();

                    status.setText(
                            valid
                                    ?"Edge 服务器密钥验证成功"
                                    :"Edge 验证失败："+result
                    );
                });

            } catch(Exception e) {

                runOnUiThread(() -> {

                    keyVerified=false;

                    prefs.edit()
                            .putBoolean("edge_verified",false)
                            .apply();

                    updatePlatformStatus();

                    status.setText("Edge 验证失败："+e.getMessage());
                });

            } finally {

                if(conn!=null) {
                    conn.disconnect();
                }
            }

        }).start();
    }

    // ================= 文件夹选择 =================

    void chooseTree() {

        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);

        startActivityForResult(intent,PICK_TREE);
    }

    // ================= 文件选择 =================

    void chooseFile() {

        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);

        intent.addCategory(Intent.CATEGORY_OPENABLE);

        intent.setType("*/*");

        startActivityForResult(intent,PICK_FILE);
    }

    // ================= Google JSON 文件选择 =================

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {

        super.onActivityResult(requestCode,resultCode,data);

        if(resultCode!=RESULT_OK || data==null) {
            return;
        }

        Uri uri=data.getData();

        if(uri==null) {
            return;
        }

        if(requestCode==PICK_GOOGLE_JSON) {

            importGoogleJson(uri);

            return;
        }

        if(requestCode==PICK_TREE) {

            try {

                getContentResolver().takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION |
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                );

            } catch(Exception ignored) {}

            treeUri=uri;

            prefs.edit()
                    .putString("tree_uri",uri.toString())
                    .apply();

            refreshSelection();

            status.setText("文件夹已选择");

            return;
        }

        if(requestCode==PICK_FILE) {

            try {

                getContentResolver().takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                );

            } catch(Exception ignored) {}

            fileUri=uri;

            prefs.edit()
                    .putString("file_uri",uri.toString())
                    .apply();

            refreshSelection();

            status.setText("文件已选择");

        }
    }

    // ================= 刷新文件选择显示 =================

    void refreshSelection() {

        if(selected==null) return;

        StringBuilder value=new StringBuilder();

        if(treeUri!=null) {
            value.append("监控文件夹：已选择");
        } else {
            value.append("监控文件夹：未选择");
        }

        if(fileUri!=null) {
            value.append("\n手动朗读文件：已选择");
        } else {
            value.append("\n手动朗读文件：未选择");
        }

        selected.setText(value.toString());
    }

    // ================= 开始监控 =================

    void startMonitor() {

        if(!checkPlatformReady()) {
            return;
        }

        boolean local=monitorLocal.isChecked();
        boolean web=monitorWeb.isChecked();

        if(!local && !web) {
            status.setText("请至少选择一种监控来源");
            return;
        }

        if(local && treeUri==null) {
            status.setText("请先选择本地监控文件夹");
            return;
        }

        String url=webUrl.getText().toString().trim();

        if(web) {

          if(!confirmWebUrl()) {
            return;
          }

          url=webUrl.getText().toString().trim();
        }

        int intervalValue=5;

        try {

            intervalValue=Integer.parseInt(
                    interval.getText().toString().trim()
            );

            if(intervalValue<1) {
                intervalValue=1;
            }

        } catch(Exception ignored) {}

        Intent intent=new Intent(this,ReaderService.class);

        intent.setAction("START");

        intent.putExtra(
                "tree_uri",
                treeUri==null?"":treeUri.toString()
        );

        intent.putExtra("monitor_local",local);
        intent.putExtra("monitor_web",web);
        intent.putExtra("url",url);
        intent.putExtra("interval",intervalValue);

        putSpeechSettings(intent);

        startReaderService(intent);

        status.setText("监控任务启动请求已发送");

    }

    // ================= 手动朗读文件最后一行 =================

    void readSelectedFile() {

        if(fileUri==null) {
            status.setText("请先选择一个文件");
            return;
        }

        if(!checkPlatformReady()) {
            return;
        }

        Intent intent=new Intent(this,ReaderService.class);

        intent.setAction("READ_FILE");

        intent.putExtra(
                "file_uri",
                fileUri.toString()
        );

        putSpeechSettings(intent);

        startReaderService(intent);

        status.setText("已发送文件朗读请求");

    }

    // ================= 朗读目录最新文件 =================

    void readLatestFile() {

        if(treeUri==null) {
            status.setText("请先选择监控文件夹");
            return;
        }

        if(!checkPlatformReady()) {
            return;
        }

        Intent intent=new Intent(this,ReaderService.class);

        intent.setAction("READ_LATEST");

        intent.putExtra(
                "tree_uri",
                treeUri.toString()
        );

        putSpeechSettings(intent);

        startReaderService(intent);

        status.setText("已发送最新文件朗读请求");

    }

    // ================= 暂停 =================

    void pauseMonitor() {

        Intent intent=new Intent(this,ReaderService.class);

        intent.setAction("PAUSE");

        startReaderService(intent);

        status.setText("已发送暂停请求");
    }

    // ================= 继续 =================

    void resumeMonitor() {

        Intent intent=new Intent(this,ReaderService.class);

        intent.setAction("RESUME");

        startReaderService(intent);

        status.setText("已发送继续请求");
    }

    // ================= 停止 =================

    void stopMonitor() {

        Intent intent=new Intent(this,ReaderService.class);

        intent.setAction("STOP");

        startReaderService(intent);

        status.setText("已发送停止请求");
    }

    // ================= 启动前台服务 =================

    void startReaderService(Intent intent) {

        if(android.os.Build.VERSION.SDK_INT>=26) {

            startForegroundService(intent);

        } else {

            startService(intent);
        }
    }
    
    // ================= 本机 TTS 初始化 =================

    void initTTS() {

        ttsReady=false;

        tts=new TextToSpeech(
                this,
                result -> {

                    if(result==TextToSpeech.SUCCESS) {

                        int languageResult=tts.setLanguage(
                                Locale.SIMPLIFIED_CHINESE
                        );

                        ttsReady=
                                languageResult!=TextToSpeech.LANG_MISSING_DATA &&
                                languageResult!=TextToSpeech.LANG_NOT_SUPPORTED;

                        if(ttsReady) {

                            runOnUiThread(() -> {

                                loadLocalVoices();

                                updatePlatformStatus();

                                status.setText("本机 TTS 初始化成功");

                            });

                        } else {

                            runOnUiThread(() -> {
                                status.setText("本机 TTS 不支持中文，请安装中文语音数据");
                                updatePlatformStatus();
                            });
                        }

                    } else {

                        runOnUiThread(() -> {

                            ttsReady=false;

                            status.setText("本机 TTS 初始化失败");

                            updatePlatformStatus();
                        });
                    }
                }
        );
    }

    // ================= 加载本机中文音色 =================

    void loadLocalVoices() {

        if(tts==null || localVoice==null) {
            return;
        }

        try {

            ArrayList<String> names=new ArrayList<>();

            Set<Voice> voices=tts.getVoices();

            if(voices!=null) {

                for(Voice voice:voices) {

                    Locale locale=voice.getLocale();

                    if(locale!=null &&
                            locale.getLanguage().equals("zh")) {

                        String name=voice.getName();

                        if(name!=null && !names.contains(name)) {
                            names.add(name);
                        }
                    }
                }
            }

            if(names.isEmpty()) {
                names.add("系统默认中文音色");
            }

            ArrayAdapter<String> adapter=new ArrayAdapter<>(
                    this,
                    android.R.layout.simple_spinner_item,
                    names
            );

            adapter.setDropDownViewResource(
                    android.R.layout.simple_spinner_dropdown_item
            );

            localVoice.setAdapter(adapter);

        } catch(Exception e) {

            status.setText("读取本机音色失败："+e.getMessage());
        }
    }

    // ================= 获取语速位置 =================

    int selectedRatePosition() {

        if(rate==null) {
            return 2;
        }

        int p=rate.getSelectedItemPosition();

        if(p<0 || p>=speedValues.length) {
            return 2;
        }

        return p;
    }

    // ================= 本机语音朗读 =================

    void speakLocal(String text) {

        if(tts==null || !ttsReady) {

            status.setText("本机 TTS 尚未准备好");

            return;
        }

        if(text==null || text.trim().isEmpty()) {
            status.setText("没有可朗读的内容");
            return;
        }

        try {

            int position=selectedRatePosition();

            float speed=speedValues[position];

            tts.setSpeechRate(speed);

            if(localVoice!=null &&
                    localVoice.getSelectedItem()!=null) {

                String voiceName=
                        localVoice.getSelectedItem().toString();

                if(!voiceName.equals("系统默认中文音色")) {

                    for(Voice voice:tts.getVoices()) {

                        if(voiceName.equals(voice.getName())) {

                            tts.setVoice(voice);

                            break;
                        }
                    }
                }
            }

            Bundle params=new Bundle();

            params.putFloat(
                    TextToSpeech.Engine.KEY_PARAM_VOLUME,
                    Math.min(3.0f,(50+gainBar.getProgress())/100f)
            );

            tts.speak(
                    text,
                    TextToSpeech.QUEUE_FLUSH,
                    params,
                    "reader_local"
            );

            status.setText("本机 TTS 正在朗读");

        } catch(Exception e) {

            status.setText("本机朗读失败："+e.getMessage());
        }
    }

    // ================= 在线语音朗读 =================

    void speakThroughService(String text) {

        if(text==null || text.trim().isEmpty()) {
            status.setText("没有可朗读的内容");
            return;
        }

        Intent intent=new Intent(this,ReaderService.class);

        intent.setAction("SPEAK");

        intent.putExtra("text",text);

        putSpeechSettings(intent);

        startReaderService(intent);

        status.setText("已发送在线语音请求");
    }

    // ================= 根据平台朗读 =================

    void speakText(String text) {

        if(!checkPlatformReady()) {
            return;
        }

        int p=platform.getSelectedItemPosition();

        if(p==PLATFORM_LOCAL) {

            speakLocal(text);

        } else {

            speakThroughService(text);
        }
    }

    // ================= 测试当前语音 =================

    void testSpeech() {

        if(!checkPlatformReady()) {
            return;
        }

        String testText="语音测试成功，现在开始朗读。";

        speakText(testText);
    }

    // ================= 网页地址检查 =================

    boolean confirmWebUrl() {

        if(webUrl==null) {
            return false;
        }

        String value=webUrl.getText().toString().trim();

        if(value.isEmpty()) {

            status.setText("请输入网页地址");

            return false;
        }

        if(!value.startsWith("http://") &&
                !value.startsWith("https://")) {

            value="https://"+value;

            webUrl.setText(value);
        }

        try {

            URL url=new URL(value);

            String protocol=url.getProtocol();

            if(!protocol.equals("http") &&
                    !protocol.equals("https")) {

                status.setText("只支持 HTTP 或 HTTPS 地址");

                return false;
            }

            return true;

        } catch(Exception e) {

            status.setText("网页地址格式不正确");

            return false;
        }
    }

    // ================= 平台切换 =================

    void onPlatformChanged(int position) {

        if(position<PLATFORM_LOCAL ||
                position>PLATFORM_ELEVEN) {

            position=PLATFORM_LOCAL;
        }

        currentPlatform=position;

        showPlatformConfig(position);

        updatePlatformStatus();

        prefs.edit()
                .putInt("platform",position)
                .apply();

        status.setText(
                "已切换语音平台。新设置将在下一次启动任务时使用。"
        );
    }

    // ================= Activity 销毁 =================

    @Override
    protected void onDestroy() {

        if(tts!=null) {

            try {
                tts.stop();
                tts.shutdown();
            } catch(Exception ignored) {}

            tts=null;
        }

        super.onDestroy();
    }
}

