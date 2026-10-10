package com.nextgis.maplibui.mapui;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Base64;
import com.nextgis.maplib.util.TrackSendSettings;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Last confirmed registration, scoped to the exact Tracker Hub and device UID. */
public final class TrackRegistrationState {
    public static final String PREF_REGISTERED_DEVICE = "track_registered_device";
    public static final String PREF_HUB = "tracker_hub_url";

    private TrackRegistrationState() {}

    static String deviceUrl(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.getString(PREF_HUB, TrackWorker.HOST) + TrackWorker.URL + "/" + TrackWorker.getUid(context);
    }

    public static boolean canShowPending(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return TrackSendSettings.isEnabled(preferences)
                && fingerprint(deviceUrl(context)).equals(preferences.getString(PREF_REGISTERED_DEVICE, null));
    }

    static void record(Context context, String device, boolean registered) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String identity = fingerprint(device);
        if (registered) preferences.edit().putString(PREF_REGISTERED_DEVICE, identity).apply();
        else if (identity.equals(preferences.getString(PREF_REGISTERED_DEVICE, null)))
            preferences.edit().remove(PREF_REGISTERED_DEVICE).apply();
        // Transport failures do not revoke an earlier positive server response.
    }

    private static String fingerprint(String device) {
        try {
            return Base64.encodeToString(MessageDigest.getInstance("SHA-256")
                    .digest(device.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
