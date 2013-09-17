package ru.shutoff.cgstarter;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Environment;
import android.provider.Settings;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Calendar;
import java.util.Date;

public class State {

    static final String CG_PACKAGE = "cityguide.probki.net";

    static final String AUTO_PAUSE = "auto_pause";
    static final String INACTIVE_PAUSE = "inactive_pause";
    static final String INACTIVE_LAUNCH = "inactive_launch";
    static final String CAR_MODE = "carmode";
    static final String CAR_STATE = "car_state";
    static final String POWER_TIME = "powertime";
    static final String ABOUT = "about";
    static final String BT = "bt";
    static final String BT_CONNECTED = "bt_connected";
    static final String SAVE_BT = "save_bt";
    static final String VOLUME = "volume";
    static final String LEVEL = "level";
    static final String PHONE = "phone";
    static final String DATA = "data";
    static final String SPEAKER = "speaker";
    static final String ANSWER_TIME = "answertime";
    static final String ID = "ID";
    static final String ROTATE = "rotate";
    static final String SAVE_ROTATE = "save_rotate";
    static final String SAVE_CHANNEL = "channel";
    static final String SAVE_LEVEL = "save_level";
    static final String GPS = "gps";
    static final String GPS_SAVE = "gps_save";
    static final String URL = "URL";
    static final String INTENTS = "intents";
    static final String SAVE_WIFI = "save_wifi";
    static final String SAVE_DATA = "save_data";
    static final String CAR_START_CG = "car_start_cg";

    static class Point {
        String name;
        String original;
        String lat;
        String lng;
        String interval;
        String points;
        int days;
    }

    static final String NAME = "Name";
    static final String ORIGINAL = "Original";
    static final String LATITUDE = "Latitude";
    static final String LONGITUDE = "Longitude";
    static final String INTERVAL = "Interval";
    static final String DAYS = "Days";
    static final String POINTS = "Points";

    static final int WORKDAYS = 1;
    static final int HOLIDAYS = 2;
    static final int ALLDAYS = 3;

    static Point[] points;

    static Point[] get(SharedPreferences preferences) {
        if (points == null) {
            points = new Point[8];
            boolean is_init = false;
            for (int i = 0; i < 8; i++) {
                Point p = new Point();
                p.name = preferences.getString(NAME + i, "");
                p.original = preferences.getString(ORIGINAL + i, "");
                p.lat = preferences.getString(LATITUDE + i, "");
                p.lng = preferences.getString(LONGITUDE + i, "");
                p.interval = preferences.getString(INTERVAL + i, "");
                p.days = preferences.getInt(DAYS + i, 0);
                p.points = preferences.getString(POINTS + i, "");
                if (p.lat.equals("") || p.lng.equals("")) {
                    p.name = "";
                    p.original = "";
                    p.lat = "";
                    p.lng = "";
                    p.interval = "";
                    p.days = 0;
                    p.points = "";
                }
                if (!p.name.equals(""))
                    is_init = true;
                points[i] = p;
            }
            if (!is_init) {
                Bookmarks.Point[] from = Bookmarks.get();
                for (int i = 0; i < 8; i++) {
                    if (i >= from.length)
                        break;
                    Point p = points[i];
                    p.name = from[i].name;
                    p.original = from[i].name;
                    p.lat = from[i].lat + "";
                    p.lng = from[i].lng + "";
                    p.points = from[i].points;
                }
                save(preferences);
            }
        }
        return points;
    }

    static void save(SharedPreferences preferences) {
        SharedPreferences.Editor ed = preferences.edit();
        for (int i = 0; i < 8; i++) {
            Point p = points[i];
            if (p.name.equals("")) {
                ed.remove(NAME + i);
                ed.remove(ORIGINAL + i);
                ed.remove(LATITUDE + i);
                ed.remove(LONGITUDE + i);
                ed.remove(INTERVAL + i);
                ed.remove(DAYS + i);
                ed.remove(POINTS + i);
                continue;
            }
            ed.putString(NAME + i, p.name);
            ed.putString(ORIGINAL + i, p.original);
            ed.putString(LATITUDE + i, p.lat);
            ed.putString(LONGITUDE + i, p.lng);
            ed.putString(INTERVAL + i, p.interval);
            ed.putString(POINTS + i, p.points);
            ed.putInt(DAYS + i, p.days);
        }
        ed.commit();
    }

