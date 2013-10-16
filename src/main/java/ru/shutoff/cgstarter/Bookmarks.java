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
        String name;
        double lat;
        double lng;
        String points;
        int count;
    }

    static Point[] result;

    static Point[] get() {
        if (result == null) {
            Vector<Point> points = new Vector<Point>();
            try {
                File poi = Environment.getExternalStorageDirectory();
                poi = new File(poi, "CityGuide/CGMaps/poi.bkm");
                BufferedReader reader = new BufferedReader(new FileReader(poi));
                while (true) {
                    String line = reader.readLine();
                    if (line == null)
                        break;
                    String[] parts = line.split("\\|");
                    if (parts.length < 4)
                        continue;
                    if (parts[1].length() == 0)
                        continue;
                    Point p = new Point();
                    p.name = parts[1];
                    try {
                        p.lat = Double.parseDouble(parts[2]);
                        p.lng = Double.parseDouble(parts[3]);
                        p.count = Integer.parseInt(parts[parts.length - 1]);
                    } catch (Exception ex) {
                        // ignore
                    }
                    points.add(p);
                }
                reader.close();
            } catch (Exception ex) {
                // ignore
            }
            try {
                File poi = Environment.getExternalStorageDirectory();
                poi = new File(poi, "CityGuide/routes.dat");
                BufferedReader reader = new BufferedReader(new FileReader(poi));
                reader.readLine();
                Point p = null;
                while (true) {
                    String line = reader.readLine();
                    if (line == null)
                        break;
                    String[] parts = line.split("\\|");
                    if (parts.length == 0)
                        continue;
                    String name = parts[0];
                    if ((name.length() > 0) && (name.substring(0, 1).equals("#"))) {
                        if (p != null) {
                            if ((p.lat != 0) || (p.lng != 0))
                                points.add(p);
                            p = null;
                        }
                        if (name.equals("#[CURRENT]"))
                            continue;
                        p = new Point();
                        p.name = name.substring(1);
                        continue;
                    }
                    if (name.equals("Finish")) {
                        try {
                            p.lat = Double.parseDouble(parts[1]);
                            p.lng = Double.parseDouble(parts[2]);
                        } catch (Exception ex) {
                            // ignore
                        }
                    }
                    if (name.equals("Point")) {
                        try {
                            double lat = Double.parseDouble(parts[1]);
                            double lng = Double.parseDouble(parts[2]);
                            String point = lat + "|" + lng;
                            if (p.points == null) {
                                p.points = point;
                            } else {
                                p.points += ";" + point;
                            }
                        } catch (Exception ex) {
                            // ignore
                        }
                    }
                }
                if (p != null) {
                    if ((p.lat != 0) || (p.lng != 0))
                        points.add(p);
                }
                reader.close();
            } catch (Exception ex) {
                // ignore
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
