package ru.shutoff.cgstarter;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Notification;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.RemoteViews;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class NotificationService extends AccessibilityService {

    private boolean init;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {

        final int eventType = event.getEventType();
        if (eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            Parcelable parcelable = event.getParcelableData();

            if (parcelable instanceof Notification) {

                Notification notification = (Notification) parcelable;
                RemoteViews views = notification.contentView;
                Class secretClass = views.getClass();

                String msg_title = null;
                String msg_info = null;
                String msg_text = null;
                String msg_app = event.getPackageName().toString();

                try {
                    Field outerFields[] = secretClass.getDeclaredFields();
                    for (Field outerField : outerFields) {
                        if (!outerField.getName().equals("mActions")) continue;

                        outerField.setAccessible(true);

                        ArrayList<Object> actions = (ArrayList<Object>) outerField.get(views);
                        for (Object action : actions) {
                            Field innerFields[] = action.getClass().getDeclaredFields();

                            Object value = null;
                            Integer type = null;
                            Integer viewId = null;
                            for (Field field : innerFields) {
                                field.setAccessible(true);
                                if (field.getName().equals("value")) {
                                    value = field.get(action);
                                } else if (field.getName().equals("type")) {
                                    type = field.getInt(action);
                                } else if (field.getName().equals("viewId")) {
                                    viewId = field.getInt(action);
                                }
                            }

                            if ((value != null) && ((type == 9) || (type == 10))){
                                if ((msg_title == null) && (viewId == 16908310))
                                    msg_title = value.toString();
                                if ((msg_info == null) && (viewId == 16909082))
                                    msg_info = value.toString();
                                if ((msg_text == null) && (viewId == 16908358))
                                    msg_text = value.toString();

                            }
                        }
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (msg_text == null) {
                    List<CharSequence> messages = event.getText();
                    if (messages.size() > 0)
                        msg_text = messages.get(0).toString();
                }
                if (msg_text != null) {
                    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
                    String[] ignored = preferences.getString(State.NOTIFICATION_IGNORE, "").split(":");
                    for (String app : ignored) {
                        if (app.equals(msg_app))
                            return;
                    }
                    Intent intent = new Intent(OnExitService.NOTIFICATION);
                    intent.putExtra(State.TITLE, msg_title);
                    intent.putExtra(State.INFO, msg_info);
                    intent.putExtra(State.TEXT, msg_text);
                    intent.putExtra(State.APP, msg_app);
                    intent.putExtra(State.ICON, notification.icon);
                    sendBroadcast(intent);
                }
            }
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        if (!init) {
            AccessibilityServiceInfo info = new AccessibilityServiceInfo();
            info.eventTypes = AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED;
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN;
            setServiceInfo(info);
            init = true;
        }
    }

    @Override
    public void onInterrupt() {
        init = false;
    }

}
