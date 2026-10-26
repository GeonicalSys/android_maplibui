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
            String[] words = content.toString().split("\\s+");
            if (widestWord(paint, words) > available) {
                // Font hinting rounds advances on older Android versions: widths do not
                // scale exactly with text size. Measure each candidate instead of a ratio.
                float low = 0f, high = preferredSize;
                for (int step = 0; step < 16; step++) {
                    float candidate = (low + high) / 2f;
                    paint.setTextSize(candidate);
                    if (widestWord(paint, words) <= available - 1f) low = candidate;
                    else high = candidate;
                }
                fitted = low;
            }
        }
        // A subpixel size correction can still move a rounded glyph advance by one pixel.
        // Ignoring it splits a word at large font/display scales on Android 11.
        if (fitted > 0 && Float.compare(getTextSize(), fitted) != 0)
            super.setTextSize(TypedValue.COMPLEX_UNIT_PX, fitted);
        super.onMeasure(widthSpec, heightSpec);
    }

    private static float widestWord(TextPaint paint, String[] words) {
        float widest = 0f;
        for (String word : words) widest = Math.max(widest, paint.measureText(word));
        return widest;
    }
}
