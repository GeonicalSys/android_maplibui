package com.nextgis.maplibui.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/** Last-resort fitting after the content has wrapped; rendering and touch use the same transform. */
public class FittedPanelLayout extends FrameLayout {
    private float fitScale = 1f;

    public FittedPanelLayout(Context context, AttributeSet attrs) { super(context, attrs); }

    protected int naturalHeightSpec(int availableHeight) {
        return MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        if (getChildCount() != 1) { super.onMeasure(widthSpec, heightSpec); return; }
        View content = getChildAt(0);
        int paddingX = getPaddingLeft() + getPaddingRight(), paddingY = getPaddingTop() + getPaddingBottom();
        int widthMode = MeasureSpec.getMode(widthSpec), heightMode = MeasureSpec.getMode(heightSpec);
        int availableWidth = Math.max(0, MeasureSpec.getSize(widthSpec) - paddingX);
        int childWidth = widthMode == MeasureSpec.UNSPECIFIED ? widthSpec
                : MeasureSpec.makeMeasureSpec(availableWidth, content.getLayoutParams().width == LayoutParams.MATCH_PARENT
                    ? MeasureSpec.EXACTLY : MeasureSpec.AT_MOST);
        content.measure(childWidth, naturalHeightSpec(MeasureSpec.getSize(heightSpec)));
        fitScale = 1f;
        if (widthMode != MeasureSpec.UNSPECIFIED && content.getMeasuredWidth() > availableWidth)
            fitScale = Math.min(fitScale, availableWidth / (float) content.getMeasuredWidth());
        if (heightMode != MeasureSpec.UNSPECIFIED && content.getMeasuredHeight() > 0)
            fitScale = Math.min(fitScale, Math.max(0, MeasureSpec.getSize(heightSpec) - paddingY) / (float) content.getMeasuredHeight());
        setMeasuredDimension(resolveSize((int) Math.ceil(content.getMeasuredWidth() * fitScale) + paddingX, widthSpec),
                resolveSize((int) Math.ceil(content.getMeasuredHeight() * fitScale) + paddingY, heightSpec));
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (getChildCount() != 1) { super.onLayout(changed, left, top, right, bottom); return; }
        View content = getChildAt(0);
        int x = getPaddingLeft() + Math.max(0, Math.round((getWidth() - getPaddingLeft() - getPaddingRight()
                - content.getMeasuredWidth() * fitScale) / 2));
        if (content.getLayoutParams() instanceof LayoutParams) {
            int gravity = ((LayoutParams) content.getLayoutParams()).gravity;
            int horizontal = android.view.Gravity.getAbsoluteGravity(gravity, getLayoutDirection()) & android.view.Gravity.HORIZONTAL_GRAVITY_MASK;
            if (horizontal == android.view.Gravity.RIGHT) x = getWidth() - getPaddingRight() - Math.round(content.getMeasuredWidth() * fitScale);
            else if (horizontal == android.view.Gravity.LEFT) x = getPaddingLeft();
        }
        content.setPivotX(0); content.setPivotY(0);
        content.setScaleX(fitScale); content.setScaleY(fitScale);
        int y = getPaddingTop();
        if (content.getLayoutParams() instanceof LayoutParams
                && ((((LayoutParams) content.getLayoutParams()).gravity & android.view.Gravity.VERTICAL_GRAVITY_MASK) == android.view.Gravity.BOTTOM))
            y += Math.max(0, Math.round((getHeight() - getPaddingTop() - getPaddingBottom()) - content.getMeasuredHeight() * fitScale));
        content.layout(x, y, x + content.getMeasuredWidth(), y + content.getMeasuredHeight());
    }
}
