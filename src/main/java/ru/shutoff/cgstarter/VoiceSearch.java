package ru.shutoff.cgstarter;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.speech.RecognitionListener;
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

import java.util.Collections;
import java.util.Comparator;
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
        setResult(RESULT_CANCELED);
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
    }

    void showResults() {
        State.appendLog("show results");
        if (addr_list.size() == 0)
            return;
        dialog.dismiss();
        if (currentBestLocation != null) {
            double lat = currentBestLocation.getLatitude();
            double lon = currentBestLocation.getLongitude();
            for (SearchRequest.Address addr : addr_list) {
                addr.distance = OnExitService.calc_distance(lat, lon, addr.lat, addr.lon);
            }
            Collections.sort(addr_list, new Comparator<SearchRequest.Address>() {
                @Override
                public int compare(SearchRequest.Address address, SearchRequest.Address address2) {
                    if (address.distance < address2.distance)
                        return -1;
                    if (address.distance > address2.distance)
                        return 1;
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
                if (addr.distance == 0) {
                    tv.setText("");
                } else if (addr.distance < 1000) {
                    tv.setText((int) (addr.distance) + " m");
                } else if (addr.distance < 10000) {
                    String s = addr.distance + "";
                    s = s.substring(0, 3);
                    tv.setText(s + " km");
                } else {
                    tv.setText((int) (addr.distance / 1000) + " km");
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
    }

    @Override
    public void onResults(Bundle results) {
        List<String> res = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        phrases = new Vector<String>();
        Bookmarks.Point[] points = Bookmarks.get();
        for (String r : res) {
            for (Bookmarks.Point p : points) {
                if (p.name.equalsIgnoreCase(r)) {
                    SearchRequest.Address address = new SearchRequest.Address();
                    address.name = p.name;
                    address.address = "";
                    address.lat = p.lat;
                    address.lon = p.lng;
                    addr_list.add(address);
                }
            }
        }
        if (addr_list.size() > 0) {
            showResults();
            return;
        }
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
            search(phrases.get(phrase++));
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

    static boolean isAvailable(Context context) {
        return SpeechRecognizer.isRecognitionAvailable(context);
    }

}
