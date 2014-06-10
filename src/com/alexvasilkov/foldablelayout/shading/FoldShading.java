package com.alexvasilkov.foldablelayout.shading;

import android.graphics.Canvas;
import android.graphics.Rect;

/**
 * 用于给指定canvas绘制特效
 *
 */
public interface FoldShading {
    void onPreDraw(Canvas canvas, Rect bounds, float rotation, int gravity);

    void onPostDraw(Canvas canvas, Rect bounds, float rotation, int gravity);
}
