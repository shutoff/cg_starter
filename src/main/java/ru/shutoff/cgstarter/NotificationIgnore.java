package ru.shutoff.cgstarter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;

public class NotificationIgnore extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String app = getIntent().getStringExtra(State.APP);
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(app, 0);
            String app_name = pm.getApplicationLabel(info).toString();
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.notification_ignore);
            builder.setMessage(String.format(getString(R.string.notification_ignore_msg), app_name));
            builder.setPositiveButton(R.string.cont, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    String ignored = preferences.getString(State.NOTIFICATION_IGNORE, "");
                    if (ignored.length() > 0)
                        ignored += ":";
                    ignored += app;
                    SharedPreferences.Editor ed = preferences.edit();
                    ed.putString(State.NOTIFICATION_IGNORE, ignored);
                    ed.commit();
                }
            });
            builder.setNegativeButton(R.string.cancel, null);
            AlertDialog dialog = builder.create();
            dialog.show();
            dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    finish();
                }
            });
        } catch (Exception ex) {
            State.print(ex);
            // ignore
        }

    }
}
