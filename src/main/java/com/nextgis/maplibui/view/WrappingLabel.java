package com.nextgis.maplibui.view;

import android.content.Context;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;
import androidx.appcompat.widget.AppCompatTextView;

/** Wrap complete words; reduce type only when one word cannot fit the available width. */
public final class WrappingLabel extends AppCompatTextView {
    private float preferredSize;

    public WrappingLabel(Context context, AttributeSet attrs) {
        super(context, attrs);
        preferredSize = getTextSize();
    }

    @Override public void setTextSize(int unit, float size) {
        super.setTextSize(unit, size);
        preferredSize = getTextSize();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        float fitted = preferredSize;
        int available = MeasureSpec.getSize(widthSpec) - getCompoundPaddingLeft() - getCompoundPaddingRight();
        if (preferredSize > 0 && available > 0 && MeasureSpec.getMode(widthSpec) != MeasureSpec.UNSPECIFIED) {
            TextPaint paint = new TextPaint(getPaint()); paint.setTextSize(preferredSize);
            CharSequence content = getTransformationMethod() == null ? getText()
                    : getTransformationMethod().getTransformation(getText(), this);
            float widest = 0;
            for (String word : content.toString().split("\\s+")) widest = Math.max(widest, paint.measureText(word));
            if (widest > available) fitted *= (available - 1f) / widest;
        }
        if (fitted > 0 && Math.abs(getTextSize() - fitted) > .5f) super.setTextSize(TypedValue.COMPLEX_UNIT_PX, fitted);
        super.onMeasure(widthSpec, heightSpec);
    }
}
