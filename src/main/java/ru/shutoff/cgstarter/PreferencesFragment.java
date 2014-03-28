package ru.shutoff.cgstarter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

public class PreferencesFragment extends Fragment {

    SharedPreferences preferences;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    void setCheckBox(View v, int id, final String key, boolean defValue) {
        CheckBox checkBox = (CheckBox) v.findViewById(id);
        checkBox.setChecked(preferences.getBoolean(key, defValue));
        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SharedPreferences.Editor ed = preferences.edit();
                ed.putBoolean(key, isChecked);
                ed.commit();
            }
        });
    }

    void setCheckBox(View v, int id, final String key) {
        setCheckBox(v, id, key, false);
    }

    void setCheckBoxSU(View v, int id, final String key) {
        final CheckBox checkBox = (CheckBox) v.findViewById(id);
        checkBox.setChecked(preferences.getBoolean(key, false));
        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (!isChecked) {
                    SharedPreferences.Editor ed = preferences.edit();
                    ed.putBoolean(key, isChecked);
                    ed.commit();
                    return;
                }
                final AlertDialog dialog = new AlertDialog.Builder(getActivity())
                        .setTitle(getString(R.string.require_su))
                        .setMessage(getString(R.string.require_su_msg))
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (State.doRoot(getActivity(), "", true)) {
                                    checkBox.setChecked(true);
                                    SharedPreferences.Editor ed = preferences.edit();
                                    ed.putBoolean(key, true);
                                    ed.commit();
                                }
                                dialog.dismiss();
                            }
                        })
                        .create();
                dialog.show();
                dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        checkBox.setChecked(preferences.getBoolean(key, false));
                    }
                });
            }
        });
    }

    void setCheckBox(final View v, int id, final String key, final int id_enable) {
        CheckBox checkBox = (CheckBox) v.findViewById(id);
        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SharedPreferences.Editor ed = preferences.edit();
                ed.putBoolean(key, isChecked);
                ed.commit();
                v.findViewById(id_enable).setEnabled(isChecked);
            }
        });
        boolean isChecked = preferences.getBoolean(key, false);
        checkBox.setChecked(isChecked);
        v.findViewById(id_enable).setEnabled(isChecked);
    }

    void setSeekBar(View v, int id, final String key, int defValue) {
        SeekBar seekBar = (SeekBar) v.findViewById(id);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                SharedPreferences.Editor ed = preferences.edit();
                ed.putInt(key, progress);
                ed.commit();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        int progress = preferences.getInt(key, defValue);
        seekBar.setProgress(progress);
    }

    void setSeekBar(View v, int id, int id_text, final int id_msg, final String key, int defValue, final int minValue) {
        final TextView tvTime = (TextView) v.findViewById(id_text);
        SeekBar seekBar = (SeekBar) v.findViewById(id);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                progress += minValue;
                SharedPreferences.Editor ed = preferences.edit();
                ed.putInt(key, progress);
                ed.commit();
                tvTime.setText(getString(id_msg).replaceAll("%1", progress + ""));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        int progress = preferences.getInt(key, defValue);
        seekBar.setProgress(progress - minValue);
        tvTime.setText(getString(id_msg).replaceAll("%1", progress + ""));
    }

    void setSpinner(View v, int id, int id_entries, int id_values, final String key, String defValue) {
        Spinner spinner = (Spinner) v.findViewById(id);
        final String[] entries = getResources().getStringArray(id_entries);
        final String[] values = getResources().getStringArray(id_values);
        int len = entries.length;
        if (len > values.length)
            len = values.length;
        final int length = len;
        spinner.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return length;
            }

            @Override
            public Object getItem(int position) {
                return values[position];
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
                tv.setText(values[position]);
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
                tv.setText(values[position]);
                return v;
            }
        });
        String value = preferences.getString(key, defValue);
        for (int i = 0; i < entries.length; i++) {
            if (entries[i].equals(value)) {
                spinner.setSelection(i);
                break;
            }
        }
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                SharedPreferences.Editor ed = preferences.edit();
                ed.putString(key, entries[position]);
                ed.commit();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

}
