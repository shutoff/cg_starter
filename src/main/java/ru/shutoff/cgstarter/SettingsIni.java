package ru.shutoff.cgstarter;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;

public class SettingsIni {

    static int getParam(Context context, String name) {
        int res = 0;
        BufferedReader reader = null;
        try {
            File settings = State.CG_Folder(context);
            settings = new File(settings, "settings.ini");
            reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(settings), Charset.forName("UTF-16LE")));
            while (true) {
                String line = reader.readLine();
                if (line == null)
                    break;
                String[] parts = line.split("=");
                if (parts[0].equals(name)) {
                    res = Integer.parseInt(parts[1]);
                    break;
                }
            }
        } catch (Exception ex) {
            // ignore
        }
        if (reader != null) {
            try {
                reader.close();
            } catch (Exception ex) {
                // ignore
            }
        }
        return res;
    }

    static void setParam(Context context, String name, String value) {
        BufferedReader reader = null;
        OutputStreamWriter writer = null;
        try {
            File cg = State.CG_Folder(context);
            File settings = new File(cg, "settings.ini");
            File new_settings = new File(cg, "settings.ini_");
            reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(settings), Charset.forName("UTF-16LE")));
            writer = new OutputStreamWriter(new FileOutputStream(new_settings), Charset.forName("UTF-16LE"));
            while (true) {
                String line = reader.readLine();
                if (line == null)
                    break;
                String[] parts = line.split("=");
                if (parts[0].equals(name))
                    line = name + "=" + value;
                writer.append(line);
                writer.append("\n");
            }
            reader.close();
            writer.close();
            new_settings.renameTo(settings);
            reader.close();
            writer.close();
            return;
        } catch (Exception ex) {
            // ignore
        }
        if (reader != null) {
            try {
                reader.close();
            } catch (Exception ex) {
                // ignore
            }
        }
        if (writer != null) {
            try {
                writer.close();
            } catch (Exception ex) {
                // ignore
            }
        }

    }

}
