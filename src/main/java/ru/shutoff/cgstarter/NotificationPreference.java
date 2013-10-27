package ru.shutoff.cgstarter;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AttributeSet;

public class NotificationPreference extends SeekBarPreference {

    public NotificationPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void showDialog(Bundle state) {
        if (!isNotificationEnabled()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle(R.string.notification_title);
            builder.setMessage(R.string.notification_message);
            builder.setPositiveButton(R.string.cont, null);
            builder.create().show();
            return;
        }
        super.showDialog(state);
    }

    String summary() {
        if (isNotificationEnabled())
            return getContext().getString(R.string.notification) + " " + mValue + " " + mSuffix;
        return getContext().getString(R.string.notification_sum);
    }

    boolean isNotificationEnabled() {
        int accessibilityEnabled = 0;
        final String NOTIFICATION_SERVICE = "ru.shutoff.cgstarter/ru.shutoff.cgstarter.NotificationService";
        boolean accessibilityFound = false;
        try {
            accessibilityEnabled = Settings.Secure.getInt(getContext().getContentResolver(), android.provider.Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException e) {
            // ignore
        }
        if (accessibilityEnabled == 1) {
            String settingValue = Settings.Secure.getString(getContext().getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (settingValue != null) {
                TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
                splitter.setString(settingValue);
                while (splitter.hasNext()) {
                    String accessabilityService = splitter.next();
                    if (accessabilityService.equalsIgnoreCase(NOTIFICATION_SERVICE)) {
                        return true;
                    }
                }
            }
        }
        return accessibilityFound;
    }
}
