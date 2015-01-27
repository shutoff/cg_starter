package ru.shutoff.cgstarter;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.hotword.client.HotwordServiceClient;

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
        if (State.hasTelephony(getActivity())) {
            setSeekBar(v, R.id.ring_level, State.RING_LEVEL, 0);
        } else {
            v.findViewById(R.id.ring_level).setVisibility(View.GONE);
            v.findViewById(R.id.ring_level_msg).setVisibility(View.GONE);
        }
        setCheckBox(v, R.id.bt, State.BT);
        setCheckBox(v, R.id.data, State.DATA);
        setCheckBox(v, R.id.wifi, State.WIFI, true);
        setCheckBox(v, R.id.ping, State.PING);
        setCheckBox(v, R.id.okgoogle, State.OK_GOOGLE);
        setCheckBox(v, R.id.bt_close, State.KILL_BT);

        if (!State.can_root)
            v.findViewById(R.id.bt_close_block).setVisibility(View.GONE);

        HotwordServiceClient client = new HotwordServiceClient(getActivity());
        boolean isAvailable = client.isAvailable();
        v.findViewById(R.id.okgoogle).setVisibility(isAvailable ? View.VISIBLE : View.GONE);
        v.findViewById(R.id.okgoogle_sum).setVisibility(isAvailable ? View.VISIBLE : View.GONE);

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
                                State.get(getActivity(), true);
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
