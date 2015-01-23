package ru.shutoff.cgstarter;

import android.app.ActivityManager;
import android.app.UiModeManager;
import android.app.admin.DevicePolicyManager;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.preference.PreferenceManager;
import android.telephony.SmsMessage;
import android.widget.Toast;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CarMonitor extends BroadcastReceiver {

    static private final String START = "ru.shutoff.cgstarter.START";
    static private final String FIRE = "com.twofortyfouram.locale.intent.action.FIRE_SETTING";
    SensorManager sensorManager;
    Sensor sensorAccelerometer;
    Sensor sensorMagnetic;
    SensorEventListener sensorEventListener;
    float[] gravity;
    float[] magnetic;
    float[] orientation;
    private CountDownTimer power_timer;
    private CountDownTimer power_kill_timer;
    private CountDownTimer dock_kill_timer;

    static void startCG(Context context, String route, String route_points, SearchActivity.Address addr) {
        if (route.equals("-")) {
            MainActivity.removeRoute(context);
        } else if (!route.equals("")) {
            MainActivity.createRoute(context, route, route_points, addr);
        }
        if (!MainActivity.setState(context, new State.OnBadGPS() {
            @Override
            public void gps_message(Context context) {
                Toast toast = Toast.makeText(context, context.getString(R.string.no_gps_title), Toast.LENGTH_SHORT);
                toast.show();
            }
        })) {
            MainActivity.setState(context, null);
        }
        Intent run = context.getPackageManager().getLaunchIntentForPackage(State.CG_Package(context));
        if (run == null) {
            Toast toast = Toast.makeText(context, context.getString(R.string.no_cg), Toast.LENGTH_SHORT);
            toast.show();
            return;
        }
        context.startActivity(run);
        Intent service = new Intent(context, OnExitService.class);
        service.setAction(OnExitService.START);
        context.startService(service);
    }

    static void killCG(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> procInfos = activityManager.getRunningAppProcesses();
        if (procInfos == null)
            return;
        int i;
        for (i = 0; i < procInfos.size(); i++) {
            ActivityManager.RunningAppProcessInfo proc = procInfos.get(i);
            if (proc.processName.equals(State.CG_Package(context))) {
                State.doRoot(context, "kill " + proc.pid, true);
            }
        }
    }

    static void lockDevice(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
                DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
                dpm.lockNow();
            }
        } catch (Exception ex) {
            // ignore
        }
    }

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (intent == null)
            return;
        String action = intent.getAction();
        if (action == null)
            return;
        if ((Build.VERSION.SDK_INT > Build.VERSION_CODES.FROYO) && action.equals(UiModeManager.ACTION_ENTER_CAR_MODE)) {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            if (preferences.getBoolean(State.CAR_MODE, false)) {
                setCarMode(context, true);
                abortBroadcast();
            }
        }
        if (action.equals(Intent.ACTION_DOCK_EVENT)) {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            if (preferences.getBoolean(State.CAR_MODE, false)) {
                int dockState = intent.getIntExtra(Intent.EXTRA_DOCK_STATE, -1);
                setCarMode(context, (dockState == Intent.EXTRA_DOCK_STATE_CAR));
                abortBroadcast();
            }
        }

        if (action.equals("android.provider.Telephony.SMS_RECEIVED")) {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            if (!preferences.getBoolean(State.SHOW_SMS, false))
                return;
            Object[] pduArray = (Object[]) intent.getExtras().get("pdus");
            SmsMessage[] messages = new SmsMessage[pduArray.length];
            for (int i = 0; i < pduArray.length; i++) {
                messages[i] = SmsMessage.createFromPdu((byte[]) pduArray[i]);
            }
            final String sms_from = messages[0].getOriginatingAddress();
            StringBuilder bodyText = new StringBuilder();
            for (SmsMessage m : messages) {
                bodyText.append(m.getMessageBody());
            }
            try {
                final String body = bodyText.toString();
                Pattern pattern = Pattern.compile("([0-9]{1,2}\\.[0-9]{4,7})[^0-9]+([0-9]{1,2}\\.[0-9]{4,7})");
                Matcher matcher = pattern.matcher(body);
                if (!matcher.find())
                    return;
                Intent i = new Intent(context, SmsDialog.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                i.putExtra(State.LATITUDE, matcher.group(1));
                i.putExtra(State.LONGITUDE, matcher.group(2));
                i.putExtra(State.INFO, sms_from);
                i.putExtra(State.TEXT, body);
                context.startActivity(i);
            } catch (Exception ex) {
                // ignore
            }
        }
        if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            SharedPreferences.Editor ed = preferences.edit();
            ed.remove(State.BT_DEVICES);
            ed.commit();
        }
        if (action.equals(Intent.ACTION_POWER_CONNECTED)) {
            if (power_kill_timer != null) {
                power_kill_timer.cancel();
                power_kill_timer = null;
            }
            if (!OnExitService.is_run && (power_timer == null)) {
                final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
                String interval = preferences.getString(State.POWER_TIME, "");
                if (State.inInterval(interval)) {
                    orientation = null;
                    if (preferences.getBoolean(State.VERTICAL, true)) {
                        if (sensorEventListener == null) {
                            sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
                            if (sensorAccelerometer == null)
                                sensorAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                            if (sensorMagnetic == null)
                                sensorMagnetic = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
                            if (sensorEventListener == null) {
                                sensorEventListener = new SensorEventListener() {
                                    @Override
                                    public void onSensorChanged(SensorEvent event) {
                                        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
                                            magnetic = event.values;
                                        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
                                            gravity = event.values;
                                        if ((gravity == null) || (magnetic == null))
                                            return;
                                        float[] R = new float[9];
                                        float[] I = new float[9];
                                        if (!SensorManager.getRotationMatrix(R, I, gravity, magnetic))
                                            return;
                                        if (orientation == null)
                                            orientation = new float[3];
                                        SensorManager.getOrientation(R, orientation);
                                    }

                                    @Override
                                    public void onAccuracyChanged(Sensor sensor, int accuracy) {

                                    }
                                };
                                sensorManager.registerListener(sensorEventListener, sensorAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
                                sensorManager.registerListener(sensorEventListener, sensorMagnetic, SensorManager.SENSOR_DELAY_NORMAL);
                            }
                        }
                    }
                    power_timer = new CountDownTimer(10000, 2000) {
                        @Override
                        public void onTick(long millisUntilFinished) {
                            if (preferences.getBoolean(State.VERTICAL, true)) {
                                if (orientation == null)
                                    return;
                                if ((Math.abs(orientation[1]) + Math.abs(orientation[2])) < 1) {
                                    if (sensorEventListener != null) {
                                        sensorManager.unregisterListener(sensorEventListener);
                                        sensorEventListener = null;
                                    }
                                    orientation = null;
                                    if (power_timer != null)
                                        power_timer.cancel();
                                    power_timer = null;
                                    return;
                                }
                                orientation = null;
                            }
                            if (sensorEventListener != null) {
                                sensorManager.unregisterListener(sensorEventListener);
                                sensorEventListener = null;
                            }
                            if (power_timer != null)
                                power_timer.cancel();
                            power_timer = null;
                            if (!OnExitService.isRunCG(context)) {
                                context.sendBroadcast(new Intent("com.smartmadsoft.xposed.nolockhome.UNLOCK"));
                                Intent run = new Intent(context, MainActivity.class);
                                run.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                context.startActivity(run);
                            }
                        }

                        @Override
                        public void onFinish() {
                            if (sensorEventListener != null) {
                                sensorManager.unregisterListener(sensorEventListener);
                                sensorEventListener = null;
                            }
                            orientation = null;
                        }
                    };
                    power_timer.start();
                }
            }
        }
        if (action.equals(Intent.ACTION_POWER_DISCONNECTED)) {
            if (power_timer != null) {
                power_timer.cancel();
                power_timer = null;
            }
            if (power_kill_timer != null) {
                power_kill_timer.cancel();
                power_kill_timer = null;
            }
            if (OnExitService.is_run) {
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
                if (preferences.getBoolean(State.KILL_POWER, false) && preferences.getString(State.BT_DEVICES, "").equals("")) {
                    power_kill_timer = new CountDownTimer(8000, 8000) {
                        @Override
                        public void onTick(long millisUntilFinished) {

                        }

                        @Override
                        public void onFinish() {
                            power_kill_timer = null;
                            OnExitService.force_exit = true;
                            killCG(context);
                            lockDevice(context);
                        }
                    };
                    power_kill_timer.start();
                }
            }
        }
        if (action.equals(BluetoothDevice.ACTION_ACL_CONNECTED)) {
            BluetoothDevice device = (BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            String devices = preferences.getString(State.BT_DEVICES, "");
            if (!devices.equals(""))
                devices += ";";
            devices += device.getAddress();
            SharedPreferences.Editor ed = preferences.edit();
            ed.putString(State.BT_DEVICES, devices);
            ed.commit();
        }
        if (action.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED)) {
            BluetoothDevice device = (BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            OnExitService.turnOffBT(context, device.getAddress());
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            if (preferences.getBoolean(State.KILL_POWER, false) && preferences.getString(State.BT_DEVICES, "").equals("")) {
                OnExitService.force_exit = true;
                killCG(context);
                lockDevice(context);
            }
        }
        if (action.equals(Intent.ACTION_NEW_OUTGOING_CALL)) {
            OnExitService.call_number = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
            Pattern pattern = Pattern.compile("([0-9]{1,2}\\.[0-9]{4,7})[^0-9]+([0-9]{1,2}\\.[0-9]{4,7})");
            Matcher matcher = pattern.matcher(OnExitService.call_number);
            if (!matcher.find())
                return;
            Intent i = new Intent(context, SmsDialog.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.putExtra(State.LATITUDE, matcher.group(1));
            i.putExtra(State.LONGITUDE, matcher.group(2));
            i.putExtra(State.INFO, R.string.go);
            i.putExtra(State.TEXT, OnExitService.call_number);
            context.startActivity(i);
            abortBroadcast();
        }
        if (action.equals(FIRE)) {
            Bundle data = intent.getBundleExtra(EditActivity.EXTRA_BUNDLE);
            try {
                String route = data.get(State.ROUTE).toString();
                String points = data.get(State.POINTS).toString();
                startCG(context, route, points, null);
            } catch (Exception ex) {
                // ignore
            }
        }
        if (action.equals(START)) {
            String route = intent.getStringExtra("ROUTE");
            String route_points = intent.getStringExtra("POINTS");
            if (route == null)
                route = "";
            if (route_points == null)
                route_points = "";
            int n_route = intent.getIntExtra("ROUTE", 0);
            if (n_route > 0) {
                State.Point[] points = State.get(context, false);
                if (n_route > points.length)
                    return;
                State.Point p = points[n_route - 1];
                if ((p.lat.equals("")) && (p.lng.equals("")))
                    return;
                route = p.lat + "|" + p.lng;
                route_points = p.points;
            } else if (n_route < 0) {
                MainActivity.removeRoute(context);
                route = "";
            }
            startCG(context, route, route_points, null);
        }
    }

    void setCarMode(final Context context, boolean newMode) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean curMode = preferences.getBoolean(State.CAR_STATE, false);
        if (curMode != newMode) {
            SharedPreferences.Editor ed = preferences.edit();
            ed.putBoolean(State.CAR_STATE, newMode);
            ed.commit();
            if (newMode) {
                if (!OnExitService.isRunCG(context)) {
                    Intent run = new Intent(context, MainActivity.class);
                    run.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(run);
                    ed.commit();
                }
                if (dock_kill_timer != null) {
                    dock_kill_timer.cancel();
                    dock_kill_timer = null;
                }
            } else {
                if (preferences.getBoolean(State.KILL_CAR, false)) {
                    dock_kill_timer = new CountDownTimer(2000, 2000) {
                        @Override
                        public void onTick(long millisUntilFinished) {

                        }

                        @Override
                        public void onFinish() {
                            dock_kill_timer = null;
                            OnExitService.force_exit = true;
                            killCG(context);
                            lockDevice(context);
                        }
                    };
                    dock_kill_timer.start();
                }
            }
        }
    }
}

