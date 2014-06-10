package com.alexvasilkov.foldablelayout.shading;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;
import android.view.Gravity;

/**
 * 一个简单的绘制蒙层特效
 *
 */
public class SimpleFoldShading implements FoldShading {

    private static final int SHADOW_COLOR = Color.BLACK;
    private static final int SHADOW_MAX_ALPHA = 192;

    private final Paint mSolidShadow;

    public SimpleFoldShading() {
        mSolidShadow = new Paint();
        mSolidShadow.setColor(SHADOW_COLOR);
    }

    @Override
    public void onPreDraw(Canvas canvas, Rect bounds, float rotation, int gravity) {
        // NO-OP
    }

    @Override
    public void onPostDraw(Canvas canvas, Rect bounds, float rotation, int gravity) {
        float intencity = getShadowIntencity(rotation, gravity);
        //大于0表示需要绘制一层蒙层
        if (intencity > 0) {
            int alpha = (int) (SHADOW_MAX_ALPHA * intencity);
            mSolidShadow.setAlpha(alpha);
            canvas.drawRect(bounds, mSolidShadow);
        }
    }

    private float getShadowIntencity(float rotation, int gravity) {
        float intencity = 0;
        //根据当前旋转的角度和part view的gravity来判断是否需要在上面绘制一层蒙层
        if (gravity == Gravity.TOP) {
        	//这个区间正是top part view正在做旋转
            if (rotation > -90 && rotation < 0) { // (-90; 0) - rotation is applied
                intencity = -rotation / 90f;
            }
        } else {
        	//这个区间正是bottom part view正在做旋转
            if (rotation > 0 && rotation < 90) { // (0; 90) - rotation is applied
                intencity = rotation / 90f;
            }
        }
        return intencity;
    }

}
