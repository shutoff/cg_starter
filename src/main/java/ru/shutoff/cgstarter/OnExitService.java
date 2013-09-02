package ru.shutoff.cgstarter;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import java.util.List;

public class OnExitService extends Service {

    static final int TIMEOUT = 3000;

    static final String START = "Start";
    static final String TIMER = "Timer";

    AlarmManager alarmMgr;
    PendingIntent pi;

    PhoneStateListener phoneListener;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        alarmMgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, OnExitService.class);
        intent.setAction(TIMER);
        pi = PendingIntent.getService(this, 0, intent, 0);
    }

    @Override
    public void onDestroy(){
        if (phoneListener != null){
            TelephonyManager tm = (TelephonyManager)this.getSystemService(Context.TELEPHONY_SERVICE);
            tm.listen(phoneListener, PhoneStateListener.LISTEN_NONE);
        }
        super.onDestroy();
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null)
            return START_STICKY;
        String action = intent.getAction();
        if (action == null)
            return START_STICKY;
        if (action.equals(START)) {
            alarmMgr.setInexactRepeating(AlarmManager.RTC, System.currentTimeMillis() + TIMEOUT, TIMEOUT, pi);
            if (phoneListener == null){
                phoneListener = new PhoneStateListener(){
                    @Override
                    public void onCallStateChanged(int state, String incomingNumber){
                        State.appendLog("Call " + state);
                        super.onCallStateChanged(state, incomingNumber);
                    }
                };
                TelephonyManager tm = (TelephonyManager)this.getSystemService(Context.TELEPHONY_SERVICE);
                tm.listen(phoneListener, PhoneStateListener.LISTEN_CALL_STATE);
            }
            return START_STICKY;
        }
        if (action.equals(TIMER)) {
            if (!isRunCG(this)) {
                alarmMgr.cancel(pi);
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
                SharedPreferences.Editor ed = preferences.edit();
                int rotate = preferences.getInt("save_rotate", 0);
                if (rotate > 0) {
                    try {
                        Settings.System.putInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, rotate);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    ed.remove("save_rotate");
                }
                boolean bt = preferences.getBoolean("save_bt", false);
                if (bt) {
                    try {
                        BluetoothAdapter.getDefaultAdapter().disable();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    ed.remove("save_bt");
                }
                int channel = preferences.getInt("save_channel", 0);
                if (channel > 0) {
                    int level = preferences.getInt("save_level", 0);
                    AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    audio.setStreamVolume(channel, level, 0);
                    ed.remove("save_channel");
                }
                ed.commit();
                stopSelf();
            }
            return START_STICKY;
        }
        return START_STICKY;
    }

    static boolean isRunCG(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> procInfos = activityManager.getRunningAppProcesses();
        int i;
        for (i = 0; i < procInfos.size(); i++) {
            ActivityManager.RunningAppProcessInfo proc = procInfos.get(i);
            if (proc.processName.equals("cityguide.probki.net"))
                return true;
        }
        return false;
    }
}