    static boolean canToggleGPS(Context context) {
        PackageManager pacman = context.getPackageManager();
        PackageInfo pacInfo = null;

        try {
            pacInfo = pacman.getPackageInfo("com.android.settings", PackageManager.GET_RECEIVERS);
        } catch (PackageManager.NameNotFoundException e) {
            return false; //package not found
        }

        if (pacInfo != null) {
            for (ActivityInfo actInfo : pacInfo.receivers) {
                //test if recevier is exported. if so, we can toggle GPS.
                if (actInfo.name.equals("com.android.settings.widget.SettingsAppWidgetProvider") && actInfo.exported) {
                    return true;
                }
            }
        }
        return false; //default
    }

    static void turnGPSOn(Context context) {
        String provider = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.LOCATION_PROVIDERS_ALLOWED);

        if (!provider.contains("gps")) { //if gps is disabled
            final Intent poke = new Intent();
            poke.setClassName("com.android.settings", "com.android.settings.widget.SettingsAppWidgetProvider");
            poke.addCategory(Intent.CATEGORY_ALTERNATIVE);
            poke.setData(Uri.parse("3"));
            context.sendBroadcast(poke);
        }
    }

    static void turnGPSOff(Context context) {
        String provider = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.LOCATION_PROVIDERS_ALLOWED);

        if (provider.contains("gps")) { //if gps is enabled
            final Intent poke = new Intent();
            poke.setClassName("com.android.settings", "com.android.settings.widget.SettingsAppWidgetProvider");
            poke.addCategory(Intent.CATEGORY_ALTERNATIVE);
            poke.setData(Uri.parse("3"));
            context.sendBroadcast(poke);
        }
    }

    interface OnBadGPS {
        abstract void gps_message(Context context);
    }

    static boolean inInterval(String interval) {
        if (interval.equals(""))
            return false;
        Calendar calendar = Calendar.getInstance();
        int now_h = calendar.get(Calendar.HOUR_OF_DAY);
        int now_m = calendar.get(Calendar.MINUTE);
        String[] times = interval.split("-");
        String[] time = times[0].split(":");
        int start_h = Integer.parseInt(time[0]);
        int start_m = Integer.parseInt(time[1]);
        time = times[1].split(":");
        int end_h = Integer.parseInt(time[0]);
        int end_m = Integer.parseInt(time[1]);
        if ((end_h > start_h) || ((end_h == start_h) && (end_m >= start_m))) {
            if ((now_h < start_h) || ((now_h == start_h) && (now_m < start_m)))
                return false;
            return ((now_h < end_h) || ((now_h == end_h) && (now_m < end_m)));
        }
        if ((now_h < end_h) || ((now_h == end_h) && (now_m <= end_m)))
            return true;
        if ((now_h > start_h) || ((now_h == start_h) && (now_m >= start_m)))
            return true;
        return false;
    }

    static public void appendLog(String text) {
        File logFile = Environment.getExternalStorageDirectory();
        logFile = new File(logFile, "cg.log");
        if (!logFile.exists()) {
            try {
                logFile.createNewFile();
            } catch (IOException e) {
            }
        }
        try {
            //BufferedWriter for performance, true to set append to file flag
            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
            Date d = new Date();
            buf.append(d.toLocaleString());
            buf.append(" ");
            buf.append(text);
            buf.newLine();
            buf.close();
        } catch (IOException e) {
        }
    }

    static public void print(Throwable ex) {
        appendLog("Error: " + ex.toString());
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        String s = sw.toString();
        appendLog(s);
    }

}
