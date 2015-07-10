package com.phasip.usbterminal;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

public class NoneActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Toast.makeText(this,R.string.tost_text,Toast.LENGTH_LONG).show();
        finish();
    }
}
