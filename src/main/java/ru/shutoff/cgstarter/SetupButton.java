package ru.shutoff.cgstarter;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

public class SetupButton extends PreferenceActivity {

    ListPreference itemsPref;
    EditTextPreference namePref;
    CheckBoxPreference autoPref;
    TimePreference intervalPref;
    ListPreference daysPref;
    DaysListPreference weekPref;

    int id;
    State.Point point;

    SharedPreferences preferences;

    Intent intent;

    @Override
    public void onCreate(Bundle savedInstanceState) {

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        State.Point[] points = State.get(preferences);

        intent = getIntent();
        if (intent != null)
            id = intent.getIntExtra("ID", 0);
        if (id < 0)
            id = 0;
        if (id >= points.length)
            id = 0;
        point = points[id];

        SharedPreferences.Editor ed = preferences.edit();
        ed.putString("item", point.original);
        ed.putString("name", point.name);
        ed.putString("weekdays", "");
        if (point.days == 0)
            point.interval = "";
        if (point.interval.equals("")) {
            ed.putBoolean("auto", false);
            ed.putString("period", "00:00-00:00");
            ed.putString("days", "0");
        } else {
            ed.putBoolean("auto", true);
            ed.putString("period", point.interval);
            if ((point.days & (State.HOLIDAYS | State.WORKDAYS)) == (State.HOLIDAYS | State.WORKDAYS)) {
                ed.putString("days", "0");
            } else if ((point.days & State.WORKDAYS) == State.WORKDAYS) {
                ed.putString("days", "1");
            } else if ((point.days & State.WORKDAYS) == State.WORKDAYS) {
                ed.putString("days", "2");
            } else if (point.days == 0) {
                ed.putString("days", "0");
            } else {
                ed.putString("days", "3");
                String weekdays = null;
                for (int i = 0; i < 7; i++){
                    if ((point.days & (1 << (i + 2))) == 0)
                        continue;
                    if (weekdays == null){
                        weekdays = i + "";
                        continue;
                    }
                    weekdays += DaysListPreference.DEFAULT_SEPARATOR + i;
                }
                ed.putString("weekdays", weekdays);
            }
        }
        ed.commit();

        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.button);

        itemsPref = (ListPreference) findPreference("item");
        namePref = (EditTextPreference) findPreference("name");
        autoPref = (CheckBoxPreference) findPreference("auto");
        intervalPref = (TimePreference) findPreference("period");
        daysPref = (ListPreference) findPreference("days");
        weekPref = (DaysListPreference) findPreference("weekdays");

        setupInterval();

        Bookmarks.Point[] poi = Bookmarks.get();

        String[] values = new String[poi.length];
        for (int i = 0; i < values.length; i++) {
            values[i] = poi[i].name;
        }
        itemsPref.setEntries(values);
        itemsPref.setEntryValues(values);
        itemsPref.setDefaultValue(point.original);
        itemsPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (newValue instanceof String) {
                    String v = (String) newValue;
                    namePref.setText(v);
                    namePref.setSummary(v);
                    itemsPref.setSummary(v);
                    point.original = v;
                    point.name = v;
                    SharedPreferences.Editor ed = preferences.edit();
                    ed.putString("name", v);
                    ed.commit();
                    boolean not_empty = !v.equals("");
                    namePref.setEnabled(not_empty);
                    setupInterval();
                    return true;
                }
                return false;
            }
        });
        if (point.name.equals("")) {
            itemsPref.setSummary(getString(R.string.item_sum));
            namePref.setSummary(getString(R.string.name_sum));
            namePref.setEnabled(false);
        } else {
            itemsPref.setSummary(point.original);
            namePref.setSummary(point.name);
        }

        namePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (newValue instanceof String) {
                    String v = (String) newValue;
                    point.name = v;
                    namePref.setSummary(v);
                    return true;
                }
                return false;
            }
        });

        autoPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (newValue instanceof Boolean) {
                    Boolean v = (Boolean) newValue;
                    SharedPreferences.Editor ed = preferences.edit();
                    ed.putBoolean("auto", v);
                    ed.commit();
                    setupInterval();
                    return true;
                }
                return false;
            }
        });

        intervalPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (newValue instanceof String) {
                    String v = (String) newValue;
                    point.interval = v;
                    intervalPref.setSummary(v);
                    return true;
                }
                return false;
            }
        });

        daysPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (newValue instanceof String) {
                    switch (Integer.parseInt((String) newValue)) {
                        case 0:
                            point.days = State.WORKDAYS | State.HOLIDAYS;
                            break;
                        case 1:
                            point.days = State.WORKDAYS;
                            break;
                        case 2:
                            point.days = State.HOLIDAYS;
                            break;
                        default:
                            point.days = (point.days & 0x1FC);
                            if (point.days == 0)
                                point.days = 0x1FC;
                    }
                    setupInterval();
                    return true;
                }
                return false;
            }
        });

        weekPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (newValue instanceof String[]) {
                    String[] v = (String[])newValue;
                    point.days = 0;
                    for (String p : v) {
                        point.days |= (1 << (Integer.parseInt(p) + 2));
                    }
                    setupInterval();
                    return true;
                }
                return false;
            }
        });

        findPreference("clear").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                point.name = "";
                point.original = "";
                finish();
                return true;
            }
        });

    }

    @Override
    public void finish() {
        setResult(RESULT_OK, intent);
        super.finish();
    }

    void setupInterval() {
        if (preferences.getString("name", "").equals("")) {
            autoPref.setEnabled(false);
            intervalPref.setEnabled(false);
            intervalPref.setSummary(getString(R.string.interval_sum));
            daysPref.setEnabled(false);
            daysPref.setSummary(getString(R.string.days_sum));
            return;
        }
        autoPref.setEnabled(true);
        if (!preferences.getBoolean("auto", false)) {
            intervalPref.setEnabled(false);
            intervalPref.setSummary(getString(R.string.interval_sum));
            daysPref.setEnabled(false);
            daysPref.setSummary(getString(R.string.days_sum));
            return;
        }
        intervalPref.setEnabled(true);
        daysPref.setEnabled(true);
        weekPref.setEnabled(false);
        weekPref.setSummary(getString(R.string.days_sum));
        if (point.interval.equals(""))
            point.interval = "00:00-00:00";
        intervalPref.setSummary(point.interval);
        String[] days = getResources().getStringArray(R.array.days_list);
        String[] weekdays = getResources().getStringArray(R.array.weekdays_list);
        if (point.days == 0) {
            daysPref.setSummary(days[0]);
        } else if ((point.days & (State.HOLIDAYS | State.WORKDAYS)) == (State.HOLIDAYS | State.WORKDAYS)) {
            daysPref.setSummary(days[0]);
        } else if ((point.days & State.WORKDAYS) == State.WORKDAYS) {
            daysPref.setSummary(days[1]);
        } else if ((point.days & State.HOLIDAYS) == State.HOLIDAYS) {
            daysPref.setSummary(days[2]);
        } else {
            String sum = null;
            for (int i = 0; i < 7; i++) {
                if ((point.days & (1 << (i + 2))) == 0)
                    continue;
                if (sum == null) {
                    sum = weekdays[i];
                    continue;
                }
                sum += "," + weekdays[i];
            }
            daysPref.setSummary(sum);
            weekPref.setEnabled(true);
            weekPref.setSummary(sum);
        }
    }
}
