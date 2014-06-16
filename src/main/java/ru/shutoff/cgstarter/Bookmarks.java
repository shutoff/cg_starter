package ru.shutoff.cgstarter;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;

public class Bookmarks {

    static Point[] get(Context context) {
        Vector<Point> points = new Vector<Point>();
        try {
            File poi = State.CG_Folder(context);
            poi = new File(poi, "CGMaps/poi.bkm");
            if (!poi.exists())
                poi = new File(poi, "CGMaps/Poi.bkm");
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
                } catch (Exception ex) {
                    continue;
                }
                try {
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
            File poi = State.CG_Folder(context);
            if (State.cg_files) {
                poi = new File(poi, "routes.dat");
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
            } else {
                poi = new File(poi, "Routes");
                File[] routes = poi.listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File pathname) {
                        if (pathname.isDirectory())
                            return false;
                        if (!pathname.canRead())
                            return false;
                        String name = pathname.getName();
                        int pos = name.lastIndexOf(".");
                        if (pos < 0)
                            return false;
                        return name.substring(pos + 1).equals("route");
                    }
                });
                for (File route : routes) {
                    BufferedReader reader = null;
                    try {
                        Point p = null;
                        reader = new BufferedReader(new FileReader(route));
                        String line = reader.readLine();
                        if (line != null) {
                            String[] parts = line.split("\\|");
                            if (parts.length > 3) {
                                p = new Point();
                                p.name = parts[3];
                            }
                        }
                        if (p != null) {
                            Vector<PP> pp = new Vector<PP>();
                            while (true) {
                                line = reader.readLine();
                                if (line == null)
                                    break;
                                String[] parts = line.split("\\|");
                                if (parts.length > 3) {
                                    if (parts[0].equals("3")) {
                                        p.lat = Double.parseDouble(parts[2]);
                                        p.lng = Double.parseDouble(parts[3]);
                                    } else if (parts[0].equals("2")) {
                                        PP p1 = new PP();
                                        p1.lat = Double.parseDouble(parts[2]);
                                        p1.lng = Double.parseDouble(parts[3]);
                                        p1.order = Integer.parseInt(parts[5]);
                                        pp.add(p1);
                                    }
                                }
                            }
                            Collections.sort(pp, new Comparator<PP>() {
                                @Override
                                public int compare(PP lhs, PP rhs) {
                                    if (lhs.order < rhs.order)
                                        return -1;
                                    if (lhs.order > rhs.order)
                                        return 1;
                                    return 0;
                                }
                            });
                            for (PP p1 : pp) {
                                String point = p1.lat + "|" + p1.lng;
                                if (p.points == null) {
                                    p.points = point;
                                } else {
                                    p.points += ";" + point;
                                }
                            }
                        }
                        if ((p.lat != 0) || (p.lng != 0))
                            points.add(p);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    } finally {
                        if (reader != null)
                            reader.close();
                    }
                }
            }
        } catch (Exception ex) {
            // ignore
        }
        Point p = new Point();
        p.name = context.getString(R.string.voice_search);
        p.lat = -1;
        p.lng = -1;
        p.count = 0;
        points.add(p);
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

        Point[] result = new Point[points.size()];
        points.copyInto(result);
        return result;

    }

    static class Point {
        String name;
        double lat;
        double lng;
        String points;
        int count;
    }

    static class PP {
        double lat;
        double lng;
        int order;
    }
}
