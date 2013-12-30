package ru.shutoff.cgstarter;

import android.app.AlertDialog;
import android.content.ContentUris;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
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
import android.widget.Toast;

import java.io.InputStream;
import java.util.Vector;

public class ContactActivity extends GpsActivity implements AdapterView.OnItemClickListener {

    Vector<Contact> contacts;
    BaseAdapter adapter;

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
        SearchRequest request = new SearchRequest() {
            @Override
            Location getLocation() {
                return currentBestLocation;
            }

            @Override
            void showError(String error) {
                error(error);
            }

            @Override
            void result(Vector<Address> result) {
                searchResult(result);
            }
        };
        request.search(contact.address);
    }

    public void locationChanged(Location location) {
        if ((location != null) && isBetterLocation(location, currentBestLocation))
            currentBestLocation = location;
        locationChanged();
    }

    void error(String err) {
        Toast toast = Toast.makeText(this, err, Toast.LENGTH_LONG);
        ;
        toast.show();
    }

    void searchResult(final Vector<SearchRequest.Address> res) {
        if (res.size() == 0) {
            error(getString(R.string.no_address));
            return;
        }
        if (res.size() == 1) {
            try {
                SearchRequest.Address addr = res.get(0);
                if (OnExitService.isRunCG(ContactActivity.this))
                    CarMonitor.killCG(ContactActivity.this);
                CarMonitor.startCG(ContactActivity.this, addr.lat + "|" + addr.lon, null);
                setResult(RESULT_OK);
                finish();
                return;
            } catch (Exception ex) {
                error(ex.toString());
            }
            return;
        }
        if (res.size() > 1) {
            final LayoutInflater layoutInflater = LayoutInflater.from(ContactActivity.this);
            AlertDialog dialog = new AlertDialog.Builder(ContactActivity.this)
                    .setTitle(R.string.select_addr)
                    .setNegativeButton(R.string.cancel, null)
                    .setView(layoutInflater.inflate(R.layout.list, null))
                    .create();
            dialog.show();
            dialog.findViewById(R.id.progress).setVisibility(View.GONE);
            ListView lv = (ListView) dialog.findViewById(R.id.list);
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
                        final LayoutInflater layoutInflater = LayoutInflater.from(ContactActivity.this);
                        v = layoutInflater.inflate(R.layout.addr_item, null);
                    }
                    TextView tv = (TextView) v.findViewById(R.id.addr);
                    tv.setText(res.get(position).address);
                    return v;
                }
            });
            lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    SearchRequest.Address addr = res.get(position);
                    if (OnExitService.isRunCG(ContactActivity.this))
                        CarMonitor.killCG(ContactActivity.this);
                    CarMonitor.startCG(ContactActivity.this, addr.lat + "|" + addr.lon, null);
                    setResult(RESULT_OK);
                    finish();
                }
            });
        }

    }

    class ContactLoader extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            contacts = new Vector<Contact>();
            Cursor cursor = getContentResolver().query(ContactsContract.Data.CONTENT_URI,
                    new String[]{
                            BaseColumns._ID,
                            ContactsContract.Contacts.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.StructuredPostal.DATA1
                    },
                    ContactsContract.CommonDataKinds.StructuredPostal.MIMETYPE + "=?",
                    new String[]{ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE},
                    ContactsContract.Contacts.DISPLAY_NAME);
            if (cursor.moveToFirst()) {
                for (; ; ) {
                    Contact contact = new Contact();
                    contact.name = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
                    contact.address = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredPostal.DATA1));
                    long id = cursor.getLong(cursor.getColumnIndex(BaseColumns._ID));
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

    static class Contact {
        String address;
        String name;
        Bitmap photo;
    }

}
