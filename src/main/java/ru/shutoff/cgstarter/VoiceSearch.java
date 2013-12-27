package ru.shutoff.cgstarter;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class VoiceSearch extends GpsActivity implements RecognitionListener {

    SpeechRecognizer recognizer;
    AlertDialog dialog;
    TextView tvTitle;
    ImageView ivRecord;
    Vector<String> phrases;
    int phrase;
    Vector<SearchRequest.Address> addr_list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addr_list = new Vector<SearchRequest.Address>();
        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        } catch (Exception ex) {
            Toast toast = Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT);
            toast.show();
            finish();
        }
        setContentView(R.layout.list);
        LayoutInflater inflater = LayoutInflater.from(this);
        dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.voice_search)
                .setView(inflater.inflate(R.layout.voice, null))
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.show();
        tvTitle = (TextView) dialog.findViewById(R.id.title);
        ivRecord = (ImageView) dialog.findViewById(R.id.record);
        tvTitle.setText(R.string.prepare);
        recognizer.setRecognitionListener(this);
        Intent intent = new Intent();
        recognizer.startListening(intent);
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                try {
                    recognizer.stopListening();
                } catch (Exception ex) {
                    // ignore
                }
                if (addr_list.size() == 0)
                    finish();
            }
        });
        ArrayList<String> stub = new ArrayList<String>();
        stub.add("Авиаконструткторов 4");
        stub.add("Суши бар");
        Bundle bundle = new Bundle();
        bundle.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, stub);
        onResults(bundle);
    }

    void showResults() {
        State.appendLog("show results");
        if (addr_list.size() == 0)
            return;
        dialog.dismiss();
        findViewById(R.id.progress).setVisibility(View.GONE);
        ListView lv = (ListView) findViewById(R.id.list);
        lv.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return addr_list.size();
            }

            @Override
            public Object getItem(int position) {
                return addr_list.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = convertView;
                if (v == null) {
                    final LayoutInflater layoutInflater = LayoutInflater.from(VoiceSearch.this);
                    v = layoutInflater.inflate(R.layout.addr_item, null);
                }
                TextView tv = (TextView) v.findViewById(R.id.name);
                tv.setText(addr_list.get(position).address);
                return v;
            }
        });
        lv.setVisibility(View.VISIBLE);
    }

    @Override
    public void onReadyForSpeech(Bundle params) {
        tvTitle.setText(R.string.speak);
        ivRecord.setVisibility(View.VISIBLE);
        dialog.findViewById(R.id.progress).setVisibility(View.GONE);
    }

    @Override
    public void onBeginningOfSpeech() {
    }

    @Override
    public void onRmsChanged(float rmsdB) {
        int alpha = (int) (128 + rmsdB * 12.8);
        if (alpha > 255)
            alpha = 255;
        if (alpha < 0)
            alpha = 0;
        ivRecord.setAlpha(alpha);
    }

    @Override
    public void onBufferReceived(byte[] buffer) {
    }

    @Override
    public void onEndOfSpeech() {
        tvTitle.setText(R.string.search);
        ivRecord.setVisibility(View.GONE);
        dialog.findViewById(R.id.progress).setVisibility(View.VISIBLE);
    }

    @Override
    public void onError(int error) {
        tvTitle.setText(R.string.error);
        ivRecord.setVisibility(View.GONE);
        dialog.findViewById(R.id.progress).setVisibility(View.GONE);
    }

    @Override
    public void onResults(Bundle results) {
        List<String> res = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        phrases = new Vector<String>();
        for (String r : res) {
            phrases.add(r);
        }
        new Request();
    }

    @Override
    public void onPartialResults(Bundle partialResults) {

    }

    @Override
    public void onEvent(int eventType, Bundle params) {

    }

    class Request extends SearchRequest {

        Request() {
            if (phrase >= phrases.size()) {
                showResults();
                return;
            }
            State.appendLog("search: " + phrases.get(phrase));
            execute(phrases.get(phrase++));
        }

        @Override
        Location getLocation() {
            return currentBestLocation;
        }

        @Override
        void showError(String error) {
            next();
        }

        @Override
        void result(Vector<Address> result) {
            for (Address addr : result) {
                boolean found = false;
                for (Address a : addr_list) {
                    if ((a.lat == addr.lat) && (a.lon == addr.lon)) {
                        found = true;
                        break;
                    }
                }
                if (found)
                    continue;
                State.appendLog("add " + addr.address);
                addr_list.add(addr);
            }
            next();
        }

        void next() {
            new Request();
        }
    }

    // https://maps.googleapis.com/maps/api/place/textsearch/xml?query=%D0%BB%D0%B5%D0%BD%D0%B8%D0%BD%D0%B0%2040&location=60.0045,30.34456&radius=1000&sensor=true&key=AIzaSyAqcPdecy9uOeLMZ5VhjzfJQV9unU4GIL0
}
