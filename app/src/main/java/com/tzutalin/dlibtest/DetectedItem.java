package com.tzutalin.dlibtest;

import android.graphics.drawable.Drawable;

public class DetectedItem {
    String title;
    Drawable image;

    public DetectedItem(final Drawable pImage, final String pTitle) {
        title = pTitle;
        image = pImage;
    }
}
