package com.nextgis.maplibui.view;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.view.ViewCompat;

import com.nextgis.maplib.map.TrackLayer;
import com.nextgis.maplibui.R;

/** A compact, fitted map panel. Hiding a selected track keeps its Show action reachable. */
public class TrackSelectionPanel extends FittedPanelLayout {
    public static final int COLOR = 1, RENAME = 2, VISIBILITY = 3, DELETE = 4, CLOSE = 5, SHARE = 6;
    public interface Listener { void onAction(int action); }
    private final TextView title, visibility;
    private final int foreground;
    private Listener listener;
    private final LinearLayout[] actionRows;
    private final View[] actionButtons;

    public TrackSelectionPanel(Context context) { this(context, null); }
    public TrackSelectionPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        foreground = themeColor(android.R.attr.textColorPrimary, Color.DKGRAY);
        GradientDrawable background = new GradientDrawable();
        background.setColor(themeColor(android.R.attr.colorBackground, Color.WHITE));
        background.setCornerRadius(dp(12));
        setBackground(background);
        setElevation(dp(3));
        setPadding(dp(8), dp(4), dp(8), dp(4));
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        addView(content, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(header);
        title = label(14);
        title.setId(R.id.track_panel_title);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        AppCompatImageButton close = new AppCompatImageButton(context);
        close.setId(R.id.track_panel_close);
        close.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        close.setImageTintList(ColorStateList.valueOf(foreground));
        close.setBackgroundColor(Color.TRANSPARENT);
        close.setContentDescription(context.getString(R.string.cancel));
        ViewCompat.setTooltipText(close, close.getContentDescription());
        close.setOnClickListener(view -> action(CLOSE));
        header.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        actionRows = new LinearLayout[5];
        for (int i = 0; i < actionRows.length; i++) {
            actionRows[i] = new LinearLayout(context);
            actionRows[i].setBaselineAligned(false);
            actionRows[i].setVisibility(i == 0 ? VISIBLE : GONE);
            content.addView(actionRows[i]);
        }
        LinearLayout actions = actionRows[0];
        button(actions, R.id.track_panel_color, R.drawable.ic_color_lens_dark, R.string.track_color_short, COLOR);
        // The dark raster has only 48% alpha and looks disabled even after tinting.
        button(actions, R.id.track_panel_rename, R.drawable.ic_action_edit_light, R.string.track_rename_short, RENAME);
        visibility = button(actions, R.id.track_panel_visibility, R.drawable.ic_action_visibility_off_dark,
                R.string.menu_visibility_off, VISIBILITY);
        button(actions, R.id.track_panel_share, R.drawable.ic_action_social_share_dark, R.string.menu_share, SHARE);
        button(actions, R.id.track_panel_delete, R.drawable.ic_action_delete_dark, R.string.menu_delete, DELETE);
        actionButtons = new View[actions.getChildCount()];
        for (int i = 0; i < actionButtons.length; i++) actionButtons[i] = actions.getChildAt(i);
        setVisibility(GONE);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        // The map control rail also consumes width: wrap before actions shrink below 48 dp.
        int available = Math.max(0, MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight());
        int columns = MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED ? actionButtons.length
                : Math.max(1, Math.min(actionButtons.length, available / dp(48)));
        for (int i = 0; i < actionButtons.length; i++) {
            LinearLayout target = actionRows[i / columns];
            View button = actionButtons[i];
            int index = i % columns;
            if (button.getParent() != target || target.indexOfChild(button) != index) {
                ((LinearLayout) button.getParent()).removeView(button);
                target.addView(button, index);
            }
        }
        int rowCount = (actionButtons.length + columns - 1) / columns;
        for (int i = 0; i < actionRows.length; i++)
            actionRows[i].setVisibility(i < rowCount ? VISIBLE : GONE);
        super.onMeasure(widthSpec, heightSpec);
    }

    public void bind(TrackLayer.TrackInfo info, Listener listener) {
        this.listener = listener;
        title.setText(info.visible ? info.name : getContext().getString(R.string.track_hidden_name, info.name));
        title.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
        visibility.setText(info.visible ? R.string.menu_visibility_off : R.string.menu_visibility_on);
        visibility.setContentDescription(visibility.getText());
        visibility.setCompoundDrawablesRelativeWithIntrinsicBounds(0,
                info.visible ? R.drawable.ic_action_visibility_off_dark : R.drawable.ic_action_visibility_on_dark, 0, 0);
        visibility.setCompoundDrawableTintList(ColorStateList.valueOf(foreground));
        setVisibility(VISIBLE);
    }

    private TextView button(LinearLayout parent, int id, int icon, int text, int action) {
        TextView button = label(12);
        button.setId(id);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(64));
        button.setPadding(dp(2), dp(4), dp(2), dp(4));
        button.setText(text);
        button.setContentDescription(getContext().getString(text));
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(0, icon, 0, 0);
        button.setCompoundDrawableTintList(ColorStateList.valueOf(foreground));
        TypedValue selectable = new TypedValue();
        getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, selectable, true);
        button.setBackgroundResource(selectable.resourceId);
        button.setOnClickListener(view -> action(action));
        parent.addView(button, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        return button;
    }

    private TextView label(int size) {
        TextView label = new TextView(getContext());
        label.setTextSize(size);
        label.setTextColor(foreground);
        return label;
    }
    private void action(int action) { if (listener != null) listener.onAction(action); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private int themeColor(int attr, int fallback) {
        android.content.res.TypedArray array = getContext().obtainStyledAttributes(new int[]{attr});
        int color = array.getColor(0, fallback);
        array.recycle();
        return color;
    }
}
