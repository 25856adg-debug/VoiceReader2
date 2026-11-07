package com.example.voicereader;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private WebView webView;
    
    // 【在这里固定域名】
    private final String FIXED_DOMAIN = "voao.online"; 
    // 【启动时加载的网址】
    private final String START_URL = "https://voao.online/";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 加载刚才写的那个极简布局
        setContentView(R.layout.activity_main);
        
        // 找到布局里的 WebView
        webView = findViewById(R.id.myWebView);

        // 配置网页设置
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true); // 允许运行 JS
        webSettings.setDomStorageEnabled(true); // 允许网页本地存储（Cookie等）
        webSettings.setUseWideViewPort(true);   
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setSupportZoom(false);      // 套壳App一般禁止缩放，体验更像原生

        // 拦截网页内的跳转（核心：防止用户点到别的网站去）
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                // 如果点击的链接包含你固定的域名，就在当前 App 内继续加载
                if (url.contains(FIXED_DOMAIN)) {
                    view.loadUrl(url);
                    return false; 
                } else {
                    // 如果点击的链接跳到了外部网站，直接拦截
                    return true; 
                }
            }
        });

        // 加载你的网页
        webView.loadUrl(START_URL);
    }

    // 处理手机物理返回键
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack(); // 如果网页还能后退，就后退
            return true;
        }
        return super.onKeyDown(keyCode, event); // 退到底了就退出 App
    }
}

