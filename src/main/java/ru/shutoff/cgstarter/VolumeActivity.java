package ru.shutoff.cgstarter;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Bundle;
import android.preference.PreferenceManager;

public class VolumeActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
            int channel = preferences.getInt(State.CUR_CHANNEL, 0);
            AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            int cur_level = audio.getStreamVolume(channel);
            if (preferences.getInt(State.SAVE_CHANNEL, -1) < 0) {
                SharedPreferences.Editor ed = preferences.edit();
                ed.putInt(State.SAVE_CHANNEL, channel);
                ed.putInt(State.SAVE_LEVEL, cur_level);
                ed.commit();
            }
            if (cur_level == 0) {
                audio.setStreamVolume(channel, preferences.getInt(State.MUTE_LEVEL, audio.getStreamMaxVolume(channel)), 0);
            } else {
                SharedPreferences.Editor ed = preferences.edit();
                ed.putInt(State.MUTE_LEVEL, cur_level);
                ed.commit();
                audio.setStreamVolume(channel, 0, 0);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        finish();
    }
}
