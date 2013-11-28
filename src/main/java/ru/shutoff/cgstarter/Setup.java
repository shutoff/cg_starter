package ru.shutoff.cgstarter;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;

import java.io.File;

public class Setup extends PreferenceActivity {

    SeekBarPreference autoPref;
    SeekBarPreference launchPref;
    ListPreference answerPref;
    ListPreference ringPref;
    ListPreference rotatePref;
    SharedPreferences prefs;
    CheckBoxPreference phoneShowPrefs;
    EditTextPreference smsPref;
    ListPreference startPref;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.setup);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        autoPref = (SeekBarPreference) findPreference(State.AUTO_PAUSE);
        autoPref.setMin(3);

        launchPref = (SeekBarPreference) findPreference(State.INACTIVE_PAUSE);
        launchPref.setMin(10);

        CheckBoxPreference carMode = (CheckBoxPreference) findPreference(State.CAR_MODE);
        carMode.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                SharedPreferences.Editor ed = preference.getEditor();
                ed.putBoolean(State.CAR_STATE, false);
                ed.commit();
                return true;
            }
        });

        Preference aboutPref = (Preference) findPreference(State.ABOUT);
        try {
            PackageManager pkgManager = getPackageManager();
            PackageInfo info = pkgManager.getPackageInfo("ru.shutoff.cgstarter", 0);
            aboutPref.setSummary(aboutPref.getSummary() + " " + info.versionName);
        } catch (Exception ex) {
            aboutPref.setSummary("");
        }
        aboutPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(getBaseContext(), WebViewActivity.class);
                intent.putExtra(State.URL, "file:///android_asset/html/about.html");
                startActivity(intent);
                return true;
            }
        });

        Preference donatePref = (Preference) findPreference(State.DONATE);
        donatePref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(getBaseContext(), WebViewActivity.class);
                intent.putExtra(State.URL, "file:///android_asset/html/donate.html");
                startActivity(intent);
                return true;
            }
        });

        PreferenceScreen screen = getPreferenceScreen();
        BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
        if (bt == null) {
            SharedPreferences.Editor ed = prefs.edit();
            screen.removePreference(findPreference(State.BT));
            ed.remove(State.BT);
            ed.commit();
        }

        Preference.OnPreferenceChangeListener listener = new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (newValue instanceof Boolean) {
                    SharedPreferences.Editor ed = prefs.edit();
                    ed.putBoolean(preference.getKey(), (Boolean) newValue);
                    ed.commit();
                    setupAnswerPref();
                    return true;
                }
                return false;
            }
        };

        CheckBoxPreference btPref = (CheckBoxPreference) findPreference(State.BT);
        btPref.setOnPreferenceChangeListener(listener);

        CheckBoxPreference speakerPref = (CheckBoxPreference) findPreference(State.SPEAKER);
        speakerPref.setOnPreferenceChangeListener(listener);

        answerPref = (ListPreference) findPreference(State.ANSWER_TIME);
        answerPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (newValue instanceof String) {
                    SharedPreferences.Editor ed = prefs.edit();
                    ed.putString(State.ANSWER_TIME, (String) newValue);
                    ed.commit();
                    setupAnswerPref();
                    return true;
                }
                return false;
            }
        });

        ringPref = (ListPreference) findPreference(State.RINGING_TIME);
        ringPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (newValue instanceof String) {
                    SharedPreferences.Editor ed = prefs.edit();
                    ed.putString(State.RINGING_TIME, (String) newValue);
                    ed.commit();
                    setupRingPref();
                    return true;
                }
                return false;
            }
        });

        phoneShowPrefs = (CheckBoxPreference) findPreference(State.PHONE_SHOW);
        CheckBoxPreference phonePrefs = (CheckBoxPreference) findPreference(State.PHONE);
        phonePrefs.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (newValue instanceof Boolean) {
                    Boolean v = (Boolean) newValue;
                    phoneShowPrefs.setEnabled(v);
                    return true;
                }
                return false;
            }
        });
        phoneShowPrefs.setEnabled(prefs.getBoolean(State.PHONE, false));

        CheckBoxPreference rtaPref = (CheckBoxPreference) findPreference(State.RTA_LOGS);
        File rta_ini = Environment.getExternalStorageDirectory();
        rta_ini = new File(rta_ini, "CityGuide/rtlog.ini");
        if (!rta_ini.exists()) {
            PreferenceGroup phoneGorup = (PreferenceGroup) findPreference("power_group");
            phoneGorup.removePreference(rtaPref);
        }

        rotatePref = (ListPreference) findPreference(State.ORIENTATION);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB)
            rotatePref.setEntries(R.array.rotate_entries2);
        rotatePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (newValue instanceof String) {
                    SharedPreferences.Editor ed = prefs.edit();
                    ed.putString(State.ORIENTATION, (String) newValue);
                    ed.commit();
                    setupRotatePref();
                    return true;
                }
                return false;
            }
        });

        smsPref = (EditTextPreference) findPreference(State.SMS);
        smsPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (newValue instanceof String) {
                    smsPref.setSummary((String) newValue);
                    return true;
                }
                return false;
            }
        });
        smsPref.setSummary(prefs.getString(State.SMS, getString(R.string.def_sms)));

        PackageManager pm = getPackageManager();
        PackageInfo info = null;

        try {
            info = pm.getPackageInfo("info.mapcam.droid", 0);
        } catch (Exception ex) {
            // ignore
        }
        if (info == null) {
            PreferenceGroup phoneGorup = (PreferenceGroup) findPreference("phone_group");
            phoneGorup.removePreference(findPreference(State.MAPCAM));
        }

        info = null;
        try {
            info = pm.getPackageInfo("com.ivolk.StrelkaGPS", 0);
        } catch (Exception ex) {
            // ignore
        }
        if (info == null) {
            PreferenceGroup phoneGorup = (PreferenceGroup) findPreference("phone_group");
            phoneGorup.removePreference(findPreference(State.STRELKA));
        }

        startPref = (ListPreference) findPreference("start_point");
        Bookmarks.Point[] poi = Bookmarks.get();

        String[] values = new String[poi.length];
        String[] entries = new String[poi.length];
        String start_point = prefs.getString(State.START_POINT, "");
        for (int i = 0; i < values.length; i++) {
            entries[i] = poi[i].name;
            values[i] = poi[i].lat + "|" + poi[i].lng;
        }
        startPref.setEntries(entries);
        startPref.setEntryValues(values);
        startPref.setDefaultValue(start_point);

        setupRotatePref();
        setupAnswerPref();
        setupRingPref();
    }

    void setupAnswerPref() {
        answerPref.setEnabled(prefs.getBoolean(State.BT, false) || prefs.getBoolean(State.SPEAKER, false));
        String answer = prefs.getString(State.ANSWER_TIME, "0");
        String[] entries = getResources().getStringArray(R.array.answer_times);
        String[] values = getResources().getStringArray(R.array.times);
        for (int i = 0; i < entries.length; i++) {
            if (entries[i].equals(answer)) {
                answerPref.setSummary(values[i]);
                break;
            }
        }
    }

    void setupRingPref() {
        String answer = prefs.getString(State.RINGING_TIME, "-1");
        String[] entries = getResources().getStringArray(R.array.ring_times_value);
        String[] values = getResources().getStringArray(R.array.ring_times);
        for (int i = 0; i < entries.length; i++) {
            if (entries[i].equals(answer)) {
                ringPref.setSummary(values[i]);
                break;
            }
        }
    }

    void setupRotatePref() {
        String rotate = prefs.getString(State.ORIENTATION, "0");
        String[] entries = getResources().getStringArray(R.array.rotate_entries);
        String[] values = getResources().getStringArray(R.array.rotate_value);
        for (int i = 0; i < entries.length; i++) {
            if (values[i].equals(rotate)) {
                rotatePref.setSummary(entries[i]);
                break;
            }
        }
    }
}
