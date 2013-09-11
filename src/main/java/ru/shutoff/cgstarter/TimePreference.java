package ru.shutoff.cgstarter;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.TimePicker;

public class TimePreference extends DialogPreference {

    private int startHour = 0;
    private int startMinute = 0;

    private int endHour = 0;
    private int endMinute = 0;

    private TimePicker start_picker;
    private TimePicker end_picker;

    RadioGroup state;

    public static int getHour(String time) {
        try {
            String[] pieces = time.split(":");
            return (Integer.parseInt(pieces[0]));
        } catch (Exception ex) {
            // ignore
        }
        return 0;
    }

    public static int getMinute(String time) {
        try {
            String[] pieces = time.split(":");
            return (Integer.parseInt(pieces[1]));
        } catch (Exception ex) {
            // ignore
        }
        return 0;
    }

    public TimePreference(Context ctxt, AttributeSet attrs) {
        super(ctxt, attrs);
        setPositiveButtonText(ctxt.getString(R.string.set));
        setNegativeButtonText(ctxt.getString(R.string.cancel));
    }

    @Override
    protected View onCreateDialogView() {
        LayoutInflater layoutInflater = LayoutInflater.from(getContext());
        View view = layoutInflater.inflate(R.layout.interval, null);
        start_picker = (TimePicker) view.findViewById(R.id.start);
        end_picker = (TimePicker) view.findViewById(R.id.end);
        start_picker.setIs24HourView(true);
        end_picker.setIs24HourView(true);
        state = (RadioGroup) view.findViewById(R.id.state);
        return view;
    }

    @Override
    protected void onBindDialogView(View v) {
        super.onBindDialogView(v);
        start_picker.setCurrentHour(startHour);
        start_picker.setCurrentMinute(startMinute);
        end_picker.setCurrentHour(endHour);
        end_picker.setCurrentMinute(endMinute);
        if ((startHour == 0) && (startMinute == 0) && (endHour == 0) && (endMinute == 0)) {
            state.check(R.id.never);
        } else if ((startHour == 0) && (startMinute == 0) && (endHour == 24) && (endMinute == 0)) {
            state.check(R.id.allways);
        } else {
            state.check(R.id.interval);
        }
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        if (positiveResult) {
            String res = "";
            switch (state.getCheckedRadioButtonId()) {
                case R.id.allways:
                    res = "00:00-24:00";
                    break;
                case R.id.interval:
                    startHour = start_picker.getCurrentHour();
                    startMinute = start_picker.getCurrentMinute();
                    endHour = end_picker.getCurrentHour();
                    endMinute = end_picker.getCurrentMinute();
                    res = String.format("%02d:%02d-%02d:%02d",
                            startHour, startMinute, endHour, endMinute);
            }
            if (callChangeListener(res))
                persistString(res);
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return (a.getString(index));
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        String times = null;

        if (restoreValue) {
            if (defaultValue == null) {
                times = getPersistedString("00:00-00:00");
            } else {
                times = getPersistedString(defaultValue.toString());
            }
        } else {
            times = defaultValue.toString();
        }

        String[] time = times.split("-");

        if (time[0] != null) {
            startHour = getHour(time[0]);
            startMinute = getMinute(time[0]);
        }

        if (time[1] != null) {
            endHour = getHour(time[1]);
            endMinute = getMinute(time[1]);
        }
    }
}
