package com.example.voicereader;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.media.AudioAttributes;
import android.provider.DocumentsContract;
import android.speech.tts.TextToSpeech;
import java.io.*;
import java.util.*;

public class ReaderService extends Service implements TextToSpeech.OnInitListener {
    public static final String START="reader.START", PAUSE="reader.PAUSE",
        RESUME="reader.RESUME", STOP="reader.STOP";
    private static final int NOTIFY=71;
    Handler handler=new Handler(Looper.getMainLooper());
    TextToSpeech tts;
    Uri tree;
    int interval=10;
    boolean running=false, paused=false, ready=false, languageReady=false;
    String lastSpoken="";
    Runnable poll=new Runnable() {
        @Override public void run() {
            if(!running) return;
            if(!paused) checkLatest();
            handler.postDelayed(this,interval*1000L);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        tts=new TextToSpeech(this,this);
    }

    @Override public int onStartCommand(Intent i,int flags,int id) {
        if(i==null) return START_NOT_STICKY;
        String a=i.getAction();
        if(START.equals(a)) {
            String raw=i.getStringExtra("tree");
            if(raw!=null) tree=Uri.parse(raw);
            interval=Math.max(3,i.getIntExtra("interval",10));
            running=true; paused=false;
            startForeground(NOTIFY,notification("正在监控文件"));
            handler.removeCallbacks(poll);
            handler.post(poll);
        } else if(PAUSE.equals(a)) {
            paused=true;
            if(tts!=null) tts.stop();
            update("已暂停");
        } else if(RESUME.equals(a)) {
            paused=false;
            update("继续监控");
        } else if(STOP.equals(a)) {
            running=false;
            handler.removeCallbacks(poll);
            if(tts!=null) tts.stop();
            stopForeground(true);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override public void onInit(int result) {
        ready=(result==TextToSpeech.SUCCESS);
        if(!ready) { update("后台语音引擎初始化失败"); return; }
        try {
            tts.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            int lang=tts.setLanguage(Locale.SIMPLIFIED_CHINESE);
            languageReady=(lang>=TextToSpeech.LANG_AVAILABLE);
            tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                @Override public void onStart(String id) { update("正在朗读"); }
                @Override public void onDone(String id) { update("朗读完成"); }
                @Override public void onError(String id) { update("后台语音播放失败"); }
            });
            update(languageReady?"后台中文语音已就绪":"后台中文语音不可用");
        } catch(Exception e) {
            languageReady=false;
            update("后台 TTS 异常："+e.getMessage());
        }
    }

    void checkLatest() {
        if(tree==null) return;
        try {
            String doc=DocumentsContract.getTreeDocumentId(tree);
            Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,doc);
            Uri best=null; long mt=-1;
            try(android.database.Cursor c=getContentResolver().query(children,new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            },null,null,null)) {
                if(c==null) return;
                while(c.moveToNext()) {
                    String n=c.getString(1);
                    if(n==null) continue;
                    n=n.toLowerCase(Locale.ROOT);
                    if(!(n.endsWith(".txt")||n.endsWith(".csv")||n.endsWith(".log"))) continue;
                    long t=c.getLong(2);
                    if(t>mt) {
                        mt=t;
                        best=DocumentsContract.buildDocumentUriUsingTree(tree,c.getString(0));
                    }
                }
            }
            if(best==null) return;
            String line="";
            try(BufferedReader r=new BufferedReader(new InputStreamReader(
                getContentResolver().openInputStream(best),"UTF-8"))) {
                String l;
                while((l=r.readLine())!=null) if(!l.trim().isEmpty()) line=l.trim();
            }
            if(!line.isEmpty() && !line.equals(lastSpoken) && ready && languageReady && !paused) {
                int result=tts.speak(line,TextToSpeech.QUEUE_FLUSH,null,"auto_"+System.currentTimeMillis());
                if(result!=TextToSpeech.ERROR) {
                    lastSpoken=line;
                    update("朗读："+line);
                } else update("后台 TTS 调用失败");
            }
        } catch(Exception e) {
            update("读取异常："+e.getMessage());
        }
    }

    void createChannel() {
        if(Build.VERSION.SDK_INT>=26) {
            NotificationChannel c=new NotificationChannel("reader","文件朗读",NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
        }
    }

    Notification notification(String s) {
        Intent pause=new Intent(this,ReaderService.class);
        pause.setAction(PAUSE);
        PendingIntent pi=PendingIntent.getService(this,1,pause,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?
            new Notification.Builder(this,"reader"):new Notification.Builder(this);
        return b.setContentTitle("文件智能朗读器").setContentText(s)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .addAction(new Notification.Action.Builder(
                android.R.drawable.ic_media_pause,"暂停",pi).build())
            .setOngoing(true).build();
    }

    void update(String s) {
        if(Build.VERSION.SDK_INT>=26 || Build.VERSION.SDK_INT<26) {
            try { ((NotificationManager)getSystemService(NOTIFICATION_SERVICE))
                .notify(NOTIFY,notification(s)); } catch(Exception ignored) {}
        }
    }

    @Override public void onDestroy() {
        running=false;
        handler.removeCallbacks(poll);
        if(tts!=null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }

    @Override public android.os.IBinder onBind(Intent i) { return null; }
}
