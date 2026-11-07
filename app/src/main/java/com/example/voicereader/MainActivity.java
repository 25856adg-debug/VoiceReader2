package com.example.voicereader;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private WebView webView;
    
    // 启动页
    private final String START_URL = "https://voao.online/";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.activity_main);
        webView = findViewById(R.id.myWebView);

        // 配置网页设置
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true); // 必须允许 JS，否则大部分网页无法运行
        webSettings.setDomStorageEnabled(true); // 允许网页保存数据（比如登录状态）
        webSettings.setUseWideViewPort(true);   
        webSettings.setLoadWithOverviewMode(true);
        
        // 【关键】禁止网页弹新窗口，强制所有 target="_blank" 的链接都在当前 WebView 加载
        webSettings.setSupportMultipleWindows(false); 
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);

        // 【关键】拦截网页的所有跳转，让它们都在 App 内部进行
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // 返回 false 表示不拦截，让 WebView 自己加载这个链接
                // 这样它就会把当前页压入历史栈，实现“能进能退”
                return false; 
            }
        });

        // 【新增】设置 WebChromeClient，防止某些网页的弹窗或特殊跳转失效
        webView.setWebChromeClient(new WebChromeClient() {
            // 如果网页试图弹新窗口，我们直接把新窗口的链接在当前 WebView 里加载
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                return false; // 配合 setSupportMultipleWindows(false)，强制在本窗口加载
            }
        });

        // 加载初始网页
        webView.loadUrl(START_URL);
    }

    // 处理手机物理返回键（完美实现多级页面的“能进能退”）
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (webView.canGoBack()) {
                webView.goBack(); // 只要历史记录里还有上一页，就退一步
                return true;
            }
        }
        return super.onKeyDown(keyCode, event); // 退无可退，退出 App
    }
}


