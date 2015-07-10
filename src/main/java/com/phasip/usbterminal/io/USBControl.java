package com.phasip.usbterminal.io;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.ByteBuffer;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.util.Log;

/**
 * Configures a USB accessory and its input/output streams.
 *
 * Call this.send to sent a byte array to the accessory Override onReceive to
 * process incoming bytes from accessory
 *
 * Originally copied from https://github.com/praveendath92/bard-droid/
 */

public abstract class USBControl implements Runnable {
	private static final int ACCESSORY_PACKET_BUFFER_SIZE = 16384;
	// The permission action
	private static final String ACTION_USB_PERMISSION = "com.phasip.usbterminal.TermActivity.USBPERMISSION";

	// An instance of accessory and manager
	private UsbAccessory mAccessory;
	private UsbManager mManager;
	private Context context;
	private boolean connected = false;
	private ParcelFileDescriptor mFileDescriptor;
	private FileInputStream input;
	private FileOutputStream output;

	private PipedInputStream userInputStream;
	private PipedOutputStream outputToUser;

	// Receiver for connect/disconnect events
	BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();

			if (ACTION_USB_PERMISSION.equals(action)) {
				synchronized (this) {
					UsbAccessory accessory = (UsbAccessory) intent
							.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
					if (intent.getBooleanExtra(
							UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
						if (accessory != null)
							openAccessory(accessory);

					} else {

					}

				}
			} else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
				UsbAccessory accessory = (UsbAccessory) intent
						.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
				if (accessory != null && accessory.equals(mAccessory)) {
					closeAccessory();
				}
			}

		}
	};

	/**
	 * InputStream to read data from the USB Accessory
	 * @return
	 */
	public InputStream getInputStream() {
		return userInputStream;
	}

	/**
	 * OutputStream to write data to the USB Accessory
	 * @return
	 */
	public OutputStream getOutputStream() {
		return output;
	}

	/**
	 * Creates a USBControl object,
	 * You have to call .init() on this object!
	 * @param main
	 */
	public USBControl(Context main)  {
		context = main;
		mManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
		IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
		context.registerReceiver(mUsbReceiver, filter);
	}

	/**
	 * Initializes the USBControl.
	 * @return true if successful and false if not.
	 */
	public boolean init() {
		PendingIntent mPermissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), 0);
		UsbAccessory[] accessoryList = mManager.getAccessoryList();
		if (accessoryList == null)
			return false;
		UsbAccessory mAccessory = accessoryList[0];
		if (mAccessory == null)
			return false;

		if (!mManager.hasPermission(mAccessory)) {
			mManager.requestPermission(mAccessory, mPermissionIntent);
		} else {
			openAccessory(mAccessory);
		}
		return true;
	}

	/**
	 * Called when device is connected
	 */
	public abstract void onConnected();

	/**
	 * Called when device is disconnected
	 */
	public abstract void onDisconnected();


	@Override
	public void run() {
		int ret = 0;
		byte[] buffer = new byte[ACCESSORY_PACKET_BUFFER_SIZE];
		while (connected) {
			try {
				ret = input.read(buffer); //Blocking read
				if (ret == -1)
					break;
				outputToUser.write(buffer, 0, ret);
			} catch (IOException e) {
				break;
			}
		}
	}

	// Sets up filestreams
	private void openAccessory(UsbAccessory accessory) {
		mAccessory = accessory;
		mFileDescriptor = mManager.openAccessory(accessory);
		if (mFileDescriptor != null) {
			FileDescriptor fd = mFileDescriptor.getFileDescriptor();
			input = new FileInputStream(fd);
			output = new FileOutputStream(fd);
			Thread inputThread = new Thread(null, this, "AccessoryThread");
			connected = true;

			/* Use a separate thread to read the input from usb and pipe it to
			the user. Reading directly from the usb device may result in crashes
			if the buffer is too small. The available() doesn't work either (MOTO G LTE)

			I have had no such problems when writing to the usb device.
			 */
			outputToUser = new PipedOutputStream();
			try {
				userInputStream = new PipedInputStream(outputToUser);
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}


			inputThread.start();
			onConnected();
		}
		IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
		filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
		context.registerReceiver(mUsbReceiver, filter);
	}

	// Cleans up accessory
	public void closeAccessory() {
		connected = false;
		try {
			if (mFileDescriptor != null) {
				mFileDescriptor.close();
			}
		} catch (IOException e) {
		} finally {
			mFileDescriptor = null;
			mAccessory = null;
		}

		onDisconnected();
	}

	// Removes the usb receiver
	public void destroyReceiver() {
		System.out.println("destroyReceiver");
		connected = false;
		context.unregisterReceiver(mUsbReceiver);
	}

}
