package ru.shutoff.cgstarter;

import android.os.AsyncTask;
import android.util.Log;

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

public class AddressRequest extends AsyncTask<Double, Void, String> {

    @Override
    protected String doInBackground(Double... params) {
        HttpClient httpclient = new DefaultHttpClient();
        String url = "http://nominatim.openstreetmap.org/reverse?lat=$1&lon=$2&osm_type=N&format=json&address_details=0&accept-language=";
        Reader reader = null;
        try {
            for (int i = 0; i < params.length; i++) {
                url = url.replace("$" + (i + 1), params[i] + "");
            }
            url += Locale.getDefault().getLanguage();
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