package ru.shutoff.cgstarter;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.InputStreamReader;
import java.io.Reader;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Vector;

public class SearchActivity extends GpsActivity {

    static final int REQUEST_CODE_VOICE_SEARCH = 1;

    PlaceholderFragment fragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            fragment = new PlaceholderFragment();
            fragment.location = getLastBestLocation();
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, fragment)
                    .commit();
        }

        setResult(RESULT_CANCELED);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK) {
            finish();
            return;
        }
        fragment.voiceSearchResult(data);
    }

    @Override
    public void locationChanged() {
        if (fragment != null)
            fragment.location = getLastBestLocation();
    }

    public static class PlaceholderFragment extends Fragment {

        Vector<Address> addr_list;
        Vector<Phrase> phrases;
        int phrase;

        ListView results;
        View progress;
        int prev_size;

        Location location;

        @Override
        public View onCreateView(final LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {

            View rootView = inflater.inflate(R.layout.list, container, false);
            progress = rootView.findViewById(R.id.progress);
            progress.setVisibility(View.GONE);
            results = (ListView) rootView.findViewById(R.id.list);
            results.setVisibility(View.GONE);

            phrases = new Vector<Phrase>();
            addr_list = new Vector<Address>();

            Intent i = getActivity().getIntent();
            if (i.getStringExtra("TextSearch") != null) {
                final AlertDialog dialog = new AlertDialog.Builder(getActivity())
                        .setTitle(getString(R.string.search))
                        .setView(inflater.inflate(R.layout.text_search, null, false))
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.search, null)
                        .create();
                dialog.getWindow().setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                dialog.show();
                dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        if (phrases.size() == 0)
                            getActivity().finish();
                    }
                });
                final Button btn = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                btn.setEnabled(false);
                final EditText ed = (EditText) dialog.findViewById(R.id.text);
                ed.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {

                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        btn.setEnabled(!s.toString().isEmpty());
                    }
                });
                ed.requestFocus();
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Phrase p = new Phrase();
                        p.phrase = ed.getText().toString();
                        p.scope = 1;
                        phrases.add(p);
                        dialog.dismiss();
                        progress.setVisibility(View.VISIBLE);
                        new Request();
                    }
                });
            } else {
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                startActivityForResult(intent, REQUEST_CODE_VOICE_SEARCH);
            }
            return rootView;
        }

        void voiceSearchResult(Intent data) {

            progress.setVisibility(View.VISIBLE);

            ArrayList<String> res = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            float[] scopes = data.getFloatArrayExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES);
            if (scopes == null)
                scopes = new float[0];

            phrases = new Vector<Phrase>();
            Bookmarks.Point[] points = Bookmarks.get(getActivity());
            for (int i = 0; i < res.size(); i++) {
                String r = res.get(i);
                float scope = (i < scopes.length) ? scopes[i] : 0.01f;
                for (Bookmarks.Point p : points) {
                    float ratio = compare(p.name, r) * 10;
                    if (ratio > 4) {
                        int n = 0;
                        for (n = 0; n < addr_list.size(); n++) {
                            Address addr = addr_list.get(n);
                            if ((addr.lat == p.lat) && (addr.lon == p.lng)) {
                                addr.scope += scope * ratio;
                                break;
                            }
                        }
                        if (n >= addr_list.size()) {
                            Address address = new Address();
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
            }
            updateResults();
            phrase = 0;
            new Request();
        }

        void updateResults() {
            if (prev_size == addr_list.size())
                return;
            prev_size = addr_list.size();
            if (addr_list.size() == 0)
                return;
            if (location != null) {
                for (Address addr : addr_list) {
                    if (addr.distance != 0)
                        continue;
                    addr.distance = OnExitService.calc_distance(location.getLatitude(), location.getLongitude(), addr.lat, addr.lon);
                    addr.scope /= Math.log(200 + addr.distance);
                }
                Collections.sort(addr_list, new Comparator<Address>() {
                    @Override
                    public int compare(Address lhs, Address rhs) {
                        if (lhs.scope < rhs.scope)
                            return 1;
                        if (lhs.scope > rhs.scope)
                            return -1;
                        return 0;
                    }
                });
            }

            if (results.getVisibility() == View.VISIBLE) {
                BaseAdapter adapter = (BaseAdapter) results.getAdapter();
                adapter.notifyDataSetChanged();
                return;
            }
            results.setAdapter(new BaseAdapter() {
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
                        LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(LAYOUT_INFLATER_SERVICE);
                        v = inflater.inflate(R.layout.addr_item, null);
                    }
                    Address addr = addr_list.get(position);
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
            progress.setVisibility(View.GONE);
            results.setVisibility(View.VISIBLE);
            results.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    Address addr = addr_list.get(i);
                    if (OnExitService.isRunCG(getActivity()))
                        CarMonitor.killCG(getActivity());
                    CarMonitor.startCG(getActivity(), addr.lat + "|" + addr.lon, null);
                    getActivity().setResult(RESULT_OK);
                    getActivity().finish();
                }
            });
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

        class Request extends LocationRequest {

            Request() {
                Phrase p = phrases.get(phrase);
                execute(p.phrase);
            }

            @Override
            Location getLocation() {
                return location;
            }

            @Override
            void showError(String error) {

            }

            @Override
            void result(Vector<Address> result) {
                if (result.size() > 0) {
                    float scope = phrases.get(phrase).scope;
                    for (Address addr : result) {
                        addr.scope = scope;
                        addr_list.add(addr);
                    }
                    updateResults();
                }
                if (++phrase >= phrases.size()) {
                    if (addr_list.size() == 0) {
                        phrase = 0;
                        new NearRequest();
                        return;
                    }
                    BaseAdapter adapter = (BaseAdapter) results.getAdapter();
                    adapter.notifyDataSetChanged();
                    return;
                }
                new Request();
            }
        }

        class NearRequest extends PlaceRequest {

            NearRequest() {
                Phrase p = phrases.get(phrase);
                execute(p.phrase, "1000");
            }

            @Override
            Location getLocation() {
                return location;
            }

            @Override
            void showError(String error) {

            }

            @Override
            void result(Vector<Address> result) {
                if (result.size() > 0) {
                    float scope = phrases.get(phrase).scope;
                    for (Address addr : result) {
                        addr.scope = scope;
                        addr_list.add(addr);
                    }
                    updateResults();
                }
                if (++phrase >= phrases.size()) {
                    if (addr_list.size() == 0) {
                        phrase = 0;
                        new LongRequest();
                        return;
                    }
                    BaseAdapter adapter = (BaseAdapter) results.getAdapter();
                    adapter.notifyDataSetChanged();
                    return;
                }
                new NearRequest();
            }
        }

        class LongRequest extends PlaceRequest {

            LongRequest() {
                Phrase p = phrases.get(phrase);
                execute(p.phrase, "50000");
            }

            @Override
            Location getLocation() {
                return location;
            }

            @Override
            void showError(String error) {

            }

            @Override
            void result(Vector<Address> result) {
                if (result.size() > 0) {
                    float scope = phrases.get(phrase).scope;
                    for (Address addr : result) {
                        addr.scope = scope;
                        addr_list.add(addr);
                    }
                    updateResults();
                }
                if (++phrase >= phrases.size()) {
                    if (addr_list.size() == 0) {
                        getActivity().finish();
                        return;
                    }
                    BaseAdapter adapter = (BaseAdapter) results.getAdapter();
                    adapter.notifyDataSetChanged();
                    return;
                }
                new LongRequest();
            }
        }
    }

    static class Phrase {
        String phrase;
        float scope;
    }

    static class Address {
        String address;
        String name;
        double lat;
        double lon;
        double distance;
        float scope;
    }

    static abstract class PlaceRequest extends AsyncTask<String, Void, JsonArray> {

        abstract Location getLocation();

        abstract void showError(String error);

        abstract void result(Vector<Address> result);

        String error;

        @Override
        protected JsonArray doInBackground(String... strings) {
            HttpClient httpclient = new DefaultHttpClient();
            Reader reader = null;
            try {
                String url = "https://maps.googleapis.com/maps/api/place/textsearch/json?query=";
                String addr = strings[0];
                url += Uri.encode(addr);
                url += "&sensor=true";
                Location location = getLocation();
                if (location != null) {
                    double lat = location.getLatitude();
                    double lon = location.getLongitude();
                    url += "&location=" + lat + "," + lon + "&radius=" + strings[1];
                }
                url += "&key=AIzaSyAqcPdecy9uOeLMZ5VhjzfJQV9unU4GIL0";
                // url += "&key=AIzaSyBljQKazFWpl9nyGHp-lu8ati7QjMbwzsU";
                url += "&language=" + Locale.getDefault().getLanguage();
                Log.v("url", url);
                HttpResponse response = httpclient.execute(new HttpGet(url));
                StatusLine statusLine = response.getStatusLine();
                int status = statusLine.getStatusCode();
                reader = new InputStreamReader(response.getEntity().getContent());
                JsonValue res = JsonValue.readFrom(reader);
                reader.close();
                reader = null;
                if (status != HttpStatus.SC_OK) {
                    error = "Error " + status;
                    return null;
                }
                return res.asObject().get("results").asArray();
            } catch (Exception ex) {
                error = ex.toString();
                ex.printStackTrace();
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(JsonArray res) {
            if (res == null) {
                showError(error);
                return;
            }
            Vector<Address> r = new Vector<Address>();
            for (int i = 0; i < res.size(); i++) {
                JsonObject o = res.get(i).asObject();
                Address addr = new Address();
                addr.address = o.get("formatted_address").asString();
                addr.name = o.get("name").asString();
                JsonObject geo = o.get("geometry").asObject().get("location").asObject();
                addr.lat = geo.get("lat").asDouble();
                addr.lon = geo.get("lng").asDouble();
                r.add(addr);
            }
            result(r);
        }
    }

    static public abstract class LocationRequest extends PlaceRequest {

        @Override
        protected JsonArray doInBackground(String... strings) {
            HttpClient httpclient = new DefaultHttpClient();
            Reader reader = null;
            try {
                String url = "http://maps.googleapis.com/maps/api/geocode/json?address=";
                String addr = strings[0];
                url += Uri.encode(addr);
                url += "&sensor=true";
                Location location = getLocation();
                if (location != null) {
                    double lat = location.getLatitude();
                    double lon = location.getLongitude();
                    url += "&bounds=" + (lat - 1.5) + "," + (lon - 1.5) + Uri.encode("|") + (lat + 1.5) + "," + (lon + 1.5);
                }
                url += "&language=" + Locale.getDefault().getLanguage();
                Log.v("url", url);
                HttpResponse response = httpclient.execute(new HttpGet(url));
                StatusLine statusLine = response.getStatusLine();
                int status = statusLine.getStatusCode();
                reader = new InputStreamReader(response.getEntity().getContent());
                JsonValue res = JsonValue.readFrom(reader);
                reader.close();
                reader = null;
                if (status != HttpStatus.SC_OK) {
                    error = "Error " + status;
                    return null;
                }
                return res.asObject().get("results").asArray();
            } catch (Exception ex) {
                error = ex.toString();
                ex.printStackTrace();
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(JsonArray res) {
            if (res == null) {
                showError(error);
                return;
            }
            Vector<Address> r = new Vector<Address>();
            for (int i = 0; i < res.size(); i++) {
                JsonObject o = res.get(i).asObject();
                Address addr = new Address();
                addr.address = o.get("formatted_address").asString();
                JsonObject geo = o.get("geometry").asObject().get("location").asObject();
                addr.lat = geo.get("lat").asDouble();
                addr.lon = geo.get("lng").asDouble();
                r.add(addr);
            }
            result(r);
        }

    }

    static boolean isVoiceSearch(Context context) {
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> activities = pm.queryIntentActivities(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0);
        return activities.size() > 0;
    }

}
