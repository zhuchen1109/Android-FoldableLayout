package com.alexvasilkov.foldablelayout;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import com.alexvasilkov.foldablelayout.shading.FoldShading;
import com.alexvasilkov.foldablelayout.shading.SimpleFoldShading;

import java.util.LinkedList;
import java.util.Queue;

/**
 * 一个类似于flipboard的立体翻转控件
 */
@SuppressLint("NewApi")
public class FoldableListLayout extends FrameLayout implements GestureDetector.OnGestureListener {

	/**
	 * fling或up触发时，翻转动画的持续的时间
	 */
    private static final long ANIMATION_DURATION_PER_ITEM = 600;

    //child view的params属性
    private static final LayoutParams PARAMS = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    
    //缓存child view的最大数
    private static final int CACHED_LAYOUTS_OFFSET = 2;

    //用于翻转进行时的翻转角度回调
    private OnFoldRotationListener mFoldRotationListener;
    //适配器
    private BaseAdapter mAdapter;

    //记录当前的翻转角度
    private float mFoldRotation;
    //允许的最小和最大翻转角度 一般情况最小值是0 最大值是180*(child size - 1)
    private float mMinRotation, mMaxRotation;

/*    @Override
    protected void onOverScrolled(int scrollX, int scrollY, boolean clampedX, boolean clampedY) {
        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY);
    }*/

    //二个child view 是当前需要draw的
    private FoldableItemLayout mFirstLayout, mSecondLayout;
    //给正在翻转的view加特效用的
    private FoldShading mFoldShading;

    //保存当前所有的child view
    private SparseArray<FoldableItemLayout> mFoldableLayoutsMap = new SparseArray<FoldableItemLayout>();
    //保存缓存的child view
    private Queue<FoldableItemLayout> mFoldableLayoutsCache = new LinkedList<FoldableItemLayout>();

    //fling或up触发时，做翻转动画用
    private ObjectAnimator mAnimator;
    //标示变量 标示最后MotionEvent事件的时间点
    private long mLastEventTime;
    //标示变量 记录最后一次事件的处理结果
    private boolean mLastEventResult;
    //手势处理工具类
    private GestureDetector mGestureDetector;

    //被认为是有效滚动的最小距离
    private float mMinDistanceBeforeScroll;
    //标示变量 若true 表示现在触发滚动
    private boolean mIsScrollDetected;
    //记录此次滚动开始时，当前的Rotation值
    private float mScrollStartRotation;
    //记录此次滚动开始时，滚动的距离
    private float mScrollStartDistance;

    public FoldableListLayout(Context context) {
        super(context);
        init(context);
    }

