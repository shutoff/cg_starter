package ru.shutoff.cgstarter;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

public class SetupButton extends PreferenceActivity {

    ListPreference itemsPref;
    EditTextPreference namePref;
    TimePreference intervalPref;
    DaysPreference daysPref;
    Preference clearPref;

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
        if (point.days == 0)
            point.interval = "";
        if (point.interval.equals("")) {
            ed.putString("period", "00:00-00:00");
            ed.putInt("days", 0);
        } else {
            ed.putString("period", point.interval);
            ed.putInt("days", point.days);
        }
        ed.commit();

        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.button);

        itemsPref = (ListPreference) findPreference("item");
        namePref = (EditTextPreference) findPreference("name");
        intervalPref = (TimePreference) findPreference("period");
        daysPref = (DaysPreference) findPreference("days");
        clearPref = findPreference("clear");

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
                    Bookmarks.Point[] points = Bookmarks.get();
                    for (Bookmarks.Point p : points) {
                        if (p.name.equals(v)) {
                            point.lat = p.lat + "";
                            point.lng = p.lng + "";
                            point.original = v;
                            point.name = v;
                            point.points = p.points;
                            SharedPreferences.Editor ed = preferences.edit();
                            ed.putString("name", v);
                            ed.commit();
                            boolean not_empty = !v.equals("");
                            namePref.setEnabled(not_empty);
                            setupInterval();
                            return true;
                        }
                    }
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
                    if (point.days == 0)
                        point.days = State.ALLDAYS;
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
                    if (v.equals("") || v.equals("00:00-00:00")) {
                        daysPref.setEnabled(false);
                    } else {
                        daysPref.setEnabled(true);
                    }
                    point.interval = v;
                    if (point.days == 0)
                        point.days = State.ALLDAYS;
                    return true;
                }
                return false;
            }
        });

        daysPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (newValue instanceof Integer) {
                    preference.setSummary(DaysPreference.getSummary(preference.getContext(), (Integer) newValue));
                    point.days = (Integer) newValue;
                    return true;
                }
                return false;
            }
        });

        clearPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
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
            intervalPref.setEnabled(false);
            intervalPref.setSummary(getString(R.string.interval_sum));
            daysPref.setEnabled(false);
            clearPref.setEnabled(false);
            return;
        }
        clearPref.setEnabled(true);
        intervalPref.setEnabled(true);
        String v = point.interval;
        if (v.equals("") || v.equals("00:00-00:00")) {
            daysPref.setEnabled(false);
        } else {
            daysPref.setEnabled(true);
        }
        daysPref.setSummary(DaysPreference.getSummary(this, preferences.getInt("days", 0)));
    }
}
