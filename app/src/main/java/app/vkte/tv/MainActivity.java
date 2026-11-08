package app.vkte.tv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;

/**
 * غلاف WebView بسيط لتطبيق vkte على Android TV / Google TV.
 * - يحمّل رابط تطبيقك على Cloudflare (التحديثات تصل تلقائيًا بدون إعادة بناء).
 * - يضيف "VkteTV" إلى وكيل المتصفح فيعمل وضع التلفاز في التطبيق تلقائيًا.
 * - زر الرجوع يغلق النوافذ داخل التطبيق أولًا ثم يخرج.
 * - روابط stremio:// تُفتح في ستريميو إن كان مثبتًا. أي رابط آخر (ومنها nuvio://) مرفوض عمدًا.
 */
public class MainActivity extends Activity {
    private static final int REQ_FILE = 11;
    private WebView web;
    private ValueCallback<Uri[]> filePicker;
    private String appHost;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        final String url = BuildConfig.APP_URL;
        if (url == null || !url.startsWith("https://") || url.contains("CHANGE-ME")) {
            TextView t = new TextView(this);
            t.setText("لم يُضبط رابط التطبيق.\nأعد بناء الـAPK وأدخل رابط موقعك (https://...pages.dev/)");
            t.setTextColor(Color.WHITE);
            t.setTextSize(24);
            t.setGravity(Gravity.CENTER);
            t.setBackgroundColor(Color.parseColor("#0b0407"));
            setContentView(t);
            return;
        }
        Uri parsed = Uri.parse(url);
        appHost = parsed.getHost();

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0b0407"));
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(s.getUserAgentString() + " VkteTV");

        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        web.requestFocus();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                String scheme = u.getScheme();
                if ("https".equals(scheme) && appHost != null && appHost.equalsIgnoreCase(u.getHost())) {
                    return false; // داخل التطبيق
                }
                if ("stremio".equals(scheme)) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, u));
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(MainActivity.this, "ستريميو غير مثبّت على هذا التلفاز", Toast.LENGTH_LONG).show();
                    }
                }
                return true; // أي شيء آخر مرفوض
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    String html = "<html dir='rtl'><body style='background:#0b0407;color:#fff;font-family:sans-serif;"
                            + "display:flex;align-items:center;justify-content:center;height:100vh;margin:0;text-align:center'>"
                            + "<div><h1>لا يوجد اتصال بالإنترنت</h1><p>تأكد من الشبكة ثم أعد فتح التطبيق</p></div></body></html>";
                    view.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
                }
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (filePicker != null) filePicker.onReceiveValue(null);
                filePicker = callback;
                try {
                    startActivityForResult(params.createIntent(), REQ_FILE);
                } catch (Exception e) {
                    filePicker = null;
                    Toast.makeText(MainActivity.this, "لا يوجد تطبيق ملفات على هذا التلفاز", Toast.LENGTH_LONG).show();
                    return false;
                }
                return true;
            }
        });

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl(url);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE && filePicker != null) {
            filePicker.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            filePicker = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        if (web == null) {
            super.onBackPressed();
            return;
        }
        web.evaluateJavascript(
                "(function(){try{return !!(window.__tvBack&&window.__tvBack());}catch(e){return false;}})()",
                value -> {
                    if ("true".equals(value)) return; // التطبيق أغلق نافذة
                    if (web.canGoBack()) web.goBack();
                    else finish();
                });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (web != null) web.saveState(outState);
    }

    @Override
    protected void onPause() {
        if (web != null) web.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
