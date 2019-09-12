package ru.shutoff.cgstarter;

import android.annotation.SuppressLint;
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
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
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
import com.eclipsesource.json.Json;
import com.jaredrummler.android.processes.ProcessManager;
import com.jaredrummler.android.processes.models.AndroidAppProcess;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.InvalidParameterException;
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
    final static long UPD_INTERVAL = 3 * 60 * 1000;
    final static long VALID_INTEVAL = 15 * 60 * 1000;
    final static String TRAFFIC_URL = "https://car-online.ugona.net/level?lat=$1&lng=$2";
    final static double K_C = 100000.;
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
    static final int TWO_MINUTES = 1000 * 60 * 2;
    static final double D2R = 0.017453; // Константа для преобразования градусов в радианы
    static final double a = 6378137.0; // Основные полуоси
    static final double e2 = 0.006739496742337; // Квадрат эксцентричности эллипсоида
    static int background_count;
    static String call_number;
    static LocationManager locationManager;
    static boolean force_exit;
    static Location currentBestLocation;
    static int speacker_volume;
    static double yandex_finish_lat;
    static double yandex_finish_lon;
    static float size = 0;
    static int prev_state;
    static boolean cg_run;
    static boolean is_run;

    static ActivityManager mActivityManager;

    AlarmManager alarm;
    PendingIntent pi;
    PendingIntent piAnswer;
    PendingIntent piRinging;
    PendingIntent piAfterCall;
    PhoneStateListener phoneListener;
    WindowManager.LayoutParams layoutParams;
    BroadcastReceiver networkReciever;
    TelephonyManager tm;

    boolean phone;
    boolean speaker;
    boolean ringing;
    boolean landscape;
    int autoanswer;
    int autoswitch;
    float button_x;
    float button_y;
    Bitmap contactPhoto;
    boolean show_overlay;
    boolean foreground;
    boolean inactive_run;
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
    BroadcastReceiver phoneReceiver;
    LocationListener netListener;
    LocationListener gpsListener;
    long fetcher_time;
    long last_run;
    boolean cg_running;
    View.OnClickListener iconListener;
    PackageManager pm;
    Vector<App> apps;
    boolean yandex_error;

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
        SharedPreferences.Editor ed = preferences.edit();
        if (d == null) {
            BluetoothAdapter btAdapter;
            try {
                btAdapter = BluetoothAdapter.getDefaultAdapter();
            } catch (Exception ex) {
                ex.printStackTrace();
                return;
            }
            if (btAdapter == null)
                return;
            if (preferences.getBoolean(State.BT, false))
                btAdapter.disable();
            ed.remove(State.BT_DEVICES);
            return;
        } else {
            ed.putString(State.BT_DEVICES, d);
        }
        ed.commit();
    }

    static boolean isRun(Context context, String pkg_name) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            if (mActivityManager == null)
                mActivityManager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> procInfos = mActivityManager.getRunningAppProcesses();
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
        List<AndroidAppProcess> processes = ProcessManager.getRunningAppProcesses();
        for (AndroidAppProcess process : processes) {
            if (pkg_name.equals(process.name))
                return true;
        }
        return false;
    }

    static boolean isRunCG(Context context) {
        return isRun(context, State.CG_Package(context));
    }

    static boolean isActiveCG(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            if (mActivityManager == null)
                mActivityManager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
            try {
                List<ActivityManager.RunningTaskInfo> appProcesses = mActivityManager.getRunningTasks(1);
                return appProcesses.get(0).topActivity.getPackageName().equals(State.CG_Package(context));
            } catch (Exception ex) {
                // ignore
            }
            return false;
        }
        List<AndroidAppProcess> processes = ProcessManager.getRunningForegroundApps(context);
        String pkg_name = State.CG_Package(context);
        for (AndroidAppProcess process : processes) {
            if (pkg_name.equals(process.name))
                return true;
        }
        return false;
    }

    @SuppressLint("MissingPermission")
    static void convertFile(String bmp_name) {
        try {
            File bmp_file = new File(bmp_name);
            long last_modified = bmp_file.lastModified();
            Bitmap bmp = BitmapFactory.decodeFile(bmp_name);
            String png_name = bmp_name.substring(0, bmp_name.length() - 4);
            if (locationManager != null) {
                Location locationGPS = null;
                try {
                    if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                        locationGPS = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                } catch (Exception ex) {
                    // ignore
                }
                if (locationGPS != null) {
                    png_name += "_";
                    png_name += Math.round(locationGPS.getLatitude() * K_C) / K_C;
                    png_name += "_";
                    png_name += Math.round(locationGPS.getLongitude() * K_C) / K_C;
                }
            }
            png_name += ".png";
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, path);
        } else {
            task.execute(path);
        }
    }

    static void convertFiles(final Context context) {
        AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    File screenshots = State.CG_Folder(context);
                    screenshots = new File(screenshots, "screenshots");
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } else {
            task.execute();
        }
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

    static void removeRTA(final Context context) {
        AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    File rta = State.CG_Folder(context);
                    rta = new File(rta, "RtaLog");
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } else {
            task.execute();
        }
    }

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
            if (enable) {
                try {
                    context.sendBroadcast(new Intent("com.latedroid.juicedefender.action.ENABLE_APN")
                            .putExtra("tag", "cg_starter")
                            .putExtra("reply", true));
                } catch (Exception ex) {
                    // ignore
                }
            }
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

    static void startYan(Context context) {
        Intent intent = new Intent("ru.yandex.yandexnavi.action.BUILD_ROUTE_ON_MAP");
        intent.setPackage("ru.yandex.yandexnavi");
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> infos = pm.queryIntentActivities(intent, 0);
        if ((infos != null) && (infos.size() > 0)) {
            double finish_lat = 0;
            double finish_lon = 0;
            try {
                File poi = State.CG_Folder(context);
                if (State.cg_files) {
                    poi = new File(poi, "routes.dat");
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
                } else {
                    poi = new File(poi, "Routes/Route.curr");
                    BufferedReader reader = new BufferedReader(new FileReader(poi));
                    reader.readLine();
                    while (true) {
                        String line = reader.readLine();
                        if (line == null)
                            break;
                        String[] parts = line.split("\\|");
                        if (parts.length < 4)
                            continue;
                        String name = parts[0];
                        if (name.equals("3")) {
                            finish_lat = Double.parseDouble(parts[2]);
                            finish_lon = Double.parseDouble(parts[3]);
                        }
                    }
                    reader.close();
                }
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

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable ex) {
                State.print(ex);
                ex.printStackTrace();
            }
        });

        is_run = true;
        alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        pi = createPendingIntent(TIMER);
        try {
            File screenshots = State.CG_Folder(this);
            screenshots = new File(screenshots, "screenshots");
            screenshots_path = screenshots.getAbsolutePath();
            observer = new FileObserver(screenshots.getAbsolutePath(), FileObserver.CLOSE_WRITE) {
                @Override
                public void onEvent(int event, String path) {
                    if ((path.length() > 4) && path.substring(path.length() - 4).equals(".bmp")) {
                        convertToPng(screenshots_path + "/" + path);
                    }
                }
            };
            observer.startWatching();
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
                PixelFormat.TRANSLUCENT
        );
        layoutParams.gravity = Gravity.TOP | Gravity.LEFT;

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        pm = getPackageManager();
        String[] quick_launch = preferences.getString(State.APPS, "").split(":");
        apps = new Vector<App>();
        for (String app : quick_launch) {
            try {
                apps.add(new App(app));
            } catch (Exception ex) {
                // ignore
            }
        }
        if (preferences.getString(State.START_POINT, "").equals("-"))
            initLocation();
    }

    @Override
    public void onDestroy() {
        State.appendLog("OnDestroy");
        if (phoneListener != null)
            tm.listen(phoneListener, PhoneStateListener.LISTEN_NONE);
        if (observer != null)
            observer.startWatching();
        if (phoneReceiver != null)
            unregisterReceiver(phoneReceiver);
        if (foreground)
            stopForeground(true);
        hideOverlays(null);
        if (netListener != null) {
            try {
                locationManager.removeUpdates(netListener);
            } catch (SecurityException ex) {
                ex.printStackTrace();
            }
        }
        if (gpsListener != null) {
            try {
                locationManager.removeUpdates(gpsListener);
            } catch (SecurityException ex) {
                ex.printStackTrace();
            }
        }
        if (networkReciever != null)
            unregisterReceiver(networkReciever);
        if (currentBestLocation != null) {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
            SharedPreferences.Editor ed = preferences.edit();
            ed.putString(State.LAST_LAT, currentBestLocation.getLatitude() + "");
            ed.putString(State.LAST_LNG, currentBestLocation.getLongitude() + "");
            ed.commit();

        }
        is_run = false;
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null)
            return START_STICKY;
        String action = intent.getAction();
        if (action == null)
            return START_STICKY;
        if (phoneReceiver == null) {
            phoneReceiver = new BroadcastReceiver() {
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
            registerReceiver(phoneReceiver, filter);
        }
        if (action.equals(START)) {
            setTimer(TIMEOUT, pi);
            setPhoneListener();
            return START_STICKY;
        }
        if (action.equals(TIMER_AFTER_CALL)) {
            stopAfterCall();
            if (isRunCG(getApplicationContext()) && !isActiveCG(getApplicationContext())) {
                try {
                    Intent launch = getPackageManager().getLaunchIntentForPackage(State.CG_Package(this));
                    if (launch != null)
                        startActivity(launch);
                } catch (Exception ex) {
                    // ignore
                }
            }
            return START_STICKY;
        }
        if (action.equals(TIMER)) {
            setTimer(TIMEOUT, pi);
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
            if (isRunCG(this)) {
                last_run = new Date().getTime();
                cg_running = true;
            } else {
                if (background_count < 4)
                    force_exit = true;
                if (last_run + 600000 < new Date().getTime())
                    force_exit = true;
                if (force_exit || (hudInactive == null)) {
                    force_exit = false;
                    alarm.cancel(pi);
                    SharedPreferences.Editor ed = preferences.edit();
                    int rotate = preferences.getInt(State.SAVE_ROTATE, -1);
                    if (rotate >= 0) {
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
                            Settings.System.putInt(getContentResolver(), Settings.System.USER_ROTATION, rotate);
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
                    int channel = preferences.getInt(State.SAVE_CHANNEL, -1);
                    if (channel >= 0) {
                        int level = preferences.getInt(State.SAVE_LEVEL, 0);
                        AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                        audio.setStreamVolume(channel, level, 0);
                        ed.remove(State.SAVE_LEVEL);
                        ed.remove(State.SAVE_CHANNEL);
                    }
                    if (preferences.getBoolean(State.SAVE_WIFI, false)) {
                        try {
                            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
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
                    if (networkReciever != null) {
                        unregisterReceiver(networkReciever);
                        networkReciever = null;
                    }
                    stopSelf();
                    return START_NOT_STICKY;
                }
                if (cg_running) {
                    cg_running = false;
                    AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    try {
                        final MediaPlayer player = new MediaPlayer();
                        Uri uri = Uri.parse("android.resource://ru.shutoff.cgstarter/raw/warning");
                        player.setDataSource(this, uri);
                        player.setAudioStreamType(AudioManager.STREAM_RING);
                        player.setLooping(false);
                        player.prepare();
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.FROYO) {
                            player.start();
                        } else {
                            if (am.requestAudioFocus(new AudioManager.OnAudioFocusChangeListener() {
                                @Override
                                public void onAudioFocusChange(int focusChange) {
                                    player.stop();
                                }
                            }, AudioManager.STREAM_NOTIFICATION, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                                player.start();
                            }
                        }
                    } catch (Exception ex) {
                        // ignore
                    }
                }
            }
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
            if (isActiveCG(this)) {
                background_count = 0;
            } else {
                background_count++;
            }

            if (show_overlay) {
                if (isActiveCG(this)) {
                    showActiveOverlay();
                } else {
                    boolean run = isRunCG(this);
                    if (run != inactive_run) {
                        hideInactiveOverlay();
                    }
                    showInactiveOverlay();
                }
            }
            if ((apps.size() > 0) && !setup_button) {
                if (isActiveCG(this)) {
                    hideInactiveOverlay();
                    showApps();
                } else {
                    boolean run = isRunCG(this);
                    if (run != inactive_run)
                        hideInactiveOverlay();
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
            return START_STICKY;
        }
        if (action.equals(ANSWER)) {
            if (piAnswer != null) {
                alarm.cancel(piAnswer);
                piAnswer = null;
            }
            callAnswer();
            return START_STICKY;
        }
        if (action.equals(RINGING)) {
            if (piRinging != null) {
                alarm.cancel(piRinging);
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            pingTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } else {
            pingTask.execute();
        }
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
                Intent intent = getPackageManager().getLaunchIntentForPackage(State.CG_Package(this));
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
        hudApps = inflater.inflate(R.layout.quick_launch, null);
        int icon_size = preferences.getInt(State.QUICK_SIZE, isBig() ? 45 : 30);
        ImageView iv = (ImageView) hudApps.findViewById(R.id.icon);
        ViewGroup.LayoutParams ivLayoutParams = iv.getLayoutParams();
        int size = ivLayoutParams.width * icon_size / 30;
        ivLayoutParams.width = size;
        ivLayoutParams.height = size;
        iv.setLayoutParams(ivLayoutParams);

        WindowManager.LayoutParams lp = layoutParams;
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        if (isFull) {
            if (iconListener == null) {
                iconListener = new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        SharedPreferences.Editor ed = preferences.edit();
                        ed.remove(State.FULL_TIME);
                        ed.commit();
                        hideApps();
                        showApps();
                        App app = apps.get((Integer) v.getTag());
                        String[] component = app.name.split("/");
                        if (component[0].equals("tel")) {
                            try {
                                Intent intent = new Intent(Intent.ACTION_CALL);
                                intent.setData(Uri.parse("tel:" + component[1]));
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                            }catch (SecurityException ex){
                                ex.printStackTrace();
                            }
                            return;
                        }
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
            int in_row = 0;

            int width = wm.getDefaultDisplay().getWidth();
            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
                width = wm.getDefaultDisplay().getHeight();

            int icon_width = iv.getLayoutParams().width;
            width -= (layoutParams.x + icon_width) * 2;

            int icons = width / icon_width;
            if (icons < 3)
                icons = 3;

            int rows = (apps.size() + icons - 1) / icons;
            icons = (apps.size() + rows - 1) / rows;

            for (int i = 1; i < apps.size(); i++) {
                iv.getLayoutParams();
                App app = apps.get(i);
                ImageView img = new ImageView(this);
                app.draw(img);
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
                    PixelFormat.TRANSLUCENT
            );
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

        apps.get(0).draw(iv);
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

        ImageView ivIcon = (ImageView) hudInactive.findViewById(R.id.photo);
        int icon_size = preferences.getInt(State.QUICK_SIZE, isBig() ? 45 : 30);
        ViewGroup.LayoutParams ivLayoutParams = ivIcon.getLayoutParams();
        int size = ivLayoutParams.width * icon_size / 30;
        ivLayoutParams.width = size;
        ivLayoutParams.height = size;
        ivIcon.setLayoutParams(ivLayoutParams);

        try {
            PackageManager manager = getPackageManager();
            Drawable drawable = manager.getApplicationIcon(State.CG_Package(this));
            inactive_run = isRunCG(this);
            if (!inactive_run) {
                ColorMatrix matrix = new ColorMatrix();
                matrix.setSaturation(0);
                ColorMatrixColorFilter filter = new ColorMatrixColorFilter(matrix);
                drawable.setColorFilter(filter);
            }
            ivIcon.setImageDrawable(drawable);
        } catch (Exception ex) {
            // ignore
        }

        hudInactive.setOnTouchListener(new OverlayTouchListener() {
            @Override
            void click() {
                try {
                    Intent intent = getPackageManager().getLaunchIntentForPackage(State.CG_Package(OnExitService.this));
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

    @SuppressLint("MissingPermission")
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

    void stopAutoAnswer() {
        if (piAnswer != null) {
            alarm.cancel(piAnswer);
            piAnswer = null;
        }
        if (piRinging != null) {
            alarm.cancel(piRinging);
            piRinging = null;
        }
    }

    void stopAfterCall() {
        if (piAfterCall != null) {
            alarm.cancel(piAfterCall);
            piAfterCall = null;
        }
    }

    void setPhoneListener() {
        if (phoneListener != null)
            return;
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        phone = preferences.getBoolean(State.PHONE, false);
        speaker = preferences.getBoolean(State.SPEAKER, false);
        try {
            int answer_time = Integer.parseInt(preferences.getString(State.ANSWER_TIME, "0"));
            int ringing_time = Integer.parseInt(preferences.getString(State.RINGING_TIME, "-1"));
            autoanswer = answer_time * 1000;
            autoswitch = ringing_time * 1000 + 1;
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
                                    setTimer(AFTER_OFFHOOK_PAUSE, piAfterCall);
                                    break;
                                }
                                if (prev_state != TelephonyManager.CALL_STATE_RINGING)
                                    cg_run = isRunCG(getApplicationContext());
                                if (cg_run && !isActiveCG(getApplicationContext())) {
                                    try {
                                        Intent intent = getPackageManager().getLaunchIntentForPackage(State.CG_Package(OnExitService.this));
                                        if (intent != null)
                                            startActivity(intent);
                                    } catch (Exception ex) {
                                        // ignore
                                    }
                                }
                                showActiveOverlay();
                            }
                            if (preferences.getBoolean(State.VOLUME, false) && (preferences.getInt(State.SAVE_RING_LEVEL, -1) == -1)) {
                                int channel = preferences.getInt(State.CUR_CHANNEL, 0);
                                AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                                int cur_level = audio.getStreamVolume(channel);
                                int new_level = audio.getStreamMaxVolume(channel) * preferences.getInt(State.RING_LEVEL, 0) / 100;
                                if (new_level < cur_level) {
                                    audio.setStreamVolume(channel, new_level, 0);
                                    SharedPreferences.Editor ed = preferences.edit();
                                    ed.putInt(State.SAVE_RING_LEVEL, cur_level);
                                    ed.commit();
                                }
                            }
                            if (speaker) {
                                AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                                if (!audio.isBluetoothScoOn() && !audio.isWiredHeadsetOn()) {
                                    audio.setSpeakerphoneOn(true);
                                    speacker_volume = audio.getStreamVolume(AudioManager.STREAM_VOICE_CALL) + 1;
                                    int volume = preferences.getInt(State.CALL_VOLUME, -1);
                                    if (volume > 0)
                                        audio.setStreamVolume(AudioManager.STREAM_VOICE_CALL, volume - 1, 0);
                                }
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
                                    setTimer(autoanswer, piAnswer);
                                }
                            }
                            if (autoswitch > 0) {
                                if (piRinging == null)
                                    piRinging = createPendingIntent(RINGING);
                                setTimer(autoswitch, piRinging);
                            }
                            break;

                        case TelephonyManager.CALL_STATE_IDLE:
                            stopAfterCall();
                            stopAutoAnswer();
                            hideOverlays(OnExitService.this);

                            call_number = null;
                            if (speacker_volume > 0) {
                                AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                                int volume = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL) + 1;
                                if (volume != speacker_volume) {
                                    SharedPreferences.Editor ed = preferences.edit();
                                    ed.putInt(State.CALL_VOLUME, volume);
                                    ed.commit();
                                }
                            }

                            int save_level = preferences.getInt(State.SAVE_RING_LEVEL, -1);
                            if (save_level > 0) {
                                int channel = preferences.getInt(State.CUR_CHANNEL, 0);
                                AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                                audio.setStreamVolume(channel, save_level, 0);
                                SharedPreferences.Editor ed = preferences.edit();
                                ed.remove(State.SAVE_RING_LEVEL);
                                ed.commit();
                            }

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
                                    setTimer(AFTER_CALL_PAUSE, piAfterCall);
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

    public void initLocation() {

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

        try {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                try {
                    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 10, netListener);
                } catch (SecurityException ex) {
                    ex.printStackTrace();
                }
            } else {
                netListener = null;
            }
        } catch (Exception ex) {
            netListener = null;
        }

        if (netListener == null) {

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

            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 10, gpsListener);
            } catch (SecurityException ex) {
                gpsListener = null;
            }
        }

        currentBestLocation = getLastBestLocation();
        showApps();
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
        if ((interval > UPD_INTERVAL) && (currentBestLocation != null)) {
            final ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            if ((activeNetwork != null) && activeNetwork.isConnected()) {
                if (networkReciever != null) {
                    unregisterReceiver(networkReciever);
                    networkReciever = null;
                }
                if (fetcher_time < new Date().getTime()) {
                    yandex_error = false;
                    fetcher_time = new Date().getTime() + 60000;
                    HttpTask fetcher = new HttpTask() {

                        @Override
                        void result(String res) {
                            int lvl = Json.parse(res).asObject().get("lvl").asInt() + 1;
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
                        }

                        @Override
                        void error(String error) {
                            setYandexError(true);
                        }
                    };
                    fetcher.execute(TRAFFIC_URL, currentBestLocation.getLatitude(), currentBestLocation.getLongitude());
                }
            } else {
                if (networkReciever == null) {
                    networkReciever = new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                            if ((activeNetwork != null) && activeNetwork.isConnected()) {
                                unregisterReceiver(networkReciever);
                                networkReciever = null;
                                getYandexData();
                            }
                        }
                    };
                    registerReceiver(networkReciever, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
                }
                yandex_error = true;
            }
        }
        if (interval > VALID_INTEVAL)
            return -1;
        return preferences.getInt(State.TRAFFIC, 0);
    }

    Location getLastBestLocation() {
        Location locationGPS = null;
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                try {
                    locationGPS = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                } catch (SecurityException ex) {
                    ex.printStackTrace();
                }
            }
        } catch (Exception ex) {
            // ignore
        }
        Location locationNet = null;
        try {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                try {
                    locationNet = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                } catch (SecurityException ex) {
                    ex.printStackTrace();
                }
            }
        } catch (Exception ex) {
            // ignore
        }
        long GPSLocationTime = 0;
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

    void startApp() {
        App app = apps.get(0);
        if (app.picture != null) {
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

    void setTimer(long timeout, PendingIntent pi) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT_WATCH) {
            alarm.setExact(AlarmManager.RTC, System.currentTimeMillis() + timeout, pi);
        } else {
            alarm.set(AlarmManager.RTC, System.currentTimeMillis() + timeout, pi);
        }
    }

    class App {

        String name;
        Drawable picture;

        App(String app) {
            String[] component = app.split("/");
            if (component.length != 2)
                throw new InvalidParameterException();
            name = app;
            if (component[0].equals("tel")) {
                picture = getResources().getDrawable(R.drawable.call_contact);
                Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(component[1]));
                ContentResolver contentResolver = getContentResolver();
                Cursor contactLookup = contentResolver.query(uri, new String[]{BaseColumns._ID,
                        ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);
                try {
                    if (contactLookup != null && contactLookup.getCount() > 0) {
                        contactLookup.moveToNext();
                        long contactId = contactLookup.getLong(contactLookup.getColumnIndex(BaseColumns._ID));
                        Uri contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId);
                        Uri photoUri = Uri.withAppendedPath(contactUri, ContactsContract.Contacts.Photo.CONTENT_DIRECTORY);
                        Cursor cursor = getContentResolver().query(photoUri, new String[]{ContactsContract.Contacts.Photo.PHOTO}, null, null, null);
                        if (cursor != null) {
                            try {
                                if (cursor.moveToFirst()) {
                                    byte[] data = cursor.getBlob(0);
                                    if (data != null) {
                                        Bitmap photo = BitmapFactory.decodeStream(new ByteArrayInputStream(data));
                                        picture = new BitmapDrawable(photo);
                                    }
                                }
                            } finally {
                                cursor.close();
                            }
                        }
                    }
                } finally {
                    if (contactLookup != null) {
                        contactLookup.close();
                    }
                }
                return;
            }
            Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
            mainIntent.setPackage(component[0]);
            List<ResolveInfo> infos = pm.queryIntentActivities(mainIntent, 0);
            for (ResolveInfo info : infos) {
                if (info.activityInfo == null)
                    continue;
                if (info.activityInfo.name.equals(component[1])) {
                    if (component[0].equals(YAN) || app.equals("ru.shutoff.cgstarter/ru.shutoff.cgstarter.TrafficActivity")) {
                        initLocation();
                    } else {
                        picture = info.loadIcon(pm);
                        if (picture == null)
                            throw new InvalidParameterException();
                    }
                }
            }
        }

        void draw(ImageView img) {
            if (picture == null) {
                int level = getYandexData();
                if (level < 0) {
                    img.setImageResource(yandex_error ? R.drawable.error_loading : R.drawable.loading);
                    AnimationDrawable animation = (AnimationDrawable) img.getDrawable();
                    animation.start();
                } else {
                    img.setImageResource(res[level]);
                }
                return;
            }
            if (name.equals("ru.shutoff.cgstarter/ru.shutoff.cgstarter.VolumeActivity")) {
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(OnExitService.this);
                int channel = preferences.getInt(State.CUR_CHANNEL, 0);
                AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                int cur_level = audio.getStreamVolume(channel);
                img.setImageResource((cur_level != 0) ? R.drawable.volume : R.drawable.novolume);
                return;
            }
            img.setImageDrawable(picture);
        }
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

    class PingTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... params) {
            try {
                if (show_overlay)
                    return null;
                TelephonyManager tel = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
                if (tel.isNetworkRoaming())
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
