package ru.shutoff.cgstarter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

public class NotificationFragment extends PreferencesFragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View v = inflater.inflate(R.layout.notification_setup, container, false);
        setCheckBox(v, R.id.show_sms, State.SHOW_SMS);
        if (isNotificationEnabled()) {
            v.findViewById(R.id.disable).setVisibility(View.GONE);
            String[] ignored = preferences.getString(State.NOTIFICATION_IGNORE, "").split(":");
            String res = null;
            PackageManager pm = getActivity().getPackageManager();
            for (String ignore : ignored) {
                try {
                    ApplicationInfo appInfo = pm.getApplicationInfo(ignore, 0);
                    String title = pm.getApplicationLabel(appInfo).toString();
                    if (res == null) {
                        res = title;
                    } else {
                        res += "\n" + title;
                    }
                } catch (Exception ex) {
                    // ignore
                }
            }
            final TextView tv = (TextView) v.findViewById(R.id.ignored);
            if (res != null)
                tv.setText(res);
            v.findViewById(R.id.enable).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setIgnore(tv);
                }
            });
        } else {
            v.findViewById(R.id.enable).setVisibility(View.GONE);
        }
        setSeekBar(v, R.id.time, R.id.time_msg, R.string.notification_time, State.NOTIFICATION, 10, 1);
        return v;
    }

    boolean isNotificationEnabled() {
        int accessibilityEnabled = 0;
        final String NOTIFICATION_SERVICE = "ru.shutoff.cgstarter/ru.shutoff.cgstarter.NotificationService";
        boolean accessibilityFound = false;
        try {
            accessibilityEnabled = Settings.Secure.getInt(getActivity().getContentResolver(), android.provider.Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException e) {
            // ignore
        }
        if (accessibilityEnabled == 1) {
            String settingValue = Settings.Secure.getString(getActivity().getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (settingValue != null) {
                TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
                splitter.setString(settingValue);
                while (splitter.hasNext()) {
                    String accessabilityService = splitter.next();
                    if (accessabilityService.equalsIgnoreCase(NOTIFICATION_SERVICE)) {
                        return true;
                    }
                }
            }
        }
        return accessibilityFound;
    }

    void setIgnore(final TextView tv) {
        final Set<String> selected_packages = new HashSet<String>();
        String[] ignored = preferences.getString(State.NOTIFICATION_IGNORE, "").split(":");
        for (String ignore : ignored) {
            selected_packages.add(ignore);
        }

        final LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
        final AlertDialog dialog = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.notification_ignore)
                .setView(inflater.inflate(R.layout.list, null))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, null)
                .create();
        dialog.show();
        final PackageManager pm = getActivity().getPackageManager();
        AsyncTask<Void, Void, Vector<Package>> task = new AsyncTask<Void, Void, Vector<Package>>() {
            @Override
            protected Vector<Package> doInBackground(Void... params) {
                List<PackageInfo> pkg_info = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);
                final Vector<Package> packages = new Vector<Package>();
                for (PackageInfo info : pkg_info) {
                    Package p = new Package();
                    p.name = info.packageName;
                    p.title = info.packageName;
                    try {
                        ApplicationInfo appInfo = pm.getApplicationInfo(p.name, 0);
                        p.title = pm.getApplicationLabel(appInfo).toString();
                    } catch (Exception ex) {
                        // ignore
                    }
                    packages.add(p);
                }
                Collections.sort(packages, new Comparator<Package>() {
                    @Override
                    public int compare(Package lhs, Package rhs) {
                        return lhs.title.compareTo(rhs.title);
                    }
                });
                return packages;
            }

            @Override
            protected void onPostExecute(final Vector<Package> packages) {
                ListView list = (ListView) dialog.findViewById(R.id.list);
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
                            v = inflater.inflate(R.layout.package_item, null);
                        TextView tvName = (TextView) v.findViewById(R.id.name);
                        Package p = packages.get(position);
                        tvName.setText(p.title);
                        ImageView ivIcon = (ImageView) v.findViewById(R.id.icon);
                        try {
                            ivIcon.setImageDrawable(pm.getApplicationIcon(packages.get(position).name));
                            ivIcon.setVisibility(View.VISIBLE);
                        } catch (Exception ex) {
                            ivIcon.setVisibility(View.GONE);
                        }
                        CheckBox check = (CheckBox) v.findViewById(R.id.checked);
                        check.setTag(p.name);
                        check.setChecked(selected_packages.contains(p.name));
                        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                            @Override
                            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                                String name = (String) buttonView.getTag();
                                if (isChecked) {
                                    selected_packages.add(name);
                                } else {
                                    selected_packages.remove(name);
                                }

                            }
                        });
                        v.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                CheckBox check = (CheckBox) v.findViewById(R.id.checked);
                                check.setChecked(!check.isChecked());
                            }
                        });
                        return v;
                    }
                });
            }
        };
        task.execute();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String value = null;
                String res = null;
                for (String val : selected_packages) {
                    if (value == null) {
                        value = val;
                    } else {
                        value += ":" + val;
                    }
                    try {
                        ApplicationInfo appInfo = pm.getApplicationInfo(val, 0);
                        String title = pm.getApplicationLabel(appInfo).toString();
                        if (res == null) {
                            res = title;
                        } else {
                            res += "\n" + title;
                        }
                    } catch (Exception ex) {
                        // ignore
                    }
                }
                if (value == null)
                    value = "";
                if (res == null)
                    res = "";
                tv.setText(res);
                SharedPreferences.Editor ed = preferences.edit();
                ed.putString(State.NOTIFICATION_IGNORE, value);
                ed.commit();
                dialog.dismiss();
            }
        });
    }

    static class Package {
        String name;
        String title;
    }
}
