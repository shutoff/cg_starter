package ru.shutoff.cgstarter;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Environment;
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

        File rta_ini = Environment.getExternalStorageDirectory();
        rta_ini = new File(rta_ini, "CityGuide/rtlog.ini");
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

        final Bookmarks.Point[] points = Bookmarks.get();
        Spinner start_point = (Spinner) v.findViewById(R.id.start_point);
        start_point.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return points.length;
            }

            @Override
            public Object getItem(int position) {
                return points[position];
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
                tv.setText(points[position].name);
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
                Bookmarks.Point p = points[position];
                ed.putString(State.START_POINT, p.lat + "|" + p.lng);
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
}
