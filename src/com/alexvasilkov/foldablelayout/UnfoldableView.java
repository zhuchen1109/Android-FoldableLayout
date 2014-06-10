package com.alexvasilkov.foldablelayout;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;

/**
 * View that provides ability to switch between 2 diffrent views (cover view & details view) with fold animation.
 * <p/>
 * It is implemented as subclass of FoldableListLayout with only 2 views to scroll between.
 */
@SuppressLint("NewApi")
public class UnfoldableView extends FoldableListLayout {

	//一个空白的view 默认用来填充Details view区域
    private View mDefaultDetailsPlaceHolderView;
    //一个空白的view 默认用来填充cover view区域
    private View mDefaultCoverPlaceHolderView;

    //保存着原来的view的引用
    private View mDetailsView, mCoverView;
    //一个的view 用来填充Details view和cover view区域
    private View mDetailsPlaceHolderView, mCoverPlaceHolderView;
    //辅助view 添加原coverView进来，目的是用于做翻转动画
    private CoverHolderLayout mCoverHolderLayout;

    //出现特殊下的处理，临时保存原来的view引用
    private View mScheduledCoverView, mScheduledDetailsView;

    //保存着原来的view的LayoutParams引用
    private ViewGroup.LayoutParams mDetailsViewParams, mCoverViewParams;
    //保存着原来的view的宽、高值
    private int mDetailsViewParamWidth, mDetailsViewParamHeight, mCoverViewParamWidth, mCoverViewParamHeight;
    //保存着原来的view的在屏幕上的可显示范围
    private Rect mCoverViewPosition, mDetailsViewPosition;

    private Adapter mAdapter;

    private float mLastFoldRotation;
    //几种状态的定义
    //是否正在展开
    private boolean mIsUnfolding;
    //是否正在折叠
    private boolean mIsFoldingBack;
    //是否已经展开完毕
    private boolean mIsUnfolded;

    //翻转状态回调接口
    private OnFoldingListener mListener;

    public UnfoldableView(Context context) {
        super(context);
        init(context);
    }

