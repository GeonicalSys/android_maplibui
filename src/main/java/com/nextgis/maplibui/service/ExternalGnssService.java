package com.nextgis.maplibui.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.hypertrack.hyperlog.HyperLog;
import com.nextgis.maplib.gnss.GnssInputPrefs;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplibui.R;

/**
 * Holds the process while an in-app NMEA session is configured. Does not open
 * its own LocationManager subscription.
 */
public class ExternalGnssService extends Service {
    private static final int NOTIFICATION_ID = 18_406;

    public static void setEnabled(Context context, boolean enabled) {
        Context app = context.getApplicationContext();
        Intent intent = new Intent(app, ExternalGnssService.class);
        if (enabled) {
            try {
                ContextCompat.startForegroundService(app, intent);
            } catch (RuntimeException exception) {
                HyperLog.w(Constants.TAG, "External GNSS keep-alive start failed", exception);
            }
        } else {
            app.stopService(intent);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            startForeground(NOTIFICATION_ID, buildNotification());
        } catch (RuntimeException exception) {
            HyperLog.w(Constants.TAG, "External GNSS keep-alive foreground rejected", exception);
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification buildNotification() {
        createChannel();
        Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
        PendingIntent openApp = null;
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            openApp = PendingIntent.getActivity(
                    this,
                    0,
                    launch,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String name = GnssInputPrefs.deviceName(prefs);
        String text = TextUtils.isEmpty(name)
                ? getString(R.string.external_gnss_keep_alive_text)
                : name;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId())
                .setSmallIcon(R.drawable.ic_location)
                .setContentTitle(getString(R.string.external_gnss_keep_alive_title))
                .setContentText(text)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true);
        if (openApp != null) {
            builder.setContentIntent(openApp);
        }
        return builder.build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                channelId(),
                getString(R.string.external_gnss_keep_alive_channel),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.external_gnss_keep_alive_channel_summary));
        channel.setSound(null, null);
        channel.enableVibration(false);
        channel.setShowBadge(false);
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private String channelId() {
        return getPackageName() + ".external_gnss";
    }
}
