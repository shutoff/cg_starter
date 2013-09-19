package ru.shutoff.cgstarter;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TimePicker;

public class TimePreference extends DialogPreference {

    int start1 = 0;
    int end1 = 0;
    int start2 = 0;
    int end2 = 0;

    final static int FULL_DAY = 24 * 60;

    private TimePicker start_picker;
    private TimePicker end_picker;

    RadioGroup state;
    RadioButton interval1;
    RadioButton interval2;

    Context mContext;
    boolean set_time = false;

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
        setDefaultValue("00:00-00:00");
        mContext = ctxt;
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
        interval1 = (RadioButton) view.findViewById(R.id.interval);
        interval2 = (RadioButton) view.findViewById(R.id.interval2);
        TimePicker.OnTimeChangedListener changeListener = new TimePicker.OnTimeChangedListener() {

            @Override
            public void onTimeChanged(TimePicker view, int hourOfDay, int minute) {
                if (set_time)
                    return;
                if (state.getCheckedRadioButtonId() != R.id.interval2) {
                    state.check(R.id.interval);
                    start1 = start_picker.getCurrentHour() * 60 + start_picker.getCurrentMinute();
                    end1 = end_picker.getCurrentHour() * 60 + end_picker.getCurrentMinute();
                } else {
                    start2 = start_picker.getCurrentHour() * 60 + start_picker.getCurrentMinute();
                    end2 = end_picker.getCurrentHour() * 60 + end_picker.getCurrentMinute();
                }
                setIntervalText();
            }
        };

        state.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                switch (checkedId) {
                    case R.id.interval:
                        setInterval(start1, end1);
                        break;
                    case R.id.interval2:
                        setInterval(start2, end2);
                        break;
                    case R.id.never:
                        start1 = 0;
                        end1 = 0;
                        start2 = 0;
                        end2 = 0;
                        setIntervalText();
                        break;
                    case R.id.always:
                        start1 = 0;
                        end1 = FULL_DAY;
                        start2 = 0;
                        end2 = 0;
                        setIntervalText();
                        break;
                }
            }
        });

        start_picker.setOnTimeChangedListener(changeListener);
        end_picker.setOnTimeChangedListener(changeListener);
        return view;
    }

    @Override
    protected void onBindDialogView(View v) {

        set_time = true;

        start_picker.setCurrentHour(start1 / 60);
        start_picker.setCurrentMinute(start1 % 60);
        end_picker.setCurrentHour(end1 / 60);
        end_picker.setCurrentMinute(end1 % 60);

        set_time = false;

        if ((start1 == 0) && (end1 == 0)) {
            state.check(R.id.never);
        } else if ((start1 == 0) && (end1 == FULL_DAY)) {
            state.check(R.id.always);
        } else {
            state.check(R.id.interval);
        }
        setIntervalText();
        super.onBindDialogView(v);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        if (positiveResult) {
            String res = "";
            switch (state.getCheckedRadioButtonId()) {
                case R.id.never:
                    start1 = 0;
                    end1 = 0;
                    start2 = 0;
                    end2 = 0;
                    break;
                case R.id.always:
                    start1 = 0;
                    end1 = FULL_DAY;
                    start2 = 0;
                    end2 = FULL_DAY;
                    res = "00:00-24:00";
                    break;
                default:
                    start1 = start1 % FULL_DAY;
                    start2 = start2 % FULL_DAY;
                    end1 = end1 % FULL_DAY;
                    end2 = end2 % FULL_DAY;
                    if (start1 == end1) {
                        int r = start1;
                        start1 = start2;
                        start2 = r;
                        r = end1;
                        end1 = end2;
                        end2 = r;
                    }
                    if (start2 != end2) {
                        if (start2 < start1) {
                            int r = start1;
                            start1 = start2;
                            start2 = r;
                            r = end1;
                            end1 = end2;
                            end2 = r;
                        }
                        if (end1 < start1) {
                            end1 += FULL_DAY;
                            start2 += FULL_DAY;
                            end2 += FULL_DAY;
                        }
                        if (end2 < start2) {
                            end2 += FULL_DAY;
                            start1 += FULL_DAY;
                            end1 += FULL_DAY;
                        }
                        if (start2 < start1) {
                            int r = start1;
                            start1 = start2;
                            start2 = r;
                            r = end1;
                            end1 = end2;
                            end2 = r;
                        }
                        if (start2 < end1) {
                            if (end2 > end1)
                                end1 = end2;
                            start2 = 0;
                            end2 = 0;
                        }
                    }
                    if (start2 == end2) {
                        if (start1 != end1) {
                            if (end1 < start1)
                                end1 += FULL_DAY;
                            if (end1 - start1 >= FULL_DAY) {
                                res = "00:00-24:00";
                            } else {
                                res = toTime(start1) + "-" + toTime(end1);
                            }
                        }
                    } else {
                        if ((end1 - start1 >= FULL_DAY) || (end2 - start2 >= FULL_DAY)) {
                            res = "00:00-24:00";
                        } else {
                            res = toTime(start1) + "-" + toTime(end1) + ";" + toTime(start2) + "-" + toTime(end2);
                        }
                    }
            }
            if (callChangeListener(res))
                persistString(res);
            setSummary(summary(res));
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

        String[] parts = times.split(";");
        if (parts.length > 0) {
            String[] time = parts[0].split("-");
            if (time.length > 0)
                start1 = getHour(time[0]) * 60 + getMinute(time[0]);
            if (time.length > 1)
                end1 = getHour(time[1]) * 60 + getMinute(time[1]);
        }
        if (parts.length > 1) {
            String[] time = parts[1].split("-");
            if (time.length > 0)
                start2 = getHour(time[0]) * 60 + getMinute(time[0]);
            if (time.length > 1)
                end2 = getHour(time[1]) * 60 + getMinute(time[1]);
        }

        setSummary(summary(times));
    }

    void setIntervalText() {
        String text1 = getContext().getString(R.string.interval);
        int end = end1;
        if (end < start1)
            end += FULL_DAY;
        if ((end1 != start1) && (end - start1 < FULL_DAY))
            text1 += " (" + toTime(start1) + "-" + toTime(end1) + ")";
        interval1.setText(text1);
        String text2 = getContext().getString(R.string.interval2);
        end = end2;
        if (end < start2)
            end += FULL_DAY;
        if ((end2 != start2) && (end - start2 < FULL_DAY))
            text2 += " (" + toTime(start2) + "-" + toTime(end2) + ")";
        interval2.setText(text2);

    }

    void setInterval(int start, int end) {
        set_time = true;
        start = start % FULL_DAY;
        end = end % FULL_DAY;
        start_picker.setCurrentHour(start / 60);
        start_picker.setCurrentMinute(start % 60);
        end_picker.setCurrentHour(end / 60);
        end_picker.setCurrentMinute(end % 60);
        set_time = false;
    }

    String summary(String value) {
        if (value.equals(""))
            value = "00:00-00:00";
        if (value.equals("00:00-00:00"))
            return mContext.getString(R.string.never);
        if (value.equals("00:00-24:00"))
            return mContext.getString(R.string.always);
        return mContext.getString(R.string.interval) + " " + value;
    }

    String toTime(int time) {
        time = time % FULL_DAY;
        int hours = time / 60;
        int min = time % 60;
        return String.format("%02d:%02d", hours, min);
    }
}
