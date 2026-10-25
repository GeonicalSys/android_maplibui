package com.nextgis.maplibui.util;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.nextgis.maplib.api.IGISApplication;
import com.nextgis.maplib.map.TrackLayer;
import com.nextgis.maplibui.R;
import com.nextgis.maplibui.service.TrackerService;

import yuku.ambilwarna.AmbilWarnaDialog;

/** Shared list/map actions; metadata stays in the installation's shared track store. */
public final class TrackActions {
    private TrackActions() { }

    /** Merely opening/dismissing this window never changes recording intent or pending Stop. */
    public static AlertDialog confirmStop(Context context, String session, String source, Runnable dismissed) {
        if (session == null || !session.equals(TrackerService.getRecordingSessionId(context))) return null;
        com.hypertrack.hyperlog.HyperLog.i(com.nextgis.maplib.util.Constants.TAG,
                "TrackerService Stop confirmation shown source=" + source);
        AlertDialog dialog = FittedDialogs.compatBuilder(context).setTitle(R.string.track_stop)
                .setMessage(R.string.track_stop_confirm)
                .setNegativeButton(R.string.cancel, (ignored, which) ->
                        com.hypertrack.hyperlog.HyperLog.i(com.nextgis.maplib.util.Constants.TAG,
                                "TrackerService Stop confirmation cancelled source=" + source))
                .setPositiveButton(R.string.tracks_stop, (ignored, which) -> {
                    if (!TrackerService.stopRecording(context, session, source)
                            && session.equals(TrackerService.getRecordingSessionId(context)))
                        failed(context, null);
                }).create();
        dialog.setOnCancelListener(ignored -> com.hypertrack.hyperlog.HyperLog.i(
                com.nextgis.maplib.util.Constants.TAG,
                "TrackerService Stop confirmation dismissed source=" + source));
        if (dismissed != null) dialog.setOnDismissListener(ignored -> dismissed.run());
        dialog.show();
        return dialog;
    }

    public static Uri uri(Context context) {
        return Uri.parse("content://" + ((IGISApplication) context.getApplicationContext()).getAuthority()
                + "/" + TrackLayer.TABLE_TRACKS);
    }

    public static TrackLayer.TrackInfo find(Context context, long id) {
        try (Cursor cursor = context.getContentResolver().query(uri(context),
                new String[]{TrackLayer.FIELD_ID, TrackLayer.FIELD_NAME, TrackLayer.FIELD_COLOR,
                        TrackLayer.FIELD_VISIBLE, TrackLayer.FIELD_END},
                TrackLayer.FIELD_ID + "=?", new String[]{Long.toString(id)}, null)) {
            return cursor != null && cursor.moveToFirst() ? TrackLayer.TrackInfo.fromCursor(cursor) : null;
        }
    }

    public static AlertDialog rename(Context context, long id, Runnable changed) {
        TrackLayer.TrackInfo info = find(context, id);
        if (info == null) return null;
        EditText input = new EditText(context);
        input.setId(R.id.track_name_input);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setText(info.name);
        input.selectAll();
        input.setHint(R.string.track_name);
        int padding = Math.round(20 * context.getResources().getDisplayMetrics().density);
        input.setPadding(padding, padding / 2, padding, padding / 2);
        AlertDialog dialog = FittedDialogs.compatBuilder(context).setTitle(R.string.track_rename)
                .setView(input).setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) { input.setError(context.getString(R.string.track_name_required)); return; }
            ContentValues values = new ContentValues();
            values.put(TrackLayer.FIELD_NAME, name);
            if (update(context, new String[]{Long.toString(id)}, values, changed)) dialog.dismiss();
        }));
        dialog.show();
        return dialog;
    }

    public static android.app.Dialog color(Context context, String[] ids, Runnable changed) {
        if (ids.length == 0) return null;
        TrackLayer.TrackInfo info = find(context, Long.parseLong(ids[0]));
        if (info == null) return null;
        AmbilWarnaDialog picker = new AmbilWarnaDialog(context, info.color,
                new AmbilWarnaDialog.OnAmbilWarnaListener() {
                    @Override public void onOk(AmbilWarnaDialog dialog, int color) {
                        ContentValues values = new ContentValues();
                        values.put(TrackLayer.FIELD_COLOR, color);
                        update(context, ids, values, changed);
                    }
                    @Override public void onCancel(AmbilWarnaDialog dialog) { }
                });
        picker.show();
        FittedDialogs.fitExisting(picker.getDialog());
        return picker.getDialog();
    }

    public static void visibility(Context context, long id, boolean visible, Runnable changed) {
        ContentValues values = new ContentValues();
        values.put(TrackLayer.FIELD_VISIBLE, visible);
        update(context, new String[]{Long.toString(id)}, values, changed);
    }

    public static AlertDialog delete(Context context, String[] selectedIds, Runnable changed) {
        if (selectedIds.length == 0) return null;
        String[] ids = selectedIds.clone();
        boolean recording = TrackerService.includesRecordingTrack(context, ids);
        AlertDialog dialog = FittedDialogs.compatBuilder(context).setTitle(R.string.menu_delete)
                .setMessage(recording ? R.string.track_stop_delete_confirm : R.string.track_delete_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.menu_delete, (ignored, which) -> {
                    try {
                        if (TrackerService.deleteTracks(context, ids)) changed.run();
                        else failed(context, null);
                    } catch (RuntimeException error) { failed(context, error); }
                }).create();
        dialog.show();
        return dialog;
    }

    private static boolean update(Context context, String[] ids, ContentValues values, Runnable changed) {
        try {
            if (context.getContentResolver().update(uri(context), values,
                    TrackLayer.getSelection(ids.length), ids) == 0) return false;
            changed.run();
            return true;
        } catch (RuntimeException error) { failed(context, error); return false; }
    }

    private static void failed(Context context, RuntimeException error) {
        android.util.Log.w("Tracks", "Track action failed", error);
        Toast.makeText(context, R.string.track_action_failed, Toast.LENGTH_LONG).show();
    }
}
