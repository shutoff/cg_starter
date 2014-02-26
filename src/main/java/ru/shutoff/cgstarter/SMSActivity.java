package ru.shutoff.cgstarter;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SMSActivity extends Activity {

    Vector<SMS> smsList;
    int addr_pos;
    BaseAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.list);
        setResult(RESULT_CANCELED);
        LoadSMS task = new LoadSMS();
        task.execute();
    }

    static class SMS {
        String body;
        String to;
        String address;
        Bitmap photo;
        long date;
        double lat;
        double lon;
    }

    class LoadSMS extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {

            smsList = new Vector<SMS>();

            Uri message = Uri.parse("content://sms/");
            ContentResolver cr = getContentResolver();

            Cursor c = cr.query(message, null, null, null, null);
            startManagingCursor(c);
            int totalSMS = c.getCount();

            Pattern pattern = Pattern.compile("([0-9]{1,2}\\.[0-9]{4,7})[^0-9]+([0-9]{1,2}\\.[0-9]{4,7})");
            if (c.moveToFirst()) {
                for (int i = 0; i < totalSMS; i++) {
                    String body = c.getString(c.getColumnIndexOrThrow("body"));
                    if (body != null) {
                        Matcher matcher = pattern.matcher(body);
                        try {

                            if (matcher.find()) {
                                SMS sms = new SMS();
                                sms.body = body;
                                sms.date = c.getLong(c.getColumnIndexOrThrow("date"));
                                sms.to = c.getString(c.getColumnIndex("address"));
                                sms.lat = Double.parseDouble(matcher.group(1));
                                sms.lon = Double.parseDouble(matcher.group(2));

                                Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(sms.to));
                                ContentResolver contentResolver = getContentResolver();
                                Cursor contactLookup = contentResolver.query(uri, new String[]{BaseColumns._ID,
                                        ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);

                                try {
                                    if (contactLookup != null && contactLookup.getCount() > 0) {
                                        contactLookup.moveToNext();
                                        sms.to = contactLookup.getString(contactLookup.getColumnIndex(ContactsContract.Data.DISPLAY_NAME));
                                        long id = contactLookup.getLong(contactLookup.getColumnIndex(BaseColumns._ID));
                                        Uri photo_uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id);
                                        InputStream input = ContactsContract.Contacts.openContactPhotoInputStream(contentResolver, photo_uri);
                                        if (input != null)
                                            sms.photo = BitmapFactory.decodeStream(input);
                                    }
                                } finally {
                                    if (contactLookup != null) {
                                        contactLookup.close();
                                    }
                                }
                                smsList.add(sms);
                            }
                        } catch (Exception ex) {
                            // ignore
                        }
                    }
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
                    return smsList.size();
                }

                @Override
                public Object getItem(int i) {
                    return smsList.get(i);
                }

                @Override
                public long getItemId(int i) {
                    return i;
                }

                @Override
                public View getView(int i, View view, ViewGroup viewGroup) {
                    View v = view;
                    if (v == null) {
                        final LayoutInflater layoutInflater = LayoutInflater.from(SMSActivity.this);
                        v = layoutInflater.inflate(R.layout.sms_item, null);
                    }
                    SMS sms = smsList.get(i);
                    TextView date = (TextView) v.findViewById(R.id.date);
                    Date time = new Date(sms.date);
                    SimpleDateFormat format = new SimpleDateFormat("hh:mm dd.MM.yy");
                    date.setText(format.format(time));
                    TextView text = (TextView) v.findViewById(R.id.text);
                    text.setText(sms.body);
                    TextView to = (TextView) v.findViewById(R.id.to);
                    to.setText(sms.to);
                    TextView addr = (TextView) v.findViewById(R.id.address);
                    addr.setText(sms.address);
                    ImageView iv = (ImageView) v.findViewById(R.id.pict);
                    if (sms.photo != null) {
                        iv.setVisibility(View.VISIBLE);
                        iv.setImageBitmap(sms.photo);
                    } else {
                        iv.setVisibility(View.GONE);
                    }
                    return v;
                }
            };
            lv.setAdapter(adapter);
            lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    SMS sms = smsList.get(i);
                    if (OnExitService.isRunCG(SMSActivity.this))
                        CarMonitor.killCG(SMSActivity.this);
                    CarMonitor.startCG(SMSActivity.this, sms.lat + "|" + sms.lon, null, null);
                    setResult(RESULT_OK);
                    finish();
                }
            });
            if (smsList.size() == 0)
                return;
            new AddrResolver();
        }
    }

    class AddrResolver extends AddressRequest {

        AddrResolver() {
            if (addr_pos >= smsList.size())
                return;
            SMS sms = smsList.get(addr_pos);
            execute(sms.lat, sms.lon);
        }

        @Override
        protected void onPostExecute(String s) {
            if (s != null) {
                smsList.get(addr_pos).address = s;
                adapter.notifyDataSetChanged();
            }
            if (++addr_pos < smsList.size())
                new AddrResolver();
        }
    }
}
