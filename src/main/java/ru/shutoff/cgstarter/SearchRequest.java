package ru.shutoff.cgstarter;

import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Locale;
import java.util.Vector;

public abstract class SearchRequest extends AsyncTask<String, Void, JsonArray> {

    String error;

    abstract Location getLocation();

    abstract void showError(String error);

    abstract void result(Vector<Address> result);

    static class Address {
        String address;
        double lat;
        double lon;
    }

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
