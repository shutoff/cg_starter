package ru.shutoff.cgstarter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URLEncoder;
import java.util.Date;
import java.util.Locale;

public class SendLocationActivity extends Activity {

    static final String OSM_URL = "http://nominatim.openstreetmap.org/reverse?lat=$1&lon=$2&osm_type=N&format=json&address_details=0&accept-language=$3";

    double finish_lat;
    double finish_lon;

    TextView finish_info;

    Button current;
    TextView current_info;

    LocationManager locationManager;
    LocationListener netListener;
    LocationListener gpsListener;

    Location currentBestLocation;

    boolean location_changed;
    AddressRequest request;

    double addr_lat;
    double addr_lon;
    String address;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable ex) {
                State.print(ex);
            }
        });

        super.onCreate(savedInstanceState);
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.location);

        try {
            File poi = Environment.getExternalStorageDirectory();
            poi = new File(poi, "CityGuide/routes.dat");
            BufferedReader reader = new BufferedReader(new FileReader(poi));
            reader.readLine();
            boolean current = false;
            while (true) {
                String line = reader.readLine();
                if (line == null)
                    break;
                String[] parts = line.split("\\|");
                if (parts.length == 0)
                    continue;
                String name = parts[0];
                if ((name.length() > 0) && (name.substring(0, 1).equals("#"))) {
                    current = name.equals("#[CURRENT]");
                    continue;
                }
                if (current && name.equals("Finish")) {
                    finish_lat = Double.parseDouble(parts[1]);
                    finish_lon = Double.parseDouble(parts[2]);
                }
            }
            reader.close();
        } catch (Exception ex) {
            // ignore
        }

        Button btn = (Button) findViewById(R.id.finish);
        finish_info = (TextView) findViewById(R.id.finish_info);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if ((finish_lat != 0) && (finish_lon != 0)) {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse("sms:"));
                    intent.setType("vnd.android-dir/mms-sms");
                    intent.putExtra("sms_body", format(finish_lat) + " " + format(finish_lon));
                    startActivity(intent);
                }
            }
        });

        if ((finish_lat == 0) && (finish_lon == 0)) {
            btn.setEnabled(false);
            finish_info.setText(R.string.no_finish);
        } else {
            btn.setEnabled(true);
            finish_info.setText(format(finish_lat) + ", " + format(finish_lon) + "\n\n");
            AddressRequest req = new AddressRequest() {
                @Override
                protected void onPostExecute(String s) {
                    if (s != null)
                        finish_info.setText(format(finish_lat) + ", " + format(finish_lon) + "\n" + s);
                }
            };
            req.execute(OSM_URL, finish_lat + "", finish_lon + "", Locale.getDefault().getLanguage());
        }

        current = (Button) findViewById(R.id.current);
        current_info = (TextView) findViewById(R.id.current_info);
        current.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentBestLocation != null) {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse("sms:"));
                    intent.setType("vnd.android-dir/mms-sms");
                    intent.putExtra("sms_body", format(currentBestLocation.getLatitude()) + " " + format(currentBestLocation.getLongitude()));
                    startActivity(intent);
                }
            }
        });

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        netListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                locationChanged(location);
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {

            }

            @Override
            public void onProviderEnabled(String provider) {

            }

            @Override
            public void onProviderDisabled(String provider) {

            }
        };

        gpsListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                locationChanged(location);
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {

            }

            @Override
            public void onProviderEnabled(String provider) {

            }

            @Override
            public void onProviderDisabled(String provider) {

            }
        };

        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 10, gpsListener);
        } catch (Exception ex) {
            gpsListener = null;
        }

        try {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 10, netListener);
        } catch (Exception ex) {
            netListener = null;
        }

        locationChanged(getLastBestLocation());

    }

    @Override
    protected void onPause() {
        super.onPause();
        if (netListener != null)
            locationManager.removeUpdates(netListener);
        if (gpsListener != null)
            locationManager.removeUpdates(gpsListener);
    }

    static final int TWO_MINUTES = 1000 * 60 * 2;

    Location getLastBestLocation() {
        Location locationGPS = null;
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                locationGPS = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        } catch (Exception ex) {
            // ignore
        }
        Location locationNet = null;
        try {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                locationNet = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        } catch (Exception ex) {
            // ignore
        }
        long GPSLocationTime = 0;
        if (locationGPS != null)
            GPSLocationTime = locationGPS.getTime();
        long NetLocationTime = 0;
        if (locationNet != null)
            NetLocationTime = locationNet.getTime();
        if (GPSLocationTime > NetLocationTime)
            return locationGPS;
        return locationNet;
    }

    public void locationChanged(Location location) {
        if ((location != null) && isBetterLocation(location, currentBestLocation))
            currentBestLocation = location;
        if (currentBestLocation != null) {
            long t1 = currentBestLocation.getTime() + TWO_MINUTES;
            long t2 = new Date().getTime();
            if (t1 < t2)
                currentBestLocation = null;
        }
        setAddr();
    }

    void setAddr() {
        if (currentBestLocation == null) {
            current.setEnabled(false);
            current_info.setText(R.string.find_location);
            return;
        }
        double lat = currentBestLocation.getLatitude();
        double lon = currentBestLocation.getLongitude();
        String info = format(lat) + ", " + format(lon) + "\n";
        if (address != null)
            info += address;
        if (OnExitService.calc_distance(lat, lon, addr_lat, addr_lon) > 50) {
            location_changed = true;
            if (request != null) {
                location_changed = true;
            } else {
                if (currentBestLocation != null) {
                    addr_lat = currentBestLocation.getLatitude();
                    addr_lon = currentBestLocation.getLongitude();
                    location_changed = false;
                    request = new AddressRequest() {
                        @Override
                        protected void onPostExecute(String s) {
                            address = s;
                            setAddr();
                        }
                    };
                    request.execute(OSM_URL, currentBestLocation.getLatitude() + "", currentBestLocation.getLongitude() + "", Locale.getDefault().getLanguage());
                }
            }
        }
        current_info.setText(info);
    }

    protected boolean isBetterLocation(Location location, Location currentBestLocation) {
        if (currentBestLocation == null)
            return true;

        long timeDelta = location.getTime() - currentBestLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
        boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
        boolean isNewer = timeDelta > 0;

        // If it's been more than two minutes since the current location, use the new location
        // because the user has likely moved
        if (isSignificantlyNewer) {
            return true;
            // If the new location is more than two minutes older, it must be worse
        } else if (isSignificantlyOlder) {
            return false;
        }

        // Check whether the new location fix is more or less accurate
        int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;

        // Check if the old and new location are from the same provider
        boolean isFromSameProvider = isSameProvider(location.getProvider(),
                currentBestLocation.getProvider());

        // Determine location quality using a combination of timeliness and accuracy
        if (isMoreAccurate) {
            return true;
        } else if (isNewer && !isLessAccurate) {
            return true;
        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
            return true;
        }
        return false;
    }

    private boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null)
            return provider2 == null;
        return provider1.equals(provider2);
    }

    static String format(double n) {
        String res = n + "";
        if (res.length() > 8)
            res = res.substring(0, 8);
        return res;
    }

    class AddressRequest extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... params) {
            HttpClient httpclient = new DefaultHttpClient();
            String url = params[0];
            Reader reader = null;
            try {
                for (int i = 1; i < params.length; i++) {
                    url = url.replace("$" + i, URLEncoder.encode(params[i], "UTF-8"));
                }
                Log.v("url", url);
                HttpResponse response = httpclient.execute(new HttpGet(url));
                StatusLine statusLine = response.getStatusLine();
                int status = statusLine.getStatusCode();
                reader = new InputStreamReader(response.getEntity().getContent());
                JsonValue res = JsonValue.readFrom(reader);
                reader.close();
                reader = null;
                JsonObject result;
                if (res.isObject()) {
                    result = res.asObject();
                } else {
                    result = new JsonObject();
                    result.set("data", res);
                }
                if (status != HttpStatus.SC_OK) {
                    return null;
                }
                return result.get("display_name").asString();
            } catch (Exception ex) {
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
    }
}
