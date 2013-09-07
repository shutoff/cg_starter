package ru.shutoff.cgstarter;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.telephony.TelephonyManager;

public class Setup extends PreferenceActivity {

    SeekBarPreference autoPref;
    SeekBarPreference launchPref;
    SeekBarPreference levelPref;
    CheckBoxPreference powerStartPref;
    ListPreference answerPref;
    SharedPreferences prefs;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.setup);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        autoPref = (SeekBarPreference) findPreference(State.AUTO_PAUSE);
        autoPref.setMin(3);
        autoPref.setSummary(prefs.getInt(State.AUTO_PAUSE, 5) + " " + getString(R.string.sec));
        autoPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                autoPref.setSummary(String.valueOf(newValue) + " " + getString(R.string.sec));
                return true;
            }
        });

        launchPref = (SeekBarPreference) findPreference(State.LAUNCH_PAUSE);
        launchPref.setMin(10);
        launchPref.setSummary(prefs.getInt(State.LAUNCH_PAUSE, 30) + " " + getString(R.string.sec));
        launchPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                launchPref.setSummary(String.valueOf(newValue) + " " + getString(R.string.sec));
                return true;
            }
        });

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

        CheckBoxPreference powerMode = (CheckBoxPreference) findPreference(State.POWER_MODE);
        powerMode.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (newValue instanceof Boolean) {
                    SharedPreferences.Editor ed = preference.getEditor();
                    ed.putBoolean(State.POWER_STATE, false);
                    ed.putBoolean(State.POWER_MODE, (Boolean) newValue);
                    ed.commit();
                    setupPowerStart();
                }
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
                Intent intent = new Intent(getBaseContext(), About.class);
                startActivity(intent);
                return true;
            }
        });

        levelPref = (SeekBarPreference) findPreference(State.LEVEL);
        levelPref.setEnabled(prefs.getBoolean(State.VOLUME, false));

        CheckBoxPreference volumePref = (CheckBoxPreference) findPreference(State.VOLUME);
        volumePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (newValue instanceof Boolean){
                    levelPref.setEnabled((Boolean)newValue);
                    return true;
                }
                return false;
            }
        });

        PreferenceScreen screen = getPreferenceScreen();
        BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
        if (bt == null){
            SharedPreferences.Editor ed = prefs.edit();
            screen.removePreference(findPreference(State.BT));
            ed.remove(State.BT);
            ed.commit();
        }
        TelephonyManager tm = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
        if ((tm != null) && (tm.getPhoneType() == TelephonyManager.PHONE_TYPE_NONE))
            tm = null;
        if (tm == null){
            screen.removePreference(findPreference(State.PHONE));
            screen.removePreference(findPreference(State.DATA));
            screen.removePreference(findPreference(State.SPEAKER));
            SharedPreferences.Editor ed = prefs.edit();
            ed.remove(State.PHONE);
            ed.remove(State.DATA);
            ed.commit();
        } else {
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

            setupAnswerPref();
        }

        powerStartPref = (CheckBoxPreference) findPreference(State.POWER_START);
        powerStartPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (newValue instanceof Boolean) {
                    SharedPreferences.Editor ed = preference.getEditor();
                    ed.putBoolean(State.POWER_START, (Boolean) newValue);
                    ed.commit();
                    setupPowerStart();
                    return true;
                }
                return false;
            }
        });

        setupPowerStart();
    }

    void setupPowerStart() {
        powerStartPref.setEnabled(prefs.getBoolean(State.POWER_MODE, false));
        powerStartPref.setSummary(getString(
                prefs.getBoolean(State.POWER_START, false) ?
                        R.string.powerstart_sum : R.string.powerexit_sum));
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
}
