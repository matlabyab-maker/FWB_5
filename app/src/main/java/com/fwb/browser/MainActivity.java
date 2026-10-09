package com.fwb.browser;

import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private LinearLayout root, topBar, tabsBar, tabsHolder, bottomBar, webHolder;
    private EditText addressBar;
    private final List<BrowserTab> tabs = new ArrayList<>();
    private int activeIndex = -1;
    private ValueCallback<Uri[]> fileCallback;
    private ActivityResultLauncher<Intent> filePicker;
    private String pendingDownloadName = "download";

    private static final int CONTROL_HEIGHT_DP = 38;
    private static final int BUTTON_TEXT_SP = 12;

    private class BrowserTab {
        final DesktopWebView webView;
        String title = "New tab";
        String url = "https://www.google.com";
        Button tabButton;
        BrowserTab() { webView = new DesktopWebView(MainActivity.this); }
    }

    private class DesktopWebView extends WebView {
        float touchX, touchY;
        DesktopWebView(Context context) { super(context); }
        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_UP) {
                touchX = event.getX();
                touchY = event.getY();
            }
            return super.onTouchEvent(event);
        }
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(241, 243, 246));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        filePicker = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (fileCallback != null) {
                Uri[] results = null;
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Intent data = result.getData();
                    if (data.getClipData() != null) {
                        int count = data.getClipData().getItemCount();
                        results = new Uri[count];
                        for (int i = 0; i < count; i++) results[i] = data.getClipData().getItemAt(i).getUri();
                    } else if (data.getData() != null) {
                        results = new Uri[]{data.getData()};
                    }
                }
                fileCallback.onReceiveValue(results);
                fileCallback = null;
            } else if (result.getResultCode() == RESULT_OK) {
                Toast.makeText(this, "File selected. To upload it, use the website's upload control.", Toast.LENGTH_LONG).show();
            }
        });
        buildUi();
        addTab("https://www.google.com");
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(3), dp(2), dp(3), dp(2));
        topBar.setBackgroundColor(Color.rgb(241, 243, 246));
        addButton(topBar, "File", v -> triggerFilePicker());
        addButton(topBar, "Send", v -> sendFromSite());
        addButton(topBar, "‹", v -> { BrowserTab t = currentTab(); if (t != null && t.webView.canGoBack()) t.webView.goBack(); });
        addButton(topBar, "›", v -> { BrowserTab t = currentTab(); if (t != null && t.webView.canGoForward()) t.webView.goForward(); });
        addButton(topBar, "Copy", v -> copyTouchedContent());
        root.addView(topBar, new LinearLayout.LayoutParams(-1, dp(CONTROL_HEIGHT_DP)));

        tabsBar = new LinearLayout(this);
        tabsBar.setOrientation(LinearLayout.HORIZONTAL);
        tabsBar.setGravity(Gravity.CENTER_VERTICAL);
        tabsBar.setBackgroundColor(Color.rgb(230, 233, 238));
        HorizontalScrollView tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabsHolder = new LinearLayout(this);
        tabsHolder.setOrientation(LinearLayout.HORIZONTAL);
        tabScroll.addView(tabsHolder);
        tabsBar.addView(tabScroll, new LinearLayout.LayoutParams(0, -1, 1));
        addButton(tabsBar, "+", v -> addTab("https://www.google.com"));
        root.addView(tabsBar, new LinearLayout.LayoutParams(-1, dp(34)));

        webHolder = new LinearLayout(this);
        webHolder.setOrientation(LinearLayout.VERTICAL);
        root.addView(webHolder, new LinearLayout.LayoutParams(-1, 0, 1));

        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setPadding(dp(3), dp(2), dp(3), dp(2));
        bottomBar.setBackgroundColor(Color.rgb(241, 243, 246));
        addressBar = new EditText(this);
        addressBar.setSingleLine(true);
        addressBar.setTextSize(13);
        addressBar.setHint("Search or enter address");
        addressBar.setPadding(dp(8), 0, dp(8), 0);
        bottomBar.addView(addressBar, new LinearLayout.LayoutParams(0, dp(36), 1));
        addButton(bottomBar, "Refresh", v -> { BrowserTab t = currentTab(); if (t != null) t.webView.reload(); });
        addButton(bottomBar, "Go", v -> navigateFromAddress());
        addressBar.setOnEditorActionListener((v, actionId, event) -> { navigateFromAddress(); return true; });
        root.addView(bottomBar, new LinearLayout.LayoutParams(-1, dp(42)));
        setContentView(root);
    }

    private void addButton(LinearLayout parent, String label, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(BUTTON_TEXT_SP);
        b.setAllCaps(false);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(7), 0, dp(7), 0);
        b.setOnClickListener(listener);
        parent.addView(b, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, -1));
    }

    private void addTab(String url) {
        BrowserTab tab = new BrowserTab();
        tabs.add(tab);
        tab.tabButton = new Button(this);
        tab.tabButton.setTextSize(11);
        tab.tabButton.setAllCaps(false);
        tab.tabButton.setMinWidth(0);
        tab.tabButton.setMinimumWidth(0);
        tab.tabButton.setPadding(dp(8), 0, dp(8), 0);
        tab.tabButton.setText("Tab " + tabs.size() + " ×");
        tab.tabButton.setOnClickListener(v -> {
            int index = tabs.indexOf(tab);
            if (index >= 0) switchTab(index);
        });
        // A short tap switches; a long press closes.
        tab.tabButton.setOnLongClickListener(v -> { if (tabs.size() > 1) closeTab(tab); return true; });
        tabsHolder.addView(tab.tabButton, new LinearLayout.LayoutParams(-2, -1));
        configureWebView(tab);
        webHolder.addView(tab.webView, new LinearLayout.LayoutParams(-1, -1));
        switchTab(tabs.size() - 1);
        tab.webView.loadUrl(url);
    }

    private void configureWebView(BrowserTab tab) {
        WebSettings s = tab.webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadsImagesAutomatically(true);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setDefaultTextEncodingName("UTF-8");
        s.setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36");
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(tab.webView, true);
        tab.webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if (scheme != null && (scheme.equals("http") || scheme.equals("https"))) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (ActivityNotFoundException ignored) {}
                return true;
            }
            @Override public void onPageFinished(WebView view, String url) {
                tab.url = url;
                if (tabs.indexOf(tab) == activeIndex) addressBar.setText(url);
            }
        });
        tab.webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onReceivedTitle(WebView view, String title) {
                tab.title = title == null || title.trim().isEmpty() ? "Tab" : title;
                int idx = tabs.indexOf(tab);
                tab.tabButton.setText(shortText(tab.title, 16) + " ×");
            }
            @Override public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                try { filePicker.launch(intent); }
                catch (ActivityNotFoundException e) { fileCallback = null; callback.onReceiveValue(null); }
                return true;
            }
        });
        tab.webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            try {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimeType);
                String cookies = CookieManager.getInstance().getCookie(url);
                if (cookies != null) request.addRequestHeader("cookie", cookies);
                if (userAgent != null) request.addRequestHeader("User-Agent", userAgent);
                request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType));
                request.setDescription("Downloading with FWB_5");
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimeType));
                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                dm.enqueue(request);
                Toast.makeText(MainActivity.this, "Download started", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        tab.webView.setOnLongClickListener(v -> { copyTouchedContentFor(tab); return true; });
    }

    private void switchTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        activeIndex = index;
        for (int i = 0; i < tabs.size(); i++) {
            BrowserTab tab = tabs.get(i);
            tab.webView.setVisibility(i == index ? View.VISIBLE : View.GONE);
            tab.tabButton.setAlpha(i == index ? 1f : 0.65f);
        }
        BrowserTab t = currentTab();
        if (t != null) addressBar.setText(t.webView.getUrl() == null ? "" : t.webView.getUrl());
    }

    private void closeTab(BrowserTab tab) {
        int index = tabs.indexOf(tab);
        if (index < 0 || tabs.size() <= 1) return;
        tabs.remove(tab);
        tabsHolder.removeView(tab.tabButton);
        webHolder.removeView(tab.webView);
        tab.webView.stopLoading();
        tab.webView.destroy();
        if (index <= activeIndex) activeIndex = Math.max(0, activeIndex - 1);
        switchTab(activeIndex);
    }

    private BrowserTab currentTab() { return activeIndex >= 0 && activeIndex < tabs.size() ? tabs.get(activeIndex) : null; }

    private void navigateFromAddress() {
        String input = addressBar.getText().toString().trim();
        if (input.isEmpty()) return;
        String url;
        if (input.matches("(?i)^[a-z][a-z0-9+.-]*://.*") || input.startsWith("file:")) url = input;
        else if (input.contains(" ") || !input.contains(".")) url = "https://www.google.com/search?q=" + Uri.encode(input);
        else url = "https://" + input;
        BrowserTab t = currentTab();
        if (t != null) t.webView.loadUrl(url);
    }

    private void triggerFilePicker() {
        BrowserTab t = currentTab();
        if (t != null) {
            t.webView.evaluateJavascript("(function(){var a=[...document.querySelectorAll('input[type=file]')].find(x=>{var r=x.getBoundingClientRect();return r.width>0&&r.height>0});if(a){a.click();return 'opened'}return 'none'})()", result -> {
                if ("\"none\"".equals(result)) openStandalonePicker();
            });
        } else openStandalonePicker();
    }

    private void openStandalonePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try { filePicker.launch(intent); } catch (ActivityNotFoundException e) { Toast.makeText(this, "No file picker available", Toast.LENGTH_SHORT).show(); }
    }

    private void sendFromSite() {
        BrowserTab t = currentTab();
        if (t == null) return;
        String js = "(function(){var es=[...document.querySelectorAll('button,[role=button],input[type=submit]')];var b=es.find(e=>{var s=((e.innerText||'')+' '+(e.getAttribute('aria-label')||'')+' '+(e.title||'')+' '+(e.value||'')).toLowerCase();return /(^|\\s)(send|submit|post|ارسال|فرستادن)(\\s|$)/.test(s)||/send message|send\\s*button/.test(s)});if(b){b.click();return 'clicked'}return 'none'})()";
        t.webView.evaluateJavascript(js, result -> {
            if ("\"clicked\"".equals(result)) Toast.makeText(this, "Website send control activated", Toast.LENGTH_SHORT).show();
            else shareCurrentPage(t);
        });
    }

    private void shareCurrentPage(BrowserTab tab) {
        String url = tab.webView.getUrl();
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, url == null ? "" : url);
        try { startActivity(Intent.createChooser(send, "Send page link")); }
        catch (ActivityNotFoundException e) { Toast.makeText(this, "Sharing unavailable", Toast.LENGTH_SHORT).show(); }
    }

    private void copyTouchedContent() { BrowserTab t = currentTab(); if (t != null) copyTouchedContentFor(t); }

    private void copyTouchedContentFor(BrowserTab tab) {
        float x = tab.webView.touchX;
        float y = tab.webView.touchY;
        String js = "(function(){var s=(window.getSelection&&window.getSelection().toString())||'';if(s.trim())return s;var e=document.elementFromPoint(" + x + "," + y + ");if(!e)return location.href;var t=(e.innerText||e.textContent||e.getAttribute('aria-label')||e.getAttribute('alt')||'').trim();return t||e.href||e.src||location.href})()";
        tab.webView.evaluateJavascript(js, value -> {
            String text = decodeJsString(value);
            if (text.isEmpty() || "null".equals(text)) text = tab.webView.getUrl() == null ? "" : tab.webView.getUrl();
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("FWB_5 copied content", text));
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
        });
    }

    private String decodeJsString(String value) {
        if (value == null || value.equals("null")) return "";
        if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length()-1) == '"') {
            value = value.substring(1, value.length()-1).replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t").replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return value;
    }

    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
    private String shortText(String s, int max) { if (s == null) return "Tab"; s = s.trim(); return s.length() > max ? s.substring(0, max - 1) + "…" : s; }

    @Override public void onBackPressed() {
        BrowserTab t = currentTab();
        if (t != null && t.webView.canGoBack()) t.webView.goBack();
        else if (tabs.size() > 1 && t != null) closeTab(t);
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        for (BrowserTab tab : tabs) { tab.webView.stopLoading(); tab.webView.destroy(); }
        tabs.clear();
        super.onDestroy();
    }
}
