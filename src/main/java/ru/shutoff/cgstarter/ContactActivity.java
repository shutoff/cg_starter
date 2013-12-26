package ru.shutoff.cgstarter;

import android.app.AlertDialog;
import android.content.ContentUris;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Contacts;
import android.provider.ContactsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Vector;

public class ContactActivity extends GpsActivity implements AdapterView.OnItemClickListener {

    Vector<Contact> contacts;
    BaseAdapter adapter;

    @Override
    void locationChanged() {

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.list);
        setResult(RESULT_CANCELED);
        ContactLoader loader = new ContactLoader();
        loader.execute();
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        Contact contact = contacts.get(i);
        LocationRequest request = new LocationRequest();
        request.execute(contact.address);
    }

    class ContactLoader extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            String sortOrder = Contacts.ContactMethods.DISPLAY_NAME + " COLLATE LOCALIZED ASC";
            Cursor c = getContentResolver().query(ContactsContract.Data.CONTENT_URI,
                    null, null, null, sortOrder);
            contacts = new Vector<Contact>();
            if (c.moveToFirst()) {
                int totalContacts = c.getCount();
                for (int i = 0; i < totalContacts; i++) {
                    long id = c.getLong(c.getColumnIndex(ContactsContract.Contacts._ID));
                    String name = c.getString(c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
                    Cursor cursor = getContentResolver().query(ContactsContract.Data.CONTENT_URI,
                            new String[]{
                                    ContactsContract.CommonDataKinds.StructuredPostal.DATA1
                            },
                            ContactsContract.Data.CONTACT_ID + "=? AND " + ContactsContract.CommonDataKinds.StructuredPostal.MIMETYPE + "=?",
                            new String[]{String.valueOf(id), ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE},
                            null);
                    if (cursor.moveToFirst()) {
                        for (; ; ) {
                            Contact contact = new Contact();
                            contact.name = name;
                            contact.address = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredPostal.DATA1));
                            try {
                                Uri photo_uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id);
                                InputStream input = ContactsContract.Contacts.openContactPhotoInputStream(getContentResolver(), photo_uri);
                                if (input != null)
                                    contact.photo = BitmapFactory.decodeStream(input);
                            } catch (Exception ex) {
                                // ignore
                            }
                            contacts.add(contact);
                            if (!cursor.moveToNext())
                                break;
                        }
                    }
                    cursor.close();
                    c.moveToNext();
                }
            }
            c.close();
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            findViewById(R.id.progress).setVisibility(View.GONE);
            ListView lv = (ListView) findViewById(R.id.list);
            lv.setVisibility(View.VISIBLE);
            adapter = new BaseAdapter() {
                @Override
                public int getCount() {
                    return contacts.size();
                }

                @Override
                public Object getItem(int i) {
                    return contacts.get(i);
                }

                @Override
                public long getItemId(int i) {
                    return i;
                }

                @Override
                public View getView(int i, View view, ViewGroup viewGroup) {
                    View v = view;
                    if (v == null) {
                        final LayoutInflater layoutInflater = LayoutInflater.from(ContactActivity.this);
                        v = layoutInflater.inflate(R.layout.sms_item, null);
                    }
                    Contact contact = contacts.get(i);
                    TextView tvAddr = (TextView) v.findViewById(R.id.text);
                    tvAddr.setText(contact.address);
                    TextView tvName = (TextView) v.findViewById(R.id.to);
                    tvName.setText(contact.name);
                    ImageView iv = (ImageView) v.findViewById(R.id.pict);
                    if (contact.photo == null) {
                        iv.setVisibility(View.GONE);
                    } else {
                        iv.setVisibility(View.VISIBLE);
                        iv.setImageBitmap(contact.photo);
                    }
                    return v;
                }
            };
            lv.setAdapter(adapter);
            lv.setOnItemClickListener(ContactActivity.this);
        }
    }

    class LocationRequest extends AsyncTask<String, Void, JsonArray> {

        @Override
        protected JsonArray doInBackground(String... strings) {
            HttpClient httpclient = new DefaultHttpClient();
            Reader reader = null;
            try {
                String url = "http://maps.googleapis.com/maps/api/geocode/json?address=";
                url += Uri.encode(strings[0]);
                url += "&sensor=true";
                if (currentBestLocation != null) {
                    double lat = currentBestLocation.getLatitude();
                    double lon = currentBestLocation.getLongitude();
                    url += "&bounds=" + (lat - 1.5) + "," + (lon - 1.5) + "|" + (lat + 1.5) + "," + (lon + 1.5);
                }
                HttpResponse response = httpclient.execute(new HttpGet(url));
                StatusLine statusLine = response.getStatusLine();
                int status = statusLine.getStatusCode();
                reader = new InputStreamReader(response.getEntity().getContent());
                JsonValue res = JsonValue.readFrom(reader);
                reader.close();
                reader = null;
                if (!res.isObject())
                    return null;
                if (status != HttpStatus.SC_OK)
                    return null;
                return res.asObject().get("results").asArray();
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

        @Override
        protected void onPostExecute(JsonArray res) {
            if (res.size() == 1){
                try{
                    JsonObject obj = res.get(0).asObject();
                    obj = obj.get("geometry").asObject();
                    obj = obj.get("location").asObject();
                    double lat = obj.get("lat").asDouble();
                    double lon = obj.get("lon").asDouble();
                    if (OnExitService.isRunCG(ContactActivity.this))
                        CarMonitor.killCG(ContactActivity.this);
                    CarMonitor.startCG(ContactActivity.this, lat + "|" + lon, null);
                    setResult(RESULT_OK);
                    finish();
                    return;
                }catch (Exception ex){
                    // ignore
                }
                return;
            }
        }
    }

    static class Contact {
        String address;
        String name;
        Bitmap photo;
    }
}
