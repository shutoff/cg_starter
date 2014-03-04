package ru.shutoff.cgstarter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.mobeta.android.dslv.DragSortListView;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Vector;

public class AppsFragment extends Fragment {

    Vector<String> apps;
    SharedPreferences preferences;

    DragSortListView lv;
    BaseAdapter adapter;

    int app_index;
    int app_top;

    @Override
    public View onCreateView(final LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        final PackageManager pm = getActivity().getPackageManager();
        View v = inflater.inflate(R.layout.apps, container, false);
        if (apps == null) {
            apps = new Vector<String>();
            String[] values = preferences.getString(State.APPS, "").split(":");
            for (String app : values) {
                String[] component = app.split("/");
                if (component.length != 2)
                    continue;
                if (component[0].equals("tel")) {
                    apps.add(app);
                    continue;
                }
                boolean install = false;
                try {
                    Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
                    mainIntent.setPackage(component[0]);
                    List<ResolveInfo> infos = pm.queryIntentActivities(mainIntent, 0);
                    for (ResolveInfo info : infos) {
                        if (info.activityInfo == null)
                            continue;
                        if (info.activityInfo.name.equals(component[1])) {
                            install = true;
                            break;
                        }
                    }
                } catch (Exception ex) {
                    // ignore
                }
                if (install)
                    apps.add(app);
            }
        }
        lv = (DragSortListView) v.findViewById(R.id.apps);
        adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return apps.size();
            }

