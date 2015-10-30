package ru.shutoff.cgstarter;

import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.eclipsesource.json.ParseException;
import com.squareup.okhttp.CipherSuite;
import com.squareup.okhttp.ConnectionSpec;
import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.TlsVersion;

import java.io.IOException;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public abstract class HttpTask {

    final static String userAgent = System.getProperty("http.agent");
    static ConnectionSpec spec;
    public static final OkHttpClient client = createClient();
    AsyncTask<Object, Void, JsonObject> bgTask;
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
        if (spec == null)
            spec = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                    .tlsVersions(
                            TlsVersion.TLS_1_2,
                            TlsVersion.TLS_1_1,
                            TlsVersion.TLS_1_0)
                    .cipherSuites(
                            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
                            CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
                            CipherSuite.TLS_DHE_RSA_WITH_AES_128_GCM_SHA256,
                            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA,
                            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA,
                            CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
                            CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
                            CipherSuite.TLS_ECDHE_ECDSA_WITH_RC4_128_SHA,
                            CipherSuite.TLS_ECDHE_RSA_WITH_RC4_128_SHA,
                            CipherSuite.TLS_DHE_RSA_WITH_AES_128_CBC_SHA,
                            CipherSuite.TLS_DHE_DSS_WITH_AES_128_CBC_SHA,
                            CipherSuite.TLS_DHE_RSA_WITH_AES_256_CBC_SHA,
                            CipherSuite.TLS_RSA_WITH_AES_128_GCM_SHA256,
                            CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA,
                            CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA,
                            CipherSuite.TLS_RSA_WITH_3DES_EDE_CBC_SHA,
                            CipherSuite.TLS_RSA_WITH_RC4_128_SHA,
                            CipherSuite.TLS_RSA_WITH_RC4_128_MD5)
                    .supportsTlsExtensions(true)
                    .build();
        client.setConnectionSpecs(Collections.singletonList(spec));
        return client;
    }

    static JsonObject request(Object... params) throws Exception {
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

        JsonObject result;
        Reader reader = response.body().charStream();
        try {
            JsonValue res = Json.parse(reader);
            reader.close();

            if (res.isObject()) {
                result = res.asObject();
            } else {
                result = new JsonObject();
                result.set("data", res);
            }
            if (result.get("error") != null) {
                Log.v("http", url);
                if (data != null)
                    Log.v("data", data);
                throw new Exception(result.get("error").asString());
            }
        } finally {
            reader.close();
        }
        return result;
    }

    abstract void result(JsonObject res) throws ParseException;

    abstract void error(String error);

    void execute(Object... params) {
        if (bgTask != null)
            return;
        bgTask = new AsyncTask<Object, Void, JsonObject>() {
            @Override
            protected JsonObject doInBackground(Object... params) {
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
            protected void onPostExecute(JsonObject res) {
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
                error("No data");
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
