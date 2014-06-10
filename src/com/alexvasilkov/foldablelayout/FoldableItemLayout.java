package com.alexvasilkov.foldablelayout;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import com.alexvasilkov.foldablelayout.shading.FoldShading;

/**
 * {@link FoldableListLayout}的child view
 */
@SuppressLint("NewApi")
public class FoldableItemLayout extends FrameLayout {

    private static final int CAMERA_DISTANCE = 48;
    private static final float CAMERA_DISTANCE_MAGIC_FACTOR = 8f / CAMERA_DISTANCE;

    //是否需要开启自动Scale
    private boolean mIsAutoScaleEnabled;

    //可以看做是FoldableItemLayout的唯一child view，它会去管理所有的views,翻转过程中，它会提供一个mCacheCanvas去做翻转处理
    private BaseLayout mBaseLayout;
    //因翻转需要，会把整个view切成上下二部分来处理
    private PartView mTopPart, mBottomPart;

    //当前view 宽 高
    private int mWidth, mHeight;
    //保存整个view生成的bitmap，主要作用是在做翻转时，会对其进行操作
    private Bitmap mCacheBitmap;

    //若true 表示当前处于翻转过程
    private boolean mIsInTransformation;

    //记录当前翻转的角度 这个值是被180模过的 所有值范围在(-180,180)
    private float mFoldRotation;
    //记录当前view压缩的比值
    private float mScale;
    //配合mScale一起使用
    private float mRollingDistance;

    public FoldableItemLayout(Context context) {
        super(context);
        init(context);
    }

