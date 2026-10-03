/*
 * Project: Forest violations
 * Purpose: Mobile application for registering facts of the forest violations.
 * Author:  Dmitry Baryshnikov (aka Bishop), bishop.dev@gmail.com
 * *****************************************************************************
 * Copyright (c) 2015-2017, 2019-2020 NextGIS, info@nextgis.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextgis.maplibui.formcontrol;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.nextgis.maplib.datasource.Field;
import com.nextgis.maplibui.R;
import com.nextgis.maplibui.activity.ModifyAttributesActivity;
import com.nextgis.maplibui.api.IFormControl;
import com.nextgis.maplibui.util.ControlHelper;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * A special control for create sign picture.
 */
public class Sign extends View implements IFormControl {
    public static final String SIGN_FILE = Integer.MAX_VALUE + "";

    protected Drawable mCleanImage;
    protected int mClearBuff;
    protected int mClearImageSize;
    protected Path    mPath;
    protected final LinkedList<Path> mPaths = new LinkedList<>();
    protected Paint mPaint;
    protected float mX, mY;
    protected String mPreviousSignPath;
    protected File mPreviousSign;
    protected boolean mNotInitialized;
    protected Bitmap mPreviousSignBitmap;
    private final List<List<Float>> mStrokes = new java.util.ArrayList<>();
    private boolean mEdited;
    private int mStrokeWidth, mStrokeHeight;
    private static final String DRAFT_STROKES = "signature_strokes";

    protected final int CLEAR_BUFF_DP = 15;
    protected final int CLEAR_IMAGE_SIZE_DP = 32;
    protected static final float TOUCH_TOLERANCE = 4;

    public Sign(Context context) {
        super(context);
        init();
    }

