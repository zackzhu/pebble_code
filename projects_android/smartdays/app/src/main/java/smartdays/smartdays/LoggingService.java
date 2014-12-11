package smartdays.smartdays;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

import com.getpebble.android.kit.PebbleKit;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by hector on 01/12/14.
 */
public class LoggingService extends Service {

    private NotificationManager notificationManager;
    private int NOTIFICATION = R.string.local_service_started;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    PhoneSensorEventListener phoneSensorEventListener;

    private BufferedOutputStream bufferOutPebble = null;
    private BufferedOutputStream bufferOutPhoneSynced = null;
    private BufferedOutputStream bufferOutPhone = null;
    private PhoneDataBuffer phoneDataBuffer;

    private SmartDaysPebbleDataLogReceiver dataloggingReceiver;

    private PowerManager.WakeLock wakeLock;

    private static boolean running = false;
    private static LoggingService instance;


    public static boolean isRunning() {
        return running;
    }

    public static LoggingService getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        Log.d("SmartDAYS", "Creating...");
        instance = this;
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        PowerManager pm = (PowerManager)this.getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SmartDAYS");

        // Display a notification about us starting.  We put an icon in the status bar.
        showNotification();

        try {
            File root = Environment.getExternalStorageDirectory();

            // Create the file
            bufferOutPebble = new BufferedOutputStream(new FileOutputStream(new File(root, "testPebbleAccel")));
            bufferOutPhoneSynced = new BufferedOutputStream(new FileOutputStream(new File(root, "testPhoneSyncedAccel")));
            bufferOutPhone = new BufferedOutputStream(new FileOutputStream(new File(root, "testPhoneAccel")));
            phoneDataBuffer = new PhoneDataBuffer(10000);
            Log.d("SmartDAYS", "Files created...");

        } catch (IOException ioe) {
            Log.d("SmartDAYS", "Error creating file...");
        }
        running = false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("LocalService", "Received start id " + startId + ": " + intent);

        if (!running) {
            dataloggingReceiver = new SmartDaysPebbleDataLogReceiver(Constants.WATCHAPP_UUID, bufferOutPebble, bufferOutPhoneSynced, phoneDataBuffer);
            phoneSensorEventListener = new PhoneSensorEventListener(phoneDataBuffer, bufferOutPhone);

            startLoggingPebble();
            startLoggingPhone();
            running = true;
        }

        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {

        // Cancel the persistent notification.
        notificationManager.cancel(NOTIFICATION);

        // Tell the user we stopped.
        Toast.makeText(this, R.string.local_service_stopped, Toast.LENGTH_SHORT).show();

        try {
            unregisterReceiver(dataloggingReceiver);
            Log.d("SmartDAYS", "Unregistering receiver...");
        }
        catch (NullPointerException iae) {
            Log.d("SmartDAYS", "Ending service... null pointer");
        }
        catch (IllegalArgumentException iae) {
            Log.d("SmartDAYS", "Unregistering receiver... already unregistered");
        }

        sensorManager.unregisterListener(phoneSensorEventListener);
        wakeLock.release();

        try {
            bufferOutPebble.close();
            Log.d("SmartDAYS", "File testCapture closed...");
        }
        catch (IOException ioe) {
            Log.d("SmartDAYS", "Error closing file...");
        }
        catch (NullPointerException iae) {
            Log.d("SmartDAYS", "Closing file Pebble... null pointer");
        }

        try {
            bufferOutPhone.close();
            Log.d("SmartDAYS", "File testCapture closed...");
        }
        catch (IOException ioe) {
            Log.d("SmartDAYS", "Error closing file...");
        }
        catch (NullPointerException iae) {
            Log.d("SmartDAYS", "Closing file Phone... null pointer");
        }

        running = false;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Show a notification while this service is running.
     */
    private void showNotification() {
        // In this sample, we'll use the same text for the ticker and the expanded notification
        CharSequence text = getText(R.string.local_service_started);

        Intent resultIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        // Set the icon, scrolling text and timestamp
        Notification notification = new Notification.Builder(this)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher)
                .setTicker(text)
                .setWhen(System.currentTimeMillis())
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .build();

        // Send the notification.
        notificationManager.notify(NOTIFICATION, notification);

        // Tell the user we started.
        Toast.makeText(this, R.string.local_service_started, Toast.LENGTH_SHORT).show();
    }

    private void startLoggingPebble() {
        // Register DataLogging Receiver
        PebbleKit.registerDataLogReceiver(this, dataloggingReceiver);
    }

    private void startLoggingPhone() {
        sensorManager.registerListener(phoneSensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        wakeLock.acquire();
    }

    public void setOffset(long o) {
        dataloggingReceiver.setOffset(o);
    }
}