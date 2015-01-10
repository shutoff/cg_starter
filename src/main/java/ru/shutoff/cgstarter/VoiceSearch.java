package ru.shutoff.cgstarter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.List;

public class VoiceSearch extends Activity {

    static final int REQUEST_CODE_VOICE_SEARCH = 1;
    static final int SEARCH_RESULT = 2;

    boolean started;

    static boolean isVoiceSearch(Context context) {
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> activities = pm.queryIntentActivities(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0);
        return activities.size() > 0;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);

        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);

        try {
            Intent i = getIntent();
            if (i.getStringExtra("TextSearch") != null) {
                final AlertDialog dialog = new AlertDialog.Builder(this)
                        .setTitle(getString(R.string.search))
                        .setView(inflater.inflate(R.layout.text_search, null, false))
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.search, null)
                        .create();
                dialog.getWindow().setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                dialog.show();
                dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        if (!started)
                            finish();
                    }
                });
                final Button btn = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                btn.setEnabled(false);
                final EditText ed = (EditText) dialog.findViewById(R.id.text);
                ed.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {

                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        btn.setEnabled(!s.toString().isEmpty());
                    }
                });
                ed.requestFocus();
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        started = true;
                        Intent i = new Intent(VoiceSearch.this, SearchActivity.class);
                        ArrayList<String> res = new ArrayList<String>();
                        res.add(ed.getText().toString());
                        i.putExtra(RecognizerIntent.EXTRA_RESULTS, res);
                        startActivityForResult(i, SEARCH_RESULT);
                        dialog.dismiss();
                    }
                });
            } else {
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                startActivityForResult(intent, REQUEST_CODE_VOICE_SEARCH);
            }
        } catch (Exception ex) {
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if ((requestCode == SEARCH_RESULT) && (resultCode == RESULT_OK)) {
            setResult(RESULT_OK);
            finish();
            return;
        }

        if (resultCode != RESULT_OK) {
            finish();
            return;
        }
        Intent i = new Intent(this, SearchActivity.class);
        i.putExtra(RecognizerIntent.EXTRA_RESULTS, data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS));
        i.putExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES, data.getFloatArrayExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES));
        startActivityForResult(i, SEARCH_RESULT);
    }

}
