package com.example.voicereader;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.media.*;
import android.provider.DocumentsContract;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.database.Cursor;
import android.text.Html;
import android.text.Spanned;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.Charset;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class ReaderService extends Service
        implements TextToSpeech.OnInitListener {

    public static final String START="reader.START";
    public static final String PAUSE="reader.PAUSE";
    public static final String RESUME="reader.RESUME";
    public static final String STOP="reader.STOP";
    public static final String SPEAK="reader.SPEAK";

    private static final int NOTIFY=71;
    private static final String CHANNEL="reader";

    // 平台编号
    public static final int PLATFORM_LOCAL=0;
    public static final int PLATFORM_EDGE=1;
    public static final int PLATFORM_AZURE=2;
    public static final int PLATFORM_GOOGLE=3;
    public static final int PLATFORM_POLLY=4;
    public static final int PLATFORM_ELEVEN=5;

    private TextToSpeech tts;

    private Uri tree;

    private String webUrl="";

    // Edge
    private String serverUrl="";
    private String apiKey="";
    private String edgeVoice="zh-CN-XiaoxiaoNeural";

    // Azure
    private String azureKey="";
    private String azureRegion="";
    private String azureVoice="zh-CN-XiaoxiaoNeural";

    // Google
    private String googleJsonPath="";
    private String googleProjectId="";
    private String googleClientEmail="";
    private String googlePrivateKey="";
    private String googleTokenUri="https://oauth2.googleapis.com/token";
    private String googleAccessToken="";
    private long googleTokenExpire=0;

    // Amazon Polly
    private String awsAccessKey="";
    private String awsSecretKey="";
    private String awsRegion="us-east-1";
    private String pollyVoice="Zhiyu";
    private String pollyEngine="standard";

    // ElevenLabs
    private String elevenKey="";
    private String elevenVoiceId="";
    private String elevenModel="eleven_multilingual_v2";

    private int platform=PLATFORM_LOCAL;

    private boolean monitorLocal=true;
    private boolean monitorWeb=false;

    private int interval=10;

    private float speed=1f;
    private float gain=2f;

    private Voice selectedNetworkVoice;

    private volatile boolean running=false;
    private volatile boolean paused=false;
    private volatile boolean ready=false;

    private ScheduledExecutorService scheduler;
    private ExecutorService worker;

    private PowerManager.WakeLock wakeLock;

    private AudioTrack activeTrack;

    private final Object audioLock=new Object();

    private final Map<String,String> lastSpoken=
            new ConcurrentHashMap<>();

    @Override
    public void onCreate() {
        super.onCreate();

        createChannel();

        tts=new TextToSpeech(this,this);

        worker=Executors.newSingleThreadExecutor();

        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);

        if(pm!=null) {
            wakeLock=pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "VoiceReader:Monitor"
            );
            wakeLock.setReferenceCounted(false);
        }
    }

    @Override
    public int onStartCommand(Intent i,int flags,int startId) {

        if(i==null) return START_NOT_STICKY;

        String action=i.getAction();

        if(START.equals(action)) {

            loadSettings(i);

            String raw=i.getStringExtra("tree");
            tree=raw==null?null:Uri.parse(raw);

            monitorLocal=i.getBooleanExtra("monitor_local",raw!=null);
            monitorWeb=i.getBooleanExtra("monitor_web",false);

            webUrl=i.getStringExtra("url");
            if(webUrl==null) webUrl="";

            interval=Math.max(3,i.getIntExtra("interval",10));

            running=true;
            paused=false;

            lastSpoken.clear();

            startForeground(
                    NOTIFY,
                    notification("正在启动监控")
            );

            acquireWakeLock();

            beginSchedule();

        } else if(PAUSE.equals(action)) {

            paused=true;

            if(tts!=null) tts.stop();

            synchronized(audioLock) {
                if(activeTrack!=null) {
                    try {
                        activeTrack.pause();
                    } catch(Exception ignored) {}
                }
            }

            update("已暂停");

        } else if(RESUME.equals(action)) {

            paused=false;

            synchronized(audioLock) {
                if(activeTrack!=null) {
                    try {
                        activeTrack.play();
                    } catch(Exception ignored) {}
                }
            }

            update("继续监控");

            submitCheck();

        } else if(STOP.equals(action)) {

            stopServiceWork();

        } else if(SPEAK.equals(action)) {

            loadSettings(i);

            String text=i.getStringExtra("text");

            if(text!=null && !text.trim().isEmpty()) {

                if(!running) {
                    running=true;

                    startForeground(
                            NOTIFY,
                            notification("正在朗读")
                    );

                    acquireWakeLock();
                }

                final String content=text.trim();

                if(worker!=null && !worker.isShutdown()) {
                    worker.execute(()->speak(content));
                }
            }
        }

        return START_NOT_STICKY;
    }

    private void loadSettings(Intent i) {

        platform=i.getIntExtra("platform",PLATFORM_LOCAL);

        // 兼容旧版 MainActivity
        if(!i.hasExtra("platform")) {
            platform=i.getBooleanExtra("edge_mode",false)
                    ?PLATFORM_EDGE:PLATFORM_LOCAL;
        }

        serverUrl=value(i,"server_url");
        apiKey=value(i,"api_key");
        edgeVoice=value(i,"edge_voice","zh-CN-XiaoxiaoNeural");

        azureKey=value(i,"azure_key");
        azureRegion=value(i,"azure_region");
        azureVoice=value(i,"azure_voice","zh-CN-XiaoxiaoNeural");

        googleJsonPath=value(i,"google_json_path");
        googleProjectId=value(i,"google_project_id");
        googleClientEmail=value(i,"google_client_email");
        googlePrivateKey=value(i,"google_private_key");
        googleTokenUri=value(
                i,
                "google_token_uri",
                "https://oauth2.googleapis.com/token"
        );

        // 每次新任务重新获取 Google Token
        googleAccessToken="";
        googleTokenExpire=0;

        awsAccessKey=value(i,"aws_access_key");
        awsSecretKey=value(i,"aws_secret_key");
        awsRegion=value(i,"aws_region","us-east-1");
        pollyVoice=value(i,"polly_voice","Zhiyu");
        pollyEngine=value(i,"polly_engine","standard");

        elevenKey=value(i,"eleven_key");
        elevenVoiceId=value(i,"eleven_voice_id");
        elevenModel=value(
                i,
                "eleven_model",
                "eleven_multilingual_v2"
        );

        speed=Math.max(
                0.5f,
                Math.min(1.5f,i.getFloatExtra("speed",1f))
        );

        gain=Math.max(
                0.5f,
                Math.min(3f,i.getFloatExtra("gain",2f))
        );
    }

    private String value(Intent i,String key) {
        String s=i.getStringExtra(key);
        return s==null?"":s.trim();
    }

    private String value(Intent i,String key,String def) {
        String s=i.getStringExtra(key);
        return s==null || s.trim().isEmpty()?def:s.trim();
    }

    private void acquireWakeLock() {
        try {
            if(wakeLock!=null && !wakeLock.isHeld()) {
                wakeLock.acquire();
            }
        } catch(Exception ignored) {}
    }

    private void beginSchedule() {

        if(scheduler!=null) {
            scheduler.shutdownNow();
        }

        scheduler=Executors.newSingleThreadScheduledExecutor();

        scheduler.scheduleWithFixedDelay(
                this::submitCheck,
                0,
                interval,
                TimeUnit.SECONDS
        );
    }

    private void submitCheck() {

        if(!running || paused || worker==null || worker.isShutdown()) {
            return;
        }

        worker.execute(()->{

            if(!running || paused) return;

            if(monitorLocal && tree!=null) {

                try {

                    String[] item=getNewestLocalLine();

                    if(item!=null) {
                        speakIfChanged(
                                "local:"+item[0],
                                item[1]
                        );
                    }

                } catch(Exception e) {
                    update("本机目录读取失败："+err(e));
                }
            }

            if(monitorWeb && !webUrl.isEmpty() &&
                    running && !paused) {

                try {

                    String line=fetchWebLastLine(webUrl);

                    if(line!=null && !line.trim().isEmpty()) {

                        speakIfChanged(
                                "web:"+webUrl,
                                line.trim()
                        );

                    } else {
                        update("网页读取成功，但没有提取到文字");
                    }

                } catch(Exception e) {
                    update("网页读取失败："+err(e));
                }
            }
        });
    }

    private String[] getNewestLocalLine() throws Exception {

        String doc=DocumentsContract.getTreeDocumentId(tree);

        Uri children=
                DocumentsContract.buildChildDocumentsUriUsingTree(
                        tree,doc
                );

        Uri best=null;
        String nameBest="";
        long newest=-1;

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
                     low.endsWith(".log"))) {
                    continue;
                }

                long t=c.getLong(2);

                if(t>newest) {

                    newest=t;
                    nameBest=n;

                    best=DocumentsContract.buildDocumentUriUsingTree(
                            tree,c.getString(0)
                    );
                }
            }
        }

        if(best==null) return null;

        String line=readLocalLastLine(best);

        return line.isEmpty()
                ?null
                :new String[]{nameBest,line};
    }

    private String readLocalLastLine(Uri uri) throws Exception {

        InputStream in=getContentResolver().openInputStream(uri);

        if(in==null) return "";

        String last="";

        try(BufferedReader r=new BufferedReader(
                new InputStreamReader(in,Charset.forName("UTF-8"))
        )) {

            String s;

            while((s=r.readLine())!=null) {
                if(!s.trim().isEmpty()) last=s.trim();
            }
        }

        return last;
    }
    
    // ================= 网页读取 =================

    private String fetchWebLastLine(String address) throws Exception {

        HttpURLConnection c=null;

        try {

            URL url=new URL(address);
            URLConnection raw=url.openConnection();

            raw.setConnectTimeout(10000);
            raw.setReadTimeout(15000);
            raw.setUseCaches(false);

            raw.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 VoiceReader/3.0"
            );

            raw.setRequestProperty(
                    "Accept",
                    "text/plain,text/html,text/csv,*/*"
            );

            if(!(raw instanceof HttpURLConnection)) {
                throw new IOException("只支持 HTTP/HTTPS");
            }

            c=(HttpURLConnection)raw;
            c.setInstanceFollowRedirects(true);

            int code=c.getResponseCode();

            if(code<200 || code>=300) {
                throw new IOException("HTTP "+code);
            }

            String contentType=c.getContentType();
            String charsetName="UTF-8";

            if(contentType!=null) {

                String lower=contentType.toLowerCase(Locale.ROOT);
                int cp=lower.indexOf("charset=");

                if(cp>=0) {
                    charsetName=contentType.substring(cp+8)
                            .split("[; ]")[0]
                            .replace("\"","")
                            .trim();
                }
            }

            String body=readAll(
                    c.getInputStream(),
                    Charset.forName(charsetName)
            );

            boolean html=contentType!=null &&
                    (contentType.toLowerCase(Locale.ROOT).contains("text/html") ||
                     contentType.toLowerCase(Locale.ROOT).contains("application/xhtml"));

            String text=html?htmlToVisibleText(body):body;

            return lastNonEmptyLine(text);

        } finally {
            if(c!=null) c.disconnect();
        }
    }

    private String readAll(InputStream in,Charset charset) throws Exception {

        StringBuilder b=new StringBuilder();

        try(BufferedReader r=new BufferedReader(
                new InputStreamReader(in,charset)
        )) {

            char[] buf=new char[4096];
            int n;

            while((n=r.read(buf))!=-1) {

                b.append(buf,0,n);

                if(b.length()>5*1024*1024) {
                    throw new IOException("网页内容超过5MB");
                }
            }
        }

        return b.toString();
    }

    private String htmlToVisibleText(String html) {

        String cleaned=html
                .replaceAll("(?is)<(script|style|noscript|svg|head)\\b[^>]*>.*?</\\1\\s*>"," ")
                .replaceAll("(?is)<!--.*?-->"," ")
                .replaceAll("(?i)<br\\s*/?>","\n")
                .replaceAll("(?i)</(p|div|li|tr|h[1-6]|section|article|pre)\\s*>","\n")
                .replaceAll("(?i)<(p|div|li|tr|h[1-6]|section|article|pre)\\b[^>]*>","\n");

        Spanned sp=Html.fromHtml(
                cleaned,
                Html.FROM_HTML_MODE_LEGACY
        );

        String text=sp.toString().replace('\u00a0',' ');

        return text.replaceAll("[\\t\\x0B\\f\\r ]+"," ")
                .replaceAll(" *\\n *","\n");
    }

    private String lastNonEmptyLine(String text) {

        if(text==null) return "";

        String last="";

        for(String line:text.split("\\R")) {
            if(!line.trim().isEmpty()) last=line.trim();
        }

        return last;
    }

    // ================= 朗读分发 =================

    private void speakIfChanged(String source,String line) {

        if(!running || paused || line==null || line.isEmpty()) {
            return;
        }

        if(line.equals(lastSpoken.get(source))) {
            return;
        }

        speak(line);

        lastSpoken.put(source,line);
    }

    private void speak(String text) {

        if(!running || paused || text==null || text.isEmpty()) {
            return;
        }

        try {

            switch(platform) {

                case PLATFORM_LOCAL:
                    speakLocal(text);
                    break;

                case PLATFORM_EDGE:
                    speakOnline(playEdge(text));
                    break;

                case PLATFORM_AZURE:
                    speakOnline(playAzure(text));
                    break;

                case PLATFORM_GOOGLE:
                    speakOnline(playGoogle(text));
                    break;

                case PLATFORM_POLLY:
                    speakOnline(playPolly(text));
                    break;

                case PLATFORM_ELEVEN:
                    speakOnline(playEleven(text));
                    break;

                default:
                    throw new IOException("未知语音平台："+platform);
            }

        } catch(Exception e) {
            update("朗读失败："+err(e));
        }
    }

    private void speakOnline(byte[] mp3) throws Exception {

        if(mp3==null || mp3.length==0) {
            throw new IOException("语音平台返回了空音频");
        }

        if(!running) return;

        update("在线语音已生成，正在解码");

        File file=new File(getCacheDir(),"reader_audio.mp3");

        try(FileOutputStream out=new FileOutputStream(file)) {
            out.write(mp3);
        }

        DecodedAudio audio=decodeAudio(file);

        playPcm(audio.pcm,audio.sampleRate,audio.channels);

        update("在线语音朗读完成");
    }

    private void speakLocal(String text) {

        if(!ready || tts==null) {
            update("本机语音引擎尚未就绪");
            return;
        }

        try {

            tts.setLanguage(Locale.forLanguageTag("zh-CN"));

            tts.setSpeechRate(speed);

            String id="local_"+System.currentTimeMillis();

            Bundle params=new Bundle();

            params.putFloat(
                    TextToSpeech.Engine.KEY_PARAM_VOLUME,
                    Math.min(1f,gain)
            );

            tts.speak(
                    text,
                    TextToSpeech.QUEUE_FLUSH,
                    params,
                    id
            );

            update("正在使用本机语音朗读");

        } catch(Exception e) {
            update("本机 TTS 异常："+err(e));
        }
    }

    // ================= Edge TTS =================

    private byte[] playEdge(String text) throws Exception {

        if(serverUrl.isEmpty() || apiKey.isEmpty()) {
            throw new IOException("没有配置 Edge 服务器地址或密钥");
        }

        update("正在请求 Edge TTS");

        JSONObject json=new JSONObject();

        json.put("text",text);
        json.put("voice",edgeVoice);
        json.put("speed",(double)speed);

        return httpPost(
                serverUrl.replaceAll("/+$","")+"/tts",
                json.toString().getBytes("UTF-8"),
                new HashMap<String,String>() {{
                    put("Authorization","Bearer "+apiKey);
                    put("Accept","audio/mpeg");
                }},
                "audio/mpeg"
        );
    }

    // ================= Azure Speech =================

    private byte[] playAzure(String text) throws Exception {

        if(azureKey.isEmpty() || azureRegion.isEmpty()) {
            throw new IOException("请配置 Azure Speech Key 和 Region");
        }

        update("正在请求 Microsoft Azure Speech");

        String endpoint=
                "https://"+azureRegion+
                ".tts.speech.microsoft.com/cognitiveservices/v1";

        String escaped=xmlEscape(text);

        String rateText=String.format(
                Locale.US,
                "%+d%%",
                Math.round((speed-1f)*100f)
        );

        String ssml=
                "<speak version='1.0' " +
                "xmlns='http://www.w3.org/2001/10/synthesis' " +
                "xml:lang='zh-CN'>" +
                "<voice name='"+xmlEscape(azureVoice)+"'>" +
                "<prosody rate='"+rateText+"'>"+
                escaped+
                "</prosody></voice></speak>";

        Map<String,String> headers=new HashMap<>();

        headers.put(
                "Ocp-Apim-Subscription-Key",
                azureKey
        );

        headers.put(
                "X-Microsoft-OutputFormat",
                "audio-24khz-48kbitrate-mono-mp3"
        );

        headers.put(
                "Content-Type",
                "application/ssml+xml"
        );

        headers.put(
                "User-Agent",
                "VoiceReader"
        );

        return httpPost(
                endpoint,
                ssml.getBytes("UTF-8"),
                headers,
                "audio/mpeg"
        );
    }

    private String xmlEscape(String s) {

        if(s==null) return "";

        return s.replace("&","&amp;")
                .replace("<","&lt;")
                .replace(">","&gt;")
                .replace("\"","&quot;")
                .replace("'","&apos;");
    }

    // ================= Google Cloud TTS =================

    private byte[] playGoogle(String text) throws Exception {

        if(googleClientEmail.isEmpty() ||
                googlePrivateKey.isEmpty()) {

            throw new IOException("Google 服务账号 JSON 尚未导入");
        }

        String token=getGoogleAccessToken();

        update("正在请求 Google Cloud TTS");

        JSONObject input=new JSONObject();
        input.put("text",text);

        JSONObject voice=new JSONObject();
        voice.put("languageCode","cmn-CN");
        voice.put("name","cmn-CN-Wavenet-A");

        JSONObject audio=new JSONObject();
        audio.put("audioEncoding","MP3");

        JSONObject request=new JSONObject();
        request.put("input",input);
        request.put("voice",voice);
        request.put("audioConfig",audio);

        Map<String,String> headers=new HashMap<>();

        headers.put(
                "Authorization",
                "Bearer "+token
        );

        headers.put(
                "Content-Type",
                "application/json; charset=UTF-8"
        );

        byte[] response=httpPost(
                "https://texttospeech.googleapis.com/v1/text:synthesize",
                request.toString().getBytes("UTF-8"),
                headers,
                "application/json"
        );

        JSONObject result=new JSONObject(
                new String(response,"UTF-8")
        );

        String encoded=result.optString("audioContent","");

        if(encoded.isEmpty()) {
            throw new IOException("Google 没有返回 audioContent");
        }

        return android.util.Base64.decode(
                encoded,
                android.util.Base64.DEFAULT
        );
    }

    private synchronized String getGoogleAccessToken()
            throws Exception {

        if(!googleAccessToken.isEmpty() &&
                System.currentTimeMillis()<googleTokenExpire-60000) {

            return googleAccessToken;
        }

        update("正在获取 Google OAuth 令牌");

        long now=System.currentTimeMillis()/1000L;

        JSONObject header=new JSONObject();
        header.put("alg","RS256");
        header.put("typ","JWT");

        JSONObject claims=new JSONObject();

        claims.put("iss",googleClientEmail);
        claims.put(
                "scope",
                "https://www.googleapis.com/auth/cloud-platform"
        );
        claims.put("aud",googleTokenUri);
        claims.put("iat",now);
        claims.put("exp",now+3600);

        String first=base64Url(
                header.toString().getBytes("UTF-8")
        );

        String second=base64Url(
                claims.toString().getBytes("UTF-8")
        );

        String unsigned=first+"."+second;

        String pem=googlePrivateKey
                .replace("-----BEGIN PRIVATE KEY-----","")
                .replace("-----END PRIVATE KEY-----","")
                .replaceAll("\\s","");

        byte[] keyBytes=android.util.Base64.decode(
                pem,
                android.util.Base64.DEFAULT
        );

        PKCS8EncodedKeySpec spec=
                new PKCS8EncodedKeySpec(keyBytes);

        PrivateKey privateKey=
                KeyFactory.getInstance("RSA")
                        .generatePrivate(spec);

        Signature signature=Signature.getInstance("SHA256withRSA");

        signature.initSign(privateKey);
        signature.update(unsigned.getBytes("UTF-8"));

        String jwt=unsigned+"."+base64Url(signature.sign());

        String form=
                "grant_type="+URLEncoder.encode(
                        "urn:ietf:params:oauth:grant-type:jwt-bearer",
                        "UTF-8"
                )+
                "&assertion="+URLEncoder.encode(jwt,"UTF-8");

        byte[] response=httpPost(
                googleTokenUri,
                form.getBytes("UTF-8"),
                new HashMap<String,String>() {{
                    put(
                            "Content-Type",
                            "application/x-www-form-urlencoded"
                    );
                }},
                "application/json"
        );

        JSONObject token=new JSONObject(
                new String(response,"UTF-8")
        );

        googleAccessToken=token.getString("access_token");

        long expires=token.optLong("expires_in",3600);

        googleTokenExpire=System.currentTimeMillis()+expires*1000L;

        return googleAccessToken;
    }

    private String base64Url(byte[] data) {

        return android.util.Base64.encodeToString(
                data,
                android.util.Base64.URL_SAFE |
                android.util.Base64.NO_WRAP |
                android.util.Base64.NO_PADDING
        );
    }
    
    // ================= Amazon Polly =================

    private byte[] playPolly(String text) throws Exception {

        if(awsAccessKey.isEmpty() || awsSecretKey.isEmpty()) {
            throw new IOException("请配置 Amazon Polly Access Key 和 Secret Key");
        }

        if(awsRegion.isEmpty()) {
            throw new IOException("请填写 AWS Region");
        }

        update("正在请求 Amazon Polly");

        String host="polly."+awsRegion+".amazonaws.com";
        String endpoint="https://"+host+"/v1/speech";

        JSONObject input=new JSONObject();
        input.put("Text",text);
        input.put("TextType","text");

        JSONObject voice=new JSONObject();
        voice.put("VoiceId",pollyVoice);
        voice.put("Engine",pollyEngine);
        voice.put("LanguageCode","cmn-CN");

        JSONObject output=new JSONObject();
        output.put("OutputFormat","mp3");
        output.put("SampleRate","24000");

        JSONObject bodyJson=new JSONObject();
        bodyJson.put("Text",text);
        bodyJson.put("TextType","text");
        bodyJson.put("VoiceId",pollyVoice);
        bodyJson.put("Engine",pollyEngine);
        bodyJson.put("OutputFormat","mp3");
        bodyJson.put("SampleRate","24000");

        byte[] body=bodyJson.toString().getBytes("UTF-8");

        String amzDate=new java.text.SimpleDateFormat(
                "yyyyMMdd'T'HHmmss'Z'",
                Locale.US
        ).format(new Date());

        String dateStamp=amzDate.substring(0,8);

        String contentType="application/x-amz-json-1.0";
        String target="Polly.SynthesizeSpeech";

        String payloadHash=sha256Hex(body);

        String canonicalHeaders=
                "content-type:"+contentType+"\n"+
                "host:"+host+"\n"+
                "x-amz-date:"+amzDate+"\n"+
                "x-amz-target:"+target+"\n";

        String signedHeaders=
                "content-type;host;x-amz-date;x-amz-target";

        String canonicalRequest=
                "POST\n"+
                "/v1/speech\n"+
                "\n"+
                canonicalHeaders+"\n"+
                signedHeaders+"\n"+
                payloadHash;

        String algorithm="AWS4-HMAC-SHA256";

        String credentialScope=
                dateStamp+"/"+awsRegion+"/polly/aws4_request";

        String stringToSign=
                algorithm+"\n"+
                amzDate+"\n"+
                credentialScope+"\n"+
                sha256Hex(canonicalRequest.getBytes("UTF-8"));

        byte[] kDate=hmac(
                ("AWS4"+awsSecretKey).getBytes("UTF-8"),
                dateStamp
        );

        byte[] kRegion=hmac(kDate,awsRegion);
        byte[] kService=hmac(kRegion,"polly");
        byte[] kSigning=hmac(kService,"aws4_request");

        String signature=toHex(
                hmac(kSigning,stringToSign)
        );

        String authorization=
                algorithm+" "+
                "Credential="+awsAccessKey+"/"+credentialScope+", "+
                "SignedHeaders="+signedHeaders+", "+
                "Signature="+signature;

        Map<String,String> headers=new HashMap<>();

        headers.put("Content-Type",contentType);
        headers.put("X-Amz-Date",amzDate);
        headers.put("X-Amz-Target",target);
        headers.put("Authorization",authorization);

        return httpPost(
                endpoint,
                body,
                headers,
                "application/x-amz-json-1.0"
        );
    }

    private byte[] hmac(byte[] key,String data) throws Exception {

        Mac mac=Mac.getInstance("HmacSHA256");

        mac.init(new SecretKeySpec(key,"HmacSHA256"));

        return mac.doFinal(data.getBytes("UTF-8"));
    }

    private String sha256Hex(byte[] data) throws Exception {

        MessageDigest digest=MessageDigest.getInstance("SHA-256");

        return toHex(digest.digest(data));
    }

    private String toHex(byte[] data) {

        StringBuilder b=new StringBuilder();

        for(byte value:data) {
            b.append(String.format(
                    Locale.US,
                    "%02x",
                    value&0xff
            ));
        }

        return b.toString();
    }

    // ================= ElevenLabs =================

    private byte[] playEleven(String text) throws Exception {

        if(elevenKey.isEmpty() || elevenVoiceId.isEmpty()) {
            throw new IOException("请配置 ElevenLabs API Key 和 Voice ID");
        }

        update("正在请求 ElevenLabs");

        String endpoint=
                "https://api.elevenlabs.io/v1/text-to-speech/"+
                URLEncoder.encode(elevenVoiceId,"UTF-8")+
                "?output_format=mp3_44100_128";

        JSONObject voiceSettings=new JSONObject();
        voiceSettings.put("stability",0.5);
        voiceSettings.put("similarity_boost",0.75);
        voiceSettings.put("style",0.0);
        voiceSettings.put("use_speaker_boost",true);

        JSONObject json=new JSONObject();
        json.put("text",text);
        json.put("model_id",elevenModel);
        json.put("voice_settings",voiceSettings);

        Map<String,String> headers=new HashMap<>();

        headers.put("xi-api-key",elevenKey);
        headers.put("Accept","audio/mpeg");
        headers.put("Content-Type","application/json");

        return httpPost(
                endpoint,
                json.toString().getBytes("UTF-8"),
                headers,
                "audio/mpeg"
        );
    }

    // ================= 通用 HTTP POST =================

    private byte[] httpPost(
            String endpoint,
            byte[] body,
            Map<String,String> headers,
            String expectedType
    ) throws Exception {

        HttpURLConnection conn=null;

        try {

            URL url=new URL(endpoint);

            conn=(HttpURLConnection)url.openConnection();

            conn.setRequestMethod("POST");
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(90000);
            conn.setDoOutput(true);
            conn.setUseCaches(false);

            conn.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=UTF-8"
            );

            for(Map.Entry<String,String> entry:headers.entrySet()) {
                conn.setRequestProperty(
                        entry.getKey(),
                        entry.getValue()
                );
            }

            conn.setFixedLengthStreamingMode(body.length);

            try(OutputStream out=conn.getOutputStream()) {
                out.write(body);
                out.flush();
            }

            int code=conn.getResponseCode();

            InputStream in;

            if(code>=200 && code<300) {
                in=conn.getInputStream();
            } else {
                in=conn.getErrorStream();
            }

            byte[] response;

            if(in==null) {
                response=new byte[0];
            } else {
                try(InputStream source=in;
                    ByteArrayOutputStream out=new ByteArrayOutputStream()) {

                    byte[] buffer=new byte[8192];
                    int n;

                    while((n=source.read(buffer))!=-1) {

                        if(!running) {
                            throw new IOException("朗读任务已停止");
                        }

                        out.write(buffer,0,n);

                        if(out.size()>30*1024*1024) {
                            throw new IOException("语音文件超过30MB");
                        }
                    }

                    response=out.toByteArray();
                }
            }

            if(code<200 || code>=300) {

                String message=new String(
                        response,
                        "UTF-8"
                );

                if(message.length()>1000) {
                    message=message.substring(0,1000);
                }

                throw new IOException(
                        "HTTP "+code+"："+message
                );
            }

            if(response.length==0) {
                throw new IOException("服务器返回空数据");
            }

            String contentType=conn.getContentType();

            if(expectedType!=null &&
                    contentType!=null &&
                    contentType.toLowerCase(Locale.ROOT)
                            .contains("application/json") &&
                    !expectedType.contains("json")) {

                throw new IOException(
                        "平台返回了 JSON 错误信息："+ 
                        new String(response,"UTF-8")
                );
            }

            return response;

        } finally {

            if(conn!=null) {
                conn.disconnect();
            }
        }
    }

    // ================= MP3 解码 =================

    private static class DecodedAudio {

        byte[] pcm;
        int sampleRate;
        int channels;

        DecodedAudio(
                byte[] pcm,
                int sampleRate,
                int channels
        ) {
            this.pcm=pcm;
            this.sampleRate=sampleRate;
            this.channels=channels;
        }
    }

    private DecodedAudio decodeAudio(File file) throws Exception {

        MediaExtractor extractor=new MediaExtractor();

        MediaCodec decoder=null;

        ByteArrayOutputStream pcm=new ByteArrayOutputStream();

        int sampleRate=24000;
        int channels=1;

        try {

            extractor.setDataSource(file.getAbsolutePath());

            int trackIndex=-1;
            MediaFormat inputFormat=null;

            for(int i=0;i<extractor.getTrackCount();i++) {

                MediaFormat f=extractor.getTrackFormat(i);

                String mime=f.getString(MediaFormat.KEY_MIME);

                if(mime!=null && mime.startsWith("audio/")) {
                    trackIndex=i;
                    inputFormat=f;
                    break;
                }
            }

            if(trackIndex<0) {
                throw new IOException("音频文件没有音频轨道");
            }

            extractor.selectTrack(trackIndex);

            String mime=inputFormat.getString(MediaFormat.KEY_MIME);

            decoder=MediaCodec.createDecoderByType(mime);

            decoder.configure(inputFormat,null,null,0);
            decoder.start();

            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();

            boolean inputDone=false;
            boolean outputDone=false;

            while(!outputDone && running) {

                if(!inputDone) {

                    int index=decoder.dequeueInputBuffer(10000);

                    if(index>=0) {

                        ByteBuffer input=decoder.getInputBuffer(index);

                        if(input==null) {
                            throw new IOException("解码输入缓冲区不可用");
                        }

                        input.clear();

                        int size=extractor.readSampleData(input,0);

                        if(size<0) {

                            decoder.queueInputBuffer(
                                    index,
                                    0,
                                    0,
                                    0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            );

                            inputDone=true;

                        } else {

                            long time=extractor.getSampleTime();

                            decoder.queueInputBuffer(
                                    index,
                                    0,
                                    size,
                                    time,
                                    0
                            );

                            extractor.advance();
                        }
                    }
                }

                int outputIndex=decoder.dequeueOutputBuffer(
                        info,
                        10000
                );

                if(outputIndex>=0) {

                    ByteBuffer output=decoder.getOutputBuffer(outputIndex);

                    if(output!=null && info.size>0) {

                        output.position(info.offset);
                        output.limit(info.offset+info.size);

                        byte[] data=new byte[info.size];

                        output.get(data);

                        pcm.write(data);
                    }

                    decoder.releaseOutputBuffer(outputIndex,false);

                    if((info.flags &
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0) {

                        outputDone=true;
                    }

                } else if(outputIndex==
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {

                    MediaFormat format=decoder.getOutputFormat();

                    if(format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate=format.getInteger(
                                MediaFormat.KEY_SAMPLE_RATE
                        );
                    }

                    if(format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        channels=format.getInteger(
                                MediaFormat.KEY_CHANNEL_COUNT
                        );
                    }
                }
            }

            if(!running) {
                throw new IOException("朗读任务已停止");
            }

            if(channels<1 || channels>2) {
                throw new IOException("暂不支持该声道数："+channels);
            }

            byte[] result=pcm.toByteArray();

            if(result.length==0) {
                throw new IOException("音频解码结果为空");
            }

            return new DecodedAudio(
                    result,
                    sampleRate,
                    channels
            );

        } finally {

            try {
                if(decoder!=null) {
                    decoder.stop();
                    decoder.release();
                }
            } catch(Exception ignored) {}

            extractor.release();
        }
    }

    // ================= PCM 增益与播放 =================

    private void playPcm(
            byte[] input,
            int sampleRate,
            int channelCount
    ) throws Exception {

        if(input==null || input.length<2) {
            throw new IOException("PCM 音频数据为空");
        }

        final int channelMask=channelCount==1
                ?AudioFormat.CHANNEL_OUT_MONO
                :AudioFormat.CHANNEL_OUT_STEREO;

        final int encoding=AudioFormat.ENCODING_PCM_16BIT;

        byte[] pcm=input.clone();

        ByteBuffer b=ByteBuffer.wrap(pcm)
                .order(ByteOrder.LITTLE_ENDIAN);

        while(b.remaining()>=2) {

            short value=b.getShort();

            double x=value/32768.0;

            double y=Math.tanh(x*gain);

            int out=(int)Math.round(y*32767.0);

            if(out>32767) out=32767;
            if(out<-32768) out=-32768;

            b.position(b.position()-2);
            b.putShort((short)out);
        }

        int min=AudioTrack.getMinBufferSize(
                sampleRate,
                channelMask,
                encoding
        );

        if(min<=0) {
            throw new IOException("设备不支持该音频格式");
        }

        int bufferSize=Math.max(min,8192);

        AudioTrack track=new AudioTrack.Builder()
                .setAudioAttributes(
                        new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(
                                        AudioAttributes.CONTENT_TYPE_SPEECH
                                )
                                .build()
                )
                .setAudioFormat(
                        new AudioFormat.Builder()
                                .setSampleRate(sampleRate)
                                .setChannelMask(channelMask)
                                .setEncoding(encoding)
                                .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();

        synchronized(audioLock) {
            activeTrack=track;
        }

        try {

            track.play();

            int offset=0;

            while(offset<pcm.length && running) {

                while(paused && running) {
                    Thread.sleep(100);
                }

                if(!running) break;

                int count=Math.min(8192,pcm.length-offset);

                int written=track.write(
                        pcm,
                        offset,
                        count
                );

                if(written<0) {
                    throw new IOException(
                            "AudioTrack 写入失败："+written
                    );
                }

                if(written==0) {
                    Thread.sleep(10);
                    continue;
                }

                offset+=written;
            }

            long frames=pcm.length/(2L*channelCount);

            long deadline=System.currentTimeMillis()+
                    Math.max(
                            5000,
                            frames*1000L/sampleRate+5000
                    );

            while(running &&
                    track.getPlaybackHeadPosition()<frames &&
                    System.currentTimeMillis()<deadline) {

                while(paused && running) {
                    Thread.sleep(100);
                }

                Thread.sleep(30);
            }

        } finally {

            synchronized(audioLock) {
                if(activeTrack==track) {
                    activeTrack=null;
                }
            }

            try {
                track.stop();
            } catch(Exception ignored) {}

            try {
                track.release();
            } catch(Exception ignored) {}
        }
    }
    
    // ================= 本机 TTS 初始化 =================

    @Override
    public void onInit(int status) {

        if(status!=TextToSpeech.SUCCESS) {
            update("本机 TTS 初始化失败");
            return;
        }

        int result=tts.setLanguage(Locale.SIMPLIFIED_CHINESE);

        if(result==TextToSpeech.LANG_MISSING_DATA ||
                result==TextToSpeech.LANG_NOT_SUPPORTED) {

            update("本机 TTS 不支持中文，请安装中文语音包");
            return;
        }

        tts.setSpeechRate((float)speed);

        try {

            Voice best=findBestNetworkChineseVoice();

            if(best!=null) {
                tts.setVoice(best);
            }

        } catch(Exception ignored) {}

        update("本机 TTS 已准备就绪");
    }

    private Voice findBestNetworkChineseVoice() {

        if(tts==null) return null;

        try {

            Set<Voice> voices=tts.getVoices();

            if(voices==null) return null;

            Voice best=null;

            for(Voice voice:voices) {

                if(voice==null) continue;

                String language=voice.getLocale()
                        .toLanguageTag()
                        .toLowerCase(Locale.ROOT);

                if(!language.startsWith("zh")) continue;

                if(voice.isNetworkConnectionRequired()) {
                    return voice;
                }

                if(best==null) {
                    best=voice;
                }
            }

            return best;

        } catch(Exception e) {
            return null;
        }
    }

    // ================= 错误处理 =================

    private void err(String message) {

        if(message==null || message.trim().isEmpty()) {
            message="未知错误";
        }

        update("朗读错误："+message);

        Log.e("VoiceReader",message);
    }

    // ================= 通知栏 =================

    private static final int NOTIFICATION_ID=6363;

    private void createChannel() {

        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O) {

            NotificationChannel channel=new NotificationChannel(
                    "voice_reader_channel",
                    "语音朗读服务",
                    NotificationManager.IMPORTANCE_LOW
            );

            channel.setDescription("文件和网页语音朗读状态");

            NotificationManager manager=
                    getSystemService(NotificationManager.class);

            if(manager!=null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification notification(String message) {

        Notification.Builder builder;

        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O) {

            builder=new Notification.Builder(
                    this,
                    "voice_reader_channel"
            );

        } else {

            builder=new Notification.Builder(this);
        }

        Intent pauseIntent=new Intent(this,ReaderService.class);

        pauseIntent.setAction(
                paused?"RESUME":"PAUSE"
        );

        PendingIntent pausePending=PendingIntent.getService(
                this,
                1,
                pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT |
                        PendingIntent.FLAG_IMMUTABLE
        );

        Intent stopIntent=new Intent(this,ReaderService.class);
        stopIntent.setAction("STOP");

        PendingIntent stopPending=PendingIntent.getService(
                this,
                2,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT |
                        PendingIntent.FLAG_IMMUTABLE
        );

        builder.setContentTitle("文件智能朗读")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(running)
                .setOnlyAlertOnce(true)
                .addAction(
                        0,
                        paused?"继续":"暂停",
                        pausePending
                )
                .addAction(
                        0,
                        "停止",
                        stopPending
                );

        return builder.build();
    }

    private void update(String message) {

        try {

            Notification n=notification(message);

            NotificationManager manager=
                    (NotificationManager)getSystemService(
                            Context.NOTIFICATION_SERVICE
                    );

            if(manager!=null) {
                manager.notify(NOTIFICATION_ID,n);
            }

            if(running) {
                startForeground(NOTIFICATION_ID,n);
            }

        } catch(Exception e) {

            Log.e("VoiceReader","通知更新失败",e);
        }
    }

    // ================= 停止朗读任务 =================

    private void stopServiceWork() {

        running=false;
        paused=false;

        try {
            if(scheduler!=null) {
                scheduler.shutdownNow();
                scheduler=null;
            }
        } catch(Exception ignored) {}

        synchronized(audioLock) {

            if(activeTrack!=null) {

                try {
                    activeTrack.pause();
                    activeTrack.flush();
                    activeTrack.stop();
                    activeTrack.release();
                } catch(Exception ignored) {}

                activeTrack=null;
            }
        }

        try {
            if(tts!=null) {
                tts.stop();
            }
        } catch(Exception ignored) {}

        try {
            if(wakeLock!=null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch(Exception ignored) {}

        try {
            NotificationManager manager=
                    (NotificationManager)getSystemService(
                            Context.NOTIFICATION_SERVICE
                    );

            if(manager!=null) {
                manager.cancel(NOTIFICATION_ID);
            }
        } catch(Exception ignored) {}

        stopForeground(true);

        stopSelf();
    }

    // ================= Service 生命周期 =================

    @Override
    public void onDestroy() {

        running=false;
        paused=false;

        try {
            if(scheduler!=null) {
                scheduler.shutdownNow();
                scheduler=null;
            }
        } catch(Exception ignored) {}

        synchronized(audioLock) {

            if(activeTrack!=null) {

                try {
                    activeTrack.stop();
                    activeTrack.release();
                } catch(Exception ignored) {}

                activeTrack=null;
            }
        }

        try {
            if(tts!=null) {
                tts.stop();
                tts.shutdown();
                tts=null;
            }
        } catch(Exception ignored) {}

        try {
            if(wakeLock!=null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch(Exception ignored) {}

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

}