    public Sign(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public Sign(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public Sign(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    protected void init(){
        //1. get clean
        int[] attrs = new int[] { R.attr.ic_clear };
        TypedArray ta = getContext().obtainStyledAttributes(attrs);
        mCleanImage = ta.getDrawable(0);
        ta.recycle();

        mClearBuff = (int) (getContext().getResources().getDisplayMetrics().density * CLEAR_BUFF_DP);
        mClearImageSize = (int) (getContext().getResources().getDisplayMetrics().density * CLEAR_IMAGE_SIZE_DP);

        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(3);
        mPaint.setDither(true);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
        mPaint.setStrokeCap(Paint.Cap.ROUND);

        boolean bDark = ControlHelper.isDarkTheme(getContext());
        if(bDark)
            mPaint.setColor(Color.WHITE);
        else
            mPaint.setColor(Color.BLACK);

        mPaths.clear();
        mPath = new Path();
        mPaths.add(mPath);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        if (mEdited) {
            rebuildPaths(w, h);
            mNotInitialized = false;
            return;
        }
        if (mPreviousSignPath == null || w <= 0 || h <= 0) return;
        mPreviousSign = new File(mPreviousSignPath, SIGN_FILE);
        if (mNotInitialized = mPreviousSign.isFile()) {
            try (FileInputStream bounds = new FileInputStream(mPreviousSign);
                 FileInputStream pixels = new FileInputStream(mPreviousSign)) {
                BitmapFactory.Options options = ControlHelper.getOptions(bounds, w, h);
                Bitmap previous = mPreviousSignBitmap;
                mPreviousSignBitmap = ControlHelper.getBitmap(pixels, options);
                if (previous != null && previous != mPreviousSignBitmap) previous.recycle();
            } catch (IOException | RuntimeException error) {
                android.util.Log.w(com.nextgis.maplib.util.Constants.TAG, "Cannot load signature", error);
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
        if (mNotInitialized && mPreviousSignBitmap != null)
            canvas.drawBitmap(mPreviousSignBitmap, 0, 0, null);

        int posX = canvas.getWidth() - mClearImageSize - mClearBuff;
        if (mCleanImage != null) {
            mCleanImage.setBounds(posX, mClearBuff, posX + mClearImageSize, mClearImageSize + mClearBuff);
            mCleanImage.draw(canvas);
        }

        if (!mNotInitialized)
            for (Path path : mPaths)
                canvas.drawPath(path, mPaint);
    }

    protected void drawSign(Canvas canvas, int bkColor, Paint paint){
        canvas.drawColor(bkColor);

        for (Path path : mPaths)
            canvas.drawPath(path, paint);
    }

    protected void touchStart(float x, float y) {
        mEdited = true;
        mStrokeWidth = getWidth();
        mStrokeHeight = getHeight();
        List<Float> stroke = new java.util.ArrayList<>();
        stroke.add(x); stroke.add(y);
        mStrokes.add(stroke);
        mPath.reset();
        mPath.moveTo(x, y);
        mX = x;
        mY = y;
    }

    protected void touchMove(float x, float y) {
        float dx = Math.abs(x - mX);
        float dy = Math.abs(y - mY);
        if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
            if (!mStrokes.isEmpty()) {
                List<Float> stroke = mStrokes.get(mStrokes.size() - 1);
                stroke.add(x); stroke.add(y);
            }
            mPath.quadTo(mX, mY, (x + mX)/2, (y + mY)/2);
            mX = x;
            mY = y;
        }
    }

    protected void touchUp() {
        mPath.lineTo(mX, mY);

        postInvalidate();

        // kill this so we don't double draw
        mPath = new Path();
        mPaths.add(mPath);
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (!isEnabled())
            return true;

        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (!mNotInitialized)
                    touchStart(x, y);
                break;

            case MotionEvent.ACTION_MOVE:
                if (!mNotInitialized) {
                    touchMove(x, y);
                    postInvalidate();
                }
                break;

            case MotionEvent.ACTION_UP:
                int posX = getWidth() - mClearImageSize - mClearBuff;
                //Log.d(Constants.TAG, "x: " + event.getX() + " y: " + event.getY() + " posX: " + posX + " posY: " + mClearBuff);
                if(event.getX() > posX && event.getY() < mClearImageSize + mClearBuff){
                    onClearSign();
                } else if (!mNotInitialized) {
                    touchUp();
                    invalidate();
                }
                break;

            default:
                break;
        }

        return true;
    }

    private void onClearSign() {
        mEdited = true;
        mStrokes.clear();
        mStrokeWidth = getWidth();
        mStrokeHeight = getHeight();
        if (mNotInitialized)
            mNotInitialized = false;

        mPaths.clear();
        mPath = new Path();
        mPaths.add(mPath);

        postInvalidate();
    }

    public boolean needsSave() {
        return mEdited && getWidth() > 0 && getHeight() > 0;
    }

    public boolean hasEdits() { return mEdited; }

    public void save(int width, int height, boolean transparentBackground, File sigFile) throws IOException {
        if (!mEdited) return;
        if (width <= 0 || height <= 0 || getWidth() <= 0 || getHeight() <= 0)
            throw new IOException("Signature has no drawable size");

        float scale = Math.min((float) width / getWidth(), (float) height / getHeight());
        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);

        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        try (FileOutputStream out = new FileOutputStream(sigFile)) {
            Canvas canvas = new Canvas(bmp);
            canvas.setMatrix(matrix);
            int color = transparentBackground ? Color.TRANSPARENT : 0xFFFFFF - mPaint.getColor();
            drawSign(canvas, color, mPaint);
            if (!bmp.compress(Bitmap.CompressFormat.PNG, 90, out))
                throw new IOException("Signature encoding failed");
            out.flush();
            out.getFD().sync();
        } finally { bmp.recycle(); }
    }

    public void setPath(String path) {
        mPreviousSignPath = path;
    }

    @Override
    public void init(JSONObject element, List<Field> fields, Bundle savedState,
                     Cursor featureCursor, SharedPreferences lastValue,
                     Map<String, Map<String, String>> translations,
                     final ModifyAttributesActivity modifyAttributesActivity) {
        init();
        if (savedState != null && savedState.containsKey(DRAFT_STROKES)) {
            try {
                JSONObject state = new JSONObject(savedState.getString(DRAFT_STROKES));
                mStrokeWidth = state.getInt("width");
                mStrokeHeight = state.getInt("height");
                JSONArray strokes = state.getJSONArray("strokes");
                mStrokes.clear();
                for (int i=0; i<strokes.length(); i++) {
                    JSONArray coordinates = strokes.getJSONArray(i);
                    if (coordinates.length() % 2 != 0) throw new JSONException("Invalid signature coordinates");
                    List<Float> stroke = new java.util.ArrayList<>();
                    for (int j=0; j<coordinates.length(); j++) stroke.add((float) coordinates.getDouble(j));
                    mStrokes.add(stroke);
                }
                mEdited = true;
                mNotInitialized = false;
                if (getWidth() > 0 && getHeight() > 0) rebuildPaths(getWidth(), getHeight());
            } catch (JSONException | RuntimeException error) {
                android.util.Log.w(com.nextgis.maplib.util.Constants.TAG, "Cannot restore signature draft", error);
            }
        }
    }

    @Override
    public void saveLastValue(SharedPreferences preferences) {

    }

    @Override
    public boolean isShowLast() {
        return false;
    }

    @Override
    public String getFieldName() {
        return null;
    }

    @Override
    public void addToLayout(ViewGroup layout) {
        layout.addView(this);
    }

    @Override
    public Object getValue() {
        return null;
    }

    @Override
    public void saveState(Bundle outState) {
        if (!mEdited) return;
        try {
            JSONObject state = new JSONObject();
            state.put("width", mStrokeWidth);
            state.put("height", mStrokeHeight);
            JSONArray strokes = new JSONArray();
            for (List<Float> stroke : mStrokes) strokes.put(new JSONArray(stroke));
            state.put("strokes", strokes);
            outState.putString(DRAFT_STROKES, state.toString());
        } catch (JSONException error) {
            throw new IllegalStateException("Cannot checkpoint signature", error);
        }
    }

    private void rebuildPaths(int width, int height) {
        float sx = mStrokeWidth > 0 ? (float) width / mStrokeWidth : 1;
        float sy = mStrokeHeight > 0 ? (float) height / mStrokeHeight : 1;
        mPaths.clear();
        for (List<Float> stroke : mStrokes) {
            if (stroke.size() < 2) continue;
            for (int i=0; i<stroke.size(); i+=2) {
                stroke.set(i, stroke.get(i) * sx); stroke.set(i+1, stroke.get(i+1) * sy);
            }
            Path path = new Path();
            float x = stroke.get(0), y = stroke.get(1);
            path.moveTo(x, y);
            for (int i=2; i<stroke.size(); i+=2) {
                float nextX = stroke.get(i), nextY = stroke.get(i+1);
                path.quadTo(x, y, (x+nextX)/2, (y+nextY)/2);
                x = nextX; y = nextY;
            }
            path.lineTo(x, y);
            mPaths.add(path);
        }
        mStrokeWidth = width; mStrokeHeight = height;
        mPath = new Path();
        mPaths.add(mPath);
    }
}
