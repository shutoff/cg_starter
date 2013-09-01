package ru.shutoff.cgstarter;

import android.content.Intent;
import android.net.MailTo;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class About extends WebViewActivity {

    @Override
    String loadURL() {
        WebViewClient mWebClient = new WebViewClient(){

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if(url.startsWith("mailto:")){
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
        return "file:///android_asset/html/about.html";
    }

    public static Intent newEmailIntent(String address, String subject, String body, String cc) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_EMAIL, new String[] { address });
        intent.putExtra(Intent.EXTRA_TEXT, body);
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_CC, cc);
        intent.setType("message/rfc822");
        return intent;
    }

}