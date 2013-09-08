package ru.shutoff.cgstarter;

import android.content.Intent;
import android.net.MailTo;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

public class WebViewActivity extends ActionBarActivity {

    String url;

    String loadURL() {
        WebViewClient mWebClient = new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("mailto:")) {
                    MailTo mt = MailTo.parse(url);
                    Intent i = newEmailIntent(mt.getTo(), mt.getSubject(), mt.getBody(), mt.getCc());
                    startActivity(i);
                    view.reload();
                    return true;
                }
                return false;
            }
        };
        webView.setWebViewClient(mWebClient);
        return url;
    }

    FrameLayout holder;
    WebView webView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        if (intent != null)
            url = intent.getStringExtra(State.URL);
        setContentView(R.layout.webview);
        webView = (WebView) getLastCustomNonConfigurationInstance();
        initUI();
    }

    void initUI() {
        holder = (FrameLayout) findViewById(R.id.webview);
        if (webView == null) {
            webView = new WebView(this);
            webView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.FILL_PARENT));

            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);

            WebChromeClient mChromeClient = new WebChromeClient() {
                @Override
                public void onConsoleMessage(String message, int lineNumber, String sourceID) {
                    Log.v("ChromeClient", "invoked: onConsoleMessage() - " + sourceID + ":"
                            + lineNumber + " - " + message);
                    super.onConsoleMessage(message, lineNumber, sourceID);
                }

                @Override
                public boolean onConsoleMessage(ConsoleMessage cm) {
                    Log.v("ChromeClient", cm.message() + " -- From line "
                            + cm.lineNumber() + " of "
                            + cm.sourceId());
                    return true;
                }
            };
            webView.setWebChromeClient(mChromeClient);
            webView.loadUrl(loadURL());
        }
        holder.addView(webView);
    }

    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        if (webView != null)
            holder.removeView(webView);
        return webView;
    }

    public static Intent newEmailIntent(String address, String subject, String body, String cc) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{address});
        intent.putExtra(Intent.EXTRA_TEXT, body);
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_CC, cc);
        intent.setType("message/rfc822");
        return intent;
    }
}

