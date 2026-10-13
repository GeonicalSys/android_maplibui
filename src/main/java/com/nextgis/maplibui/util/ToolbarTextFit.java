package com.nextgis.maplibui.util;

import android.text.TextPaint;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.appcompat.widget.Toolbar;
import com.nextgis.maplibui.R;

/** Fixed toolbar titles cannot wrap freely: fit only the overflowing text at enlarged system scales. */
public final class ToolbarTextFit {
    private ToolbarTextFit() { }

    public static void attach(Toolbar toolbar) {
        toolbar.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            for (int i = 0; i < toolbar.getChildCount(); i++) {
                if (!(toolbar.getChildAt(i) instanceof TextView)) continue;
                TextView text = (TextView) toolbar.getChildAt(i);
                if (text.getTag(R.id.preferred_text_size) == null) text.setTag(R.id.preferred_text_size, text.getTextSize());
                float preferred = (Float) text.getTag(R.id.preferred_text_size), fitted = preferred;
                int start = Math.max(toolbar.getPaddingLeft(), toolbar.getCurrentContentInsetLeft());
                int end = toolbar.getWidth() - Math.max(toolbar.getPaddingRight(), toolbar.getCurrentContentInsetRight());
                for (int j = 0; j < toolbar.getChildCount(); j++) {
                    View child = toolbar.getChildAt(j);
                    if (child.getVisibility() == View.GONE || child instanceof TextView) continue;
                    int before = 0, after = 0;
                    if (child.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                        ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) child.getLayoutParams();
                        before = p.leftMargin; after = p.rightMargin;
                    }
                    if (child.getRight() <= text.getLeft()) start = Math.max(start, child.getRight() + after);
                    else if (child.getLeft() >= text.getRight()) end = Math.min(end, child.getLeft() - before);
                }
                int available = Math.max(0, end - start - toolbar.getTitleMarginStart() - toolbar.getTitleMarginEnd());
                if (available > 0 && text.getHeight() > 0) {
                    TextPaint paint = new TextPaint(text.getPaint()); paint.setTextSize(preferred);
                    CharSequence content = text.getTransformationMethod() == null ? text.getText()
                            : text.getTransformationMethod().getTransformation(text.getText(), text);
                    float width = paint.measureText(content.toString());
                    if (width > available) fitted *= available / width;
                    float height = paint.getFontMetrics().bottom - paint.getFontMetrics().top;
                    int space = toolbar.getHeight() - toolbar.getPaddingTop() - toolbar.getPaddingBottom()
                            - toolbar.getTitleMarginTop() - toolbar.getTitleMarginBottom();
                    if (toolbar.getSubtitle() != null && toolbar.getSubtitle().length() > 0) space /= 2;
                    space -= text.getCompoundPaddingTop() + text.getCompoundPaddingBottom();
                    if (height > space && space > 0) fitted = Math.min(fitted, preferred * space / height);
                }
                if (Math.abs(text.getTextSize() - fitted) > .5f) text.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.max(1, fitted));
            }
        });
    }
}
