package ru.shutoff.cgstarter;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Vector;

public class AppsPreference extends DialogPreference implements View.OnClickListener {

    Vector<String> apps;
    ListView lv;
    BaseAdapter adapter;

    public AppsPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected View onCreateDialogView() {
        final LayoutInflater layoutInflater = LayoutInflater.from(getContext());
        View view = layoutInflater.inflate(R.layout.apps, null);
        final PackageManager pm = getContext().getPackageManager();
        if (apps == null) {
            apps = new Vector<String>();
        } else {
            for (int pos = 0; pos < apps.size(); pos++) {
                boolean install = false;
                try {
                    String[] component = apps.get(pos).split("/");
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
                if (!install) {
                    apps.remove(pos);
                    pos--;
                }
            }
        }

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
                    v = layoutInflater.inflate(R.layout.quick_item, null);
                try {
                    String name = apps.get(position);
                    String[] component = name.split("/");
                    Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
                    mainIntent.setPackage(component[0]);
                    List<ResolveInfo> infos = pm.queryIntentActivities(mainIntent, 0);
                    for (ResolveInfo info : infos) {
                        if (info.activityInfo == null)
                            continue;
                        if (info.activityInfo.name.equals(component[1])) {
                            TextView tv = (TextView) v.findViewById(R.id.name);
                            tv.setText(info.loadLabel(pm));
                            ImageView ivIcon = (ImageView) v.findViewById(R.id.icon);
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
        lv = (ListView) view.findViewById(R.id.apps);
        lv.setAdapter(adapter);
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
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
                AlertDialog dialog = new AlertDialog.Builder(getContext())
                        .setTitle(R.string.remove)
                        .setMessage(getContext().getString(R.string.remove) + " " + name + "?")
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                apps.remove(position);
                                adapter.notifyDataSetChanged();
                            }
                        }).create();
                dialog.show();
            }
        });
        view.findViewById(R.id.add).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addApp();
            }
        });
        return view;
    }

    @Override
    protected void onSetInitialValue(boolean restore, Object defaultValue) {
        super.onSetInitialValue(restore, defaultValue);
        apps = new Vector<String>();
        String value = "";
        if (defaultValue != null)
            value = defaultValue.toString();
        if (restore)
            value = shouldPersist() ? getPersistedString(value) : "";
        String[] v = value.split(":");
        for (String app : v) {
            String[] component = app.split("/");
            if (component.length != 2)
                continue;
            apps.add(app);
        }
        if (adapter != null)
            adapter.notifyDataSetChanged();
    }

    @Override
    public void showDialog(Bundle state) {
        super.showDialog(state);
        Button button = ((AlertDialog) getDialog()).getButton(AlertDialog.BUTTON_POSITIVE);
        button.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        String res = null;
        for (String app : apps) {
            if (res == null) {
                res = app;
                continue;
            }
            res += ":" + app;
        }
        if (shouldPersist())
            persistString(res);
        ((AlertDialog) getDialog()).dismiss();
    }

    void addApp() {
        final LayoutInflater layoutInflater = LayoutInflater.from(getContext());
        final PackageManager pm = getContext().getPackageManager();
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(R.string.add);
        builder.setView(layoutInflater.inflate(R.layout.packages, null));
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
                    if (info.activityInfo.packageName.equals(State.CG_PACKAGE))
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
                    if (name.equals("VoiceSearch") && VoiceSearch.isAvailable(getContext()))
                        apps.add(info);
                    if (State.hasTelephony(getContext())) {
                        if (name.equals("SMSActivity") || name.equals("SendLocationActivity"))
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
                        if (v == null) {
                            LayoutInflater inflater = (LayoutInflater) getContext()
                                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                            v = inflater.inflate(R.layout.quick_item, null);
                        }
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
            }
        };
        task.execute();
        list.setOnItemClickListener(new AdapterView.OnItemClickListener()

        {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                dialog.dismiss();
                if (view.getTag() == null)
                    return;
                ActivityInfo info = (ActivityInfo) view.getTag();
                apps.add(info.packageName + "/" + info.name);
                adapter.notifyDataSetChanged();
                lv.setSelection(apps.size() - 1);
            }
        }

        );
    }
}
