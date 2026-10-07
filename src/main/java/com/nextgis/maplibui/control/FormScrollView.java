package com.nextgis.maplibui.control;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.SeekBar;

import com.nextgis.maplibui.formcontrol.Sign;
import com.nextgis.maplibui.formcontrol.Tabs;

import java.util.ArrayList;
import java.util.List;

/** Vertical form scrolling with horizontal swipes between its outer NGFP pages. */
public class FormScrollView extends ScrollView {
    private final List<Tabs> formTabs = new ArrayList<>();
    private final float minDistance;
    private final int touchSlop;
    private Tabs gestureTabs;
    private float startX, startY;
    private long started;

    public FormScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
        minDistance = 64 * getResources().getDisplayMetrics().density;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void addFormTabs(Tabs tabs) {
        if (!formTabs.contains(tabs)) formTabs.add(tabs);
    }

    @Override public void requestDisallowInterceptTouchEvent(boolean disallow) {
        if (disallow) gestureTabs = null;
        super.requestDisallowInterceptTouchEvent(disallow);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            startX = event.getX(); startY = event.getY(); started = event.getEventTime();
            gestureTabs = null;
            for (Tabs tabs : formTabs) {
                if (hits(tabs, event) && !hasOwnGesture(tabs, event)) {
                    gestureTabs = tabs;
                    break;
                }
            }
        }
        if (event.getPointerCount() != 1 || action == MotionEvent.ACTION_CANCEL) gestureTabs = null;
        float dx = event.getX() - startX, dy = event.getY() - startY;
        if (action == MotionEvent.ACTION_MOVE && Math.abs(dy) > touchSlop && Math.abs(dy) > Math.abs(dx))
            gestureTabs = null;
        boolean changed = action == MotionEvent.ACTION_UP && gestureTabs != null
                && event.getEventTime() - started <= 1500
                && Math.abs(dx) >= minDistance && Math.abs(dx) >= Math.abs(dy) * 1.5f
                && gestureTabs.selectAdjacentTab(dx < 0);
        if (action == MotionEvent.ACTION_UP && changed) {
            // The previous page must receive Cancel rather than a click after a fling.
            MotionEvent cancel = MotionEvent.obtain(event);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            super.dispatchTouchEvent(cancel);
            cancel.recycle();
            gestureTabs = null;
            return true;
        }
        boolean handled = super.dispatchTouchEvent(event);
        if (action == MotionEvent.ACTION_UP) gestureTabs = null;
        return handled;
    }

    private static boolean hits(View view, MotionEvent event) {
        Rect bounds = new Rect();
        if (!view.getLocalVisibleRect(bounds)) return false;
        // Visible bounds are local/root coordinates; raw touch coordinates include the window inset.
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        bounds.offset(location[0], location[1]);
        return bounds.contains((int) event.getRawX(), (int) event.getRawY());
    }

    private static boolean hasOwnGesture(View view, MotionEvent event) {
        if (!hits(view, event)) return false;
        if (view instanceof Sign || view instanceof PhotoGallery || view instanceof EditText
                || view instanceof SeekBar || view instanceof HorizontalScrollView || view instanceof WebView
                || view.canScrollHorizontally(-1) || view.canScrollHorizontally(1)) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--)
                if (hasOwnGesture(group.getChildAt(i), event)) return true;
        }
        return false;
    }
}
