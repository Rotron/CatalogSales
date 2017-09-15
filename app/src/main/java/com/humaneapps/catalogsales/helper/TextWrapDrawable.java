/*
 * Copyright (C) 2017 Vladimir Markovic
 */

package com.humaneapps.catalogsales.helper;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

/**
 * Helper class for drawing text to display product name; for use with Glide as placeholder and
 * when image is unavailable.
 */
public class TextWrapDrawable extends Drawable {

    private final String mText;
    private final TextPaint mPaint;
    private final int mWidth;
    private final int mHeight;
    private int mPadding;
    private final float mTextSize;


    // Constructor
    public TextWrapDrawable(int width, int height, int padding, String text, float textSize) {
        mWidth = width;
        mHeight = height;
        mText = text;
        mTextSize = textSize;
        mPadding = padding;
        // Set paint.
        mPaint = new TextPaint();
        mPaint.setColor(Color.BLACK);
        mPaint.setTextSize(mTextSize);
        mPaint.setFakeBoldText(true);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setTextAlign(Paint.Align.LEFT);
    }


    @Override
    public void draw(@NonNull Canvas canvas) {
        // Add 'padding'.
        int width = mWidth;
        if (width < mPadding) {
            mPadding = 60;
        }
        if (width > mPadding) {
            width -= mPadding;
        }
        // Add text to StaticLayout.
        StaticLayout staticLayout =
                new StaticLayout(mText, mPaint, width, Layout.Alignment.ALIGN_NORMAL, 1, 1, false);
        canvas.save();
        // Position text.
        int numberOfTextLines = staticLayout.getLineCount();
        float textYCoordinate = (mHeight - (numberOfTextLines + 3) * mTextSize) / 2;
        canvas.translate(0, textYCoordinate);
        // Draws StaticLayout containing text on canvas.
        staticLayout.draw(canvas);
        canvas.restore();
    }


    @Override
    public void setAlpha(int alpha) { mPaint.setAlpha(alpha); }


    @Override
    public void setColorFilter(ColorFilter cf) { mPaint.setColorFilter(cf); }


    @Override
    public int getOpacity() { return PixelFormat.TRANSLUCENT; }


} // End TextWrapDrawable class.
