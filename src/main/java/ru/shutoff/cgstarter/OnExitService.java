package ru.shutoff.cgstarter;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.FileObserver;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.telephony.PhoneNumberUtils;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.internal.telephony.ITelephony;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
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
    static final int AFTER_OFFHOOK_PAUSE = 5000;

    AlarmManager alarmMgr;
    PendingIntent pi;
    PendingIntent piAnswer;
    PendingIntent piAfterCall;

    PhoneStateListener phoneListener;

    TelephonyManager tm;

    boolean phone;
    boolean speaker;
    boolean offhook;
    static String call_number;

    int autoanswer;

    float button_x;
    float button_y;

    View hudView;
    CountDownTimer setupTimer;
    boolean setup_button;

    FileObserver observer;
    String screenshots_path;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        alarmMgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        pi = createPendingIntent(TIMER);
        try {
            File screenshots = Environment.getExternalStorageDirectory();
            screenshots = new File(screenshots, "CityGuide/screenshots");
            screenshots_path = screenshots.getAbsolutePath();
            observer = new FileObserver(screenshots.getAbsolutePath(), FileObserver.CLOSE_WRITE) {
                @Override
                public void onEvent(int event, String path) {
                    State.appendLog(path);
                    if ((path.length() > 4) && path.substring(path.length() - 4).equals(".bmp")) {
                        convertToPng(screenshots_path + "/" + path);
                    }
                }
            };
        } catch (Exception ex) {
            State.print(ex);
        }
        observer.startWatching();
    }

    @Override
    public void onDestroy() {
        if (phoneListener != null)
            tm.listen(phoneListener, PhoneStateListener.LISTEN_NONE);
        if (observer != null)
            observer.startWatching();
        hideOverlay();
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
                        // ignore
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
                if (offhook) {
                    if (isActiveCG(this)) {
                        showOverlay();
                    } else {
                        hideOverlay();
                    }
                }
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

    void showOverlay() {
        if (hudView != null)
            return;

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (!preferences.getBoolean(State.PHONE_SHOW, false))
            return;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;

        params.x = preferences.getInt(State.PHONE_X, 50);
        params.y = preferences.getInt(State.PHONE_Y, 50);

        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        hudView = inflater.inflate(R.layout.call, null);
        TextView number = (TextView) hudView.findViewById(R.id.number);
        if ((call_number != null) && !call_number.equals("")) {
            number.setText(PhoneNumberUtils.formatNumber(call_number));
        } else {
            number.setText(getString(R.string.unknown));
        }

        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(call_number));
        ContentResolver contentResolver = getContentResolver();
        Cursor contactLookup = contentResolver.query(uri, new String[]{BaseColumns._ID,
                ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);

        try {
            if (contactLookup != null && contactLookup.getCount() > 0) {
                contactLookup.moveToNext();
                String name = contactLookup.getString(contactLookup.getColumnIndex(ContactsContract.Data.DISPLAY_NAME));
                TextView tvName = (TextView) hudView.findViewById(R.id.name);
                tvName.setText(name);
            }
        } finally {
            if (contactLookup != null) {
                contactLookup.close();
            }
        }

        hudView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        setupTimer = new CountDownTimer(1000, 1000) {
                            @Override
                            public void onTick(long millisUntilFinished) {
                            }

                            @Override
                            public void onFinish() {
                                setupPhoneButton();
                            }
                        };
                        setupTimer.start();
                        button_x = event.getX();
                        button_y = event.getY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        int dx = 0;
                        int dy = 0;
                        if (setup_button) {
                            dx = (int) (event.getX() - button_x);
                            dy = (int) (event.getY() - button_y);
                            moveButton(dx, dy);
                        }
                        button_x = event.getX() - dx;
                        button_y = event.getY() - dy;
                        break;

                    case MotionEvent.ACTION_UP:
                        if (setup_button) {
                            cancelSetup();
                            break;
                        }
                        cancelSetup();
                        try {
                            TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
                            Class c = Class.forName(tm.getClass().getName());
                            Method m = c.getDeclaredMethod("getITelephony");
                            m.setAccessible(true);
                            ITelephony telephonyService;
                            telephonyService = (ITelephony) m.invoke(tm);
                            telephonyService.showCallScreen();
                        } catch (Exception ex) {
                            // ignore
                        }
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        cancelSetup();
                        break;
                }
                return false;
            }
        });

        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        wm.addView(hudView, params);
    }

    void moveButton(int dx, int dy) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        int x = preferences.getInt(State.PHONE_X, 50) + dx;
        int y = preferences.getInt(State.PHONE_Y, 50) + dy;
        SharedPreferences.Editor ed = preferences.edit();
        ed.putInt(State.PHONE_X, x);
        ed.putInt(State.PHONE_Y, y);
        ed.commit();
        if (hudView == null)
            return;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = x;
        params.y = y;
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        wm.updateViewLayout(hudView, params);
    }

    void setupPhoneButton() {
        cancelSetup();
        if (hudView == null)
            return;
        LinearLayout layout = (LinearLayout) hudView;
        layout.setBackgroundResource(R.drawable.setup_call);
        setup_button = true;
    }

    void cancelSetup() {
        if (setup_button) {
            LinearLayout layout = (LinearLayout) hudView;
            layout.setBackgroundResource(R.drawable.call);
            setup_button = false;
        }
        if (setupTimer == null)
            return;
        setupTimer.cancel();
        setupTimer = null;
    }

    void hideOverlay() {
        cancelSetup();
        if (hudView == null)
            return;
        ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(hudView);
        hudView = null;
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
            prev_state = TelephonyManager.CALL_STATE_IDLE;
            phoneListener = new PhoneStateListener() {
                @Override
                public void onCallStateChanged(int state, String incomingNumber) {
                    if ((incomingNumber != null) && (!incomingNumber.equals("")))
                        call_number = incomingNumber;
                    super.onCallStateChanged(state, incomingNumber);
                    State.appendLog("phone state " + state);
                    switch (state) {
                        case TelephonyManager.CALL_STATE_OFFHOOK:
                            stopAutoAnswer();
                            stopAfterCall();
                            if (phone) {
                                offhook = true;
                                if (prev_state == TelephonyManager.CALL_STATE_IDLE) {
                                    if (piAfterCall == null)
                                        piAfterCall = createPendingIntent(TIMER_AFTER_CALL);
                                    alarmMgr.setRepeating(AlarmManager.RTC,
                                            System.currentTimeMillis() + AFTER_OFFHOOK_PAUSE, AFTER_OFFHOOK_PAUSE, piAfterCall);
                                    break;
                                }
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
                                showOverlay();
                            }
                            if (speaker) {
                                AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                                if (!audio.isBluetoothScoOn())
                                    audio.setSpeakerphoneOn(true);
                            }
                            break;
                        case TelephonyManager.CALL_STATE_RINGING:
                            stopAfterCall();
                            hideOverlay();
                            offhook = false;
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
                            hideOverlay();
                            call_number = null;
                            offhook = false;
                            if (prev_state == TelephonyManager.CALL_STATE_IDLE)
                                break;
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
        if (processname == null || processname.equals(""))
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

    static void convertFile(String bmp_name) {
        try {
            File bmp_file = new File(bmp_name);
            long last_modified = bmp_file.lastModified();
            Bitmap bmp = BitmapFactory.decodeFile(bmp_name);
            String png_name = bmp_name.substring(0, bmp_name.length() - 3) + "png";
            FileOutputStream out = new FileOutputStream(png_name);
            boolean res = bmp.compress(Bitmap.CompressFormat.PNG, 1, out);
            out.flush();
            out.close();
            File file = new File(res ? bmp_name : png_name);
            file.delete();
            if (res) {
                File png_file = new File(png_name);
                png_file.setLastModified(last_modified);
            }
        } catch (Exception ex) {
            State.print(ex);
        }

    }

    static void convertToPng(String path) {
        AsyncTask<String, Void, Void> task = new AsyncTask<String, Void, Void>() {
            @Override
            protected Void doInBackground(String... params) {
                convertFile(params[0]);
                return null;
            }
        };
        task.execute(path);
    }

    static void convertFiles() {
        AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    File screenshots = Environment.getExternalStorageDirectory();
                    screenshots = new File(screenshots, "CityGuide/screenshots");
                    String[] bmp_files = screenshots.list(new FilenameFilter() {
                        @Override
                        public boolean accept(File dir, String filename) {
                            return filename.substring(filename.length() - 4).equals(".bmp");
                        }
                    });
                    for (String bmp_file : bmp_files) {
                        convertFile(screenshots.getAbsolutePath() + "/" + bmp_file);
                    }
                } catch (Exception ex) {
                    State.print(ex);
                }
                return null;
            }
        };
        task.execute();
    }
}
