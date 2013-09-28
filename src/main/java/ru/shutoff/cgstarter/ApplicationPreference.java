package ru.shutoff.cgstarter;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

public class ApplicationPreference extends DialogPreference {

    ListView appList;
    final HashSet<String> selected;
    final PackageManager pm;

    public ApplicationPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        selected = new HashSet<String>();
        pm = getContext().getPackageManager();
    }

    @Override
    protected View onCreateDialogView() {
        appList = new ListView(getContext());
        return appList;
    }

    @Override
    protected void onBindDialogView(View view) {
        appList.setAdapter(new PackagesAdapter(getContext()));
        super.onBindDialogView(view);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        if (positiveResult) {
            String res = null;
            for (String name : selected) {
                if (res == null) {
                    res = name;
                } else {
                    res += "|" + name;
                }
            }
            if (res == null)
                res = "";
            if (callChangeListener(res))
                persistString(res);
            setSummary(summary(res));
        }
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        String value = "";
        if (restorePersistedValue)
            value = getPersistedString("");
        selected.clear();
        if (!value.equals("")) {
            String[] apps = value.split("\\|");
            for (String app : apps) {
                selected.add(app);
            }
        }
        setSummary(summary(value));
    }

    String summary(String value) {
        if (value.equals(""))
            return getContext().getString(R.string.launch_app_sum);
        String res = null;
        String[] apps = value.split("\\|");
        for (String app : apps) {
            try {
                ApplicationInfo info = pm.getApplicationInfo(app, 0);
                app = pm.getApplicationLabel(info).toString();
            } catch (Exception ex) {
            }
            if (res == null) {
                res = app;
            } else {
                res += ", " + app;
            }
        }
        return res;
    }

    static class AppInfo {
        String name;
        Drawable icon;
        String pkg_name;
    }

    ;

    class PackagesAdapter extends BaseAdapter {

        final ArrayList<AppInfo> apps;
        final LayoutInflater inflater;
        final CompoundButton.OnCheckedChangeListener selectedChangeListener;

        PackagesAdapter(Context context) {
            final Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> pkgAppsList = pm.queryIntentActivities(mainIntent, 0);
            apps = new ArrayList<AppInfo>();
            for (ResolveInfo info : pkgAppsList) {
                AppInfo appInfo = new AppInfo();
                appInfo.name = info.loadLabel(pm).toString();
                appInfo.icon = info.loadIcon(pm);
                appInfo.pkg_name = info.activityInfo.applicationInfo.packageName;
                apps.add(appInfo);
            }

            Collections.sort(apps, new Comparator<AppInfo>() {
                @Override
                public int compare(AppInfo lhs, AppInfo rhs) {
                    return lhs.name.compareToIgnoreCase(rhs.name);
                }
            });
            inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            selectedChangeListener = new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isChecked) {
                        selected.add(buttonView.getTag().toString());
                    } else {
                        selected.remove(buttonView.getTag().toString());
                    }
                }
            };
        }

        @Override
        public int getCount() {
            return apps.size();
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null)
                view = inflater.inflate(R.layout.app_item, parent, false);
            AppInfo info = apps.get(position);
            TextView name = (TextView) view.findViewById(R.id.name);
            name.setText(info.name);
            ImageView icon = (ImageView) view.findViewById(R.id.icon);
            icon.setImageDrawable(info.icon);
            CheckBox select = (CheckBox) view.findViewById(R.id.selected);
            select.setTag(info.pkg_name);
            select.setChecked(selected.contains(info.pkg_name));
            select.setOnCheckedChangeListener(selectedChangeListener);
            return view;
        }
    }

    ;
}
