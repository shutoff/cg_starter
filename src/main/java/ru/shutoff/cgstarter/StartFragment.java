package ru.shutoff.cgstarter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Xml;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

public class StartFragment extends PreferencesFragment {

    SensorManager sensorManager;
    Sensor sensorAccelerometer;
    Sensor sensorMagnetic;
    SensorEventListener sensorEventListener;

    float[] gravity;
    float[] magnetic;
    float[] orientation;

    TextView tvVert;
    int vert_id;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View v = inflater.inflate(R.layout.start, container, false);
        setSeekBar(v, R.id.auto_pause, R.id.auto_pause_text, R.string.auto_pause, State.AUTO_PAUSE, 5, 2);
        setSeekBar(v, R.id.inactive_pause, R.id.inactive_pause_text, R.string.inactive_pause, State.INACTIVE_PAUSE, 60, 5);
        setSpinner(v, R.id.inactive_mode, R.array.inactive_entries, R.array.inactive_values, State.INACTIVE_MODE, "0");
        setCheckBox(v, R.id.carmode, State.CAR_MODE);
        setCheckBoxSU(v, R.id.kill_car, State.KILL_CAR);
        setCheckBoxSU(v, R.id.kill_power, State.KILL_POWER);
        setCheckBox(v, R.id.vertical, State.VERTICAL, true);
        setCheckBox(v, R.id.mapcam, State.MAPCAM);
        setCheckBox(v, R.id.strelka, State.STRELKA);
        setCheckBox(v, R.id.remove_rta, State.RTA_LOGS);

