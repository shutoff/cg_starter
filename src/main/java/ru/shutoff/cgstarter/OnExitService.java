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
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.view.KeyEvent;

import com.android.internal.telephony.ITelephony;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class OnExitService extends Service {

    static final int TIMEOUT = 3000;

    static final String START = "Start";
    static final String TIMER = "Timer";
    static final String TIMER_AFTER_CALL = "TimerAfterCall";
    static final String ANSWER = "Answer";

    static final int AFTER_CALL_PAUSE = 2000;

    AlarmManager alarmMgr;
    PendingIntent pi;
    PendingIntent piAnswer;
    PendingIntent piAfterCall;

    PhoneStateListener phoneListener;
    TelephonyManager tm;

    boolean phone;
    boolean speaker;
    int autoanswer;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        alarmMgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        pi = createPendingIntent(TIMER);
    }

    @Override
    public void onDestroy() {
        if (phoneListener != null)
            tm.listen(phoneListener, PhoneStateListener.LISTEN_NONE);
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
            setPhoneListener();
            return START_STICKY;
        }
        if (action.equals(TIMER_AFTER_CALL)) {
            stopAfterCall();
            if (isRunCG(getApplicationContext()) && !isActiveCG(getApplicationContext())) {
                try {
                    Intent launch = getPackageManager().getLaunchIntentForPackage(State.CG_PACKAGE);
                    if (launch != null)
                        startActivity(launch);
                } catch (Exception ex) {
                    // ignore
                }
            }
            return START_STICKY;
        }
        if (action.equals(TIMER)) {
            if (!isRunCG(this)) {
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
                alarmMgr.cancel(pi);
                SharedPreferences.Editor ed = preferences.edit();
                int rotate = preferences.getInt(State.SAVE_ROTATE, 0);
                if (rotate > 0) {
                    try {
                        Settings.System.putInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, rotate);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    ed.remove(State.SAVE_ROTATE);
                }
                if (preferences.getBoolean(State.GPS_SAVE, false)) {
                    try {
                        State.turnGPSOff(this);
                    } catch (Exception ex) {
                        // ignore
                    }
                    ed.remove(State.GPS_SAVE);
                }
                if (!preferences.getBoolean(State.CAR_BT, false))
                    turnOffBT(this);
                int channel = preferences.getInt(State.SAVE_CHANNEL, 0);
                if (channel > 0) {
                    int level = preferences.getInt(State.SAVE_LEVEL, 0);
                    AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    audio.setStreamVolume(channel, level, 0);
                    ed.remove(State.SAVE_LEVEL);
                    ed.remove(State.SAVE_CHANNEL);
                }
                if (preferences.getBoolean(State.SAVE_WIFI, false)) {
                    try {
                        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
                        if (wifiManager != null)
                            wifiManager.setWifiEnabled(true);
                    } catch (Exception ex) {
                        // ignore
                    }
                    ed.remove(State.SAVE_WIFI);
                }
                if (preferences.getBoolean(State.SAVE_DATA, false)) {
                    try {
                        ConnectivityManager conman = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                        Class conmanClass = Class.forName(conman.getClass().getName());
                        Field iConnectivityManagerField = conmanClass.getDeclaredField("mService");
                        iConnectivityManagerField.setAccessible(true);
                        Object iConnectivityManager = iConnectivityManagerField.get(conman);
                        Class iConnectivityManagerClass = Class.forName(iConnectivityManager.getClass().getName());

                        Method setMobileDataEnabledMethod = iConnectivityManagerClass.getDeclaredMethod("setMobileDataEnabled", Boolean.TYPE);
                        setMobileDataEnabledMethod.setAccessible(true);
                        setMobileDataEnabledMethod.invoke(iConnectivityManager, false);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    ed.remove(State.SAVE_DATA);
                }
                ed.remove(State.CAR_START_CG);
                ed.commit();
                stopSelf();
            } else {
                setPhoneListener();
            }
            return START_STICKY;
        }
        if (action.equals(ANSWER)) {
            if (piAnswer != null) {
                alarmMgr.cancel(piAnswer);
                piAnswer = null;
            }
            try {
                TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
                if (tm.getCallState() != TelephonyManager.CALL_STATE_RINGING)
                    return START_STICKY;

                Class c = Class.forName(tm.getClass().getName());
                Method m = c.getDeclaredMethod("getITelephony");
                m.setAccessible(true);
                ITelephony telephonyService;
                telephonyService = (ITelephony) m.invoke(tm);

                telephonyService.silenceRinger();
                telephonyService.answerRingingCall();
            } catch (Exception e) {
                Intent buttonDown = new Intent(Intent.ACTION_MEDIA_BUTTON);
                buttonDown.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK));
                sendOrderedBroadcast(buttonDown, "android.permission.CALL_PRIVILEGED");

                Intent buttonUp = new Intent(Intent.ACTION_MEDIA_BUTTON);
                buttonUp.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HEADSETHOOK));
                sendOrderedBroadcast(buttonUp, "android.permission.CALL_PRIVILEGED");
            }

        }
        return START_STICKY;
    }

    static void turnOffBT(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean bt = preferences.getBoolean(State.SAVE_BT, false);
        if (!bt)
            return;
        boolean bt_connected = preferences.getBoolean(State.BT_CONNECTED, false);
        if (bt_connected)
            return;
        BluetoothAdapter btAdapter;
        try {
            btAdapter = BluetoothAdapter.getDefaultAdapter();
        } catch (Exception ex) {
            ex.printStackTrace();
            return;
        }
        if (btAdapter == null)
            return;
        btAdapter.disable();
        SharedPreferences.Editor ed = preferences.edit();
        ed.remove(State.SAVE_BT);
        ed.commit();
    }

    static int prev_state;
    static boolean cg_run;

    void stopAutoAnswer() {
        if (piAnswer != null) {
            alarmMgr.cancel(piAnswer);
            piAnswer = null;
        }
    }

    void stopAfterCall() {
        if (piAfterCall != null) {
            alarmMgr.cancel(piAfterCall);
            piAfterCall = null;
        }
    }

    void setPhoneListener() {
        if (phoneListener != null)
            return;
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        phone = preferences.getBoolean(State.PHONE, false);
        speaker = preferences.getBoolean(State.SPEAKER, false);
        try {
            autoanswer = Integer.parseInt(preferences.getString(State.ANSWER_TIME, "0")) * 1000;
        } catch (Exception ex) {
            // ignore
        }
        if (phone || speaker || (autoanswer > 0)) {
            phoneListener = new PhoneStateListener() {
                @Override
                public void onCallStateChanged(int state, String incomingNumber) {
                    super.onCallStateChanged(state, incomingNumber);
                    switch (state) {
                        case TelephonyManager.CALL_STATE_OFFHOOK:
                            stopAutoAnswer();
                            stopAfterCall();
                            if (phone) {
                                if (prev_state != TelephonyManager.CALL_STATE_RINGING)
                                    cg_run = isRunCG(getApplicationContext());
                                if (cg_run && !isActiveCG(getApplicationContext())) {
                                    try {
                                        Intent intent = getPackageManager().getLaunchIntentForPackage(State.CG_PACKAGE);
                                        if (intent != null)
                                            startActivity(intent);
                                    } catch (Exception ex) {
                                        // ignore
                                    }
                                }
                            }
                            if (speaker) {
                                AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                                if (!audio.isBluetoothScoOn())
                                    audio.setSpeakerphoneOn(true);
                            }
                            break;
                        case TelephonyManager.CALL_STATE_RINGING:
                            stopAfterCall();
                            cg_run = isRunCG(getApplicationContext());
                            if (autoanswer > 0) {
                                AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                                if (speaker || audio.isBluetoothScoOn()) {
                                    if (piAnswer == null)
                                        piAnswer = createPendingIntent(ANSWER);
                                    alarmMgr.setRepeating(AlarmManager.RTC,
                                            System.currentTimeMillis() + autoanswer, autoanswer, piAnswer);
                                }
                            }
                            break;
                        case TelephonyManager.CALL_STATE_IDLE:
                            stopAfterCall();
                            stopAutoAnswer();
                            if (phone) {
                                if (isRunCG(getApplicationContext())) {
                                    if (!isActiveCG(getApplicationContext())) {
                                        try {
                                            Intent intent = getPackageManager().getLaunchIntentForPackage(State.CG_PACKAGE);
                                            if (intent != null)
                                                startActivity(intent);
                                        } catch (Exception ex) {
                                            // ignore
                                        }
                                    }
                                    if (piAfterCall == null)
                                        piAfterCall = createPendingIntent(TIMER_AFTER_CALL);
                                    alarmMgr.setRepeating(AlarmManager.RTC,
                                            System.currentTimeMillis() + AFTER_CALL_PAUSE, AFTER_CALL_PAUSE, piAfterCall);
                                }
                            }
                            break;
                    }
                    prev_state = state;
                }
            };
            tm = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
            tm.listen(phoneListener, PhoneStateListener.LISTEN_CALL_STATE);
        }
    }

    PendingIntent createPendingIntent(String action) {
        Intent intent = new Intent(this, OnExitService.class);
        intent.setAction(action);
        return PendingIntent.getService(this, 0, intent, 0);
    }

    static boolean isRunCG(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> procInfos = activityManager.getRunningAppProcesses();
        if (procInfos == null)
            return false;
        int i;
        for (i = 0; i < procInfos.size(); i++) {
            ActivityManager.RunningAppProcessInfo proc = procInfos.get(i);
            if (proc.processName.equals(State.CG_PACKAGE))
                return true;
        }
        return false;
    }

    static ActivityManager mActivityManager;

    static boolean isActiveCG(Context context) {
        if (mActivityManager == null)
            mActivityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        try {
            for (ActivityManager.RunningAppProcessInfo info : mActivityManager.getRunningAppProcesses()) {
                if (info.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                        && !isRunningService(info.processName)
                        && info.processName.equals(State.CG_PACKAGE))
                    return true;
            }
        } catch (Exception ex) {
            // ignore
        }
        return false;
    }

    static void killCG(Context context) {
        if (mActivityManager == null)
            mActivityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        try {
            for (ActivityManager.RunningAppProcessInfo info : mActivityManager.getRunningAppProcesses()) {
                if (info.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                        && !isRunningService(info.processName)
                        && info.processName.equals(State.CG_PACKAGE)) {
                    int pid = android.os.Process.getUidForName(State.CG_PACKAGE);
                    android.os.Process.killProcess(pid);
                }
            }
        } catch (Exception ex) {
            // ignore
        }
    }

    static boolean isRunningService(String processname) {
        if (processname == null || processname.isEmpty())
            return false;
        try {
            for (ActivityManager.RunningServiceInfo service : mActivityManager.getRunningServices(9999)) {
                if (service.process.equals(processname))
                    return true;
            }
        } catch (Exception ex) {
            // ignore
        }
        return false;
    }
}
