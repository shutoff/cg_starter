package ru.shutoff.cgstarter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class CarMonitor extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null)
            return;
        String action = intent.getAction();
        if (action == null)
            return;
        if (action.equals(Intent.ACTION_DOCK_EVENT)) {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            if (preferences.getBoolean("carmode", false)) {
                int dockState = intent.getIntExtra(Intent.EXTRA_DOCK_STATE, -1);
                if (dockState == Intent.EXTRA_DOCK_STATE_CAR) {
                    if (!OnExitService.isRunCG(context)) {
                        Intent run = new Intent(context, MainActivity.class);
                        context.startActivity(run);
                    }
                }
            }
        }
    }
}

