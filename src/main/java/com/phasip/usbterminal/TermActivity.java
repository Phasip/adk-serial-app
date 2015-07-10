package com.phasip.usbterminal;


import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.DisplayMetrics;
import android.widget.EditText;

import com.phasip.usbterminal.io.USBControl;

import jackpal.androidterm.emulatorview.EmulatorView;
import jackpal.androidterm.emulatorview.TermSession;


/**
 * Initializes an emulatorview that is connected to our USBControl
 *
 * Modified from the telnet emulatorview sample (https://github.com/jackpal/Android-Terminal-Emulator/tree/master/samples/telnet).
 */
public class TermActivity extends Activity
{
    final private static String TAG = "TermActivity";
    private EmulatorView mEmulatorView;
    private TermSession mSession;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.term_activity);

        /**
         * EmulatorView setup.
         */
        EmulatorView view = (EmulatorView) findViewById(R.id.emulatorView);
        mEmulatorView = view;

        /* Let the EmulatorView know the screen's density. */
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        view.setDensity(metrics);


        connectToUsb();
    }

    @Override
    protected void onResume() {
        super.onResume();

        /* You should call this to let EmulatorView know that it's visible
           on screen. */

        mEmulatorView.onResume();

    }

    @Override
    protected void onPause() {
        /* You should call this to let EmulatorView know that it's no longer
           visible on screen. */
        mEmulatorView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        /**
         * Finish the TermSession when we're destroyed.  This will free
         * resources, stop I/O threads, and close the I/O streams attached
         * to the session.
         */
        if (mSession != null) {
            mSession.finish();
        }
        if (uc != null)
            uc.destroyReceiver();

        super.onDestroy();
    }

    private USBControl uc;
    /**
     * Connect to the USB Accessory
     */
    public void connectToUsb() {
        uc = new USBControl(this) {
            @Override
            public void onConnected() {
                mHandler.sendEmptyMessage(MSG_CONNECTED);
            }

            @Override
            public void onDisconnected() {
                mHandler.sendEmptyMessage(MSG_DISCONNECT);
            }
        };
        /* Not really sure how I should handle a uc.init() = false
           Fix this before using for anything serious
         */
        while (uc.init() == false)
            continue;

    }

    /**
     * Handler which will receive the message from the USBControl
     */
    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_CONNECTED) {
                createUsbSession();
            }
            if (msg.what == MSG_DISCONNECT) {
                uc = null;
                finish();
            }
        }
    };
    private static final int MSG_CONNECTED = 1;
    private static final int MSG_DISCONNECT = 2;

    /* Connect the USB session to the terminal emulation. */
    private void createUsbSession() {

        /* Create the TermSession and attach it to the view.. */
        TermSession ts = new TermSession();
        ts.setTermIn(uc.getInputStream());
        ts.setTermOut(uc.getOutputStream());
        mEmulatorView.attachSession(ts);
        mSession = ts;
    }
}
