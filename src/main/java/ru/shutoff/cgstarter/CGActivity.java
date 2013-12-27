package ru.shutoff.cgstarter;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;

public class CGActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.cg_menu);
        setResult(RESULT_CANCELED);
        findViewById(R.id.from_contacts).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(CGActivity.this, ContactActivity.class);
                startActivityForResult(i, 1);
            }
        });
        findViewById(R.id.from_sms).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(CGActivity.this, SMSActivity.class);
                startActivityForResult(i, 1);
            }
        });
        findViewById(R.id.no_route).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (OnExitService.isRunCG(CGActivity.this))
                    CarMonitor.killCG(CGActivity.this);
                CarMonitor.startCG(CGActivity.this, "-", null);
                setResult(RESULT_OK);
                finish();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if ((requestCode == 1) && (resultCode == RESULT_OK)) {
            setResult(RESULT_OK);
            finish();
        }
    }
}
