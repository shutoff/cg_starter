package ru.shutoff.cgstarter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.speech.RecognizerIntent;

import java.util.ArrayList;

public class GoogleSearchReceiver extends BroadcastReceiver {

    public static final String KEY_QUERY_TEXT = "query_text";

    @Override
    public void onReceive(Context context, Intent intent) {
        String queryText = intent.getStringExtra(KEY_QUERY_TEXT);
        queryText = queryText.toLowerCase();
        if (queryText.substring(0, 8).equals("едем ")) {
            abortBroadcast();
            queryText = queryText.substring(8);
            Intent i = new Intent(context, SearchActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ArrayList<String> query = new ArrayList<>();
            query.add(queryText);
            i.putExtra(RecognizerIntent.EXTRA_RESULTS, query);
            context.startActivity(i);
        }
    }
}
