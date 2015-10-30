package ru.shutoff.cgstarter;

import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.util.concurrent.TimeUnit;

public abstract class HttpTask {

    final static String userAgent = System.getProperty("http.agent");
    public static final OkHttpClient client = createClient();
    AsyncTask<Object, Void, JSONObject> bgTask;
    String error_text;
    boolean canceled;

    static OkHttpClient createClient() {
        OkHttpClient client = new OkHttpClient();
        client.setConnectTimeout(15, TimeUnit.SECONDS);
        client.setReadTimeout(40, TimeUnit.SECONDS);
        client.interceptors().add(new Interceptor() {
            @Override
            public Response intercept(Chain chain) throws IOException {
                Request originalRequest = chain.request();
                Request requestWithUserAgent = originalRequest.newBuilder()
                        .removeHeader("User-Agent")
                        .addHeader("User-Agent", userAgent)
                        .build();
                return chain.proceed(requestWithUserAgent);
            }
        });
        return client;
    }

    static JSONObject request(Object... params) throws Exception {
        String url = params[0].toString();
        String data = "";
        int last_param = 1;
        for (; ; last_param++) {
            if (!url.contains("$" + last_param))
                break;
        }
        for (int i = 1; i < last_param; i++) {
            url = url.replace("$" + i, URLEncoder.encode(params[i].toString(), "UTF-8"));
        }
        for (; last_param + 1 < params.length; last_param += 2) {
            if (params[last_param + 1] == null)
                continue;
            url += "&" + params[last_param].toString();
            url += "=" + URLEncoder.encode(params[last_param + 1].toString(), "UTF-8");
        }

        Request.Builder builder = new Request.Builder().url(url);

        Request request = builder.build();
        Response response = client.newCall(request).execute();

        if (response.code() != HttpURLConnection.HTTP_OK) {
            Log.v("http", url);
            if (data != null)
                Log.v("data", data);
            throw new Exception(response.message());
        }
        Reader reader = response.body().charStream();
        char[] arr = new char[8 * 1024];
        StringBuilder buffer = new StringBuilder();
        int numCharsRead;
        while ((numCharsRead = reader.read(arr, 0, arr.length)) != -1) {
            buffer.append(arr, 0, numCharsRead);
        }
        return new JSONObject(buffer.toString());
    }

    abstract void result(JSONObject res) throws JSONException;

    abstract void error(String error);

    void execute(Object... params) {
        if (bgTask != null)
            return;
        bgTask = new AsyncTask<Object, Void, JSONObject>() {
            @Override
            protected JSONObject doInBackground(Object... params) {
                try {
                    return request(params);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    error_text = ex.getLocalizedMessage();
                    if (error_text != null) {
                        int pos = error_text.indexOf(":");
                        if (pos > 0)
                            error_text = error_text.substring(0, pos);
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(JSONObject res) {
                bgTask = null;
                if (canceled) {
                    canceled = false;
                    return;
                }
                if (res != null) {
                    try {
                        result(res);
                        return;
                    } catch (Exception ex) {
                        if (error_text == null) {
                            String msg = ex.getLocalizedMessage();
                            if (msg == null)
                                msg = ex.getMessage();
                            if (msg == null)
                                msg = ex.toString();
                            error_text = msg;
                        }
                        ex.printStackTrace();
                    }
                }
                error(error_text);
            }
        };
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                bgTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params);
            } else {
                bgTask.execute(params);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            error(ex.getLocalizedMessage());
        }
    }

    void cancel() {
        canceled = true;
    }

}
