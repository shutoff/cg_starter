package ru.shutoff.cgstarter;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.Calendar;
import java.util.Date;

public class MainActivity
        extends Activity
        implements View.OnTouchListener, State.OnBadGPS {

    Button[] buttons;

    CountDownTimer timer;
    CountDownTimer autostart_timer;
    CountDownTimer launch_timer;

    State.Point[] points;

    View activeButton;
    boolean set_state;
    boolean do_launch;
    double start;

    SharedPreferences preferences;

    static final int SETUP_BUTTON = 3000;
    static final int RUN_CG = 3001;
    static final int GPS_ON = 3002;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {

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

        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        int auto_pause = preferences.getInt(State.AUTO_PAUSE, 5);
        if (auto_pause < 3)
            auto_pause = 3;
        auto_pause = auto_pause * 1000;
        int launch_pause = preferences.getInt(State.INACTIVE_PAUSE, 30);
        if (launch_pause < 10)
            launch_pause = 10;
        launch_pause = launch_pause * 1000;

        points = State.get(preferences);

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
                    if (preferences.getBoolean(State.INACTIVE_LAUNCH, false)) {
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
        ImageView cg_icon = (ImageView) findViewById(R.id.cg_icon);
        try {
            PackageManager manager = getPackageManager();
            cg_icon.setImageDrawable(manager.getApplicationIcon(State.CG_PACKAGE));
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
            removeRoute(this);
            launch();
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
        createRoute(this, p.lat + "|" + p.lng, p.points);
        launch();
    }

    static String routes() {
        try {
            File routes_dat = Environment.getExternalStorageDirectory();
            routes_dat = new File(routes_dat, "CityGuide/routes.dat");
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

    static void createRoute(Context context, String route, String points_str) {
        try {
            String tail = routes();
            File routes_dat = Environment.getExternalStorageDirectory();
            routes_dat = new File(routes_dat, "CityGuide/routes.dat");
            if (!routes_dat.exists())
                routes_dat.createNewFile();
            BufferedWriter writer = new BufferedWriter(new FileWriter(routes_dat));
            writer.append("1|router|65001\n");
            writer.append("#[CURRENT]|1|1\n");
            writer.append("Start|0|0\n");
            if (!points_str.equals("")) {
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
        } catch (IOException e) {
            Toast toast = Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG);
            toast.show();
            return;
        }
    }

    static void removeRoute(Context context) {
        try {
            String tail = routes();
            File routes_dat = Environment.getExternalStorageDirectory();
            routes_dat = new File(routes_dat, "CityGuide/routes.dat");
            if (!routes_dat.exists())
                routes_dat.createNewFile();
            BufferedWriter writer = new BufferedWriter(new FileWriter(routes_dat));
            writer.append("1|router|65001\n");
            writer.append("#[CURRENT]|0|0\n");
            writer.append(tail);
            writer.close();
        } catch (IOException e) {
            Toast toast = Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG);
            toast.show();
        }
    }

    void setState() {
        if (setState(this, this))
            set_state = true;
    }

    void setStateForce() {
        setState(this, null);
        set_state = true;
    }

    static class TestDataTask extends AsyncTask<Context, Void, Void> {
        @Override
        protected Void doInBackground(Context... params) {
            try {
                Thread.sleep(1500);
                ConnectivityManager conman = (ConnectivityManager) params[0].getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo networkInfo = conman.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
                if ((networkInfo == null) || !networkInfo.isConnected()) {
                    setAirplaneMode(params[0], true);
                    setAirplaneMode(params[1], false);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            return null;
        }
    }

    ;

    static void setAirplaneMode(Context context, boolean state) {
        Settings.System.putInt(
                context.getContentResolver(),
                Settings.System.AIRPLANE_MODE_ON, state ? 0 : 1);
        Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intent.putExtra("state", !state);
        context.sendBroadcast(intent);
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
        if (preferences.getBoolean(State.ROTATE, false)) {
            try {
                int save_rotation = Settings.System.getInt(context.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION);
                Settings.System.putInt(context.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0);
                if (!preferences.contains(State.SAVE_ROTATE))
                    ed.putInt(State.SAVE_ROTATE, save_rotation);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        if (preferences.getBoolean(State.BT, false)) {
            try {
                BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
                if ((bt != null) && !bt.isEnabled()) {
                    bt.enable();
                    ed.putBoolean(State.SAVE_BT, true);
                    ed.remove(State.BT_CONNECTED);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
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
                        ed.putBoolean(State.SAVE_DATA, true);
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            TestDataTask testDataTask = new TestDataTask();
            testDataTask.execute(context);

        }

        if (preferences.getBoolean(State.VOLUME, false)) {
            try {
                int channel = 0;
                int level = preferences.getInt(State.LEVEL, 100);
                File settings = Environment.getExternalStorageDirectory();
                settings = new File(settings, "CityGuide/settings.ini");
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(settings), Charset.forName("UTF-16LE")));
                while (true) {
                    String line = reader.readLine();
                    if (line == null)
                        break;
                    String[] parts = line.split("=");
                    if (parts[0].equals("audiostream")) {
                        channel = Integer.parseInt(parts[1]);
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
                        break;
                    }
                }
                reader.close();
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
        ed.commit();
        return true;
    }

    void launch() {
        timer.cancel();
        stopTimers();
        setState();
        if (!set_state) {
            do_launch = true;
            return;
        }
        launch_cg();
    }

    void launch_cg() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(State.CG_PACKAGE);
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
        Intent intent = new Intent(this, Setup.class);
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
                if (do_launch)
                    launch_cg();
                dialog.cancel();
            }
        });
        ad.show();
    }
}
