package ru.shutoff.cgstarter;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RadioGroup;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

public class DaysPreference extends DialogPreference {

    int value;

    RadioGroup days;
    boolean no_clear;

    CheckBox[] wd;

    public DaysPreference(Context ctxt, AttributeSet attrs) {
        super(ctxt, attrs);
        setPositiveButtonText(ctxt.getString(R.string.set));
        setNegativeButtonText(ctxt.getString(R.string.cancel));
    }

    @Override
    protected View onCreateDialogView() {
        LayoutInflater layoutInflater = LayoutInflater.from(getContext());
        View view = layoutInflater.inflate(R.layout.days, null);
        days = (RadioGroup) view.findViewById(R.id.days);
        wd = new CheckBox[7];
        String[] names = view.getContext().getResources().getStringArray(R.array.days);

        TableLayout table = (TableLayout) view.findViewById(R.id.week_table);
        TableRow tr1 = new TableRow(view.getContext());
        table.addView(tr1);
        TableRow tr2 = new TableRow(view.getContext());
        table.addView(tr2);

        CheckBox.OnCheckedChangeListener listener = new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (no_clear)
                    return;
                no_clear = true;
                days.check(R.id.weekdays);
                no_clear = false;
            }
        };
        for (int i = 0; i < 7; i++) {
            wd[i] = new CheckBox(view.getContext());
            wd[i].setOnCheckedChangeListener(listener);
            tr1.addView(wd[i]);
            TextView tv = new TextView(view.getContext());
            tv.setText(names[i]);
            tr2.addView(tv);
        }

        days.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if ((checkedId != R.id.weekdays) && !no_clear) {
                    no_clear = true;
                    clearDays();
                    no_clear = false;
                }
            }
        });
        return view;
    }

    @Override
    protected void onBindDialogView(View v) {
        if (value == 0)
            value = State.ALLDAYS;
        clearDays();
        if ((value & State.ALLDAYS) == State.ALLDAYS) {
            days.check(R.id.alldays);
        } else if ((value & State.WORKDAYS) == State.WORKDAYS) {
            days.check(R.id.workdays);
        } else if ((value & State.HOLIDAYS) == State.HOLIDAYS) {
            days.check(R.id.holidays);
        } else {
            days.check(R.id.weekdays);
            for (int i = 0; i < 7; i++) {
                int m = (1 << (i + 2));
                wd[i].setChecked((value & m) == m);
            }
        }
        super.onBindDialogView(v);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        if (positiveResult) {
            switch (days.getCheckedRadioButtonId()) {
                case R.id.alldays:
                    value = State.ALLDAYS;
                    break;
                case R.id.workdays:
                    value = State.WORKDAYS;
                    break;
                case R.id.holidays:
                    value = State.HOLIDAYS;
                    break;
                default:
                    value = 0;
                    for (int i = 0; i < 7; i++) {
                        if (wd[i].isChecked())
                            value |= (1 << (i + 2));
                    }
            }
            if (callChangeListener(value))
                persistInt(value);
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return (a.getString(index));
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        if (restoreValue) {
            if (defaultValue == null)
                defaultValue = 0;
            value = getPersistedInt((Integer) defaultValue);
        } else {
            value = (Integer) defaultValue;
        }
    }

    void clearDays() {
        for (CheckBox b : wd) {
            b.setChecked(false);
        }
    }

    static String getSummary(Context context, int value) {
        if (value == 0)
            value = State.ALLDAYS;
        if ((value & State.ALLDAYS) == State.ALLDAYS)
            return context.getString(R.string.alldays);
        if ((value & State.WORKDAYS) == State.WORKDAYS)
            return context.getString(R.string.workdays);
        if ((value & State.HOLIDAYS) == State.HOLIDAYS)
            return context.getString(R.string.holidays);
        String[] days = context.getResources().getStringArray(R.array.days);
        String res = null;
        for (int i = 0; i < 7; i++) {
            int m = (1 << (i + 2));
            if ((value & m) == m) {
                if (res == null) {
                    res = days[i];
                    continue;
                }
                res += ", " + days[i];
            }
        }
        return res;
    }
}