        State.CG_Package(getActivity());
        if (!State.is_cg || !State.is_cn) {
            v.findViewById(R.id.cg_run).setVisibility(View.GONE);
            v.findViewById(R.id.cg_app).setVisibility(View.GONE);
        } else {
            Spinner spinner = (Spinner) v.findViewById(R.id.cg_app);
            final String[] names = new String[2];
            try {
                PackageManager pm = getActivity().getPackageManager();
                PackageInfo pi = pm.getPackageInfo(State.cg, 0);
                names[0] = pi.packageName;
                ApplicationInfo appInfo = pm.getApplicationInfo(pi.packageName, 0);
                names[0] = pm.getApplicationLabel(appInfo).toString();
                pi = pm.getPackageInfo(State.cn, 0);
                names[1] = pi.packageName;
                appInfo = pm.getApplicationInfo(pi.packageName, 0);
                names[1] = pm.getApplicationLabel(appInfo).toString();
            } catch (Exception ex) {
                // ignore
            }
            spinner.setAdapter(new BaseAdapter() {
                @Override
                public int getCount() {
                    return 2;
                }

                @Override
                public Object getItem(int position) {
                    return names[position];
                }

                @Override
                public long getItemId(int position) {
                    return position;
                }

                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    View v = convertView;
                    if (v == null) {
                        LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
                        v = inflater.inflate(R.layout.item, null);
                    }
                    TextView tv = (TextView) v.findViewById(R.id.name);
                    tv.setText(names[position]);
                    return v;
                }

                @Override
                public View getDropDownView(int position, View convertView, ViewGroup parent) {
                    View v = convertView;
                    if (v == null) {
                        LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
                        v = inflater.inflate(R.layout.dropdown_item, null);
                    }
                    TextView tv = (TextView) v.findViewById(R.id.name);
                    tv.setText(names[position]);
                    return v;
                }
            });
            spinner.setSelection(preferences.getString("cg_app", "").equals(State.cn) ? 1 : 0);
            spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    SharedPreferences.Editor ed = preferences.edit();
                    ed.putString("cg_app", (position == 1) ? State.cn : State.cg);
                    ed.commit();
                    State.cg_package = null;
                    getActivity().sendBroadcast(new Intent(MainActivity.CHANGE_APP));
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {

                }
            });
        }

        File rta_ini = State.CG_Folder(getActivity());
        rta_ini = new File(rta_ini, "rtlog.ini");
        if (!rta_ini.exists())
            v.findViewById(R.id.rta).setVisibility(View.GONE);

        PackageManager pm = getActivity().getPackageManager();
        PackageInfo info = null;

        try {
            info = pm.getPackageInfo("info.mapcam.droid", 0);
        } catch (Exception ex) {
            // ignore
        }
        if (info == null)
            v.findViewById(R.id.mapcam).setVisibility(View.GONE);

        info = null;
        try {
            info = pm.getPackageInfo("com.ivolk.StrelkaGPS", 0);
        } catch (Exception ex) {
            // ignore
        }
        if (info == null)
            v.findViewById(R.id.strelka).setVisibility(View.GONE);

        info = null;
        boolean nolock = false;
        boolean nav = false;
        try {
            info = pm.getPackageInfo("de.robv.android.xposed.installer", 0);
        } catch (Exception ex) {
            // ignore
        }
        if (info != null) {
            info = null;
            try {
                info = pm.getPackageInfo("com.smartmadsoft.xposed.nolockhome", 0);
            } catch (Exception ex) {
                // ignore
            }
            if (info == null)
                nolock = true;
            info = null;
            try {
                info = pm.getPackageInfo("ru.shutoff.routeselect", 0);
            } catch (Exception ex) {
                // ignore
            }
            if (info == null)
                nav = true;
        }
        if (nolock) {
            v.findViewById(R.id.xposed_lock).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    install("nolockhome");
                }
            });
        } else {
            v.findViewById(R.id.xposed_lock).setVisibility(View.GONE);
        }
        if (nav) {
            v.findViewById(R.id.xposed_route).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    install("routeselect");
                }
            });
        } else {
            v.findViewById(R.id.xposed_route).setVisibility(View.GONE);
        }

        if (!State.can_root) {
            v.findViewById(R.id.kill_car_block).setVisibility(View.GONE);
            v.findViewById(R.id.kill_power_block).setVisibility(View.GONE);
        }

        final TextView power_time = (TextView) v.findViewById(R.id.powertime);
        XmlPullParser parser = getResources().getXml(R.xml.power_time);
        AttributeSet attributes = Xml.asAttributeSet(parser);
        final TimePreference power_pref = new TimePreference(getActivity(), attributes) {
            @Override
            public void setSummary(CharSequence summary) {
                power_time.setText(summary);
            }

            @Override
            protected boolean persistString(String value) {
                SharedPreferences.Editor ed = preferences.edit();
                ed.putString(State.POWER_TIME, value);
                ed.commit();
                return true;
            }
        };
        power_pref.onSetInitialValue(false, preferences.getString(State.POWER_TIME, ""));
        v.findViewById(R.id.power_block).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                power_pref.showDialog();
            }
        });
        tvVert = (TextView) v.findViewById(R.id.sensor_vertical);

        final Bookmarks.Point[] points = Bookmarks.get(getActivity());
        Spinner start_point = (Spinner) v.findViewById(R.id.start_point);
        start_point.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return points.length + 1;
            }

            @Override
            public Object getItem(int position) {
                if (position > 0)
                    return points[position];
                return null;
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = convertView;
                if (v == null) {
                    LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
                    v = inflater.inflate(R.layout.item, null);
                }
                TextView tv = (TextView) v.findViewById(R.id.name);
                if (position > 0) {
                    tv.setText(points[position - 1].name);
                } else {
                    tv.setText(R.string.last_position);
                }
                return v;
            }
        });
        String point = preferences.getString(State.START_POINT, "");
        for (int i = 0; i < points.length; i++) {
            Bookmarks.Point p = points[i];
            if (point.equals(p.lat + "|" + p.lng)) {
                start_point.setSelection(i);
                break;
            }
        }
        start_point.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                SharedPreferences.Editor ed = preferences.edit();
                if (position > 0) {
                    Bookmarks.Point p = points[position - 1];
                    ed.putString(State.START_POINT, p.lat + "|" + p.lng);
                } else {
                    ed.putString(State.START_POINT, "-");
                }
                ed.commit();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        return v;
    }

    @Override
    public void onStart() {
        super.onStart();
        sensorManager = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
        sensorAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorMagnetic = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        sensorEventListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
                    magnetic = event.values;
                if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
                    gravity = event.values;
                if ((gravity == null) || (magnetic == null))
                    return;
                float[] fR = new float[9];
                float[] fI = new float[9];
                if (!SensorManager.getRotationMatrix(fR, fI, gravity, magnetic))
                    return;
                if (orientation == null)
                    orientation = new float[3];
                SensorManager.getOrientation(fR, orientation);
                int new_id = R.string.sensor_vertical;
                if ((Math.abs(orientation[1]) + Math.abs(orientation[2])) < 1)
                    new_id = R.string.sensor_horizontal;
                if (new_id != vert_id) {
                    vert_id = new_id;
                    if (tvVert != null)
                        tvVert.setText(vert_id);
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {

            }
        };
        sensorManager.registerListener(sensorEventListener, sensorAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(sensorEventListener, sensorMagnetic, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onStop() {
        super.onStop();
        sensorManager.unregisterListener(sensorEventListener);
    }

    public void install(String name) {
        InputStream in = null;
        OutputStream out = null;

        AssetManager assetManager = getActivity().getAssets();

        try {
            String apk = name + ".apk";
            in = assetManager.open(apk);

            out = getActivity().openFileOutput(apk, Context.MODE_WORLD_READABLE);

            byte[] buffer = new byte[1024];

            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }

            in.close();

            out.flush();
            out.close();

            Intent intent = new Intent(Intent.ACTION_VIEW);

            intent.setDataAndType(Uri.fromFile(getActivity().getFileStreamPath(apk)),
                    "application/vnd.android.package-archive");

            startActivity(intent);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
