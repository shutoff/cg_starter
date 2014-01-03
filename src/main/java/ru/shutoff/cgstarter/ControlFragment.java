package ru.shutoff.cgstarter;

import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class ControlFragment extends PreferencesFragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View v = inflater.inflate(R.layout.control, container, false);
        int id_entries = R.array.rotate_entries;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB)
            id_entries = R.array.rotate_entries2;
        setSpinner(v, R.id.orientation, R.array.rotate_value, id_entries, State.ORIENTATION, "0");
        setCheckBox(v, R.id.gps, State.GPS);
        setCheckBox(v, R.id.volume, State.VOLUME, R.id.level);
        setSeekBar(v, R.id.level, State.LEVEL, 100);
        setCheckBox(v, R.id.bt, State.BT);
        setCheckBox(v, R.id.data, State.DATA);
        setCheckBox(v, R.id.ping, State.PING);
        return v;
    }
}
