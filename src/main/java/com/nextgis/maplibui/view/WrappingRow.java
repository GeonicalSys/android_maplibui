package com.nextgis.maplibui.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.List;

/** Keeps natural text/control sizes, wrapping complete items onto another row. */
public final class WrappingRow extends ViewGroup {
    private final int gap;
    private final List<List<View>> rows = new ArrayList<>();

    public WrappingRow(Context context, AttributeSet attrs) {
        super(context, attrs);
        gap = Math.round(4 * getResources().getDisplayMetrics().density);
    }

    private int width(View child) {
        MarginLayoutParams p = (MarginLayoutParams) child.getLayoutParams();
        return child.getMeasuredWidth() + p.leftMargin + p.rightMargin;
    }

    private int height(View child) {
        MarginLayoutParams p = (MarginLayoutParams) child.getLayoutParams();
        return child.getMeasuredHeight() + p.topMargin + p.bottomMargin;
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        rows.clear();
        int available = MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED ? Integer.MAX_VALUE
                : Math.max(0, MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight());
        List<View> row = new ArrayList<>();
        int rowWidth = 0, rowHeight = 0, maxWidth = 0, totalHeight = 0, state = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            measureChildWithMargins(child, widthSpec, 0, heightSpec, 0);
            if (!row.isEmpty() && rowWidth + gap + width(child) > available) {
                rows.add(row);
                maxWidth = Math.max(maxWidth, rowWidth);
                totalHeight += rowHeight + gap;
                row = new ArrayList<>(); rowWidth = rowHeight = 0;
            }
            if (!row.isEmpty()) rowWidth += gap;
            row.add(child); rowWidth += width(child); rowHeight = Math.max(rowHeight, height(child));
            state = combineMeasuredStates(state, child.getMeasuredState());
        }
        if (!row.isEmpty()) { rows.add(row); maxWidth = Math.max(maxWidth, rowWidth); totalHeight += rowHeight; }
        setMeasuredDimension(resolveSizeAndState(maxWidth + getPaddingLeft() + getPaddingRight(), widthSpec, state),
                resolveSizeAndState(totalHeight + getPaddingTop() + getPaddingBottom(), heightSpec, state << MEASURED_HEIGHT_STATE_SHIFT));
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int y = getPaddingTop(), available = getWidth() - getPaddingLeft() - getPaddingRight();
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        for (List<View> row : rows) {
            int used = Math.max(0, row.size() - 1) * gap, rowHeight = 0;
            for (View child : row) { used += width(child); rowHeight = Math.max(rowHeight, height(child)); }
            int x = getPaddingLeft() + Math.max(0, (available - used) / 2);
            if (rtl) x = getWidth() - getPaddingRight() - Math.max(0, (available - used) / 2);
            for (View child : row) {
                MarginLayoutParams p = (MarginLayoutParams) child.getLayoutParams();
                int childLeft = rtl ? x - p.rightMargin - child.getMeasuredWidth() : x + p.leftMargin;
                int childTop = y + p.topMargin + (rowHeight - height(child)) / 2;
                child.layout(childLeft, childTop, childLeft + child.getMeasuredWidth(), childTop + child.getMeasuredHeight());
                x += (rtl ? -1 : 1) * (width(child) + gap);
            }
            y += rowHeight + gap;
        }
    }

    @Override public LayoutParams generateLayoutParams(AttributeSet attrs) { return new MarginLayoutParams(getContext(), attrs); }
    @Override protected LayoutParams generateDefaultLayoutParams() { return new MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT); }
    @Override protected LayoutParams generateLayoutParams(LayoutParams p) { return new MarginLayoutParams(p); }
    @Override protected boolean checkLayoutParams(LayoutParams p) { return p instanceof MarginLayoutParams; }
}
