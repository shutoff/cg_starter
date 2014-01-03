package ru.shutoff.cgstarter;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

public class PhoneFragment extends PreferencesFragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View v = inflater.inflate(R.layout.phone, container, false);
        setCheckBox(v, R.id.phone, State.PHONE);
        setCheckBox(v, R.id.phone_show, State.PHONE_SHOW);
        setCheckBox(v, R.id.speaker, State.SPEAKER);
        setSpinner(v, R.id.autoanswer, R.array.answer_times, R.array.times, State.ANSWER_TIME, "0");
        setSpinner(v, R.id.ringtime, R.array.ring_times_value, R.array.ring_times, State.RINGING_TIME, "-1");
        String sms_text = preferences.getString(State.SMS, getString(R.string.def_sms));
        TextView tvSms = (TextView) v.findViewById(R.id.sms_text);
        tvSms.setText(sms_text);
        v.findViewById(R.id.sms).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final EditText et = new EditText(getActivity());
                AlertDialog dialog = new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.sms)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                SharedPreferences.Editor ed = preferences.edit();
                                ed.putString(State.SMS, et.getText().toString());
                                ed.commit();
                            }
                        })
                        .setView(et)
                        .create();
                dialog.show();
                et.setText(preferences.getString(State.SMS, getString(R.string.def_sms)));
            }
        });
        return v;
    }

}
