package ru.shutoff.cgstarter;

import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StartActivity extends GpsActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String q = null;
        try {
            Uri uri = getIntent().getData();
            String[] parts = uri.getQuery().split("&");
            for (String part : parts) {
                if (part.length() < 2)
                    continue;
                if (!part.substring(0, 2).equals("q="))
                    continue;
                q = Uri.decode(part.substring(2));
                break;
            }
        } catch (Exception ex) {
            // ignore
        }
        if (q == null) {
            finish();
            return;
        }
        Pattern pat = Pattern.compile("loc:([0-9]+\\.[0-9]+),([0-9]+\\.[0-9]+)");
        Matcher matcher = pat.matcher(q);
        if (matcher.find()) {
            try {
                double lat = Double.parseDouble(matcher.group(1));
                double lon = Double.parseDouble(matcher.group(2));
                if (OnExitService.isRunCG(StartActivity.this))
                    CarMonitor.killCG(StartActivity.this);
                CarMonitor.startCG(StartActivity.this, lat + "|" + lon, null, null);
                setResult(RESULT_OK);
                finish();
                return;
            } catch (Exception ex) {
                // ignore
            }
        }
        final String query = q;
        setContentView(R.layout.list);
        CountDownTimer timer = new CountDownTimer(1000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {

            }

            @Override
            public void onFinish() {
                SearchActivity.LocationRequest req = new SearchActivity.LocationRequest() {
                    @Override
                    Location getLocation() {
                        return currentBestLocation;
                    }

                    @Override
                    void showError(String err) {
                        error(err);
                    }

                    @Override
                    void result(Vector<SearchActivity.Address> result) {
                        if (result.size() == 0) {
                            error(getString(R.string.no_address));
                            return;
                        }
                        if (result.size() == 1) {
                            try {
                                SearchActivity.Address addr = result.get(0);
                                if (OnExitService.isRunCG(StartActivity.this))
                                    CarMonitor.killCG(StartActivity.this);
                                CarMonitor.startCG(StartActivity.this, addr.lat + "|" + addr.lon, null, addr);
                                setResult(RESULT_OK);
                                finish();
                                return;
                            } catch (Exception ex) {
                                error(ex.toString());
                            }
                            return;
                        }
                        showResult(result);
                    }
                };
                req.execute(query);
            }
        };
        timer.start();
    }

    void showResult(final Vector<SearchActivity.Address> res) {
        findViewById(R.id.progress).setVisibility(View.GONE);
        ListView lv = (ListView) findViewById(R.id.list);
        lv.setVisibility(View.VISIBLE);
        lv.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return res.size();
            }

            @Override
            public Object getItem(int position) {
                return res.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = convertView;
                if (v == null) {
                    final LayoutInflater layoutInflater = LayoutInflater.from(StartActivity.this);
                    v = layoutInflater.inflate(R.layout.addr_item, null);
                }
                TextView tv = (TextView) v.findViewById(R.id.name);
                tv.setText(res.get(position).address);
                return v;
            }
        });
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                SearchActivity.Address addr = res.get(position);
                if (OnExitService.isRunCG(StartActivity.this))
                    CarMonitor.killCG(StartActivity.this);
                CarMonitor.startCG(StartActivity.this, addr.lat + "|" + addr.lon, null, addr);
                setResult(RESULT_OK);
                finish();
            }
        });
    }

    public void locationChanged(Location location) {
        if ((location != null) && isBetterLocation(location, currentBestLocation))
            currentBestLocation = location;
        locationChanged();
    }

    void error(String error) {
        Toast toast = Toast.makeText(this, error, Toast.LENGTH_SHORT);
        toast.show();
        finish();
    }
}
