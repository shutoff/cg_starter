package ru.shutoff.cgstarter;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.mobeta.android.dslv.DragSortListView;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Vector;

public class AppsFragment extends Fragment {

    Vector<String> apps;
    SharedPreferences preferences;
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
        final DragSortListView lv = (DragSortListView) v.findViewById(R.id.apps);
        final BaseAdapter adapter = new BaseAdapter() {
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
                            if (name.equals("VoiceSearch") && VoiceSearch.isAvailable(getActivity()))
                                apps.add(info);
                            if (State.hasTelephony(getActivity())) {
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
                        apps.add(info.packageName + "/" + info.name);
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
}
