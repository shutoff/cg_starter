package ru.shutoff.cgstarter;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.Vector;

public class MainActivity
        extends Activity
        implements View.OnTouchListener, State.OnBadGPS {

    static final int SETUP_BUTTON = 3000;
    static final int RUN_CG = 3001;
    static final int RUN_DIALOG = 3002;
    static final int ADMIN_INTENT = 3003;
    static final String CHANGE_APP = "ru.shutoff.cg_starter.CHANGE_APP";
    static int[][] holidays = {
            {1, 1},
            {2, 1},
            {3, 1},
            {4, 1},
            {5, 1},
            {6, 1},
            {7, 1},
            {8, 1},
            {23, 2},
            {8, 3},
            {1, 5},
            {9, 5},
            {12, 6},
            {4, 11}
    };
    Button[] buttons;
    CountDownTimer timer;
    CountDownTimer autostart_timer;
    CountDownTimer launch_timer;
    State.Point[] points;
    View activeButton;
    boolean set_state;
    double start;
    SharedPreferences preferences;
    DevicePolicyManager dpm;
    BroadcastReceiver br;
    ImageView cg_icon;

    static String routes(Context context) {
        try {
            File routes_dat = State.CG_Folder(context);
            routes_dat = new File(routes_dat, "routes.dat");
            BufferedReader reader = new BufferedReader(new FileReader(routes_dat));
            String line = reader.readLine();
            if (line == null)
                return "";
            boolean in_current = false;
            String res = "";
            for (; ; ) {
                line = reader.readLine();
                if (line == null)
                    break;
                if (in_current) {
                    if ((line.length() > 0) && line.substring(0, 1).equals("#"))
                        in_current = false;
                } else {
                    if ((line.length() > 11) && line.substring(0, 11).equals("#[CURRENT]|"))
                        in_current = true;
                }
                if (in_current)
                    continue;
                res += "\n";
                res += line;
            }
            return res;

        } catch (IOException e) {
            // ignore
        }
        return "";
    }

    static void createRoute(Context context, String route, String points_str, SearchActivity.Address addr) {
        try {
            File routes_dat = State.CG_Folder(context);
            if (State.cg_files) {
                String tail = routes(context);
                routes_dat = new File(routes_dat, "routes.dat");
                if (!routes_dat.exists())
                    routes_dat.createNewFile();
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
                String start = preferences.getString(State.START_POINT, "0|0");
                BufferedWriter writer = new BufferedWriter(new FileWriter(routes_dat));
                writer.append("1|router|65001\n");
                writer.append("#[CURRENT]|1|1\n");
                writer.append("Start|");
                writer.append(start);
                writer.append("\n");
                if ((points_str != null) && !points_str.equals("")) {
                    String[] points = points_str.split(";");
                    for (String point : points) {
                        writer.append("Point|");
                        writer.append(point);
                        writer.append("\n");
                    }
                }
                writer.append("Finish|");
                writer.append(route);
                writer.append(tail);
                writer.close();
            } else {
                routes_dat = new File(routes_dat, "Routes/Route.curr");
                if (!routes_dat.exists())
                    routes_dat.createNewFile();
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
                String start = preferences.getString(State.START_POINT, "0|0");
                BufferedWriter writer = new BufferedWriter(new FileWriter(routes_dat));
                writer.append("2|AuxObjects|65001|\n");
                writer.append("4|Финиш|");
                writer.append(route);
                writer.append("\n3||");
                writer.append(route);
                writer.append("|1750|0|\n");
                if ((points_str != null) && !points_str.equals("")) {
                    String[] points = points_str.split(";");
                    for (String point : points) {
                        writer.append("2||");
                        writer.append(point);
                        writer.append("|1750|0|\n");
                    }
                }
                writer.append("1||");
                writer.append(start);
                writer.append("|1750|0|\n");
                writer.close();
            }
            String name = null;
            if (addr != null) {
                name = addr.name;
                if (name == null)
                    name = addr.address;
            }
            if (name != null) {
                String[] r = route.split("\\|");
                double lat = Double.parseDouble(r[0]);
                double lng = Double.parseDouble(r[1]);
                File history = State.CG_Folder(context);
                Vector<String> lines = new Vector<String>();
                if (State.cg_files) {
                    history = new File(history, "history.dat");
                    BufferedReader reader = new BufferedReader(new FileReader(history));
                    boolean first = true;
                    while (true) {
                        String line = reader.readLine();
                        if (line == null)
                            break;
                        String[] parts = line.split("\\|");
                        if (parts.length < 4) {
                            lines.add(line);
                            continue;
                        }
                        if (first) {
                            first = false;
                            lines.add(name + "|Адрес|" + route);
                        }
                        if (name.equals(parts[0])) {
                            try {
                                if (OnExitService.calc_distance(lat, lng, Double.parseDouble(parts[2]), Double.parseDouble(parts[3])) < 100)
                                    continue;
                                ;
                            } catch (Exception ex) {
                                // ignore
                            }
                        }
                        lines.add(line);
                    }
                    reader.close();
                } else {
                    history = new File(history, "CGMaps/History.bkm");
                    BufferedReader reader = new BufferedReader(new FileReader(history));
                    int next = 0;
                    while (true) {
                        String line = reader.readLine();
                        if (line == null)
                            break;
                        String[] parts = line.split("\\|");
                        if (parts.length < 6) {
                            lines.add(line);
                            continue;
                        }
                        try {
                            int count = Integer.parseInt(parts[5]);
                            if (count > next)
                                next = count;
                        } catch (Exception ex) {
                            // ignore
                        }
                        if (name.equals(parts[1])) {
                            try {
                                if (OnExitService.calc_distance(lat, lng, Double.parseDouble(parts[2]), Double.parseDouble(parts[3])) < 100)
                                    continue;
                                ;
                            } catch (Exception ex) {
                                // ignore
                            }
                        }
                        lines.add(line);
                    }
                    reader.close();
                    lines.add("18888|" + name + "|" + route + "|30000|" + (next + 1));
                }
                BufferedWriter writer = new BufferedWriter(new FileWriter(history));
                for (String line : lines) {
                    writer.write(line);
                    writer.write("\n");
                }
                writer.close();
            }
        } catch (IOException e) {
            Toast toast = Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG);
            toast.show();
        }
    }

    static void removeRoute(Context context) {
        try {
            File routes_dat = State.CG_Folder(context);
            if (State.cg_files) {
                String tail = routes(context);
                routes_dat = new File(routes_dat, "routes.dat");
                if (!routes_dat.exists())
                    routes_dat.createNewFile();
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
                String start = preferences.getString(State.START_POINT, "0|0");
                BufferedWriter writer = new BufferedWriter(new FileWriter(routes_dat));
                writer.append("1|router|65001\n");
                writer.append("#[CURRENT]|1|0\n");
                writer.append("Start|");
                writer.append(start);
                writer.append("\n");
                writer.append(tail);
                writer.close();
            } else {
                routes_dat = new File(routes_dat, "Routes/Route.curr");
                if (!routes_dat.exists())
                    routes_dat.createNewFile();
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
                String start = preferences.getString(State.START_POINT, "0|0");
                BufferedWriter writer = new BufferedWriter(new FileWriter(routes_dat));
                writer.append("2|AuxObjects|65001|\n");
                writer.append("1||");
                writer.append(start);
                writer.append("|1750|0|\n");
                writer.close();
            }
        } catch (IOException e) {
            Toast toast = Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG);
            toast.show();
        }
    }

    static void copyFiles(Context context, File from, File to) {
        if (!from.isDirectory())
            return;
        String[] files = from.list();
        for (String f : files) {
            if (f.substring(0, 1).equals("."))
                continue;
            File file = new File(from, f);
            if (file.isHidden())
                continue;
            if (file.isDirectory()) {
                File to_dir = new File(to, f);
                if (!to_dir.isDirectory()) {
                    try {
                        to_dir.delete();
                    } catch (Exception ex) {
                        // ignore
                    }
                    to_dir.mkdirs();
                }
                copyFiles(context, file, to_dir);
                continue;
            }
            try {
                File to_file = new File(to, f);
                to_file.createNewFile();
                InputStream in = new FileInputStream(file);
                OutputStream out = new FileOutputStream(to_file);
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                in.close();
                out.close();
            } catch (Exception ex) {
                Toast toast = Toast.makeText(context, ex.getMessage(), Toast.LENGTH_LONG);
                toast.show();
            }
        }
    }

    static void copyFiles(Context context) {
        File cg_folder = State.CG_Folder(context);
        File backup = new File(cg_folder, "backup");
        if (backup.exists())
            copyFiles(context, backup, cg_folder);
    }

    static boolean setState(Context context, State.OnBadGPS badGPS) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor ed = preferences.edit();
        if (preferences.getBoolean(State.GPS, false)) {
            boolean gps_enabled = false;
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            try {
                gps_enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
            } catch (Exception ex) {
                // ignore
            }
            if (!gps_enabled) {
                if (State.canToggleGPS(context)) {
                    try {
                        State.turnGPSOn(context);
                        ed.putBoolean(State.GPS_SAVE, true);
                    } catch (Exception ex) {
                        // ignore
                    }
                } else if (badGPS != null) {
                    badGPS.gps_message(context);
                    return false;
                }
            }
        }
        String orientation = preferences.getString(State.ORIENTATION, "0");
        if (!orientation.equals("0")) {
            try {
                int save_rotation = Settings.System.getInt(context.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION);
                Settings.System.putInt(context.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0);
                if (!preferences.contains(State.SAVE_ROTATE))
                    ed.putInt(State.SAVE_ROTATE, save_rotation);
                if (orientation.equals("2")) {
                    int rotate = context.getResources().getConfiguration().orientation;
                    if ((rotate != Surface.ROTATION_0) && (rotate != Surface.ROTATION_180))
                        setOrientation(context, Surface.ROTATION_0);
                }
                if (orientation.equals("3")) {
                    int rotate = context.getResources().getConfiguration().orientation;
                    if ((rotate != Surface.ROTATION_90) && (rotate != Surface.ROTATION_270))
                        setOrientation(context, Surface.ROTATION_90);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        OnExitService.turnOnBT(context);
        if (preferences.getBoolean(State.DATA, false)) {

            try {
                WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                if (wifiManager != null) {
                    if (wifiManager.isWifiEnabled()) {
                        wifiManager.setWifiEnabled(false);
                        ed.putBoolean(State.SAVE_WIFI, true);
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            try {
                ConnectivityManager conman = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo activeNetwork = conman.getActiveNetworkInfo();
                if ((activeNetwork == null) ||
                        (activeNetwork.getType() != ConnectivityManager.TYPE_MOBILE) ||
                        !activeNetwork.isConnected()) {
                    if (!OnExitService.getMobileDataEnabled(context)) {
                        OnExitService.enableMobileData(context, true);
                        ed.putBoolean(State.SAVE_DATA, true);
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        if (preferences.getBoolean(State.VOLUME, false)) {
            try {
                int level = preferences.getInt(State.LEVEL, 100);
                int channel = SettingsIni.getParam(context, "audiostream");
                switch (channel) {
                    case 0:
                        channel = AudioManager.STREAM_SYSTEM;
                        break;
                    case 1:
                        channel = AudioManager.STREAM_RING;
                        break;
                    case 2:
                        channel = AudioManager.STREAM_MUSIC;
                        break;
                    case 3:
                        channel = AudioManager.STREAM_ALARM;
                        break;
                    case 4:
                        channel = AudioManager.STREAM_NOTIFICATION;
                        break;
                }
                if (channel > 0) {
                    ed.putInt(State.SAVE_CHANNEL, channel);
                    AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                    if (!preferences.contains(State.SAVE_LEVEL)) {
                        int prev_level = audio.getStreamVolume(channel);
                        ed.putInt(State.SAVE_LEVEL, prev_level);
                    }
                    int max_level = audio.getStreamMaxVolume(channel);
                    int new_level = level * max_level / 100;
                    audio.setStreamVolume(channel, new_level, 0);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        if (preferences.getBoolean(State.MAPCAM, false)) {
            Intent intent = new Intent("info.mapcam.droid.SERVICE_START");
            context.sendBroadcast(intent);
        }
        if (preferences.getBoolean(State.STRELKA, false)) {
            Intent intent = new Intent("com.ivolk.StrelkaGPS.action.START_SERVICE");
            context.sendBroadcast(intent);
        }

        ed.commit();
        copyFiles(context);
        return true;
    }

    static void setOrientation(Context context, int rotate) {
        try {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            SharedPreferences.Editor ed = preferences.edit();
            ed.putInt(State.SAVE_ORIENTATION, Settings.System.getInt(context.getContentResolver(), Settings.System.USER_ROTATION));
            ed.commit();
            Settings.System.putInt(context.getContentResolver(), Settings.System.USER_ROTATION, rotate);
        } catch (Exception ex) {
            // ignore
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        String orientation = preferences.getString(State.ORIENTATION, "");
        if (orientation.equals("2"))
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        if (orientation.equals("3"))
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        if (preferences.getBoolean("yandex", false)) {
            SharedPreferences.Editor ed = preferences.edit();
            ed.remove("yandex");
            ed.putString(State.APPS, "ru.shutoff.cgstarter/ru.shutoff.cgstarter.TrafficActivity");
            ed.commit();
        }

        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.main);

        double work_time = 0;
        if (savedInstanceState != null)
            work_time = savedInstanceState.getDouble(State.START, -1);

        buttons = new Button[8];
        buttons[0] = (Button) findViewById(R.id.btn1);
        buttons[1] = (Button) findViewById(R.id.btn2);
        buttons[2] = (Button) findViewById(R.id.btn3);
        buttons[3] = (Button) findViewById(R.id.btn4);
        buttons[4] = (Button) findViewById(R.id.btn5);
        buttons[5] = (Button) findViewById(R.id.btn6);
        buttons[6] = (Button) findViewById(R.id.btn7);
        buttons[7] = (Button) findViewById(R.id.btn8);

        int auto_pause = preferences.getInt(State.AUTO_PAUSE, 5);
        if (auto_pause < 3)
            auto_pause = 3;
        auto_pause = auto_pause * 1000;
        int launch_pause = preferences.getInt(State.INACTIVE_PAUSE, 30);
        if (launch_pause < 10)
            launch_pause = 10;
        launch_pause = launch_pause * 1000;

        points = State.get(this, false);

        Calendar calendar = Calendar.getInstance();
        int now_day = calendar.get(Calendar.DAY_OF_MONTH);
        int now_month = calendar.get(Calendar.MONTH) + 1;
        int now_wday = calendar.get(Calendar.DAY_OF_WEEK) - 2;
        if (now_wday < 0)
            now_wday += 7;
        boolean is_holiday = (now_wday >= 5);
        if (!is_holiday) {
            for (int[] h : holidays) {
                if ((now_day == h[0]) && (now_month == h[1])) {
                    is_holiday = true;
                    break;
                }
            }
        }
        int days = (is_holiday ? State.HOLIDAYS : State.WORKDAYS);
        days |= (1 << (now_wday + 2));

        if (work_time >= 0) {
            auto_pause -= work_time;
            launch_pause -= work_time;

            autostart_timer = new CountDownTimer(auto_pause, auto_pause) {
                @Override
                public void onTick(long millisUntilFinished) {
                }

                @Override
                public void onFinish() {
                    action();
                    activeButton = null;
                }
            };
            launch_timer = new CountDownTimer(launch_pause, launch_pause) {
                @Override
                public void onTick(long millisUntilFinished) {
                }

                @Override
                public void onFinish() {
                    if (preferences.getString(State.INACTIVE_MODE, "0").equals("0")) {
                        launch();
                    } else {
                        finish();
                    }
                }
            };
            launch_timer.start();
        }

        for (int i = 0; i < 8; i++) {
            buttons[i].setText(points[i].name);
            buttons[i].setOnTouchListener(this);
            if (activeButton != null)
                continue;
            State.Point p = points[i];
            if (p.name.equals(""))
                continue;
            if ((days & p.days) == 0)
                continue;
            if (autostart_timer == null)
                continue;
            if (State.inInterval(p.interval)) {
                activeButton = buttons[i];
                buttons[i].setBackgroundResource(R.drawable.auto);
                autostart_timer.start();
            }
        }

        timer = new CountDownTimer(1000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
            }

            @Override
            public void onFinish() {
                setup();
                if (activeButton != null) {
                    activeButton.setBackgroundResource(R.drawable.button);
                    activeButton = null;
                }
            }
        };

        View setup = findViewById(R.id.setup);
        setup.setOnTouchListener(this);

        View run = findViewById(R.id.run);
        run.setOnTouchListener(this);

        LinearLayout cg = (LinearLayout) findViewById(R.id.cg);
        cg_icon = (ImageView) findViewById(R.id.cg_icon);
        try {
            PackageManager manager = getPackageManager();
            cg_icon.setImageDrawable(manager.getApplicationIcon(State.CG_Package(this)));
            View cg_button = findViewById(R.id.cg);
            cg_button.setOnTouchListener(this);
        } catch (Exception e) {
            cg.setVisibility(View.GONE);
        }

        if (preferences.getBoolean(State.CAR_MODE, false) && preferences.getBoolean(State.CAR_STATE, false))
            setState();

        if (launch_timer != null) {
            Date now = new Date();
            start = now.getTime() - work_time;
        }

        if (savedInstanceState == null) {
            OnExitService.convertFiles(this);
            if (preferences.getBoolean(State.RTA_LOGS, false))
                OnExitService.removeRTA(this);
            State.doRoot(this, "", false);
            int route_type = SettingsIni.getParam(this, "route_type");
            if ((route_type > 0) && (route_type <= 2)) {
                stopTimers();
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.route_type);
                builder.setMessage((route_type == 1) ? R.string.route_type_short : R.string.route_type_foot);
                builder.setPositiveButton(R.string.cont, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SettingsIni.setParam(MainActivity.this, "route_type", "0");
                    }
                });
                builder.setNegativeButton(R.string.cancel, null);
                builder.create().show();
            }
        }

        if (preferences.getBoolean(State.KILL_CAR, false) && preferences.getBoolean(State.KILL_POWER, false)
                && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO)) {
            dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
            ComponentName componentName = new ComponentName(this, AdminReceiver.class);
            if (!dpm.isAdminActive(componentName)) {
                Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName);
                intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "");
                startActivityForResult(intent, ADMIN_INTENT);
            }
        }

        br = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                try {
                    PackageManager manager = getPackageManager();
                    cg_icon.setImageDrawable(manager.getApplicationIcon(State.CG_Package(context)));
                } catch (Exception e) {
                    // ignore
                }
            }
        };
        registerReceiver(br, new IntentFilter(CHANGE_APP));
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(br);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        points = State.get(this, false);
        for (int i = 0; i < 8; i++) {
            buttons[i].setText(points[i].name);
        }
    }

    @Override
    public void onAttachedToWindow() {
        //make the activity show even the screen is locked.
        Window window = getWindow();

        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                + WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                + WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                + WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
    }

    @Override
    public void finish() {
        if (set_state) {
            Intent intent = new Intent(this, OnExitService.class);
            intent.setAction(OnExitService.START);
            startService(intent);
        }
        super.finish();
    }

    @Override
    protected void onStop() {
        super.onStop();
        timer.cancel();
        stopTimers();
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (activeButton != null) {
                    activeButton.setBackgroundResource(R.drawable.button);
                    activeButton = null;
                }
                activeButton = v;
                v.setBackgroundResource(R.drawable.pressed);
                stopTimers();
                timer.cancel();
                timer.start();
                start = 0;
                return true;
            case MotionEvent.ACTION_UP:
                if (activeButton != null)
                    action();
            case MotionEvent.ACTION_CANCEL:
                v.setBackgroundResource(R.drawable.button);
                timer.cancel();
                return true;
            case MotionEvent.ACTION_MOVE: {
                float x = event.getX();
                float y = event.getY();
                if ((x < 0) || (x > v.getWidth()) || (y < 0) || (y > v.getHeight())) {
                    v.setBackgroundResource(R.drawable.button);
                    activeButton = null;
                    timer.cancel();
                }
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case RUN_DIALOG:
                if (resultCode == RESULT_OK)
                    finish();
                return;
            case SETUP_BUTTON:
                if (data != null) {
                    int id = data.getIntExtra(State.ID, -1);
                    if ((id >= 0) && (id < buttons.length)) {
                        Button btn = buttons[id];
                        State.Point point = points[id];
                        btn.setText(point.name);
                        State.save(preferences);
                    }
                }
                break;
            case RUN_CG:
                finish();
                break;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            setup_app();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        double work_time = -1;
        if (start > 0) {
            Date now = new Date();
            work_time = now.getTime() - start;
        }
        outState.putDouble(State.START, work_time);
    }

    void stopTimers() {
        start = 0;
        if (autostart_timer != null) {
            autostart_timer.cancel();
            autostart_timer = null;
        }
        if (launch_timer != null) {
            launch_timer.cancel();
            launch_timer = null;
        }
    }

    void action() {
        if (activeButton == null)
            return;
        if (activeButton == findViewById(R.id.setup)) {
            setup_app();
            return;
        }
        if (activeButton == findViewById(R.id.run)) {
            launch();
            return;
        }
        if (activeButton == findViewById(R.id.cg)) {
            Intent i = new Intent(this, CGActivity.class);
            startActivityForResult(i, RUN_DIALOG);
            return;
        }

        int i;
        for (i = 0; i < 8; i++) {
            if (buttons[i] == activeButton)
                break;
        }
        if (i >= 8)
            return;

        State.Point p = points[i];
        if (p.name.equals(""))
            return;
        SearchActivity.Address addr = new SearchActivity.Address();
        addr.name = p.name;
        createRoute(this, p.lat + "|" + p.lng, p.points, addr);
        launch();
    }

    void setState() {
        if (setState(this, this))
            set_state = true;
    }

    void setStateForce() {
        setState(this, null);
        set_state = true;
    }

    void launch() {
        timer.cancel();
        stopTimers();
        setState();
        if (!set_state)
            return;
        launch_cg();
    }

    void launch_cg() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(State.CG_Package(this));
        if (intent == null) {
            Toast toast = Toast.makeText(this, getString(R.string.no_cg), Toast.LENGTH_SHORT);
            toast.show();
            return;
        }
        startActivityForResult(intent, RUN_CG);
    }

    void setup() {
        timer.cancel();
        stopTimers();
        int i;
        for (i = 0; i < 8; i++) {
            if (buttons[i] == activeButton)
                break;
        }
        if (i >= 8)
            return;

        Intent intent = new Intent(this, SetupButton.class);
        intent.putExtra(State.ID, i);
        startActivityForResult(intent, SETUP_BUTTON);
    }

    void setup_app() {
        timer.cancel();
        stopTimers();
        Intent intent = new Intent(this, Preferences.class);
        startActivity(intent);
    }

    @Override
    public void gps_message(Context context) {
        AlertDialog.Builder ad = new AlertDialog.Builder(this);
        ad.setTitle(R.string.no_gps_title);
        ad.setMessage(R.string.no_gps_message);
        ad.setPositiveButton(R.string.settings, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivity(intent);
            }
        });
        ad.setNegativeButton(R.string.cont, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                setStateForce();
                dialog.cancel();
                launch_cg();
            }
        });
        ad.show();
    }

}
