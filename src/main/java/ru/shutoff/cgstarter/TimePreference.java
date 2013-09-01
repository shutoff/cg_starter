package ru.shutoff.cgstarter;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TimePicker;

public class TimePreference extends DialogPreference {

    private int startHour = 0;
    private int startMinute = 0;

    private int endHour = 0;
    private int endMinute = 0;

    private boolean setEnd = false;

    private TimePicker picker=null;

    public static int getHour(String time) {
        String[] pieces = time.split(":");
        return(Integer.parseInt(pieces[0]));
    }

    public static int getMinute(String time) {
        String[] pieces=time.split(":");
        return(Integer.parseInt(pieces[1]));
    }

    public TimePreference(Context ctxt, AttributeSet attrs) {
        super(ctxt, attrs);

        setPositiveButtonText(ctxt.getString(R.string.set));
        setNegativeButtonText(ctxt.getString(R.string.cancel));
    }

    @Override
    protected View onCreateDialogView() {
        picker = new TimePicker(getContext());
        picker.setIs24HourView(DateFormat.is24HourFormat(getContext()));
        return(picker);
    }

    @Override
    protected void onBindDialogView(View v) {
        super.onBindDialogView(v);
        if (setEnd){
            picker.setCurrentHour(endHour);
            picker.setCurrentMinute(endMinute);
        }else{
            picker.setCurrentHour(startHour);
            picker.setCurrentMinute(startMinute);
        }
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        if (positiveResult) {
            if (setEnd){
                endHour = picker.getCurrentHour();
                endMinute = picker.getCurrentMinute();
                String time = String.format("%02d:%02d-%02d:%02d",
                        startHour, startMinute, endHour, endMinute);
                if (callChangeListener(time)) {
                    persistString(time);
                }
                setEnd = false;
                return;
            }
            startHour = picker.getCurrentHour();
            startMinute = picker.getCurrentMinute();
            setEnd = true;
            showDialog(null);
            return;
        }
        setEnd = false;
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
            }
            else {
                times = getPersistedString(defaultValue.toString());
            }
        }
        else {
            times = defaultValue.toString();
        }

        String[] time = times.split("-");

        startHour = getHour(time[0]);
        startMinute = getMinute(time[0]);

        endHour = getHour(time[1]);
        endMinute = getMinute(time[1]);
    }
}
