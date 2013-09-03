package ru.shutoff.cgstarter;

import android.content.SharedPreferences;
import android.os.Environment;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;

public class State {

    static class Point {
        String name;
        String original;
        String lat;
        String lng;
        String interval;
        int days;
    }

    static final String NAME = "Name";
    static final String ORIGINAL = "Original";
    static final String LATITUDE = "Latitude";
    static final String LONGITUDE = "Longitude";
    static final String INTERVAL = "Interval";
    static final String DAYS = "Days";

    static final int WORKDAYS = 1;
    static final int HOLIDAYS = 2;

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
                if (p.lat.equals("") || p.lng.equals("")){
                    p.name = "";
                    p.original = "";
                    p.lat = "";
                    p.lng = "";
                    p.interval = "";
                    p.days = 0;
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
                continue;
            }
            ed.putString(NAME + i, p.name);
            ed.putString(ORIGINAL + i, p.original);
            ed.putString(LATITUDE + i, p.lat);
            ed.putString(LONGITUDE + i, p.lng);
            ed.putString(INTERVAL + i, p.interval);
            ed.putInt(DAYS + i, p.days);
        }
        ed.commit();
    }

/*
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

    static public void print(Exception ex) {
        appendLog("Error: " + ex.toString());
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        String s = sw.toString();
        appendLog(s);
    }
*/
}
