package com.nextgis.maplibui.view;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatButton;
import androidx.core.content.ContextCompat;

import com.nextgis.maplib.api.IGISApplication;
import com.nextgis.maplib.api.ILayer;
import com.nextgis.maplibui.R;
import com.nextgis.maplibui.service.WalkEditService;
import com.nextgis.maplibui.util.WalkSessionPolicy;
import com.nextgis.maplibui.util.WalkSessionStore;

/** Passive recording status remains visible while point creation disables every walk action. */
public class WalkRecordingPanel extends LinearLayout {
    public interface Listener {
        void onSessionChanged(WalkSessionStore.Snapshot session);
        void onFinishWalk(WalkSessionStore.Snapshot session);
        void onDiscardWalk(WalkSessionStore.Snapshot session);
    }

    private final TextView title, status, hint;
    private final AppCompatButton finish, discard, resume;
    private final LinearLayout actions;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Listener listener;
    private boolean editorAvailable = true;
    private boolean attached;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            refresh();
            if (attached) handler.postDelayed(this, 1000);
        }
    };
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { refresh(); }
    };

    public WalkRecordingPanel(Context context) { this(context, null); }
    public WalkRecordingPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        int padding = dp(4);
        setPadding(padding, padding, padding, padding);
        TypedValue color = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.colorBackground, color, true);
        GradientDrawable background = new GradientDrawable();
        background.setColor(color.data);
        background.setCornerRadius(dp(10));
        setBackground(background);
        setElevation(dp(3));
        title = label(13);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        status = label(12);
        hint = label(12);
        hint.setText(R.string.walk_controls_point_locked);
        actions = new LinearLayout(context);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        resume = button(R.string.walk_gps_resume);
        finish = button(R.string.walk_finish);
        discard = button(R.string.walk_cancel);
        actions.addView(resume, new LayoutParams(0, dp(48), 1));
        actions.addView(finish, new LayoutParams(0, dp(48), 1));
        actions.addView(discard, new LayoutParams(0, dp(48), 1));
        addView(actions);
        finish.setOnClickListener(view -> {
            WalkSessionStore.Snapshot session = actionableSession();
            if (session != null && listener != null) listener.onFinishWalk(session);
        });
        resume.setOnClickListener(view -> {
            WalkSessionStore.Snapshot session = actionableSession();
            if (session != null) WalkEditService.requestCommand(context, session.id, WalkSessionPolicy.Command.RESUME);
        });
        discard.setOnClickListener(view -> {
            WalkSessionStore.Snapshot session = actionableSession();
            if (session != null && listener != null) listener.onDiscardWalk(session);
        });
        setVisibility(GONE);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView label(int size) {
        TextView text = new TextView(getContext());
        text.setTextSize(size);
        addView(text, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        return text;
    }
    private AppCompatButton button(int text) {
        AppCompatButton button = new AppCompatButton(getContext(), null, androidx.appcompat.R.attr.borderlessButtonStyle);
        button.setText(text);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(3), 0, dp(3), 0);
        return button;
    }

    public void setListener(Listener value) { listener = value; refresh(); }
    public void setEditorAvailable(boolean value) { editorAvailable = value; refresh(); }
    public void setCompact(boolean value) {
        actions.setVisibility(value ? GONE : VISIBLE);
        setEditorAvailable(!value);
    }

    private WalkSessionStore.Snapshot actionableSession() {
        WalkSessionStore.Snapshot session = WalkSessionStore.load(getContext());
        return editorAvailable && WalkSessionStore.isCurrentMap(getContext(), session)
                && !session.isPointActive() && session.phase != WalkSessionPolicy.Phase.FINISHING ? session : null;
    }

    public void refresh() {
        WalkSessionStore.Snapshot session = WalkSessionStore.load(getContext());
        if (!WalkSessionStore.isCurrentMap(getContext(), session)) session = null;
        if (listener != null) listener.onSessionChanged(session);
        setVisibility(session == null ? GONE : VISIBLE);
        if (session == null) return;
        IGISApplication app = (IGISApplication) getContext().getApplicationContext();
        ILayer layer = app.getMap().getLayerById(session.layerId);
        title.setText(getContext().getString(R.string.walk_panel_title, layer == null ? "" : layer.getName()));
        boolean running = WalkEditService.isSessionRunning(session.id);
        Location location = app.getGpsEventSource().getLastRecordingLocation();
        if (WalkEditService.isPersistenceFailed(session.id)) status.setText(R.string.walk_checkpoint_failed);
        else if (session.phase == WalkSessionPolicy.Phase.FINISHED) status.setText(R.string.walk_finished_pending);
        else if (session.phase == WalkSessionPolicy.Phase.FINISHING) status.setText(R.string.walk_finishing);
        else if (!running) status.setText(R.string.walkedit_interrupted_title);
        else if (session.gpsPaused) status.setText(R.string.walk_gps_paused);
        else if (location == null) status.setText(R.string.walk_gps_wait);
        else status.setText(R.string.walk_recording);
        boolean enabled = actionableSession() != null;
        finish.setEnabled(enabled && listener != null);
        discard.setEnabled(enabled && listener != null);
        resume.setEnabled(enabled);
        finish.setAlpha(finish.isEnabled() ? 1f : .38f);
        discard.setAlpha(discard.isEnabled() ? 1f : .38f);
        resume.setAlpha(resume.isEnabled() ? 1f : .38f);
        finish.setText(session.phase == WalkSessionPolicy.Phase.FINISHED ? R.string.walk_complete_object : R.string.walk_finish);
        resume.setVisibility(session.phase == WalkSessionPolicy.Phase.RECORDING && (!running || session.gpsPaused) ? VISIBLE : GONE);
        hint.setVisibility(session.isPointActive() ? VISIBLE : GONE);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        ContextCompat.registerReceiver(getContext(), receiver, new IntentFilter(WalkEditService.WALKEDIT_CHANGE), ContextCompat.RECEIVER_NOT_EXPORTED);
        handler.post(refresh);
    }

    @Override protected void onDetachedFromWindow() {
        attached = false;
        handler.removeCallbacks(refresh);
        getContext().unregisterReceiver(receiver);
        super.onDetachedFromWindow();
    }
}
