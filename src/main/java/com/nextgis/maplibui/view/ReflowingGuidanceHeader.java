package com.nextgis.maplibui.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/** Direction, complete text and Close share a row when possible; narrow viewports put text below controls. */
public final class ReflowingGuidanceHeader extends ViewGroup {
    private boolean stacked;
    public ReflowingGuidanceHeader(Context context, AttributeSet attrs) { super(context, attrs); }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        if (getChildCount() != 3) { setMeasuredDimension(0, 0); return; }
        View direction = getChildAt(0), text = getChildAt(1), actions = getChildAt(2);
        int natural = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        direction.measure(getChildMeasureSpec(widthSpec, 0, direction.getLayoutParams().width),
                getChildMeasureSpec(natural, 0, direction.getLayoutParams().height));
        actions.measure(getChildMeasureSpec(widthSpec, 0, actions.getLayoutParams().width), natural);
        text.measure(natural, natural);
        int controls = direction.getMeasuredWidth() + actions.getMeasuredWidth();
        int preferred = controls + text.getMeasuredWidth();
        int width = MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED ? preferred : MeasureSpec.getSize(widthSpec);
        stacked = preferred > width;
        text.measure(MeasureSpec.makeMeasureSpec(Math.max(1, stacked ? width : width - controls), MeasureSpec.EXACTLY), natural);
        int controlHeight = Math.max(direction.getMeasuredHeight(), actions.getMeasuredHeight());
        int height = stacked ? controlHeight + text.getMeasuredHeight() : Math.max(controlHeight, text.getMeasuredHeight());
        setMeasuredDimension(resolveSize(width, widthSpec), resolveSize(height, heightSpec));
    }

    private void place(View child, int x, int y) {
        if (getLayoutDirection() == LAYOUT_DIRECTION_RTL) x = getWidth() - x - child.getMeasuredWidth();
        child.layout(x, y, x + child.getMeasuredWidth(), y + child.getMeasuredHeight());
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (getChildCount() != 3) return;
        View direction = getChildAt(0), text = getChildAt(1), actions = getChildAt(2);
        int controlsHeight = Math.max(direction.getMeasuredHeight(), actions.getMeasuredHeight());
        int rowHeight = stacked ? controlsHeight : getHeight();
        place(direction, 0, Math.max(0, (rowHeight - direction.getMeasuredHeight()) / 2));
        place(actions, getWidth() - actions.getMeasuredWidth(), Math.max(0, (rowHeight - actions.getMeasuredHeight()) / 2));
        place(text, stacked ? 0 : direction.getMeasuredWidth(), stacked ? controlsHeight : Math.max(0, (getHeight() - text.getMeasuredHeight()) / 2));
    }

    @Override public LayoutParams generateLayoutParams(AttributeSet attrs) { return new MarginLayoutParams(getContext(), attrs); }
    @Override protected LayoutParams generateDefaultLayoutParams() { return new MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT); }
}
