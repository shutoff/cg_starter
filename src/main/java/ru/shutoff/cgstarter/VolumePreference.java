package ru.shutoff.cgstarter;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;

public class VolumePreference extends SeekBarPreference {

    CheckBox mVolume;

    public VolumePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    void createExtraControls(LinearLayout layout) {
        mVolume = new CheckBox(mContext);
        mVolume.setText(mContext.getString(R.string.set_volume));
        mVolume.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mSeekBar.setEnabled(isChecked);
            }
        });
        layout.addView(mVolume);
    }

    @Override
    protected void onBindDialogView(View v) {
        super.onBindDialogView(v);
        boolean is_set = getSharedPreferences().getBoolean(State.VOLUME, false);
        mVolume.setChecked(is_set);
        mSeekBar.setEnabled(is_set);
    }

    @Override
    public void onClick(View v) {
        SharedPreferences.Editor ed = getSharedPreferences().edit();
        ed.putBoolean(State.VOLUME, mVolume.isChecked());
        ed.commit();
        super.onClick(v);
    }

    @Override
    String summary() {
        if (!getSharedPreferences().getBoolean(State.VOLUME, false))
            return mContext.getString(R.string.no_volume);
        return mContext.getString(R.string.set_volume) + " " + mValue + " " + mSuffix;
    }
}
