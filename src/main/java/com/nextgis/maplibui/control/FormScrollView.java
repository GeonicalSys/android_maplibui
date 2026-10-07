package com.nextgis.maplibui.control;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityManager;
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
    private EditText gestureEditor;
    private boolean swiping;
    private float startX, startY;
    private long started;

    public FormScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
        minDistance = 48 * getResources().getDisplayMetrics().density;
        touchSlop = Math.max(2 * ViewConfiguration.get(context).getScaledTouchSlop(),
                Math.round(16 * getResources().getDisplayMetrics().density));
    }

    public void addFormTabs(Tabs tabs) {
        if (!formTabs.contains(tabs)) formTabs.add(tabs);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            startX = event.getX(); startY = event.getY(); started = event.getEventTime();
            gestureTabs = null;
            gestureEditor = null;
            swiping = false;
            AccessibilityManager accessibility = (AccessibilityManager)getContext()
                    .getSystemService(Context.ACCESSIBILITY_SERVICE);
            for (Tabs tabs : formTabs) {
                // Short pages do not fill the scroll viewport; its empty area is navigable too.
                if (tabs.isShown() && !hasOwnGesture(this, event)
                        && (accessibility == null || !accessibility.isTouchExplorationEnabled())) {
                    gestureTabs = tabs;
                    gestureEditor = findEditor(tabs, event);
                    break;
                }
            }
        }
        if (event.getPointerCount() != 1 || action == MotionEvent.ACTION_CANCEL) {
            gestureTabs = null;
            gestureEditor = null;
        }
        if (swiping) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) swiping = false;
            return true;
        }
        float dx = event.getX() - startX, dy = event.getY() - startY;
        if (action == MotionEvent.ACTION_MOVE && Math.abs(dy) > touchSlop && Math.abs(dy) > Math.abs(dx) * 1.25f)
            gestureTabs = null;
        if (gestureEditor != null && (gestureEditor.hasSelection()
                || event.getEventTime() - started >= ViewConfiguration.getLongPressTimeout()))
            gestureTabs = null;
        boolean horizontal = (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP)
                && gestureTabs != null
                && event.getEventTime() - started <= 1500
                && Math.abs(dx) >= minDistance && Math.abs(dx) >= Math.abs(dy) * 1.25f;
        if (horizontal) {
            // Cancel the old control before navigation, including at the first/last page.
            // Observing dispatch also covers clickables that disallow parent interception.
            MotionEvent cancel = MotionEvent.obtain(event);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            super.dispatchTouchEvent(cancel);
            cancel.recycle();
            gestureTabs.selectAdjacentTab(dx < 0);
            gestureTabs = null;
            swiping = action != MotionEvent.ACTION_UP;
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
        if (view instanceof Sign || view instanceof PhotoGallery
                || view instanceof SeekBar || view instanceof HorizontalScrollView || view instanceof WebView
                || view.canScrollHorizontally(-1) || view.canScrollHorizontally(1)) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--)
                if (hasOwnGesture(group.getChildAt(i), event)) return true;
        }
        return false;
    }

    private static EditText findEditor(View view, MotionEvent event) {
        if (!hits(view, event)) return null;
        if (view instanceof EditText) return (EditText)view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i=group.getChildCount()-1;i>=0;i--) {
                EditText found=findEditor(group.getChildAt(i),event);
                if (found!=null) return found;
            }
        }
        return null;
    }
}