    public FoldableListLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public FoldableListLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    //初始化
    private void init(Context context) {
        mGestureDetector = new GestureDetector(context, this);
        //动画类初始化 第二个参数foldRotation表示 动画的回调函数是setFoldRotation(..)反射调用的
        mAnimator = ObjectAnimator.ofFloat(this, "foldRotation", 0);
        mMinDistanceBeforeScroll = ViewConfiguration.get(context).getScaledPagingTouchSlop();

        mFoldShading = new SimpleFoldShading();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        //需要draw的二个child view
        if (mFirstLayout != null) mFirstLayout.draw(canvas);
        if (mSecondLayout != null) mSecondLayout.draw(canvas);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        super.dispatchTouchEvent(ev);
        return getCount() > 0;//有child 就拦截事件 用于做翻转处理
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return processTouch(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return processTouch(event);
    }

    /**
     * 若想通过翻转动作处理一些值 可调用此函数
     * @param listener
     */
    public void setOnFoldRotationListener(OnFoldRotationListener listener) {
        mFoldRotationListener = listener;
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
    }

    /**
     * 若想处理当前翻转view的特效，可实现此函数
     * 需要在{@link #setAdapter(android.widget.BaseAdapter)}之前调用
     * @param shading
     */
    public void setFoldShading(FoldShading shading) {
        mFoldShading = shading;
    }

    /**
     * 设置用于填充数据的适配器
     * @param adapter
     */
    public void setAdapter(BaseAdapter adapter) {
        if (mAdapter != null) mAdapter.unregisterDataSetObserver(mDataObserver);
        mAdapter = adapter;
        if (mAdapter != null) mAdapter.registerDataSetObserver(mDataObserver);
        updateAdapterData();
    }

    public BaseAdapter getAdapter() {
        return mAdapter;
    }

    public int getCount() {
        return mAdapter == null ? 0 : mAdapter.getCount();
    }

    /**
     * 更新adapter，重新初始化view，通常在初始化adapter或adapter因数据变化需要初始化时 会调用
     */
    private void updateAdapterData() {
        int size = getCount();
        mMinRotation = 0;
        mMaxRotation = size == 0 ? 0 : 180 * (size - 1);

        freeAllLayouts(); // clearing old bindings

        //重新计算 并draw
        setFoldRotation(mFoldRotation);
    }

    /**
     * 根据参数rotation值更新view
     */
    public final void setFoldRotation(float rotation) {
        setFoldRotation(rotation, false);
    }

    /**
     * 根据参数rotation值更新view
     * 这个函数是处理翻转的核心函数
     */
    protected void setFoldRotation(float rotation, boolean isFromUser) {
        if (isFromUser) mAnimator.cancel();//取消当前的动画

        //边界判断 保证rotation值在有效范围之内
        rotation = Math.min(Math.max(mMinRotation, rotation), mMaxRotation);
        
        //记录每次翻转时的角度值
        mFoldRotation = rotation;
        
        //获取当前页的索引
        int firstVisiblePosition = (int) (rotation / 180);
        //获取在当前页，已经翻转的角度
        float localRotation = rotation % 180;

        int size = getCount();
        //判断是否还存在当前页
        boolean isHasFirst = firstVisiblePosition < size;
        //判断是否还存在下一页
        boolean isHasSecond = firstVisiblePosition + 1 < size;

        //若存在获取child view
        FoldableItemLayout firstLayout = isHasFirst ? getLayoutForItem(firstVisiblePosition) : null;
        FoldableItemLayout secondLayout = isHasSecond ? getLayoutForItem(firstVisiblePosition + 1) : null;

        if (isHasFirst) {
        	//做翻转处理
            firstLayout.setFoldRotation(localRotation);
            onFoldRotationChanged(firstLayout, firstVisiblePosition);
        }

        if (isHasSecond) {
        	//做翻转处理
            secondLayout.setFoldRotation(localRotation - 180);
            onFoldRotationChanged(secondLayout, firstVisiblePosition + 1);
        }

        boolean isReversedOrder = localRotation <= 90;
        //这个判断算是一个较复杂的计算和巧妙的技巧得出的，解释起来较费劲，可细细品读
        //基本思路是：哪个child view 要做翻转，就把谁放在上面
        if (isReversedOrder) {
            mFirstLayout = secondLayout;
            mSecondLayout = firstLayout;
        } else {
            mFirstLayout = firstLayout;
            mSecondLayout = secondLayout;
        }

        //翻转过程中的回调
        if (mFoldRotationListener != null) mFoldRotationListener.onFoldRotation(rotation, isFromUser);

        invalidate(); // when hardware acceleration is enabled view may not be invalidated and redrawn, but we need it
    }

    /**
     * 子类可在这处理翻转的状态
     * @param layout
     * @param position
     */
    protected void onFoldRotationChanged(FoldableItemLayout layout, int position) {

    }

    public float getFoldRotation() {
        return mFoldRotation;
    }

    /**
     * 得到一个child view
     * @param position
     * @return
     */
    private FoldableItemLayout getLayoutForItem(int position) {
        FoldableItemLayout layout = mFoldableLayoutsMap.get(position);
        //当前已存在此child view 直接返回
        if (layout != null) return layout;

        //尝试在缓存中获取
        layout = mFoldableLayoutsCache.poll();

        //尝试复用mFoldableLayoutsMap里多余的child view
        if (layout == null) {
            int farthestItem = position;

            int size = mFoldableLayoutsMap.size();
            for (int i = 0; i < size; i++) {
                int pos = mFoldableLayoutsMap.keyAt(i);
                if (Math.abs(position - pos) > Math.abs(position - farthestItem)) {
                    farthestItem = pos;
                }
            }

            if (Math.abs(farthestItem - position) > CACHED_LAYOUTS_OFFSET) {
                layout = mFoldableLayoutsMap.get(farthestItem);
                mFoldableLayoutsMap.remove(farthestItem);
                layout.getBaseLayout().removeAllViews(); // clearing old data
            }
        }

        //若还没找到有效child view 创建一个
        if (layout == null) {
            // if still no suited layout - create it
            layout = new FoldableItemLayout(getContext());
            layout.setFoldShading(mFoldShading);
            addView(layout, PARAMS);
        }

        //创建完毕 绑定数据源
        View view = mAdapter.getView(position, null, layout.getBaseLayout()); // TODO: use recycler
        layout.getBaseLayout().addView(view, PARAMS);

        //放入集合中管理
        mFoldableLayoutsMap.put(position, layout);

        return layout;
    }

    /**
     * 释放所有child view
     */
    private void freeAllLayouts() {
        int size = mFoldableLayoutsMap.size();
        for (int i = 0; i < size; i++) {
            FoldableItemLayout layout = mFoldableLayoutsMap.valueAt(i);
            layout.getBaseLayout().removeAllViews();
            //把移除的child view放到缓存中
            mFoldableLayoutsCache.offer(layout);
        }
        mFoldableLayoutsMap.clear();
    }

    /**
     * 滚动到上一页或下一页
     * @param index
     */
    public void scrollToPosition(int index) {
        index = Math.max(0, Math.min(index, getCount() - 1));

        //获取目标位置的角度
        float rotation = index * 180f;
        //当前所在位置的角度
        float current = getFoldRotation();
        //根据剩余需要翻转的角度计算动画的持续时间
        long duration = (long) Math.abs(ANIMATION_DURATION_PER_ITEM * (rotation - current) / 180f);

        //设置动画属性 并开始动画
        mAnimator.cancel();
        mAnimator.setFloatValues(current, rotation);
        mAnimator.setDuration(duration).start();
    }

    /**
     * 根据当前位置的角度 判断滚动停留在当前页还是滚动到下一页
     */
    protected void scrollToNearestPosition() {
        float current = getFoldRotation();
        //假设翻转角度rotation%180后的值分成二个区间(0,90)(90,180)，当(0,90)时滚到上一页，当(90,180)时滚到下一页
        //通过rotation = (rotation+90) rotation值区间变成(90,180)(180,270)，那么rotation/180此时二个区间的值一个不变、一个值+1
        //以此推算(rotation+90)/180的值要么等于 rotation/180 要么等于(rotation/180+1)
        scrollToPosition((int) ((current + 90f) / 180f));
    }

    /**
     * 触摸事件处理
     * @param event
     * @return
     */
    private boolean processTouch(MotionEvent event) {
    	//用来判断that event是否已被执行了，出现这种情况的原因是onInterceptTouchEvent和onTouchEvent都有可能去处理that event
        long eventTime = event.getEventTime();
        if (mLastEventTime == eventTime) return mLastEventResult;
        mLastEventTime = eventTime;

        //up事件触发时，需要对正在翻转的view做复位处理,是停留在当前页还是滚动到下一页
        if (event.getActionMasked() == MotionEvent.ACTION_UP && mIsScrollDetected) {
            mIsScrollDetected = false;
            scrollToNearestPosition();
        }

        if (getCount() > 0) {
            MotionEvent eventCopy = MotionEvent.obtain(event);
            //当整个view Y线上发生位移时，需要矫正Y值
            eventCopy.offsetLocation(0, getTranslationY());
            mLastEventResult = mGestureDetector.onTouchEvent(eventCopy);
            eventCopy.recycle();
        } else {
            mLastEventResult = false;
        }

        return mLastEventResult;
    }

    @Override
    public boolean onDown(MotionEvent event) {
        return false;
    }

    @Override
    public void onShowPress(MotionEvent event) {
        // NO-OP
    }

    @Override
    public boolean onSingleTapUp(MotionEvent event) {
        return false;
    }

    @Override
    public void onLongPress(MotionEvent event) {
        // NO-OP
    }

    @Override
    public boolean onRequestSendAccessibilityEvent(View child, AccessibilityEvent event) {
        return super.onRequestSendAccessibilityEvent(child, event);
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        //这个计算至关重要，是e1减去e2.那么可得出一个结论，向上滑动distance是正数 向下滑动distance是负数
    	float distance = e1.getY() - e2.getY();
        //滚动触发 开始初始化一些变量
        if (!mIsScrollDetected && Math.abs(distance) > mMinDistanceBeforeScroll) {
            mIsScrollDetected = true;
            mScrollStartRotation = getFoldRotation();
            mScrollStartDistance = distance;
        }

        if (mIsScrollDetected) {
            float rotation = (2 * (distance - mScrollStartDistance) / getHeight()) * 180f;
            //开始做翻转处理
            setFoldRotation(mScrollStartRotation + rotation, true);
        }

        return mIsScrollDetected;
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        float rotation = getFoldRotation();
        if (rotation % 180 == 0) return false;

        int position = (int) (rotation / 180f);
        //也许你很疑惑，为什么是position而不是position-1呢，想深究这个的话，你可以深入理解在滚动过程中，rotation这个值的生成策略
        //这里解释起来还是很费劲的
        scrollToPosition(velocityY > 0 ? position : position + 1);
        return true;
    }

    //adapter用的监听函数
    private DataSetObserver mDataObserver = new DataSetObserver() {
        @Override
        public void onChanged() {
            super.onChanged();
            updateAdapterData();
        }

        @Override
        public void onInvalidated() {
            super.onInvalidated();
            updateAdapterData();
        }
    };

    public interface OnFoldRotationListener {
        void onFoldRotation(float rotation, boolean isFromUser);
    }

}
