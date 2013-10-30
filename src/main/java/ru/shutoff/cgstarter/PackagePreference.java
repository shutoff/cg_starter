package ru.shutoff.cgstarter;

import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
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

public class PackagePreference
        extends DialogPreference
        implements View.OnClickListener {

    Set<String> selected_packages;

    public PackagePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected View onCreateDialogView() {
        LayoutInflater inflater = (LayoutInflater) getContext()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View v = inflater.inflate(R.layout.packages, null);
        if (selected_packages == null)
            selected_packages = new HashSet<String>();
        return v;
    }

    @Override
    protected void onBindDialogView(final View view) {
        final PackageManager pm = getContext().getPackageManager();
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
                ListView list = (ListView) view.findViewById(R.id.list);
                list.setVisibility(View.VISIBLE);
                view.findViewById(R.id.progress).setVisibility(View.GONE);
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
                            v = inflater.inflate(R.layout.package_item, null);
                        }
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
    }

    @Override
    protected void onSetInitialValue(boolean restore, Object defaultValue) {
        super.onSetInitialValue(restore, defaultValue);
        String value = "";
        if (restore)
            value = shouldPersist() ? getPersistedString((String) defaultValue) : "";
        else
            value = (String) defaultValue;
        selected_packages = new HashSet<String>();
        String[] values = value.split(":");
        for (String v : values)
            selected_packages.add(v);
    }

    @Override
    public void showDialog(Bundle state) {

        super.showDialog(state);
        Button positiveButton = ((AlertDialog) getDialog()).getButton(AlertDialog.BUTTON_POSITIVE);
        positiveButton.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        String value = null;
        for (String val : selected_packages) {
            if (value == null) {
                value = val;
            } else {
                value += ":" + val;
            }
        }
        if (shouldPersist())
            persistString(value);
        callChangeListener(value);
        ((AlertDialog) getDialog()).dismiss();
    }

    static class Package {
        String name;
        String title;
    }

}