    public UnfoldableView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public UnfoldableView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context) {
        mCoverHolderLayout = new CoverHolderLayout(context);
        mDefaultDetailsPlaceHolderView = new View(context);
        mDefaultCoverPlaceHolderView = new View(context);
        mAdapter = new Adapter();
    }

    public void setOnFoldingListener(OnFoldingListener listener) {
        mListener = listener;
    }

    /**
     * 对detailsView做内部初始化
     * 主要做2件事：
     * 1、保存原detailsView数据
     * 2、在detailsView父容器里移除detailsView，添加一个holder view进去
     */
    private void setDetailsViewInternal(View detailsView) {
    	//保存原detailsView的数据
        mDetailsView = detailsView;
        mDetailsViewParams = detailsView.getLayoutParams();
        mDetailsViewParamWidth = mDetailsViewParams.width;
        mDetailsViewParamHeight = mDetailsViewParams.height;

        //保存原detailsView的在屏幕的可显示范围
        mDetailsViewPosition = getViewGlobalPosition(detailsView);

        //创建一个view用来填充details view区域
        mDetailsPlaceHolderView = createDetailsPlaceHolderView();

        //设置真实的宽高值
        mDetailsViewParams.width = mDetailsViewPosition.width();
        mDetailsViewParams.height = mDetailsViewPosition.height();
        //view转换 在detailsView的父容器里，用mDetailsPlaceHolderView替换detailsView
        switchViews(detailsView, mDetailsPlaceHolderView, mDetailsViewParams);
    }

    private void clearDetailsViewInternal() {
        if (mDetailsView == null) return; // nothing to do

        // restoring original width/height params and adding cover view back to it's place
        mDetailsViewParams.width = mDetailsViewParamWidth;
        mDetailsViewParams.height = mDetailsViewParamHeight;
        switchViews(mDetailsPlaceHolderView, mDetailsView, mDetailsViewParams);

        // clearing references
        mDetailsView = null;
        mDetailsViewParams = null;
        mDetailsViewPosition = null;
        mDetailsPlaceHolderView = null;
    }

    /**
     * 对CoverView做内部初始化
     * 主要做3件事：
     * 1、保存原CoverView数据
     * 2、在CoverView父容器里移除CoverView，添加一个holder view进去
     * 3、把原CoverView添加到cover holder layout中去，用于做翻转动画
     */
    private void setCoverViewInternal(View coverView) {
        //保存原coverView的数据
        mCoverView = coverView;
        mCoverViewParams = coverView.getLayoutParams();
        mCoverViewParamWidth = mCoverViewParams.width;
        mCoverViewParamHeight = mCoverViewParams.height;

        //保存原coverView的在屏幕的可显示范围
        mCoverViewPosition = getViewGlobalPosition(coverView);

        //创建一个view用来填充cover view区域
        mCoverPlaceHolderView = createCoverPlaceHolderView();

        //设置真实的宽高值
        mCoverViewParams.width = mCoverViewPosition.width();
        mCoverViewParams.height = mCoverViewPosition.height();
        //view转换 在coverView的父容器里，用mCoverPlaceHolderView替换coverView
        switchViews(coverView, mCoverPlaceHolderView, mCoverViewParams);

        //把原coverView view填充到cover holder layout里去(用于做翻转动画用)
        mCoverHolderLayout.setView(coverView, mCoverViewPosition.width(), mCoverViewPosition.height());
    }

    private void clearCoverViewInternal() {
        if (mCoverView == null) return; // nothing to do

        // freeing coverView so we can add it back to it's palce
        mCoverHolderLayout.clearView();

        //恢复原cover view到原来的父容器里
        mCoverViewParams.width = mCoverViewParamWidth;
        mCoverViewParams.height = mCoverViewParamHeight;
        switchViews(mCoverPlaceHolderView, mCoverView, mCoverViewParams);

        //清楚对原cover view各个数据引用
        mCoverView = null;
        mCoverViewParams = null;
        mCoverViewPosition = null;
        mCoverPlaceHolderView = null;
    }

    public void changeCoverView(View coverView) {
        if (mCoverView == null || mCoverView == coverView) return; // nothing to do
        clearCoverViewInternal();
        setCoverViewInternal(coverView);
    }

    /**
     * 用来填充Details view区域,可子类覆盖自定义这个view
     * @return
     */
    protected View createDetailsPlaceHolderView() {
        return mDefaultDetailsPlaceHolderView;
    }

    /**
     * 用来填充cover view区域,可子类覆盖自定义这个view
     * @return
     */
    protected View createCoverPlaceHolderView() {
        return mDefaultCoverPlaceHolderView;
    }

    /**
     * 根据给定的view做展开动画
     * @param coverView
     * @param detailsView
     */
    public void unfold(View coverView, View detailsView) {
        if (mCoverView == coverView && mDetailsView == detailsView) return; // already in place

        //cover or details view出现不一致时，做特殊处理
        if ((mCoverView != null && mCoverView != coverView) || (mDetailsView != null && mDetailsView != detailsView)) {
            // cover or details view is differ - closing details and schedule reopening
            mScheduledDetailsView = detailsView;
            mScheduledCoverView = coverView;
            foldBack();
            return;
        }

        //为了实现翻转动画，需要对coverView和detailsView做处理
        setCoverViewInternal(coverView);
        setDetailsViewInternal(detailsView);

        //初始化adpater
        setAdapter(mAdapter);

        //开始展开动画
        scrollToPosition(1);
    }

    /**
     * 做折叠动画
     */
    public void foldBack() {
        scrollToPosition(0);
    }

    /**
     * 折叠完毕后的处理
     */
    private void onFoldedBack() {
        // clearing all foldable views
        setAdapter(null);

        clearCoverViewInternal();
        clearDetailsViewInternal();

        // clearing translations
        setTranslationX(0);
        setTranslationY(0);

        if (mScheduledCoverView != null && mScheduledDetailsView != null) {
            View scheduledDetails = mScheduledDetailsView;
            View scheduledCover = mScheduledCoverView;
            mScheduledDetailsView = mScheduledCoverView = null;
            unfold(scheduledCover, scheduledDetails);
        }
    }

    public boolean isUnfolding() {
        return mIsUnfolding;
    }

    public boolean isFoldingBack() {
        return mIsFoldingBack;
    }

    public boolean isUnfolded() {
        return mIsUnfolded;
    }

    /**
     * 重写父类此函数，主要目的是处理翻转过程中的位移和翻转状态回调
     */
    @Override
    public void setFoldRotation(float rotation, boolean isFromUser) {
        super.setFoldRotation(rotation, isFromUser);
        if (mCoverView == null || mDetailsView == null) return; // nothing we can do here

        rotation = getFoldRotation(); // parent view will correctly keep rotation in bounds for us

        // translating from cover's position to details position
        float stage = rotation / 180; // from 0 = only cover view, to 1 - only details view

        float fromX = mCoverViewPosition.centerX();
        float toX = mDetailsViewPosition.centerX();

        float fromY = mCoverViewPosition.top;
        float toY = mDetailsViewPosition.centerY();

        //这里有些特别 fromX是终点、toX是开始点 (1 - stage)表示一开始就移动fromX位置，然后慢慢的回到toX
        setTranslationX((fromX - toX) * (1 - stage));
        setTranslationY((fromY - toY) * (1 - stage));

        // tracking states
        float lastRotatation = mLastFoldRotation;
        mLastFoldRotation = rotation;

        if (mListener != null) mListener.onFoldProgress(this, stage);

        //表示当前正在展开
        if (rotation > lastRotatation && !mIsUnfolding) {
            mIsUnfolding = true;
            mIsFoldingBack = false;
            mIsUnfolded = false;

            if (mListener != null) mListener.onUnfolding(this);
        }

        //表示当前正在折叠
        if (rotation < lastRotatation && !mIsFoldingBack) {
            mIsUnfolding = false;
            mIsFoldingBack = true;
            mIsUnfolded = false;

            if (mListener != null) mListener.onFoldingBack(this);
        }

        //表示已经展开完毕
        if (rotation == 180 && !mIsUnfolded) {
            mIsUnfolding = false;
            mIsFoldingBack = false;
            mIsUnfolded = true;

            if (mListener != null) mListener.onUnfolded(this);
        }

        //表示折叠完毕
        if (rotation == 0 && mIsFoldingBack) {
            mIsUnfolding = false;
            mIsFoldingBack = false;
            mIsUnfolded = false;

            onFoldedBack();
            if (mListener != null) mListener.onFoldedBack(this);
        }
    }

    @Override
    protected void onFoldRotationChanged(FoldableItemLayout layout, int position) {
        super.onFoldRotationChanged(layout, position);

        float stage = getFoldRotation() / 180; // from 0 = only cover view, to 1 - only details view

        float coverW = mCoverViewPosition.width();
        float detailsW = mDetailsViewPosition.width();

        if (position == 0) { //cover view
            // 计算cover view的Scale值 若这样不好理解 可以这样看 coverScale = 1 + (detailsW / coverW - 1) * stage;
            float coverScale = 1 - (1 - detailsW / coverW) * stage;
            layout.setScale(coverScale);
        } else { // details view
            // 计算detailsScale的Scale值 若这样不好理解 可以这样看 coverScale = 1 + (detailsW / coverW - 1) * (1 - stage);
        	//至于为何是1 - stage 可把1 - stage改成stage测试下，就知道原因了
            float detailsScale = 1 - (1 - coverW / detailsW) * (1 - stage);
            layout.setScale(detailsScale);

            float dH = mDetailsViewPosition.height() / 2 - mCoverViewPosition.height() * detailsW / coverW;
            float translationY = stage < 0.5f ? -dH * (1 - 2 * stage) : 0;

            layout.setRollingDistance(translationY);
        }
    }

    /**
     * view转换
     * 把origin view从其父容器里移除，并把replacement view添加进其父容器中
     * @param origin
     * @param replacement
     * @param params
     */
    private void switchViews(View origin, View replacement, ViewGroup.LayoutParams params) {
        ViewGroup parent = (ViewGroup) origin.getParent();

        if (params == null) params = origin.getLayoutParams();

        //移除原view，添加新的view
        int index = parent.indexOfChild(origin);
        parent.removeViewAt(index);
        parent.addView(replacement, index, params);
    }

    /**
     * 获取view的可显示范围
     * @param view
     * @return
     */
    private Rect getViewGlobalPosition(View view) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        return new Rect(location[0], location[1], location[0] + view.getWidth(), location[1] + view.getHeight());
    }


    /**
     * Simple adapter that will alternate between cover view holder layout and details layout
     */
    private class Adapter extends BaseAdapter {

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public Object getItem(int i) {
            return null;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View recycledView, ViewGroup parent) {
            return i == 0 ? mCoverHolderLayout : mDetailsView;
        }
    }


    /**
     * Cover view holder layout. It can contain at most one child which will be positioned in the top|center_horisontal
     * location of bottom half of the view.
     */
    private static class CoverHolderLayout extends FrameLayout {

        private final Rect mVisibleBounds = new Rect();

        private CoverHolderLayout(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            int h = getMeasuredHeight();
            setPadding(0, h / 2, 0, 0);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);

            // Collecting visible bounds of child view, it will be used to correctly draw shadows and to
            // improve drawing performance
            View view = getView();
            if (view != null) {
                mVisibleBounds.set(view.getLeft(), view.getTop(),
                        view.getLeft() + view.getWidth(), view.getTop() + view.getHeight());

                FoldableItemLayout foldableLayout = findParentFoldableLayout();
                if (foldableLayout != null) foldableLayout.setLayoutVisibleBounds(mVisibleBounds);
            } else {
                mVisibleBounds.set(0, 0, 0, 0);
            }
        }

        private FoldableItemLayout findParentFoldableLayout() {
            ViewGroup parent = this;
            while (parent != null) {
                parent = (ViewGroup) parent.getParent();
                if (parent instanceof FoldableItemLayout) {
                    return (FoldableItemLayout) parent;
                }
            }
            return null;
        }

        /**
         * 添加一个view做为child view 用于做翻转动画
         * @param view
         * @param w
         * @param h
         */
        private void setView(View view, int w, int h) {
            removeAllViews();
            LayoutParams params = new LayoutParams(w, h, Gravity.CENTER_HORIZONTAL);
            addView(view, params);
        }

        private View getView() {
            return getChildCount() > 0 ? getChildAt(0) : null;
        }

        /**
         * 动画结束 清楚所有child view 
         */
        private void clearView() {
            removeAllViews();
        }

    }

    /**
     * 当前翻转状态回调接口
     */
    public interface OnFoldingListener {
    	
    	/**
    	 * 当前处于正在展开时回调
    	 */
        void onUnfolding(UnfoldableView unfoldableView);

        /**
    	 * 当前已经展开完毕时回调
    	 */
        void onUnfolded(UnfoldableView unfoldableView);

        /**
    	 * 当前处于正在折叠时回调
    	 */
        void onFoldingBack(UnfoldableView unfoldableView);

        /**
    	 * 当前已经折叠完毕时回调
    	 */
        void onFoldedBack(UnfoldableView unfoldableView);

        /**
    	 * 当前展开或折叠的百分比回调
    	 */
        void onFoldProgress(UnfoldableView unfoldableView, float progress);
    }

    public static class SimpleFoldingListener implements OnFoldingListener {
        @Override
        public void onUnfolding(UnfoldableView unfoldableView) {
        }

        @Override
        public void onUnfolded(UnfoldableView unfoldableView) {
        }

        @Override
        public void onFoldingBack(UnfoldableView unfoldableView) {
        }

        @Override
        public void onFoldedBack(UnfoldableView unfoldableView) {
        }

        @Override
        public void onFoldProgress(UnfoldableView unfoldableView, float progress) {
        }
    }

}
