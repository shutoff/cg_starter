package ru.shutoff.cgstarter;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.commons.lang3.StringUtils;

import java.text.DecimalFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Vector;

public class VoiceSearch extends GpsActivity implements RecognitionListener {

    SpeechRecognizer recognizer;
    AlertDialog dialog;
    TextView tvTitle;
    ImageView ivRecord;
    Vector<Phrase> phrases;
    int phrase;
    Vector<SearchRequest.Address> addr_list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);
        if (!isAvailable(this)) {
            Toast toast = Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT);
            toast.show();
            finish();
        }
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
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
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
                showResult();
            }
        });
    }

    void showResult() {
        dialog.dismiss();
        if (addr_list.size() == 0)
            return;
        if (currentBestLocation != null) {
            double lat = currentBestLocation.getLatitude();
            double lon = currentBestLocation.getLongitude();
            for (SearchRequest.Address addr : addr_list) {
                addr.distance = OnExitService.calc_distance(lat, lon, addr.lat, addr.lon);
                addr.scope /= Math.log(200 + addr.distance);
            }
            Collections.sort(addr_list, new Comparator<SearchRequest.Address>() {
                @Override
                public int compare(SearchRequest.Address address, SearchRequest.Address address2) {
                    if (address.scope < address2.scope)
                        return 1;
                    if (address.scope > address2.scope)
                        return -1;
                    return 0;
                }
            });
        }
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
                SearchRequest.Address addr = addr_list.get(position);
                TextView tv = (TextView) v.findViewById(R.id.addr);
                tv.setText(addr.address);
                tv = (TextView) v.findViewById(R.id.name);
                tv.setText(addr.name);
                tv = (TextView) v.findViewById(R.id.dist);
                if (addr.distance < 100) {
                    tv.setText("");
                } else {
                    DecimalFormat df = new DecimalFormat("#.#");
                    tv.setText(df.format(addr.distance / 1000) + getString(R.string.km));
                }
                return v;
            }
        });
        lv.setVisibility(View.VISIBLE);
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                SearchRequest.Address addr = addr_list.get(i);
                if (OnExitService.isRunCG(VoiceSearch.this))
                    CarMonitor.killCG(VoiceSearch.this);
                CarMonitor.startCG(VoiceSearch.this, addr.lat + "|" + addr.lon, null);
                setResult(RESULT_OK);
                finish();
            }
        });
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
        if (addr_list.size() > 0)
            dialog.dismiss();
    }

    @Override
    public void onResults(Bundle results) {
        List<String> res = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        float[] scopes = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
        if (scopes == null)
            scopes = new float[0];

        phrases = new Vector<Phrase>();
        Bookmarks.Point[] points = Bookmarks.get();
        for (int i = 0; i < res.size(); i++) {
            String r = res.get(i);
            State.appendLog(r);
            float scope = (i < scopes.length) ? scopes[i] : 0.01f;
            for (Bookmarks.Point p : points) {
                float ratio = compare(p.name, r) * 10;
                if (ratio > 0)
                    State.appendLog(p.name + " " + r + " " + ratio);
                if (ratio > 4) {
                    int n = 0;
                    for (n = 0; n < addr_list.size(); n++) {
                        SearchRequest.Address addr = addr_list.get(n);
                        if ((addr.lat == p.lat) && (addr.lon == p.lng)) {
                            addr.scope += scope * ratio;
                            break;
                        }
                    }
                    if (n >= addr_list.size()) {
                        SearchRequest.Address address = new SearchRequest.Address();
                        address.name = p.name;
                        address.address = "";
                        address.lat = p.lat;
                        address.lon = p.lng;
                        address.scope = scope * ratio;
                        addr_list.add(address);
                    }
                }
            }
            if (scope == 0)
                continue;
            Phrase phrase = new Phrase();
            phrase.phrase = r;
            phrase.scope = scope;
            phrases.add(phrase);
            State.appendLog("add " + r + " " + scope);
        }
        phrase = 0;
        new Request();
    }

    float compare(String s1, String s2) {
        String[] w1 = s1.toUpperCase().split(" ");
        String[] w2 = s2.toUpperCase().split(" ");
        float res = 0;
        for (String w : w1) {
            if (w.equals(""))
                continue;
            for (String s : w2) {
                if (s.equals(""))
                    continue;
                int lfd = StringUtils.getLevenshteinDistance(w, s);
                float ratio = ((float) lfd) / Math.min(s.length(), w.length());
                if (ratio < 0.5)
                    res += 1 - ratio * 2;
            }
        }
        return res / Math.max(w1.length, w2.length);
    }

    @Override
    public void onPartialResults(Bundle partialResults) {

    }

    @Override
    public void onEvent(int eventType, Bundle params) {

    }

    class Request extends SearchRequest {

        float scope;

        Request() {
            State.appendLog("new Request " + phrase + "," + phrases.size());
            if (phrase >= phrases.size()) {
                dialog.dismiss();
                return;
            }
            Phrase p = phrases.get(phrase++);
            State.appendLog("search " + p.phrase);
            search(p.phrase);
            scope = p.scope;
        }

        @Override
        Location getLocation() {
            return currentBestLocation;
        }

        @Override
        void showError(String error) {
            State.appendLog("error " + error);
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
                if (found) {
                    addr.scope += scope;
                    continue;
                }
                addr.scope = scope;
                addr_list.add(addr);
            }
            next();
        }

        void next() {
            new Request();
        }
    }

    static boolean isAvailable(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.FROYO)
            return false;
        return SpeechRecognizer.isRecognitionAvailable(context);
    }

    static class Phrase {
        String phrase;
        float scope;
    }

}