package ru.shutoff.cgstarter;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioGroup;

public class InactivePreference extends SeekBarPreference {

    RadioGroup action;

    public InactivePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    void createExtraControls(LinearLayout layout) {
        LayoutInflater layoutInflater = LayoutInflater.from(getContext());
        View view = layoutInflater.inflate(R.layout.inactive, null);
        layout.addView(view);
        action = (RadioGroup) view;
    }

    @Override
    protected void onBindDialogView(View v) {
        super.onBindDialogView(v);
        action.check(getSharedPreferences().getBoolean(State.INACTIVE_LAUNCH, false) ? R.id.launch : R.id.exit);
    }

    @Override
    public void onClick(View v) {
        SharedPreferences.Editor ed = getSharedPreferences().edit();
        ed.putBoolean(State.INACTIVE_LAUNCH, action.getCheckedRadioButtonId() == R.id.launch);
        ed.commit();
        super.onClick(v);
    }

    String summary() {
        boolean launch = getSharedPreferences().getBoolean(State.INACTIVE_LAUNCH, false);
        String summary = getContext().getString(launch ? R.string.launch : R.string.exit);
        return summary + " " + super.summary();
    }
}
