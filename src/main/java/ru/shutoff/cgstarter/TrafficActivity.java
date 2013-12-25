package ru.shutoff.cgstarter;

import android.app.Activity;
import android.os.Bundle;

public class TrafficActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        OnExitService.startYan(this);
        finish();
    }
}
