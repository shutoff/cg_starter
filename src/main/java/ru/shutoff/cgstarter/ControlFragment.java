package ru.shutoff.cgstarter;

import android.app.AlertDialog;
import android.content.DialogInterface;
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
        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog dialog = new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.refresh)
                        .setMessage(R.string.refresh_msg)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        })
                        .create();
                dialog.show();
            }
        };
        v.findViewById(R.id.refresh).setOnClickListener(listener);
        v.findViewById(R.id.refresh_msg).setOnClickListener(listener);
        return v;
    }
}
