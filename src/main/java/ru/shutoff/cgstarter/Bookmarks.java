package ru.shutoff.cgstarter;

import android.os.Environment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;

public class Bookmarks {

    static class Point {
        String  name;
        double  lat;
        double  lng;
        int     count;
    }

    static Point[] result;

    static Point[] get(){
        if (result == null){
            Vector<Point> points = new Vector<Point>();
            try{
                File poi = Environment.getExternalStorageDirectory();
                poi = new File(poi, "CityGuide/CGMaps/poi.bkm");
                BufferedReader reader = new BufferedReader(new FileReader(poi));
                while (true){
                    String line = reader.readLine();
                    if (line == null)
                        break;
                    String[] parts = line.split("\\|");
                    if (parts.length < 4)
                        continue;
                    Point p = new Point();
                    p.name = parts[1];
                    p.lat = Double.parseDouble(parts[2]);
                    p.lng = Double.parseDouble(parts[3]);
                    p.count = Integer.parseInt(parts[parts.length - 1]);
                    points.add(p);
                }
                reader.close();
            } catch (Exception ex){
            }
            Collections.sort(points, new Comparator<Point>() {
                @Override
                public int compare(Point lhs, Point rhs) {
                    if (lhs.count > rhs.count)
                        return -1;
                    if (rhs.count > lhs.count)
                        return 1;
                    return 0;
                }
            });
            result = new Point[points.size()];
            points.copyInto(result);
        }
        return result;
    }
}