    public FoldableItemLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public FoldableItemLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context) {
    	//配合翻转用 主要是提供一块画布
        mBaseLayout = new BaseLayout(this);

        //做翻转用的二个view
        mTopPart = new PartView(this, Gravity.TOP);
        mBottomPart = new PartView(this, Gravity.BOTTOM);
        
        //初始化当前状态是非翻转
        setInTransformation(false);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        //mBaseLayout要完全覆盖掉FoldableItemLayout，所有mBaseLayout要接管除FoldableItemLayout定义的3个child view
        mBaseLayout.moveInflatedChildren(this, 3); // skipping mBaseLayout & mTopPart & mBottomPart views
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        mWidth = w;
        mHeight = h;

        if (mCacheBitmap != null) {
            mCacheBitmap.recycle();
            mCacheBitmap = null;
        }

        //初始化一块和当前view一样大小的bitmap做为mBaseLayout的画布
        mCacheBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);

        mBaseLayout.setCacheCanvas(new Canvas(mCacheBitmap));

        //mTopPart和mBottomPart这二个view绘制mCacheBitmap，会有一些变换和裁剪
        mTopPart.setCacheBitmap(mCacheBitmap);
        mBottomPart.setCacheBitmap(mCacheBitmap);
    }

    /**
     * 根据传入的角度值rotation做翻转处理
     * @param rotation
     */
    public void setFoldRotation(float rotation) {
    	//保存角度值
        mFoldRotation = rotation;

        //mTopPart和mBottomPart去处理翻转
        mTopPart.applyFoldRotation(rotation);
        mBottomPart.applyFoldRotation(rotation);

        //若为0表示，当前的实际角度是-180|0|180,是平铺的，那么关闭对翻转过程的处理
        setInTransformation(rotation != 0);

        if (mIsAutoScaleEnabled) {
            float viewScale = 1.0f;
            if (mWidth > 0) {
                float dW = (float) (mHeight * Math.abs(Math.sin(Math.toRadians(rotation)))) * CAMERA_DISTANCE_MAGIC_FACTOR;
                viewScale = mWidth / (mWidth + dW);
            }
            //Scale处理
            setScale(viewScale);
        }
    }

    /**
     * 获取当前的mFoldRotation值 这个值是被180模过的 所有值范围在(-180,180)
     * @return
     */
    public float getFoldRotation() {
        return mFoldRotation;
    }

    /**
     * 对当前view和做翻转的mTopPart、mBottomPart做scale处理
     * @param scale
     */
    public void setScale(float scale) {
        mScale = scale;
        mTopPart.applyScale(scale);
        mBottomPart.applyScale(scale);
    }

    public float getScale() {
        return mScale;
    }

    /**
     * 根据位移重新计算中间折叠线
     * @param distance
     */
    public void setRollingDistance(float distance) {
        mRollingDistance = distance;
        mTopPart.applyRollingDistance(distance, mScale);
        mBottomPart.applyRollingDistance(distance, mScale);
    }

    public float getRollingDistance() {
        return mRollingDistance;
    }

    /**
     * 设置当前是否还是在翻转过程
     * @param isInTransformation 若true 表示还处于翻转过程，并启用mTopPart和mBottomPart
     */
    private void setInTransformation(boolean isInTransformation) {
        if (mIsInTransformation == isInTransformation) return;
        mIsInTransformation = isInTransformation;

        //根据isInTransformation来判断现在是否处于翻转过程，来处理当前的draw
        mBaseLayout.setDrawToCache(isInTransformation);
        mTopPart.setVisibility(isInTransformation ? VISIBLE : INVISIBLE);
        mBottomPart.setVisibility(isInTransformation ? VISIBLE : INVISIBLE);
    }

    /**
     * 设置是否能自动处理Scale属性
     * @param isAutoScaleEnabled
     */
    public void setAutoScaleEnabled(boolean isAutoScaleEnabled) {
        mIsAutoScaleEnabled = isAutoScaleEnabled;
    }

    /**
     * 一个root view
     * 可以看做是FoldableItemLayout的唯一child view，它会去管理所有的views
     */
    public FrameLayout getBaseLayout() {
        return mBaseLayout;
    }

    public void setLayoutVisibleBounds(Rect visibleBounds) {
        mTopPart.setVisibleBounds(visibleBounds);
        mBottomPart.setVisibleBounds(visibleBounds);
    }

    /**
     * 主要给正在翻转的view加特效用的
     * @param shading
     */
    public void setFoldShading(FoldShading shading) {
        mTopPart.setFoldShading(shading);
        mBottomPart.setFoldShading(shading);
    }


    /**
     * 可以看做是FoldableItemLayout的唯一child view，它会去管理所有的child views,翻转过程中，它会提供一个mCacheCanvas去做翻转处理
     * 不需要处理翻转时，会恢复正常的draw
     *
     */
    private static class BaseLayout extends FrameLayout {

        private Canvas mCacheCanvas;
        private boolean mIsDrawToCache;

        @SuppressWarnings("deprecation")
        private BaseLayout(FoldableItemLayout layout) {
            super(layout.getContext());

            //把自己做为child view添加到FoldableItemLayout中去
            int matchParent = ViewGroup.LayoutParams.MATCH_PARENT;
            LayoutParams params = new LayoutParams(matchParent, matchParent);
            layout.addView(this, params);

            //因BaseLayout覆盖整个父容器，所有要处理下背景
            this.setBackgroundDrawable(layout.getBackground());
            layout.setBackgroundDrawable(null);

            setWillNotDraw(false);
        }

        //对FoldableItemLayout的child count大于firstSkippedItems的child view添加到BaseLayout中去
        private void moveInflatedChildren(FoldableItemLayout layout, int firstSkippedItems) {
            while (layout.getChildCount() > firstSkippedItems) {
                View view = layout.getChildAt(firstSkippedItems);
                LayoutParams params = (LayoutParams) view.getLayoutParams();
                layout.removeViewAt(firstSkippedItems);
                addView(view, params);
            }
        }

        @Override
        public void draw(Canvas canvas) {
            if (mIsDrawToCache) {//处理翻转时，会走这个分支
                mCacheCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
                super.draw(mCacheCanvas);
            } else {
                super.draw(canvas);
            }
        }
        
        //设置一个翻转处理需要用的Canvas
        private void setCacheCanvas(Canvas cacheCanvas) {
            mCacheCanvas = cacheCanvas;
        }

        //翻转过程中，会设置为true
        private void setDrawToCache(boolean drawToCache) {
            if (mIsDrawToCache == drawToCache) return;
            mIsDrawToCache = drawToCache;
            invalidate();
        }

    }

    /**
     * 做翻转处理的核心类 它会在正在翻转的view(top part or bottom part)绘制cached bitmap和overlay shadows
     * @author zhuchen
     *
     */
    private static class PartView extends View {

    	//若为Gravity.TOP表示是上部分；若为Gravity.BOTTOM表示是下部分
        private final int mGravity;

        //传入的canvas的bitmap 所有的处理draw在这上面
        private Bitmap mBitmap;
        //根据mGravity熟悉 判断当前是属于上半部分还是下半部分
        private final Rect mBitmapBounds = new Rect();

        //配合mBitmapBounds计算用的
        private float mClippingFactor = 0.5f;

        //一paint 无特别设置
        private final Paint mBitmapPaint;

        //记录当前view的可见范围
        private Rect mVisibleBounds;

        //这二个值用于判断是否当前view能显示用的
        private int mInternalVisibility;
        private int mExtrenalVisibility;

        //记录当前要做翻转的角度
        private float mLocalFoldRotation;
        //翻转view加特效用的
        private FoldShading mShading;

        public PartView(FoldableItemLayout parent, int gravity) {
            super(parent.getContext());
            mGravity = gravity;

            final int matchParent = LayoutParams.MATCH_PARENT;
            parent.addView(this, new LayoutParams(matchParent, matchParent));
            //使用rotationX或rotationY时，会使view变大，这时候可以使用这个函数来改善效果
            setCameraDistance(CAMERA_DISTANCE * getResources().getDisplayMetrics().densityDpi);

            mBitmapPaint = new Paint();
            mBitmapPaint.setDither(true);
            mBitmapPaint.setFilterBitmap(true);

            setWillNotDraw(false);
        }

        /**
         * 设置翻转处理的bitmap并计算选取bitmap的范围
         * @param bitmap
         */
        private void setCacheBitmap(Bitmap bitmap) {
            mBitmap = bitmap;
            calculateBitmapBounds();
        }

        /**
         * 设置当前view的可见范围 并和mBitmapBounds进行判断是否有交集
         * @param visibleBounds
         */
        private void setVisibleBounds(Rect visibleBounds) {
            mVisibleBounds = visibleBounds;
            calculateBitmapBounds();
        }

        //主要给正在翻转的view加特效用的
        private void setFoldShading(FoldShading shading) {
            mShading = shading;
        }

        /**
         * 根据mGravity计算mBitmapBounds
         */
        private void calculateBitmapBounds() {
            if (mBitmap == null) {
                mBitmapBounds.set(0, 0, 0, 0);
            } else {
                int h = mBitmap.getHeight();
                int w = mBitmap.getWidth();
                
                //加入参数mClippingFactor计算top和bottom,h * (1 - mClippingFactor)和h * mClippingFactor看上去不一样，
                //实际上是一回事，可以根据计算mClippingFactor值的方法推导出来
                int top = mGravity == Gravity.TOP ? 0 : (int) (h * (1 - mClippingFactor) - 0.5f);
                int bottom = mGravity == Gravity.TOP ? (int) (h * mClippingFactor + 0.5f) : h;

                mBitmapBounds.set(0, top, w, bottom);
                if (mVisibleBounds != null) {//若mVisibleBounds!=null 且和mVisibleBounds无交集，那么不需要处理再draw了
                    if (!mBitmapBounds.intersect(mVisibleBounds)) {
                        mBitmapBounds.set(0, 0, 0, 0); // no intersection
                    }
                }
            }

            invalidate();
        }

        private void applyFoldRotation(float rotation) {
            float position = rotation;
            //校验判断 保证值的范围在(-180; 180]
            while (position < 0) position += 360;
            position %= 360;
            if (position > 180) position -= 360; // now poistion within (-180; 180]
            
            float rotationX = 0;
            boolean isVisible = true;

            //这个判断也比较复杂，可自己细细分析
            //基本思路是：做翻转的part view处理setRotationX，其背面的part view隐藏，其他part view保存不变
            if (mGravity == Gravity.TOP) {
                if (position <= -90 || position == 180) { // (-180; -90] || {180} - will not show
                    isVisible = false;
                } else if (position < 0) { // (-90; 0) - applying rotation
                    rotationX = position;
                }
                // [0; 180) - holding still
            } else {
                if (position >= 90) { // [90; 180] - will not show
                    isVisible = false;
                } else if (position > 0) { // (0; 90) - applying rotation
                    rotationX = position;
                }
                // else: (-180; 0] - holding still
            }

            //做旋转的方法
            setRotationX(rotationX);

            mInternalVisibility = isVisible ? VISIBLE : INVISIBLE;
            applyVisibility();

            mLocalFoldRotation = position;

            invalidate(); // needed to draw shadow overlay
        }

        /**
         * scale处理
         * @param scale
         */
        private void applyScale(float scale) {
            setScaleX(scale);
            setScaleY(scale);
        }

        /**
         * 应用旋转动画时，part view的位移
         */
        private void applyRollingDistance(float distance, float scale) {
            // applying translation
            setTranslationY((int) (distance * scale + 0.5f));

            // computing clipping for top view (bottom clipping will be 1 - topClipping)
            final int h = getHeight() / 2;
            final float topClipping = h == 0 ? 0.5f : (h - distance) / h / 2;

            //默认这个值是0.5 若part view本身发生位移时，也需要跟着做微调
            mClippingFactor = mGravity == Gravity.TOP ? topClipping : 1f - topClipping;

            calculateBitmapBounds();
        }

        @Override
        public void setVisibility(int visibility) {
            mExtrenalVisibility = visibility;
            applyVisibility();
        }

        /**
         * 根据mExtrenalVisibility和mInternalVisibility二个值来觉得view的是否可见
         */
        private void applyVisibility() {
            super.setVisibility(mExtrenalVisibility == VISIBLE ? mInternalVisibility : mExtrenalVisibility);
        }

        @Override
        public void draw(Canvas canvas) {
            if (mShading != null) mShading.onPreDraw(canvas, mBitmapBounds, mLocalFoldRotation, mGravity);
            if (mBitmap != null) canvas.drawBitmap(mBitmap, mBitmapBounds, mBitmapBounds, mBitmapPaint);
            //在旋转的part view上做特效处理
            if (mShading != null) mShading.onPostDraw(canvas, mBitmapBounds, mLocalFoldRotation, mGravity);
        }

    }

}
