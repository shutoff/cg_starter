package ru.shutoff.cgstarter;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.FileObserver;
import android.os.IBinder;
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
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.internal.telephony.ITelephony;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.List;

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

    TelephonyManager tm;

    boolean phone;
    boolean speaker;
    boolean ringing;

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

    CountDownTimer setupTimer;
    CountDownTimer notificationTimer;

    boolean setup_button;

    FileObserver observer;
    String screenshots_path;

    PingTask pingTask;
    BroadcastReceiver br;

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
                    if ((path.length() > 4) && path.substring(path.length() - 4).equals(".bmp")) {
                        convertToPng(screenshots_path + "/" + path);
                    }
                }
            };
        } catch (Exception ex) {
            // ignore
        }
        observer.startWatching();
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
        hideOverlays();
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
                stopSelf();
            } else {
                setPhoneListener();
                if (show_overlay) {
                    if (isActiveCG(this)) {
                        showActiveOverlay();
                    } else {
                        showInactiveOverlay();
                    }
                }
                if (preferences.getBoolean(State.PING, false))
                    ping();
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
                    click();
                    break;
                case MotionEvent.ACTION_CANCEL:
                    cancelSetup();
                    break;
            }
            return false;
        }
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
        wm.addView(hudNotification, params);

        setForeground();

        notificationTimer = new CountDownTimer(timeout * 1000, timeout * 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                hideNotification();
            }

            @Override
            public void onFinish() {
                hideNotification();
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
        wm.addView(hudNotification, params);

        setForeground();

        notificationTimer = new CountDownTimer(timeout * 1000, timeout * 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                hideNotification();
            }

            @Override
            public void onFinish() {
                hideNotification();
            }
        };
        notificationTimer.start();
    }

    void showActiveOverlay() {
        if (hudActive != null)
            return;
        hideInactiveOverlay();
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
                    hideOverlays();
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
                    hideOverlays();
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
                    hideOverlays();
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
        wm.addView(hudActive, params);

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
        hudInactive = inflater.inflate(R.layout.call, null);
        hudInactive.findViewById(R.id.number).setVisibility(View.GONE);
        hudInactive.findViewById(R.id.name).setVisibility(View.GONE);
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
                    showActiveOverlay();
                } catch (Exception ex) {
                    // ignore
                }
            }
        });

        hudInactive.findViewById(R.id.phone).setVisibility(View.GONE);

        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        wm.addView(hudInactive, params);

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

    void moveButton(int dx, int dy) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        int x = preferences.getInt(State.PHONE_X, 50) + dx;
        int y = preferences.getInt(State.PHONE_Y, 50) + dy;
        SharedPreferences.Editor ed = preferences.edit();
        ed.putInt(State.PHONE_X, x);
        ed.putInt(State.PHONE_Y, y);
        ed.commit();
        if ((hudActive == null) && (hudInactive == null))
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
        if (hudActive != null)
            wm.updateViewLayout(hudActive, params);
        if (hudInactive != null)
            wm.updateViewLayout(hudInactive, params);
    }

    void setupPhoneButton() {
        cancelSetup();
        LinearLayout layout = null;
        if (hudActive != null)
            layout = (LinearLayout) hudActive;
        if (hudInactive != null)
            layout = (LinearLayout) hudInactive;
        if (layout == null)
            return;
        layout.setBackgroundResource(R.drawable.setup_call);
        setup_button = true;
    }

    void cancelSetup() {
        if (setup_button) {
            LinearLayout layout = null;
            if (hudActive != null)
                layout = (LinearLayout) hudActive;
            if (hudInactive != null)
                layout = (LinearLayout) hudInactive;
            if (layout != null)
                layout.setBackgroundResource(R.drawable.call);
            setup_button = false;
        }
        if (setupTimer != null) {
            setupTimer.cancel();
            setupTimer = null;
        }
        if (notificationTimer != null) {
            notificationTimer.cancel();
            notificationTimer = null;
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
        hideNotification();
        cancelSetup();
        if (hudInactive == null)
            return;
        ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(hudInactive);
        hudInactive = null;
    }

    void hideNotification() {
        cancelSetup();
        if (hudNotification == null)
            return;
        ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(hudNotification);
        hudNotification = null;
    }

    void hideOverlays() {
        hideActiveOverlay();
        hideInactiveOverlay();
        hideNotification();
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
                                hideOverlays();
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
                            hideOverlays();
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
                            hideOverlays();
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
}
