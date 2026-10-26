package com.nextgis.maplibui.activity;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import com.nextgis.maplibui.R;
import com.nextgis.maplibui.service.TrackerService;
import com.nextgis.maplibui.util.TrackActions;

/** Direct notification Activity avoids background-service notification trampolines. */
public final class TrackStopConfirmationActivity extends NGActivity {
    private AlertDialog dialog;

    public static Intent intent(Context context, String session) {
        return new Intent(context, TrackStopConfirmationActivity.class)
                .setData(Uri.parse("nextgis-track-stop:/" + Uri.encode(String.valueOf(session))))
                .putExtra(TrackerService.EXTRA_STOP_SESSION, session)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
    }

    @Override public int getThemeId() {
        return mIsDarkTheme ? R.style.Theme_NextGIS_TrackStop_Dark : R.style.Theme_NextGIS_TrackStop_Light;
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        dialog = TrackActions.confirmStop(this,
                getIntent().getStringExtra(TrackerService.EXTRA_STOP_SESSION), "notification", this::finish);
        if (dialog == null) finish();
    }

    @Override protected void onDestroy() {
        if (dialog != null) {
            dialog.setOnDismissListener(null);
            dialog.dismiss();
        }
        super.onDestroy();
    }
}