            @Override
            public Object getItem(int position) {
                return apps.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = convertView;
                if (v == null)
                    v = inflater.inflate(R.layout.quick_item, null);
                TextView tv = (TextView) v.findViewById(R.id.name);
                TextView tvNumber = (TextView) v.findViewById(R.id.number);
                ImageView ivIcon = (ImageView) v.findViewById(R.id.icon);
                tvNumber.setVisibility(View.GONE);
                try {
                    String name = apps.get(position);
                    String[] component = name.split("/");
                    if (component[0].equals("tel")) {
                        tv.setText(component[1]);
                        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(component[1]));
                        ContentResolver contentResolver = getActivity().getContentResolver();
                        Cursor contactLookup = contentResolver.query(uri, new String[]{BaseColumns._ID,
                                ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);
                        ivIcon.setImageResource(R.drawable.call_contact);
                        try {
                            if (contactLookup != null && contactLookup.getCount() > 0) {
                                contactLookup.moveToNext();
                                tv.setText(contactLookup.getString(contactLookup.getColumnIndex(ContactsContract.Data.DISPLAY_NAME)));
                                tvNumber.setText(component[1]);
                                tvNumber.setVisibility(View.VISIBLE);
                                long contactId = contactLookup.getLong(contactLookup.getColumnIndex(BaseColumns._ID));
                                Uri contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId);
                                Uri photoUri = Uri.withAppendedPath(contactUri, ContactsContract.Contacts.Photo.CONTENT_DIRECTORY);
                                Cursor cursor = getActivity().getContentResolver().query(photoUri, new String[]{ContactsContract.Contacts.Photo.PHOTO}, null, null, null);
                                if (cursor != null) {
                                    try {
                                        if (cursor.moveToFirst()) {
                                            byte[] data = cursor.getBlob(0);
                                            if (data != null) {
                                                Bitmap photo = BitmapFactory.decodeStream(new ByteArrayInputStream(data));
                                                ivIcon.setImageBitmap(photo);
                                            }
                                        }
                                    } finally {
                                        cursor.close();
                                    }
                                }
                            }
                        } finally {
                            if (contactLookup != null) {
                                contactLookup.close();
                            }
                        }
                        return v;
                    }
                    Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
                    mainIntent.setPackage(component[0]);
                    List<ResolveInfo> infos = pm.queryIntentActivities(mainIntent, 0);
                    for (ResolveInfo info : infos) {
                        if (info.activityInfo == null)
                            continue;
                        if (info.activityInfo.name.equals(component[1])) {
                            tv.setText(info.loadLabel(pm));
                            try {
                                ivIcon.setImageDrawable(info.loadIcon(pm));
                                ivIcon.setVisibility(View.VISIBLE);
                            } catch (Exception ex) {
                                ivIcon.setVisibility(View.GONE);
                            }

                        }
                    }
                } catch (Exception ex) {
                    // ignore
                }
                return v;
            }
        };
        lv.setAdapter(adapter);
        lv.setDragSortListener(new DragSortListView.DragSortListener() {
            @Override
            public void drag(int from, int to) {

            }

            @Override
            public void drop(int from, int to) {
                String s = apps.get(from);
                apps.set(from, apps.get(to));
                apps.set(to, s);
                adapter.notifyDataSetChanged();
                saveValue();
            }

            @Override
            public void remove(final int position) {
                String name = apps.get(position);
                try {
                    String[] component = name.split("/");
                    Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
                    mainIntent.setPackage(component[0]);
                    List<ResolveInfo> infos = pm.queryIntentActivities(mainIntent, 0);
                    for (ResolveInfo info : infos) {
                        if (info.activityInfo == null)
                            continue;
                        if (info.activityInfo.name.equals(component[1])) {
                            name = info.loadLabel(pm).toString();
                            break;
                        }
                    }
                } catch (Exception ex) {
                    // ignore
                }
                AlertDialog dialog = new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.remove)
                        .setMessage(getString(R.string.remove) + " " + name + "?")
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                apps.remove(position);
                                saveValue();
                            }
                        }).create();
                dialog.show();
                dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        adapter.notifyDataSetChanged();
                    }
                });
            }
        });

        v.findViewById(R.id.add).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setTitle(R.string.add);
                builder.setView(inflater.inflate(R.layout.packages, null));
                builder.setNegativeButton(R.string.cancel, null);
                final AlertDialog dialog = builder.create();
                dialog.show();
                final ListView list = (ListView) dialog.findViewById(R.id.list);
                AsyncTask<Void, Void, Vector<ResolveInfo>> task = new AsyncTask<Void, Void, Vector<ResolveInfo>>() {

                    @Override
                    protected Vector<ResolveInfo> doInBackground(Void... params) {
                        final Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
                        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                        final List<ResolveInfo> pkgAppsList = pm.queryIntentActivities(mainIntent, 0);
                        final Vector<ResolveInfo> apps = new Vector<ResolveInfo>();

                        Vector<ResolveInfo> other = new Vector<ResolveInfo>();
                        for (ResolveInfo info : pkgAppsList) {
                            if (info.activityInfo == null)
                                continue;
                            if (info.activityInfo.packageName.equals("ru.yandex.yandexnavi"))
                                continue;
                            if (info.activityInfo.packageName.equals(State.cg))
                                continue;
                            if (info.activityInfo.packageName.equals(State.cn))
                                continue;
                            if (info.activityInfo.packageName.equals("ru.shutoff.cgstarter"))
                                continue;
                            other.add(info);
                        }

                        Intent intent = new Intent(Intent.ACTION_MAIN, null);
                        intent.setPackage("ru.shutoff.cgstarter");
                        List<ResolveInfo> infos = pm.queryIntentActivities(intent, 0);
                        for (ResolveInfo info : infos) {
                            if (info.activityInfo == null)
                                continue;
                            String name = info.activityInfo.name.substring(21);
                            if (name.equals("TrafficActivity") || name.equals("ContactActivity"))
                                apps.add(info);
                            if (name.equals("VoiceSearch") && VoiceSearch.isVoiceSearch(getActivity()))
                                apps.add(info);
                            if (State.hasTelephony(getActivity())) {
                                if (name.equals("SMSActivity") || name.equals("SendLocationActivity") || name.equals("CallContactActivity") || name.equals("LastCallActivity"))
                                    apps.add(info);
                            }
                        }

                        Collections.sort(other, new Comparator<ResolveInfo>() {
                            @Override
                            public int compare(ResolveInfo lhs, ResolveInfo rhs) {
                                String lTitle = lhs.loadLabel(pm).toString();
                                String rTitle = rhs.loadLabel(pm).toString();
                                return lTitle.compareTo(rTitle);
                            }
                        });
                        apps.addAll(other);
                        return apps;
                    }

                    @Override
                    protected void onPostExecute(final Vector<ResolveInfo> packages) {
                        list.setVisibility(View.VISIBLE);
                        dialog.findViewById(R.id.progress).setVisibility(View.GONE);
                        list.setAdapter(new BaseAdapter() {
                            @Override
                            public int getCount() {
                                return packages.size();
                            }

                            @Override
                            public Object getItem(int position) {
                                return packages.get(position);
                            }

                            @Override
                            public long getItemId(int position) {
                                return position;
                            }

                            @Override
                            public View getView(int position, View convertView, ViewGroup parent) {
                                View v = convertView;
                                if (v == null)
                                    v = inflater.inflate(R.layout.quick_item, null);
                                TextView tvName = (TextView) v.findViewById(R.id.name);
                                ResolveInfo info = packages.get(position);
                                tvName.setText(info.loadLabel(pm));
                                ImageView ivIcon = (ImageView) v.findViewById(R.id.icon);
                                try {
                                    ivIcon.setImageDrawable(info.loadIcon(pm));
                                    ivIcon.setVisibility(View.VISIBLE);
                                } catch (Exception ex) {
                                    ivIcon.setVisibility(View.GONE);
                                }
                                v.setTag(info.activityInfo);
                                return v;
                            }
                        });
                        list.setSelectionFromTop(app_index, app_top);
                    }
                };
                task.execute();
                list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        dialog.dismiss();
                        if (view.getTag() == null)
                            return;
                        ActivityInfo info = (ActivityInfo) view.getTag();
                        String name = info.packageName + "/" + info.name;
                        if (name.equals("ru.shutoff.cgstarter/ru.shutoff.cgstarter.CallContactActivity")) {
                            Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
                            startActivityForResult(intent, 1);
                            return;
                        }
                        apps.add(name);
                        adapter.notifyDataSetChanged();
                        lv.setSelection(apps.size() - 1);
                        saveValue();
                    }
                });
                dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        app_index = list.getFirstVisiblePosition();
                        View v = list.getChildAt(0);
                        app_top = (v == null) ? 0 : v.getTop();
                    }
                });
            }
        });
        return v;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if ((resultCode == Activity.RESULT_OK) && (requestCode == 1)) {
            final Vector<PhoneWithType> allNumbers = new Vector<PhoneWithType>();
            try {
                Uri result = data.getData();
                String id = result.getLastPathSegment();
                Cursor cursor = getActivity().getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=?", new String[]{id}, null);
                int phoneIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DATA);
                int typeIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DATA2);

                if (cursor.moveToFirst()) {
                    while (!cursor.isAfterLast()) {
                        PhoneWithType phone = new PhoneWithType();
                        phone.number = cursor.getString(phoneIdx);
                        phone.type = cursor.getInt(typeIdx);
                        allNumbers.add(phone);
                        cursor.moveToNext();
                    }
                }
            } catch (Exception ex) {
                // ignore
            }
            if (allNumbers.size() == 0) {
                Toast toast = Toast.makeText(getActivity(), R.string.no_phone, Toast.LENGTH_SHORT);
                toast.show();
                return;
            }
            if (allNumbers.size() == 1) {
                addPhoneNumber(allNumbers.get(0).number);
                return;
            }
            ListView list = new ListView(getActivity());
            list.setAdapter(new BaseAdapter() {
                @Override
                public int getCount() {
                    return allNumbers.size();
                }

                @Override
                public Object getItem(int position) {
                    return allNumbers.get(position);
                }

                @Override
                public long getItemId(int position) {
                    return position;
                }

                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    View v = convertView;
                    if (v == null) {
                        LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        v = inflater.inflate(R.layout.number_item, null);
                    }
                    TextView tvNumber = (TextView) v.findViewById(R.id.number);
                    tvNumber.setText(allNumbers.get(position).number);
                    TextView tvType = (TextView) v.findViewById(R.id.type);
                    String type = "";
                    switch (allNumbers.get(position).type) {
                        case ContactsContract.CommonDataKinds.Phone.TYPE_HOME:
                            type = getString(R.string.phone_home);
                            break;
                        case ContactsContract.CommonDataKinds.Phone.TYPE_WORK:
                            type = getString(R.string.phone_work);
                            break;
                        case ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE:
                            type = getString(R.string.phone_mobile);
                            break;
                    }
                    tvType.setText(type);
                    return v;
                }
            });
            final AlertDialog dialog = new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.select_phone)
                    .setNegativeButton(R.string.cancel, null)
                    .setView(list)
                    .create();
            dialog.show();
            list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    addPhoneNumber(allNumbers.get(position).number);
                    dialog.dismiss();
                }
            });
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);

    }

    void saveValue() {
        String res = null;
        for (String app : apps) {
            if (res == null) {
                res = app;
                continue;
            }
            res += ":" + app;
        }
        SharedPreferences.Editor ed = preferences.edit();
        if (res == null) {
            ed.remove(State.APPS);
        } else {
            ed.putString(State.APPS, res);
        }
        ed.commit();
    }

    void addPhoneNumber(String number) {
        apps.add("tel/" + number);
        adapter.notifyDataSetChanged();
        lv.setSelection(apps.size() - 1);
        saveValue();
    }

    static class PhoneWithType {
        String number;
        int type;
    }
}
