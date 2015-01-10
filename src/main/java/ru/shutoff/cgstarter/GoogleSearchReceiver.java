package ru.shutoff.cgstarter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

public class GoogleSearchReceiver extends BroadcastReceiver {

    public static final String KEY_QUERY_TEXT = "query_text";

    @Override
    public void onReceive(Context context, Intent intent) {
        String queryText = intent.getStringExtra(KEY_QUERY_TEXT);
        queryText = queryText.toLowerCase();
        if (queryText.substring(0, 8).equals("поехали ")) {
            abortBroadcast();
            queryText = queryText.substring(8);
            Intent i = new Intent(context, StartActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.setData(Uri.parse("geo:?q=" + Uri.encode(queryText)));
            context.startActivity(i);
        }
    }
}
