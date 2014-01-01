package ru.shutoff.cgstarter;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.FileObserver;
import android.os.IBinder;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.telephony.PhoneNumberUtils;
import android.telephony.PhoneStateListener;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.internal.telephony.ITelephony;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.List;
import java.util.Vector;

public class OnExitService extends Service {

    static final int TIMEOUT = 3000;

    static final String START = "Start";
    static final String TIMER = "Timer";
    static final String TIMER_AFTER_CALL = "TimerAfterCall";
    static final String ANSWER = "Answer";
    static final String RINGING = "Ringing";
    static final String PHONE = "Phone";

    static final int AFTER_CALL_PAUSE = 2000;
    static final int AFTER_OFFHOOK_PAUSE = 5000;

    static final int NOTIFICATION_ID = 1234;

    static final String NOTIFICATION = "ru.shutoff.cg_starter.NOTIFICATION";
    static final String SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";

    AlarmManager alarmMgr;
    PendingIntent pi;
    PendingIntent piAnswer;
    PendingIntent piRinging;
    PendingIntent piAfterCall;

    PhoneStateListener phoneListener;

    WindowManager.LayoutParams layoutParams;

    TelephonyManager tm;

    boolean phone;
    boolean speaker;
    boolean ringing;
    boolean landscape;

    static String call_number;

    int autoanswer;
    int autoswitch;

    float button_x;
    float button_y;
    Bitmap contactPhoto;

    boolean show_overlay;
    boolean foreground;

    View hudActive;
    View hudInactive;
    View hudNotification;
    View hudApps;

    CountDownTimer setupTimer;
    CountDownTimer notificationTimer;

    boolean setup_button;

    FileObserver observer;
    String screenshots_path;

    PingTask pingTask;
    BroadcastReceiver br;

    HttpTask fetcher;
    LocationManager locationManager;
    LocationListener netListener;
    LocationListener gpsListener;

    static Location currentBestLocation;
    View.OnClickListener iconListener;

    PackageManager pm;

    static double yandex_finish_lat;
    static double yandex_finish_lon;

    final static long UPD_INTERVAL = 3 * 60 * 1000;
    final static long VALID_INTEVAL = 15 * 60 * 1000;
    final static String TRAFFIC_URL = "http://api-maps.yandex.ru/services/traffic-info/1.0/?format=json&lang=ru-RU'";

    final static String YAN = "ru.yandex.yandexnavi";

    final static int res[] = {
            R.drawable.gray,
            R.drawable.p0,
            R.drawable.p1,
            R.drawable.p2,
            R.drawable.p3,
            R.drawable.p4,
            R.drawable.p5,
            R.drawable.p6,
            R.drawable.p7,
            R.drawable.p8,
            R.drawable.p9,
            R.drawable.p10,
    };

    static class App {
        String name;
        Drawable icon;
    }

    Vector<App> apps;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

/*
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable ex) {
                State.print(ex);
                ex.printStackTrace();
            }
        });
*/

        alarmMgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        pi = createPendingIntent(TIMER);
        try {
            File screenshots = Environment.getExternalStorageDirectory();
            screenshots = new File(screenshots, "CityGuide/screenshots");
            screenshots_path = screenshots.getAbsolutePath();
            observer = new FileObserver(screenshots.getAbsolutePath(), FileObserver.CLOSE_WRITE) {
                @Override
                public void onEvent(int event, String path) {
                    if ((path.length() > 4) && path.substring(path.length() - 4).equals(".bmp")) {
                        convertToPng(screenshots_path + "/" + path);
                    }
                }
            };
        } catch (Exception ex) {
            // ignore
        }

        layoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        layoutParams.gravity = Gravity.TOP | Gravity.LEFT;

