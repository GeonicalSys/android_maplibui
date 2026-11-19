package com.nextgis.maplibui.util;

import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ScrollView;
import android.widget.TextView;

/** Script notices retain the platform's bounded scrolling message and fixed actions. */
public final class ScriptNoticeDialogs {
    private ScriptNoticeDialogs() { }

    public static AlertDialog.Builder builder(Context context) {
        return new AlertDialog.Builder(context) {
            @Override public AlertDialog create() {
                AlertDialog dialog=super.create();
                dialog.create();
                TextView message=dialog.findViewById(android.R.id.message);
                if(message!=null) {
                    message.setHorizontallyScrolling(false);
                }
                wrap(dialog.getButton(AlertDialog.BUTTON_POSITIVE));
                wrap(dialog.getButton(AlertDialog.BUTTON_NEGATIVE));
                wrap(dialog.getButton(AlertDialog.BUTTON_NEUTRAL));
                int titleId=android.content.res.Resources.getSystem().getIdentifier("alertTitle","id","android");
                if(titleId!=0)wrap(dialog.findViewById(titleId));
                if(context.getResources().getConfiguration().fontScale>=1.5f
                        &&context.getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                    // In compact landscape windows large text already supplies header height.
                    // Reclaim optional top spacing, keeping all text and touch-target sizes.
                    int templateId=android.content.res.Resources.getSystem().getIdentifier("title_template","id","android");
                    View template=templateId==0?null:dialog.findViewById(templateId);
                    if(template!=null)template.setPadding(template.getPaddingLeft(),0,template.getPaddingRight(),template.getPaddingBottom());
                }
                if(message!=null) {
                    // The native AlertController owns height, title and action allocation.
                    // Fit only the message through scrolling; never scale the dialog text.
                    for(ViewParent parent=message.getParent();parent instanceof View;parent=parent.getParent()) {
                        if(parent instanceof ScrollView) {
                            View viewport=(View)parent;
                            viewport.setVerticalScrollBarEnabled(true);
                            viewport.setScrollbarFadingEnabled(false);
                            break;
                        }
                    }
                }
                reserveFixedPanels(dialog,message);
                return dialog;
            }
        };
    }

    private static void wrap(TextView view) {
        if(view==null)return;
        view.setSingleLine(false);view.setMaxLines(Integer.MAX_VALUE);view.setEllipsize(null);
        if(view.getLayoutParams()!=null)view.getLayoutParams().height=ViewGroup.LayoutParams.WRAP_CONTENT;
    }

    private static boolean contains(View ancestor,View child) {
        for(View current=child;current!=null;current=current.getParent() instanceof View?(View)current.getParent():null)
            if(current==ancestor)return true;
        return false;
    }

    private static void reserveFixedPanels(AlertDialog dialog,TextView message) {
        View positive=dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if(message==null||positive==null)return;
        // Keep the existing platform views and click listeners. AlertDialogLayout reserves only
        // a ButtonBar's minimum height, which can clip a wrapped/stacked second action.
        for(ViewParent parent=message.getParent();parent instanceof ViewGroup;parent=parent.getParent()) {
            ViewGroup original=(ViewGroup)parent;
            if(!contains(original,positive))continue;
            if(!(original.getParent() instanceof ViewGroup))return;
            ViewGroup host=(ViewGroup)original.getParent();int position=host.indexOfChild(original);
            ViewGroup.LayoutParams outer=original.getLayoutParams();MessagePanels panels=new MessagePanels(dialog.getContext());
            panels.setId(original.getId());panels.setPadding(original.getPaddingLeft(),original.getPaddingTop(),original.getPaddingRight(),original.getPaddingBottom());
            while(original.getChildCount()>0) {
                View child=original.getChildAt(0);if(contains(child,message))panels.messagePanel=child;
                ViewGroup.MarginLayoutParams params=new ViewGroup.MarginLayoutParams(child.getLayoutParams());
                original.removeView(child);panels.addView(child,params);
            }
            android.util.TypedValue surface=new android.util.TypedValue();
            if(dialog.getContext().getTheme().resolveAttribute(android.R.attr.colorBackgroundFloating,surface,true)
                    &&surface.type>=android.util.TypedValue.TYPE_FIRST_COLOR_INT&&surface.type<=android.util.TypedValue.TYPE_LAST_COLOR_INT)
                panels.setBackgroundColor(surface.data|0xff000000);
            host.removeViewAt(position);host.addView(panels,position,outer);return;
        }
    }

    /** Measure complete fixed panels first; give only the message the remaining bounded height. */
    private static final class MessagePanels extends ViewGroup {
        View messagePanel;
        MessagePanels(Context context){super(context);}
        @Override protected void onMeasure(int widthSpec,int heightSpec) {
            int fixed=getPaddingTop()+getPaddingBottom(),widest=0;
            for(int i=0;i<getChildCount();i++) {
                View child=getChildAt(i);if(child.getVisibility()==GONE||child==messagePanel)continue;
                measurePanel(child,widthSpec,MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
                fixed+=height(child);widest=Math.max(widest,width(child));
            }
            int total=fixed;
            if(messagePanel!=null&&messagePanel.getVisibility()!=GONE) {
                MarginLayoutParams params=(MarginLayoutParams)messagePanel.getLayoutParams();
                int remaining=Math.max(0,MeasureSpec.getSize(heightSpec)-fixed-params.topMargin-params.bottomMargin);
                measurePanel(messagePanel,widthSpec,MeasureSpec.makeMeasureSpec(remaining,MeasureSpec.getMode(heightSpec)==MeasureSpec.UNSPECIFIED?MeasureSpec.UNSPECIFIED:MeasureSpec.AT_MOST));
                total+=height(messagePanel);widest=Math.max(widest,width(messagePanel));
            }
            setMeasuredDimension(resolveSize(widest+getPaddingLeft()+getPaddingRight(),widthSpec),resolveSize(total,heightSpec));
        }
        private void measurePanel(View child,int widthSpec,int heightSpec) {
            MarginLayoutParams p=(MarginLayoutParams)child.getLayoutParams();
            child.measure(getChildMeasureSpec(widthSpec,getPaddingLeft()+getPaddingRight()+p.leftMargin+p.rightMargin,p.width),heightSpec);
        }
        private int height(View child){MarginLayoutParams p=(MarginLayoutParams)child.getLayoutParams();return child.getMeasuredHeight()+p.topMargin+p.bottomMargin;}
        private int width(View child){MarginLayoutParams p=(MarginLayoutParams)child.getLayoutParams();return child.getMeasuredWidth()+p.leftMargin+p.rightMargin;}
        @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom) {
            int y=getPaddingTop();
            for(int i=0;i<getChildCount();i++) {
                View child=getChildAt(i);if(child.getVisibility()==GONE)continue;MarginLayoutParams p=(MarginLayoutParams)child.getLayoutParams();
                y+=p.topMargin;int x=getPaddingLeft()+p.leftMargin;child.layout(x,y,x+child.getMeasuredWidth(),y+child.getMeasuredHeight());y+=child.getMeasuredHeight()+p.bottomMargin;
            }
        }
    }
}
