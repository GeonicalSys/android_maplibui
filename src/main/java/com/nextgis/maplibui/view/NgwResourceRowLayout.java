package com.nextgis.maplibui.view;

import android.content.Context;
import android.text.Layout;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.nextgis.maplibui.R;

/** Keeps the full resource name above the mode controls when their horizontal row clips it. */
public final class NgwResourceRowLayout extends LinearLayout {
    private boolean stacked;

    public NgwResourceRowLayout(Context context) { super(context); }
    public NgwResourceRowLayout(Context context, AttributeSet attrs) { super(context, attrs); }
    public NgwResourceRowLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        View header = findViewById(R.id.ngw_resource_header);
        View modes = findViewById(R.id.ngw_resource_modes);
        TextView name = findViewById(R.id.tvName);
        // We inspect descendant text layouts during this pass, before View.layout can
        // replay cached measurements. Rebuild both small subtrees for the current widths.
        forceMeasureTree(header);
        forceMeasureTree(modes);
        // Always retry the ordinary horizontal layout: recycled rows and wider viewports recover.
        stacked = false;
        super.onMeasure(widthSpec, heightSpec);
        if (header == null || modes == null || name == null
                || MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED
                || !clipsText(name)) return;

        stacked = true;
        int innerWidth = Math.max(0, getMeasuredWidth() - getPaddingLeft() - getPaddingRight());
        measureFullWidth(header, innerWidth, heightSpec);
        measureFullWidth(modes, innerWidth, heightSpec);
        int height = getPaddingTop() + getPaddingBottom()
                + outerHeight(header) + outerHeight(modes);
        setMeasuredDimension(getMeasuredWidth(), resolveSizeAndState(
                Math.max(getSuggestedMinimumHeight(), height), heightSpec, 0));
    }

    private static void forceMeasureTree(View view) {
        if (view == null) return;
        // Unlike requestLayout(), forceLayout() does not schedule a parent traversal.
        view.forceLayout();
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) forceMeasureTree(group.getChildAt(i));
        }
    }

    private static boolean clipsText(TextView text) {
        Layout layout = text.getLayout();
        if (text.getText().length() == 0) return false;
        int width = text.getMeasuredWidth()
                - text.getCompoundPaddingLeft() - text.getCompoundPaddingRight();
        if (layout == null || width <= 0 || layout.getLineCount() == 0) return true;
        int last = layout.getLineCount() - 1;
        if (layout.getLineEnd(last) != text.getText().length()) return true;
        for (int line = 0; line <= last; line++) {
            if (layout.getEllipsisCount(line) != 0 || layout.getLineLeft(line) < -1f
                    || layout.getLineRight(line) > width + 1f) return true;
        }
        return false;
    }

    private void measureFullWidth(View child, int width, int heightSpec) {
        if (child.getVisibility() == GONE) return;
        LayoutParams params = (LayoutParams) child.getLayoutParams();
        forceMeasureTree(child);
        child.measure(MeasureSpec.makeMeasureSpec(Math.max(0,
                        width - params.leftMargin - params.rightMargin), MeasureSpec.EXACTLY),
                getChildMeasureSpec(heightSpec, getPaddingTop() + getPaddingBottom()
                        + params.topMargin + params.bottomMargin, params.height));
    }

    private static int outerHeight(View child) {
        if (child.getVisibility() == GONE) return 0;
        LayoutParams params = (LayoutParams) child.getLayoutParams();
        return child.getMeasuredHeight() + params.topMargin + params.bottomMargin;
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (!stacked) {
            super.onLayout(changed, left, top, right, bottom);
            return;
        }
        View header = findViewById(R.id.ngw_resource_header);
        View modes = findViewById(R.id.ngw_resource_modes);
        int available = getHeight() - getPaddingTop() - getPaddingBottom();
        int y = getPaddingTop() + Math.max(0,
                (available - outerHeight(header) - outerHeight(modes)) / 2);
        y = placeFullWidth(header, y);
        placeFullWidth(modes, y);
    }

    private int placeFullWidth(View child, int y) {
        if (child.getVisibility() == GONE) return y;
        LayoutParams params = (LayoutParams) child.getLayoutParams();
        int x = getPaddingLeft() + params.leftMargin;
        int childTop = y + params.topMargin;
        child.layout(x, childTop, x + child.getMeasuredWidth(), childTop + child.getMeasuredHeight());
        return childTop + child.getMeasuredHeight() + params.bottomMargin;
    }
}