        observer.startWatching();

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        pm = getPackageManager();
        String[] quick_launch = preferences.getString(State.APPS, "").split(":");
        boolean yandex = false;
        apps = new Vector<App>();
        for (String app : quick_launch) {
            String[] component = app.split("/");
            if (component.length != 2)
                continue;
            Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
            mainIntent.setPackage(component[0]);
            List<ResolveInfo> infos = pm.queryIntentActivities(mainIntent, 0);
            for (ResolveInfo info : infos) {
                if (info.activityInfo == null)
                    continue;
                if (info.activityInfo.name.equals(component[1])) {
                    App a = new App();
                    a.name = app;
                    if (component[0].equals(YAN) || app.equals("ru.shutoff.cgstarter/ru.shutoff.cgstarter.TrafficActivity")) {
                        yandex = true;
                    } else {
                        a.icon = info.loadIcon(pm);
                        if (a.icon == null)
                            continue;
                    }
                    apps.add(a);
                    break;
                }
            }
        }
        if (yandex)
            initLocation();
    }

    @Override
    public void onDestroy() {
        if (phoneListener != null)
            tm.listen(phoneListener, PhoneStateListener.LISTEN_NONE);
        if (observer != null)
            observer.startWatching();
        if (br != null)
            unregisterReceiver(br);
        if (foreground)
            stopForeground(true);
        hideOverlays(null);
        if (netListener != null)
            locationManager.removeUpdates(netListener);
        if (gpsListener != null)
            locationManager.removeUpdates(gpsListener);
        if ((fetcher != null) && (fetcher.br != null))
            unregisterReceiver(fetcher.br);
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null)
            return START_STICKY;
        String action = intent.getAction();
        if (action == null)
            return START_STICKY;
        if (br == null) {
            br = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent.getAction().equals(SMS_RECEIVED)) {
                        showSMS(intent);
                    } else {
                        showNotification(intent);
                    }
                }
            };
            IntentFilter filter = new IntentFilter(NOTIFICATION);
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
            if (preferences.getBoolean(State.SHOW_SMS, false)) {
                filter.addAction(SMS_RECEIVED);
                filter.setPriority(10);
            }
            registerReceiver(br, filter);
        }
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
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
            if (!isRunCG(this)) {
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
                rotate = preferences.getInt(State.SAVE_ORIENTATION, -1);
                if (rotate >= 0) {
                    try {
                        Settings.System.putInt(getContentResolver(), Settings.System.USER_ROTATION, Surface.ROTATION_0);
                    } catch (Exception ex) {
                        // ignore
                    }
                    ed.remove(State.SAVE_ORIENTATION);
                }
                if (preferences.getBoolean(State.GPS_SAVE, false)) {
                    try {
                        State.turnGPSOff(this);
                    } catch (Exception ex) {
                        // ignore
                    }
                    ed.remove(State.GPS_SAVE);
                }
                turnOffBT(this, "-");
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
                    enableMobileData(this, false);
                    ed.remove(State.SAVE_DATA);
                }
                try {
                    sendBroadcast(new Intent("com.latedroid.juicedefender.action.ALLOW_APN")
                            .putExtra("tag", "cg_starter")
                            .putExtra("reply", true));
                } catch (Exception ex) {
                    // ignore
                }
                if (preferences.getBoolean(State.MAPCAM, false)) {
                    Intent i = new Intent("info.mapcam.droid.SERVICE_STOP");
                    sendBroadcast(i);
                }
                if (preferences.getBoolean(State.STRELKA, false)) {
                    Intent i = new Intent("com.ivolk.StrelkaGPS.action.STOP_SERVICE");
                    sendBroadcast(i);
                }
                ed.commit();
                if ((fetcher != null) && (fetcher.br != null)) {
                    unregisterReceiver(fetcher.br);
                    fetcher.br = null;
                }
                stopSelf();
            } else {
                setPhoneListener();
                if ((hudActive != null) || (hudNotification != null) || (hudInactive != null) || (hudApps != null)) {
                    if (landscape != isLandscape()) {
                        landscape = !landscape;
                        if (hudActive != null) {
                            hideActiveOverlay();
                            showActiveOverlay();
                        }
                        if (hudNotification != null)
                            hideNotification();
                        if (hudInactive != null) {
                            hideInactiveOverlay();
                            showInactiveOverlay();
                        }
                        if (hudApps != null) {
                            hideApps();
                            showApps();
                        }
                    }
                }
                if (show_overlay) {
                    if (isActiveCG(this)) {
                        showActiveOverlay();
                    } else {
                        showInactiveOverlay();
                    }
                }
                if ((apps.size() > 0) && !setup_button) {
                    if (isActiveCG(this)) {
                        hideInactiveOverlay();
                        showApps();
                    } else {
                        showInactiveOverlay();
                    }
                }
                if (preferences.getBoolean(State.PING, false))
                    ping();
                if ((yandex_finish_lat != 0) || (yandex_finish_lon != 0)) {
                    if (!isRun(this, "ru.yandex.yandexnavi")) {
                        yandex_finish_lat = 0;
                        yandex_finish_lon = 0;
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
            callAnswer();
            return START_STICKY;
        }
        if (action.equals(RINGING)) {
            if (piRinging != null) {
                alarmMgr.cancel(piRinging);
                piRinging = null;
            }
            switchToCG();
            return START_STICKY;
        }
        if (action.equals(PHONE)) {
            switchToPhone();
            return START_STICKY;
        }
        return START_STICKY;
    }

    void ping() {
        if (pingTask != null)
            return;
        pingTask = new PingTask();
        pingTask.execute();
    }

    void setAirplaneMode(boolean mode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
            Settings.System.putInt(getContentResolver(),
                    Settings.System.AIRPLANE_MODE_ON, mode ? 1 : 0);
        } else {
            Settings.Global.putInt(getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON, mode ? 1 : 0);
        }
        Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intent.putExtra("state", mode);
        sendBroadcast(intent);
    }


    void callAnswer() {
        TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        if (tm.getCallState() != TelephonyManager.CALL_STATE_RINGING)
            return;

        try {
            Class c = Class.forName(tm.getClass().getName());
            Method m = c.getDeclaredMethod("getITelephony");
            m.setAccessible(true);
            ITelephony telephonyService;
            telephonyService = (ITelephony) m.invoke(tm);

            telephonyService.silenceRinger();
            telephonyService.answerRingingCall();
            return;
        } catch (Exception e) {
            // ignore
        }

        try {
            Intent buttonDown = new Intent(Intent.ACTION_MEDIA_BUTTON);
            buttonDown.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK));
            sendOrderedBroadcast(buttonDown, "android.permission.CALL_PRIVILEGED");

            Intent buttonUp = new Intent(Intent.ACTION_MEDIA_BUTTON);
            buttonUp.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HEADSETHOOK));
            sendOrderedBroadcast(buttonUp, "android.permission.CALL_PRIVILEGED");
        } catch (Exception e) {
            // ignore
        }

        try {
            Intent headSetUnPluggedintent = new Intent(Intent.ACTION_HEADSET_PLUG);
            headSetUnPluggedintent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
            headSetUnPluggedintent.putExtra("state", 0);
            headSetUnPluggedintent.putExtra("name", "Headset");
            sendOrderedBroadcast(headSetUnPluggedintent, null);
        } catch (Exception e) {
            // ignore
        }
    }

    void callReject() {
        ITelephony telephonyService;
        TelephonyManager telephony = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        try {
            Class c = Class.forName(telephony.getClass().getName());
            Method m = c.getDeclaredMethod("getITelephony");
            m.setAccessible(true);
            telephonyService = (ITelephony) m.invoke(telephony);
            telephonyService.endCall();
        } catch (Exception ex) {
            // ignore
        }
    }

    void switchToCG() {
        if (!isActiveCG(getApplicationContext())) {
            try {
                Intent intent = getPackageManager().getLaunchIntentForPackage(State.CG_PACKAGE);
                if (intent != null)
                    startActivity(intent);
            } catch (Exception ex) {
                // ignore
            }
        }
    }

    void switchToPhone() {
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
        showInactiveOverlay();
    }

    abstract class OverlayTouchListener implements View.OnTouchListener {

        abstract void click();

        void setup() {
            setupPhoneButton();
        }

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
                            setup();
                        }
                    };
                    setupTimer.start();
                    button_x = event.getX();
                    button_y = event.getY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (setup_button)
                        moveButton((int) (event.getRawX() - button_x), (int) (event.getRawY() - button_y));
                    break;

                case MotionEvent.ACTION_UP:
                    if (setup_button) {
                        cancelSetup();
                        break;
                    }
                    cancelSetup();
                    click();
                    break;
                case MotionEvent.ACTION_CANCEL:
                    cancelSetup();
                    break;
            }
            return false;
        }
    }

    boolean isLandscape() {
        int rotation = Surface.ROTATION_0;
        try {
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            Display getOrient = wm.getDefaultDisplay();
            Class conmanClass = Class.forName(getOrient.getClass().getName());
            final Method[] methods = conmanClass.getDeclaredMethods();
            for (final Method method : methods) {
                if (method.getName().equals("getRotation"))
                    rotation = (Integer) method.invoke(getOrient);
            }
        } catch (Exception ex) {
            // ignore
        }
        return ((rotation == Surface.ROTATION_90) || (rotation == Surface.ROTATION_270));
    }

    int[] getHudPosition(SharedPreferences preferences) {
        int[] res = new int[2];
        res[0] = preferences.getInt(State.PHONE_X, 50);
        res[1] = preferences.getInt(State.PHONE_Y, 50);
        if (isLandscape()) {
            res[0] = preferences.getInt(State.PHONE_LAND_X, res[0]);
            res[1] = preferences.getInt(State.PHONE_LAND_Y, res[1]);
        }
        return res;
    }

    void showNotification(Intent intent) {

        String title = intent.getStringExtra(State.TITLE);
        String info = intent.getStringExtra(State.INFO);
        String text = intent.getStringExtra(State.TEXT);
        final String app = intent.getStringExtra(State.APP);
        int icon = intent.getIntExtra(State.ICON, 0);

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (preferences.getBoolean(State.SHOW_SMS, false) && app.equals("com.android.mms"))
            return;

        if ((hudActive != null) || (hudInactive != null))
            return;

        hideNotification();

        if (text == null)
            return;

        int timeout = preferences.getInt(State.NOTIFICATION, 10);
        if (timeout <= 0)
            return;

        int[] position = getHudPosition(preferences);
        layoutParams.x = position[0];
        layoutParams.y = position[1];

        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        hudNotification = inflater.inflate(R.layout.notification, null);

        hudNotification.setOnTouchListener(new OverlayTouchListener() {
            @Override
            void click() {
                hideNotification();
            }

            @Override
            void setup() {
                hideNotification();
                Intent intent = new Intent(OnExitService.this, NotificationIgnore.class);
                intent.putExtra(State.APP, app);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        });

        TextView tvTitle = (TextView) hudNotification.findViewById(R.id.title);
        if (title != null)
            tvTitle.setText(title);
        TextView tvInfo = (TextView) hudNotification.findViewById(R.id.info);
        if (info != null)
            tvInfo.setText(info);
        TextView tvText = (TextView) hudNotification.findViewById(R.id.text);
        tvText.setText(text);
        ImageView ivIcon = (ImageView) hudNotification.findViewById(R.id.icon);
        if (app != null) {
            try {
                Context remotePackageContext = createPackageContext(app, 0);
                ivIcon.setImageDrawable(remotePackageContext.getResources().getDrawable(icon));
            } catch (Exception ex) {
                // ignore
            }
        }


        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        wm.addView(hudNotification, layoutParams);

        setForeground();

        notificationTimer = new CountDownTimer(timeout * 1000, timeout * 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                hideNotification();
                showApps();
            }

            @Override
            public void onFinish() {
                hideNotification();
                showApps();
            }
        };
        notificationTimer.start();
    }

    void showSMS(Intent intent) {
        if ((hudActive != null) || (hudInactive != null))
            return;

        hideNotification();

        Object[] pduArray = (Object[]) intent.getExtras().get("pdus");
        SmsMessage[] messages = new SmsMessage[pduArray.length];
        for (int i = 0; i < pduArray.length; i++) {
            messages[i] = SmsMessage.createFromPdu((byte[]) pduArray[i]);
        }
        final String sms_from = messages[0].getOriginatingAddress();
        String title = sms_from;
        StringBuilder bodyText = new StringBuilder();
        for (SmsMessage m : messages) {
            bodyText.append(m.getMessageBody());
        }
        final String body = bodyText.toString();
        if (body.equals(""))
            return;

        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(sms_from));
        ContentResolver contentResolver = getContentResolver();
        Cursor contactLookup = contentResolver.query(uri, new String[]{BaseColumns._ID,
                ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);

        Bitmap photo = null;
        try {
            if (contactLookup != null && contactLookup.getCount() > 0) {
                contactLookup.moveToNext();
                title = contactLookup.getString(contactLookup.getColumnIndex(ContactsContract.Data.DISPLAY_NAME));
                long id = contactLookup.getLong(contactLookup.getColumnIndex(BaseColumns._ID));
                Uri photo_uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id);
                InputStream input = ContactsContract.Contacts.openContactPhotoInputStream(contentResolver, photo_uri);
                if (input != null)
                    photo = BitmapFactory.decodeStream(input);
            }
        } finally {
            if (contactLookup != null) {
                contactLookup.close();
            }
        }

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        int timeout = preferences.getInt(State.NOTIFICATION, 10);
        if (timeout <= 0)
            return;

        int[] position = getHudPosition(preferences);
        layoutParams.x = position[0];
        layoutParams.y = position[1];

        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        hudNotification = inflater.inflate(R.layout.notification, null);

        hudNotification.setOnTouchListener(new OverlayTouchListener() {
            @Override
            void click() {
                hideNotification();
                try {
                    Uri uri = Uri.parse("content://sms/inbox");
                    String selection = "address = ? AND body = ? AND read = ?";
                    String[] selectionArgs = {sms_from, body, "0"};
                    ContentValues values = new ContentValues();
                    values.put("read", true);
                    getContentResolver().update(uri, values, selection, selectionArgs);
                } catch (Exception ex) {
                    // ignore
                }
            }

        });

        TextView tvTitle = (TextView) hudNotification.findViewById(R.id.title);
        tvTitle.setText(title);
        TextView tvText = (TextView) hudNotification.findViewById(R.id.text);
        tvText.setText(body);
        ImageView ivIcon = (ImageView) hudNotification.findViewById(R.id.icon);
        if (photo != null)
            ivIcon.setImageBitmap(photo);

        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        wm.addView(hudNotification, layoutParams);

        setForeground();

        notificationTimer = new CountDownTimer(timeout * 1000, timeout * 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                hideNotification();
                showApps();
            }

            @Override
            public void onFinish() {
                hideNotification();
                showApps();
            }
        };
        notificationTimer.start();
    }

    static float size = 0;

    boolean isBig() {
        if (size == 0) {
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            Display display = wm.getDefaultDisplay();
            DisplayMetrics outMetrics = new DisplayMetrics();
            display.getMetrics(outMetrics);

            float density = getResources().getDisplayMetrics().density;
            size = (outMetrics.heightPixels + outMetrics.widthPixels) / density;
        }
        return (size > 1500);
    }

    boolean yandex_error;

    void showApps() {
        if ((hudActive != null) || (hudNotification != null) || (hudInactive != null) || (hudApps != null))
            return;
        if (!isActiveCG(this))
            return;
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (apps.size() == 0)
            return;

        boolean full = false;
        if (apps.size() > 1) {
            long full_time = preferences.getLong(State.FULL_TIME, 0);
            full = full_time > new Date().getTime();
        }
        final boolean isFull = full;

        int[] position = getHudPosition(preferences);
        layoutParams.x = position[0];
        layoutParams.y = position[1];

        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        hudApps = inflater.inflate(isBig() ? R.layout.quick_launch_big : R.layout.quick_launch, null);

        WindowManager.LayoutParams lp = layoutParams;
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        ImageView iv = (ImageView) hudApps.findViewById(R.id.icon);
        if (isFull) {
            if (iconListener == null) {
                iconListener = new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        SharedPreferences.Editor ed = preferences.edit();
                        ed.remove(State.FULL_TIME);
                        ed.commit();
                        App app = apps.get((Integer) v.getTag());
                        String[] component = app.name.split("/");
                        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
                        mainIntent.setPackage(component[0]);
                        List<ResolveInfo> infos = pm.queryIntentActivities(mainIntent, 0);
                        for (ResolveInfo info : infos) {
                            if (info.activityInfo == null)
                                continue;
                            if (info.activityInfo.name.equals(component[1])) {
                                Intent intent = new Intent(Intent.ACTION_MAIN, null);
                                intent.setPackage(component[0]);
                                intent.setComponent(new ComponentName(component[0], component[1]));
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                                break;
                            }
                        }
                    }
                };
            }

            ViewGroup row = (ViewGroup) hudApps.findViewById(R.id.row);
            int in_row = 1;

            int width = wm.getDefaultDisplay().getWidth();
            int icon_width = iv.getLayoutParams().width;
            width -= layoutParams.x - icon_width;
            int icons = width * 3 / icon_width / 4;
            if (icons < 3)
                icons = 3;
            int rows = (apps.size() + icons - 1) / icons;
            icons = (apps.size() + rows - 1) / rows;

            for (int i = 1; i < apps.size(); i++) {
                iv.getLayoutParams();
                App app = apps.get(i);
                ImageView img = new ImageView(this);
                if (app.icon == null) {
                    int level = getYandexData();
                    if (level < 0) {
                        img.setImageResource(yandex_error ? R.drawable.error_loading : R.drawable.loading);
                        AnimationDrawable animation = (AnimationDrawable) img.getDrawable();
                        animation.start();
                    } else {
                        img.setImageResource(res[level]);
                    }
                } else {
                    img.setImageDrawable(app.icon);
                }
                img.setTag(i);
                img.setOnClickListener(iconListener);
                ViewGroup.LayoutParams layoutParams = iv.getLayoutParams();
                img.setPadding(layoutParams.width / 6, 0, 0, 0);
                img.setLayoutParams(layoutParams);
                if (++in_row > icons) {
                    in_row = 0;
                    LinearLayout new_row = new LinearLayout(this);
                    new_row.setLayoutParams(row.getLayoutParams());
                    new_row.setOrientation(LinearLayout.HORIZONTAL);
                    row = new_row;
                    ViewGroup group = (ViewGroup) hudApps;
                    group.addView(row);
                }
                row.addView(img);
            }
            lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.LEFT;
            lp.x = layoutParams.x;
            lp.y = layoutParams.y;
            hudApps.setBackgroundResource(R.drawable.call);
            iv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    SharedPreferences.Editor ed = preferences.edit();
                    ed.remove(State.FULL_TIME);
                    ed.commit();
                    startApp();
                }
            });
        } else {
            int alpha = preferences.getInt(State.QUICK_ALPHA, 0) * 255 / 100;
            iv.setAlpha(255 - alpha);
        }

        hudApps.setOnTouchListener(new OverlayTouchListener() {
            @Override
            void click() {
                if (apps.size() > 1) {
                    if (isFull) {
                        SharedPreferences.Editor ed = preferences.edit();
                        ed.remove(State.FULL_TIME);
                        ed.commit();
                        hideApps();
                        showApps();
                        return;
                    }
                    SharedPreferences.Editor ed = preferences.edit();
                    ed.putLong(State.FULL_TIME, new Date().getTime() + 10000);
                    ed.commit();
                    hideApps();
                    showApps();
                    CountDownTimer timer = new CountDownTimer(10000, 10000) {
                        @Override
                        public void onTick(long millisUntilFinished) {

                        }

                        @Override
                        public void onFinish() {
                            hideApps();
                            showApps();
                        }
                    };
                    timer.start();
                    return;
                }
                startApp();
            }

            @Override
            void setup() {
                if ((apps.size() > 1) && isFull)
                    return;
                super.setup();
            }
        });

        Drawable icon = apps.get(0).icon;
        if (icon == null) {
            int level = getYandexData();
            if (level < 0) {
                iv.setImageResource(yandex_error ? R.drawable.error_loading : R.drawable.loading);
                AnimationDrawable animation = (AnimationDrawable) iv.getDrawable();
                animation.start();
            } else {
                iv.setImageResource(res[level]);
            }
        } else {
            iv.setImageDrawable(icon);
        }
        wm.addView(hudApps, lp);

        setForeground();
    }

    void showActiveOverlay() {
        if (hudActive != null)
            return;
        hideInactiveOverlay();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (!preferences.getBoolean(State.PHONE_SHOW, false))
            return;

        int[] position = getHudPosition(preferences);
        layoutParams.x = position[0];
        layoutParams.y = position[1];

        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        hudActive = inflater.inflate(R.layout.call, null);
        TextView number = (TextView) hudActive.findViewById(R.id.number);
        number.setText(getNumber());

        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(call_number));
        ContentResolver contentResolver = getContentResolver();
        Cursor contactLookup = contentResolver.query(uri, new String[]{BaseColumns._ID,
                ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);

        contactPhoto = null;
        try {
            if (contactLookup != null && contactLookup.getCount() > 0) {
                contactLookup.moveToNext();
                String name = contactLookup.getString(contactLookup.getColumnIndex(ContactsContract.Data.DISPLAY_NAME));
                TextView tvName = (TextView) hudActive.findViewById(R.id.name);
                tvName.setText(name);
                long id = contactLookup.getLong(contactLookup.getColumnIndex(BaseColumns._ID));
                Uri photo_uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id);
                InputStream input = ContactsContract.Contacts.openContactPhotoInputStream(contentResolver, photo_uri);
                if (input != null)
                    contactPhoto = BitmapFactory.decodeStream(input);
            }
        } finally {
            if (contactLookup != null) {
                contactLookup.close();
            }
        }

        ImageView ivPhoto = (ImageView) hudActive.findViewById(R.id.photo);
        if (contactPhoto != null) {
            ivPhoto.setImageBitmap(contactPhoto);
        } else if (ringing) {
            ivPhoto.setVisibility(View.GONE);
        }

        if (!ringing) {
            ivPhoto.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            if (contactPhoto != null) {
                                ImageView ivPhoto = (ImageView) hudActive.findViewById(R.id.photo);
                                ivPhoto.setImageResource(R.drawable.reject);
                            }
                            break;
                        case MotionEvent.ACTION_UP:
                            callReject();
                            break;
                        case MotionEvent.ACTION_CANCEL:
                            if (contactPhoto != null) {
                                ImageView ivPhoto = (ImageView) hudActive.findViewById(R.id.photo);
                                ivPhoto.setImageBitmap(contactPhoto);
                            }
                    }
                    return true;
                }
            });
        }

        hudActive.setOnTouchListener(new OverlayTouchListener() {
            @Override
            void click() {
                switchToPhone();
            }
        });

        View phone = hudActive.findViewById(R.id.phone);
        if (ringing) {
            ImageView ivAnswer = (ImageView) hudActive.findViewById(R.id.answer);
            ivAnswer.setClickable(true);
            ivAnswer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    callAnswer();
                    ringing = false;
                    hideOverlays(OnExitService.this);
                    switchToCG();
                }
            });

            ImageView ivReject = (ImageView) hudActive.findViewById(R.id.reject);
            ivReject.setClickable(true);
            ivReject.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    callReject();
                    ringing = false;
                    hideOverlays(OnExitService.this);
                    switchToCG();
                }
            });

            ImageView ivSms = (ImageView) hudActive.findViewById(R.id.sms);
            ivSms.setClickable(true);
            ivSms.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    callReject();
                    ringing = false;
                    hideOverlays(OnExitService.this);
                    switchToCG();
                    String message = getRejectMessage();
                    SmsManager smsManager = SmsManager.getDefault();
                    smsManager.sendTextMessage(call_number, null, message, null, null);
                }
            });
        } else {
            phone.setVisibility(View.GONE);
        }


        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        wm.addView(hudActive, layoutParams);

        setForeground();
    }

    String getRejectMessage() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        return preferences.getString(State.SMS, getString(R.string.def_sms));
    }

    String getNumber() {
        if ((call_number != null) && !call_number.equals(""))
            return PhoneNumberUtils.formatNumber(call_number);
        return getString(R.string.unknown);
    }

    void showInactiveOverlay() {
        if (hudInactive != null)
            return;
        hideActiveOverlay();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (!(preferences.getBoolean(State.PHONE_SHOW, false) && show_overlay) && (apps.size() == 0))
            return;

        int[] position = getHudPosition(preferences);
        layoutParams.x = position[0];
        layoutParams.y = position[1];

        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        hudInactive = inflater.inflate(R.layout.icon, null);
        try {
            ImageView ivIcon = (ImageView) hudInactive.findViewById(R.id.photo);
            PackageManager manager = getPackageManager();
            ivIcon.setImageDrawable(manager.getApplicationIcon(State.CG_PACKAGE));
        } catch (Exception ex) {
            // ignore
        }

        hudInactive.setOnTouchListener(new OverlayTouchListener() {
            @Override
            void click() {
                try {
                    Intent intent = getPackageManager().getLaunchIntentForPackage(State.CG_PACKAGE);
                    if (intent != null)
                        startActivity(intent);
                    if (show_overlay) {
                        showActiveOverlay();
                    } else {
                        hideInactiveOverlay();
                        showApps();
                    }
                } catch (Exception ex) {
                    // ignore
                }
            }
        });

        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        wm.addView(hudInactive, layoutParams);

        setForeground();
    }

    void setForeground() {
        if (foreground)
            return;
        foreground = true;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.addAction(R.drawable.ic_launcher, getNumber(), createPendingIntent(PHONE));
        startForeground(NOTIFICATION_ID, builder.build());
    }

    void moveButton(int x, int y) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor ed = preferences.edit();
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (isLandscape()) {
            ed.putInt(State.PHONE_LAND_X, x);
            ed.putInt(State.PHONE_LAND_Y, y);
        } else {
            ed.putInt(State.PHONE_X, x);
            ed.putInt(State.PHONE_Y, y);
        }
        ed.commit();
        if ((hudActive == null) && (hudInactive == null) && (hudApps == null))
            return;

        layoutParams.x = x;
        layoutParams.y = y;
        if (hudActive != null)
            wm.updateViewLayout(hudActive, layoutParams);
        if (hudInactive != null)
            wm.updateViewLayout(hudInactive, layoutParams);
        if (hudApps != null)
            wm.updateViewLayout(hudApps, layoutParams);
    }

    void setupPhoneButton() {
        cancelSetup();
        LinearLayout layout = null;
        if (hudActive != null)
            layout = (LinearLayout) hudActive;
        if (hudInactive != null)
            layout = (LinearLayout) hudInactive;
        if (hudApps != null)
            layout = (LinearLayout) hudApps;
        if (layout == null)
            return;
        try {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            vibrator.vibrate(800);
        } catch (Exception ex) {
            // ignore
        }
        layout.setBackgroundResource(R.drawable.setup_call);
        setup_button = true;
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        wm.updateViewLayout(layout, layoutParams);
    }

    void cancelSetup() {
        if (setup_button) {
            LinearLayout layout = null;
            if (hudActive != null)
                layout = (LinearLayout) hudActive;
            if (hudInactive != null)
                layout = (LinearLayout) hudInactive;
            layoutParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
            if (layout != null) {
                WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                wm.updateViewLayout(layout, layoutParams);
                layout.setBackgroundResource(R.drawable.call);
            }
            setup_button = false;
            if (hudApps != null) {
                hideApps();
                showApps();
            }
        }
        if (setupTimer != null) {
            setupTimer.cancel();
            setupTimer = null;
        }
    }

    void hideActiveOverlay() {
        hideNotification();
        cancelSetup();
        if (hudActive == null)
            return;
        ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(hudActive);
        hudActive = null;
    }

    void hideInactiveOverlay() {
        cancelSetup();
        if (hudInactive == null)
            return;
        ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(hudInactive);
        hudInactive = null;
    }

    void hideNotification() {
        hideApps();
        if (notificationTimer != null) {
            notificationTimer.cancel();
            notificationTimer = null;
        }
        if (hudNotification == null)
            return;
        ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(hudNotification);
        hudNotification = null;
    }

    void hideApps() {
        cancelSetup();
        if (hudApps == null)
            return;
        ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(hudApps);
        hudApps = null;
    }

    void hideOverlays(Context context) {
        hideActiveOverlay();
        hideInactiveOverlay();
        hideNotification();
        if ((context != null) && isActiveCG(context)) {
            showApps();
        } else {
            hideApps();
        }
    }

    static void turnOnBT(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (preferences.getBoolean(State.BT, false)) {
            try {
                BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
                if ((bt != null) && !bt.isEnabled()) {
                    bt.enable();
                    SharedPreferences.Editor ed = preferences.edit();
                    ed.putString(State.BT_DEVICES, "-");
                    ed.commit();
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    static void turnOffBT(Context context, String device) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String devices_str = preferences.getString(State.BT_DEVICES, "");
        if (devices_str.equals(""))
            return;
        String[] devices = devices_str.split(";");
        String d = null;
        for (String dev : devices) {
            if (dev.equals(device))
                continue;
            if (d == null) {
                d = dev;
            } else {
                d += ";" + dev;
            }
        }
        if (d != null) {
            SharedPreferences.Editor ed = preferences.edit();
            ed.putString(State.BT_DEVICES, d);
            ed.commit();
            return;
        }
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
        ed.remove(State.BT_DEVICES);
        ed.commit();
    }

    static int prev_state;
    static boolean cg_run;

    void stopAutoAnswer() {
        if (piAnswer != null) {
            alarmMgr.cancel(piAnswer);
            piAnswer = null;
        }
        if (piRinging != null) {
            alarmMgr.cancel(piRinging);
            piRinging = null;
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
            autoswitch = Integer.parseInt(preferences.getString(State.RINGING_TIME, "-1")) * 1000 + 1;
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
                    switch (state) {
                        case TelephonyManager.CALL_STATE_OFFHOOK:
                            stopAutoAnswer();
                            stopAfterCall();
                            if (phone) {
                                show_overlay = true;
                                ringing = false;
                                hideOverlays(null);
                                showActiveOverlay();
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
                                showActiveOverlay();
                            }
                            if (speaker) {
                                AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                                if (!audio.isBluetoothScoOn() && !audio.isWiredHeadsetOn())
                                    audio.setSpeakerphoneOn(true);
                            }
                            break;
                        case TelephonyManager.CALL_STATE_RINGING:
                            stopAfterCall();
                            hideOverlays(null);
                            show_overlay = true;
                            ringing = true;
                            showInactiveOverlay();
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
                            if (autoswitch > 0) {
                                if (piRinging == null)
                                    piRinging = createPendingIntent(RINGING);
                                alarmMgr.setRepeating(AlarmManager.RTC,
                                        System.currentTimeMillis() + autoswitch, autoswitch, piRinging);
                            }
                            break;
                        case TelephonyManager.CALL_STATE_IDLE:
                            stopAfterCall();
                            stopAutoAnswer();
                            hideOverlays(OnExitService.this);
                            call_number = null;
                            show_overlay = false;
                            if (foreground)
                                stopForeground(true);
                            ringing = false;
                            if (prev_state == TelephonyManager.CALL_STATE_IDLE)
                                break;
                            if (phone) {
                                if (isRunCG(getApplicationContext())) {
                                    switchToCG();
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

    static boolean isRun(Context context, String pkg_name) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> procInfos = activityManager.getRunningAppProcesses();
        if (procInfos == null)
            return false;
        int i;
        for (i = 0; i < procInfos.size(); i++) {
            ActivityManager.RunningAppProcessInfo proc = procInfos.get(i);
            if (proc.processName.equals(pkg_name))
                return true;
        }
        return false;
    }

    static boolean isRunCG(Context context) {
        return isRun(context, State.CG_PACKAGE);
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
            // ignore
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
                    // ignore
                }
                return null;
            }
        };
        task.execute();
    }

    static boolean removeOldFile(File f) {
        if (f.isDirectory()) {
            boolean no_remove = false;
            String[] files = f.list();
            for (String file : files) {
                no_remove |= removeOldFile(new File(f, file));
            }
            if (no_remove)
                return true;
            f.delete();
            return false;
        }
        Date now = new Date();
        if (f.lastModified() < now.getTime() - 7 * 24 * 60 * 60 * 1000) {
            f.delete();
            return false;
        }
        return true;
    }

    static void removeRTA() {
        AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    File rta = Environment.getExternalStorageDirectory();
                    rta = new File(rta, "CityGuide/RtaLog");
                    String[] files = rta.list();
                    for (String file : files) {
                        removeOldFile(new File(rta, file));
                    }
                } catch (Exception ex) {
                    // ignore
                }
                return null;
            }
        };
        task.execute();
    }

    class PingTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... params) {
            try {
                if (show_overlay)
                    return null;
                ConnectivityManager conman = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo activeNetwork = conman.getActiveNetworkInfo();
                if ((activeNetwork == null) || !activeNetwork.isConnectedOrConnecting()) {
                    Class conmanClass = Class.forName(conman.getClass().getName());
                    Field iConnectivityManagerField = conmanClass.getDeclaredField("mService");
                    iConnectivityManagerField.setAccessible(true);
                    Object iConnectivityManager = iConnectivityManagerField.get(conman);
                    Class iConnectivityManagerClass = Class.forName(iConnectivityManager.getClass().getName());

                    Method getMobileDataEnabledMethod = iConnectivityManagerClass.getDeclaredMethod("getMobileDataEnabled");
                    getMobileDataEnabledMethod.setAccessible(true); // Make the method callable

                    if (!(Boolean) getMobileDataEnabledMethod.invoke(iConnectivityManager)) {
                        Method setMobileDataEnabledMethod = iConnectivityManagerClass.getDeclaredMethod("setMobileDataEnabled", Boolean.TYPE);
                        setMobileDataEnabledMethod.setAccessible(true);
                        setMobileDataEnabledMethod.invoke(iConnectivityManager, true);
                        Thread.sleep(5000);
                    }
                    return null;
                }
                Runtime runtime = Runtime.getRuntime();
                for (int i = 0; i < 6; i++) {
                    Process mIpAddrProcess = runtime.exec("/system/bin/ping -c 1 8.8.8.8");
                    int exitValue = mIpAddrProcess.waitFor();
                    if (exitValue == 0)
                        return null;
                    Thread.sleep(2000);
                }
                Process mIpAddrProcess = runtime.exec("/system/bin/ping -c 1 8.8.8.8");
                int exitValue = mIpAddrProcess.waitFor();
                if (exitValue == 0)
                    return null;
                activeNetwork = conman.getActiveNetworkInfo();
                if ((activeNetwork == null) || !activeNetwork.isConnectedOrConnecting())
                    return null;
                TelephonyManager tel = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
                if ((tel.getNetworkOperator() != null) && !tel.getNetworkOperator().equals("")) {
                    setAirplaneMode(true);
                    setAirplaneMode(false);
                    Thread.sleep(20000);
                }
            } catch (Exception ex) {
                // ignore
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            pingTask = null;
        }
    }

    public void initLocation() {

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        netListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                locationChanged(location);
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {

            }

            @Override
            public void onProviderEnabled(String provider) {

            }

            @Override
            public void onProviderDisabled(String provider) {

            }
        };

        gpsListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                locationChanged(location);
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {

            }

            @Override
            public void onProviderEnabled(String provider) {

            }

            @Override
            public void onProviderDisabled(String provider) {

            }
        };

        currentBestLocation = getLastBestLocation();

        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 10, gpsListener);
        } catch (Exception ex) {
            gpsListener = null;
        }

        try {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 10, netListener);
        } catch (Exception ex) {
            netListener = null;
        }

        showApps();
    }

    class HttpTask extends AsyncTask<Void, Void, Integer> {

        BroadcastReceiver br;

        @Override
        protected Integer doInBackground(Void... params) {
            if (currentBestLocation == null)
                return null;
            HttpClient httpclient = new DefaultHttpClient();
            Reader reader = null;
            try {
                HttpResponse response = httpclient.execute(new HttpGet(TRAFFIC_URL));
                StatusLine statusLine = response.getStatusLine();
                int status = statusLine.getStatusCode();
                reader = new InputStreamReader(response.getEntity().getContent());
                JsonObject result = JsonObject.readFrom(reader);
                reader.close();
                reader = null;
                if (status != HttpStatus.SC_OK)
                    return null;
                result = result.get("GeoObjectCollection").asObject();
                JsonArray data = result.get("features").asArray();
                int length = data.size();
                for (int i = 0; i < length; i++) {
                    result = data.get(i).asObject();
                    JsonObject jams = result.get("properties").asObject();
                    jams = jams.get("JamsMetaData").asObject();
                    JsonValue lvl = jams.get("level");
                    if (lvl == null)
                        continue;
                    int level = lvl.asInt();
                    result = result.get("geometry").asObject();
                    JsonArray coord = result.get("coordinates").asArray();
                    double lat = coord.get(1).asDouble();
                    double lon = coord.get(0).asDouble();
                    double d = calc_distance(lat, lon, currentBestLocation.getLatitude(), currentBestLocation.getLongitude());
                    if (d < 80000)
                        return level + 1;
                }
                return 0;
            } catch (Exception ex) {
                ex.printStackTrace();
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Integer lvl) {
            fetcher = null;
            if (br != null)
                unregisterReceiver(br);
            if (lvl != null) {
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(OnExitService.this);
                long now = new Date().getTime();
                boolean changed = (lvl != preferences.getInt(State.TRAFFIC, 0));
                if (now - preferences.getLong(State.UPD_TIME, 0) > VALID_INTEVAL)
                    changed = true;
                if (yandex_error) {
                    yandex_error = false;
                    changed = true;
                }
                SharedPreferences.Editor ed = preferences.edit();
                ed.putInt(State.TRAFFIC, lvl);
                ed.putLong(State.UPD_TIME, now);
                ed.commit();
                if (!setup_button && changed) {
                    hideApps();
                    if (isActiveCG(OnExitService.this))
                        showApps();
                }
            } else {
                setYandexError(true);
            }
        }
    }

    void setYandexError(boolean error) {
        if (error == yandex_error)
            return;
        yandex_error = error;
        long now = new Date().getTime();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(OnExitService.this);
        if (!setup_button && (now - preferences.getLong(State.UPD_TIME, 0) > VALID_INTEVAL)) {
            hideApps();
            if (isActiveCG(OnExitService.this))
                showApps();
        }
    }

    int getYandexData() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        long upd_time = preferences.getLong(State.UPD_TIME, 0);
        long interval = new Date().getTime() - upd_time;
        State.appendLog("fetcher: " + ((fetcher == null) ? "null" : "not null"));
        State.appendLog("interval " + interval);
        if (currentBestLocation == null) {
            State.appendLog("location unknown");
        } else {
            State.appendLog("? " + currentBestLocation.getLatitude() + "," + currentBestLocation.getLongitude());
        }
        if ((fetcher == null) && (interval > UPD_INTERVAL) && (currentBestLocation != null)) {
            fetcher = new HttpTask();
            final ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            if ((activeNetwork != null) && activeNetwork.isConnected()) {
                yandex_error = false;
                State.appendLog("Start yandex fetcher");
                fetcher.execute();
            } else {
                fetcher.br = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                        if ((activeNetwork != null) && activeNetwork.isConnected()) {
                            if (fetcher.br != null) {
                                unregisterReceiver(fetcher.br);
                                fetcher.br = null;
                            }
                            setYandexError(false);
                            fetcher.execute();
                        }
                    }
                };
                yandex_error = true;
                registerReceiver(fetcher.br, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
            }
        }
        if (interval > VALID_INTEVAL)
            return -1;
        return preferences.getInt(State.TRAFFIC, 0);
    }

    static final int TWO_MINUTES = 1000 * 60 * 2;

    Location getLastBestLocation() {
        Location locationGPS = null;
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                locationGPS = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        } catch (Exception ex) {
            // ignore
        }
        Location locationNet = null;
        try {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                locationNet = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        } catch (Exception ex) {
            // ignore
        }
        long GPSLocationTime = 0;
        Date now = new Date();
        if (locationGPS != null)
            GPSLocationTime = locationGPS.getTime();
        long NetLocationTime = 0;
        if (locationNet != null)
            NetLocationTime = locationNet.getTime();
        if (GPSLocationTime > NetLocationTime)
            return locationGPS;
        return locationNet;
    }

    public void locationChanged(Location location) {
        if (isBetterLocation(location, currentBestLocation))
            currentBestLocation = location;
        if (currentBestLocation == null)
            return;
        getYandexData();
    }

    protected boolean isBetterLocation(Location location, Location currentBestLocation) {
        if (currentBestLocation == null)
            return true;

        long timeDelta = location.getTime() - currentBestLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
        boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
        boolean isNewer = timeDelta > 0;

        // If it's been more than two minutes since the current location, use the new location
        // because the user has likely moved
        if (isSignificantlyNewer) {
            return true;
            // If the new location is more than two minutes older, it must be worse
        } else if (isSignificantlyOlder) {
            return false;
        }

        // Check whether the new location fix is more or less accurate
        int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;

        // Check if the old and new location are from the same provider
        boolean isFromSameProvider = isSameProvider(location.getProvider(),
                currentBestLocation.getProvider());

        // Determine location quality using a combination of timeliness and accuracy
        if (isMoreAccurate) {
            return true;
        } else if (isNewer && !isLessAccurate) {
            return true;
        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
            return true;
        }
        return false;
    }

    private boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null)
            return provider2 == null;
        return provider1.equals(provider2);
    }

    static final double D2R = 0.017453; // Константа для преобразования градусов в радианы
    static final double a = 6378137.0; // Основные полуоси
    static final double e2 = 0.006739496742337; // Квадрат эксцентричности эллипсоида

    static double calc_distance(double lat1, double lon1, double lat2, double lon2) {

        if ((lat1 == lat2) && (lon1 == lon2))
            return 0;

        double fdLambda = (lon1 - lon2) * D2R;
        double fdPhi = (lat1 - lat2) * D2R;
        double fPhimean = ((lat1 + lat2) / 2.0) * D2R;

        double fTemp = 1 - e2 * (Math.pow(Math.sin(fPhimean), 2));
        double fRho = (a * (1 - e2)) / Math.pow(fTemp, 1.5);
        double fNu = a / (Math.sqrt(1 - e2 * (Math.sin(fPhimean) * Math.sin(fPhimean))));

        double fz = Math.sqrt(Math.pow(Math.sin(fdPhi / 2.0), 2) +
                Math.cos(lat2 * D2R) * Math.cos(lat1 * D2R) * Math.pow(Math.sin(fdLambda / 2.0), 2));
        fz = 2 * Math.asin(fz);

        double fAlpha = Math.cos(lat1 * D2R) * Math.sin(fdLambda) * 1 / Math.sin(fz);
        fAlpha = Math.asin(fAlpha);

        double fR = (fRho * fNu) / ((fRho * Math.pow(Math.sin(fAlpha), 2)) + (fNu * Math.pow(Math.cos(fAlpha), 2)));

        return fz * fR;
    }

    static void enableMobileData(Context context, boolean enable) {
        try {
            ConnectivityManager conman = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            Class conmanClass = Class.forName(conman.getClass().getName());
            final Method[] methods = conmanClass.getDeclaredMethods();
            for (final Method method : methods) {
                if (method.getName().equals("setMobileDataEnabled")) {
                    method.invoke(conman, enable);
                    return;
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    static boolean getMobileDataEnabled(Context context) {
        try {
            ConnectivityManager conman = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            Class conmanClass = Class.forName(conman.getClass().getName());
            final Method[] methods = conmanClass.getDeclaredMethods();
            for (final Method method : methods) {
                if (method.getName().equals("getMobileDataEnabled"))
                    return (Boolean) method.invoke(conman);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return false;
    }

    void startApp() {
        App app = apps.get(0);
        if (app.icon != null) {
            String[] component = app.name.split("/");
            Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
            mainIntent.setPackage(component[0]);
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> infos = pm.queryIntentActivities(mainIntent, 0);
            for (ResolveInfo info : infos) {
                if (info.activityInfo == null)
                    continue;
                if (info.activityInfo.name.equals(component[1])) {
                    Intent intent = new Intent(Intent.ACTION_MAIN, null);
                    intent.setPackage(component[0]);
                    intent.addCategory(Intent.CATEGORY_LAUNCHER);
                    intent.setComponent(new ComponentName(component[0], component[1]));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    break;
                }
            }
            return;
        }
        startYan(this);
    }

    static void startYan(Context context) {
        Intent intent = new Intent("ru.yandex.yandexnavi.action.BUILD_ROUTE_ON_MAP");
        intent.setPackage("ru.yandex.yandexnavi");
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> infos = pm.queryIntentActivities(intent, 0);
        if ((infos != null) && (infos.size() > 0)) {
            double finish_lat = 0;
            double finish_lon = 0;
            try {
                File poi = Environment.getExternalStorageDirectory();
                poi = new File(poi, "CityGuide/routes.dat");
                BufferedReader reader = new BufferedReader(new FileReader(poi));
                reader.readLine();
                boolean current = false;
                while (true) {
                    String line = reader.readLine();
                    if (line == null)
                        break;
                    String[] parts = line.split("\\|");
                    if (parts.length == 0)
                        continue;
                    String name = parts[0];
                    if ((name.length() > 0) && (name.substring(0, 1).equals("#"))) {
                        current = name.equals("#[CURRENT]");
                        continue;
                    }
                    if (current && name.equals("Finish")) {
                        finish_lat = Double.parseDouble(parts[1]);
                        finish_lon = Double.parseDouble(parts[2]);
                    }
                }
                reader.close();
            } catch (Exception ex) {
                // ignore
            }

            if ((finish_lat == yandex_finish_lat) && (finish_lon == yandex_finish_lon)) {
                try {
                    intent = pm.getLaunchIntentForPackage("ru.yandex.yandexnavi");
                } catch (Exception ex) {
                    // ignore
                }
            } else {
                if (currentBestLocation != null) {
                    intent.putExtra("lat_from", currentBestLocation.getLatitude());
                    intent.putExtra("lon_from", currentBestLocation.getLongitude());
                }
                intent.putExtra("lat_to", finish_lat);
                intent.putExtra("lon_to", finish_lon);
                yandex_finish_lat = finish_lat;
                yandex_finish_lon = finish_lon;
            }
            if (intent == null)
                return;
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }
}
