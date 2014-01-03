package ru.shutoff.cgstarter;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SeekBar;

public class QuickLaunchFragment extends Fragment {

    int size;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());

        View v = inflater.inflate(R.layout.quick_launch_setup, container, false);
        final ImageView iv = (ImageView) v.findViewById(R.id.preview);
        SeekBar seekBar = (SeekBar) v.findViewById(R.id.alpha);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int alpha = progress * 255 / 100;
                iv.setAlpha(255 - alpha);
                SharedPreferences.Editor ed = preferences.edit();
                ed.putInt(State.QUICK_ALPHA, progress);
                ed.commit();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        int alpha = preferences.getInt(State.QUICK_ALPHA, 0);
        seekBar.setProgress(alpha);
        alpha = alpha * 255 / 100;
        iv.setAlpha(255 - alpha);

        SeekBar seekBarSize = (SeekBar) v.findViewById(R.id.size);
        seekBarSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                progress += 10;
                ViewGroup.LayoutParams param = iv.getLayoutParams();
                param.width = size * progress / 30;
                param.height = param.width;
                iv.setLayoutParams(param);
                SharedPreferences.Editor ed = preferences.edit();
                ed.putInt(State.QUICK_SIZE, progress);
                ed.commit();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        int progress = preferences.getInt(State.QUICK_SIZE, 30);
        ViewGroup.LayoutParams param = iv.getLayoutParams();
        size = param.width;
        param.width = size * progress / 30;
        param.height = param.width;
        iv.setLayoutParams(param);
        seekBarSize.setProgress(progress - 10);

        return v;
    }
}
