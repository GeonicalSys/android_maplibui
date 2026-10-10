package com.nextgis.maplibui.view;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;

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
        void onPauseOrResumeWalk(WalkSessionStore.Snapshot session, boolean resume);
        void onDiscardWalk(WalkSessionStore.Snapshot session);
    }

    private final TextView title, notice;
    private final AppCompatImageButton finish, discard, resume;
    private final LinearLayout actions;
    private final int primaryColor, secondaryColor;
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
        setPadding(dp(12), 0, dp(4), 0);
        primaryColor = themeColor(androidx.appcompat.R.attr.colorPrimary, Color.DKGRAY);
        int textColor = themeColor(android.R.attr.textColorPrimary, Color.DKGRAY);
        secondaryColor = themeColor(android.R.attr.textColorSecondary, Color.GRAY);
        int surface = ColorUtils.blendARGB(themeColor(android.R.attr.colorBackground, Color.WHITE), primaryColor, .035f);
        GradientDrawable background = new GradientDrawable();
        background.setColor(surface);
        background.setCornerRadius(dp(18));
        background.setStroke(dp(1), ColorUtils.blendARGB(surface, textColor, .10f));
        setBackground(background);
        setElevation(dp(2));
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        title = label(13);
        title.setId(R.id.walk_panel_title);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        title.setTextColor(textColor);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LayoutParams titleParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
        titleParams.setMarginEnd(dp(4));
        row.addView(title, titleParams);
        actions = new LinearLayout(context);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        resume = iconButton(R.id.walk_panel_resume, R.drawable.ic_walk_panel_resume, R.string.walk_resume);
        finish = iconButton(R.id.walk_panel_finish, R.drawable.ic_walk_panel_save, R.string.walk_complete_object);
        finish.setImageTintList(ColorStateList.valueOf(ColorUtils.calculateContrast(Color.WHITE, primaryColor)
                > ColorUtils.calculateContrast(Color.BLACK, primaryColor) ? Color.WHITE : Color.BLACK));
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(primaryColor);
        pill.setCornerRadius(dp(18));
        finish.setBackground(new InsetDrawable(new RippleDrawable(ColorStateList.valueOf(0x26000000), pill, null), dp(6)));
        finish.setPadding(dp(14), dp(14), dp(14), dp(14));
        finish.setStateListAnimator(null);
        discard = iconButton(R.id.walk_panel_cancel, R.drawable.ic_walk_panel_cancel, R.string.walk_discard);
        actions.addView(finish, new LayoutParams(dp(48), dp(48)));
        actions.addView(resume, new LayoutParams(dp(48), dp(48)));
        actions.addView(discard, new LayoutParams(dp(48), dp(48)));
        row.addView(actions, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        notice = label(12);
        notice.setTextColor(secondaryColor);
        notice.setPadding(0, 0, dp(6), dp(6));
        addView(notice, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        finish.setOnClickListener(view -> {
            WalkSessionStore.Snapshot session = actionableSession();
            if (session != null && listener != null) listener.onFinishWalk(session);
        });
        resume.setOnClickListener(view -> {
            WalkSessionStore.Snapshot session = actionableSession();
            if (session != null && listener != null && session.phase == WalkSessionPolicy.Phase.RECORDING)
                listener.onPauseOrResumeWalk(session, !WalkEditService.isSessionRunning(session.id) || session.gpsPaused);
        });
        discard.setOnClickListener(view -> {
            WalkSessionStore.Snapshot session = actionableSession();
            if (session != null && listener != null) listener.onDiscardWalk(session);
        });
        setVisibility(GONE);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private int themeColor(int attribute, int fallback) {
        TypedValue value = new TypedValue();
        if (!getContext().getTheme().resolveAttribute(attribute, value, true)) return fallback;
        return value.resourceId != 0 ? ContextCompat.getColorStateList(getContext(), value.resourceId).getDefaultColor() : value.data;
    }
    private TextView label(int size) {
        TextView text = new TextView(getContext());
        text.setTextSize(size);
        text.setIncludeFontPadding(false);
        return text;
    }
    private AppCompatImageButton iconButton(int id, int icon, int description) {
        AppCompatImageButton button = new AppCompatImageButton(getContext(), null, androidx.appcompat.R.attr.borderlessButtonStyle);
        button.setId(id);
        button.setImageResource(icon);
        button.setImageTintList(ColorStateList.valueOf(secondaryColor));
        button.setPadding(dp(14), dp(14), dp(14), dp(14));
        button.setContentDescription(getContext().getString(description));
        ViewCompat.setTooltipText(button, getContext().getString(description));
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
        String layerName = layer == null ? "" : layer.getName();
        title.setText(layerName);
        ViewCompat.setTooltipText(title, layerName);
        boolean running = WalkEditService.isSessionRunning(session.id);
        boolean failed = WalkEditService.isPersistenceFailed(session.id);
        title.setContentDescription(getContext().getString(R.string.walk_panel_title, layerName));
        boolean enabled = actionableSession() != null;
        finish.setEnabled(enabled && listener != null);
        discard.setEnabled(enabled && listener != null);
        resume.setEnabled(enabled && listener != null && session.phase == WalkSessionPolicy.Phase.RECORDING);
        finish.setAlpha(finish.isEnabled() ? 1f : .38f);
        discard.setAlpha(discard.isEnabled() ? 1f : .38f);
        resume.setAlpha(resume.isEnabled() ? 1f : .38f);
        boolean paused = !running || session.gpsPaused;
        resume.setImageResource(paused ? R.drawable.ic_walk_panel_resume : R.drawable.ic_walk_panel_pause);
        String action = getContext().getString(paused ? R.string.walk_resume : R.string.walk_pause);
        resume.setContentDescription(action);
        ViewCompat.setTooltipText(resume, action);
        notice.setText(failed ? R.string.walk_checkpoint_failed : R.string.walk_controls_point_locked);
        notice.setVisibility(failed || session.isPointActive() ? VISIBLE : GONE);
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
