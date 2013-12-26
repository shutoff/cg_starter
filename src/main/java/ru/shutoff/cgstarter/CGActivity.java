package ru.shutoff.cgstarter;

import android.app.Activity;
import android.os.Bundle;
import android.view.Window;

public class CGActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.cg_menu);
    }
}
