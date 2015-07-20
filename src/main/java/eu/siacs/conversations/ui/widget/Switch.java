package eu.siacs.conversations.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.kyleduo.switchbutton.SwitchButton;

public class Switch extends SwitchButton {

	private int mTouchSlop;
	private int mClickTimeout;
	private float mStartX;
	private float mStartY;
	private OnClickListener mOnClickListener;

	public Switch(Context context) {
		super(context);
		mTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
		mClickTimeout = ViewConfiguration.getPressedStateDuration() + ViewConfiguration.getTapTimeout();
	}

	public Switch(Context context, AttributeSet attrs) {
		super(context, attrs);
		mTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
		mClickTimeout = ViewConfiguration.getPressedStateDuration() + ViewConfiguration.getTapTimeout();
	}

	public Switch(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		mTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
		mClickTimeout = ViewConfiguration.getPressedStateDuration() + ViewConfiguration.getTapTimeout();
	}

	@Override
	public void setOnClickListener(OnClickListener onClickListener) {
		this.mOnClickListener = onClickListener;
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (!isEnabled()) {
			float deltaX = event.getX() - mStartX;
			float deltaY = event.getY() - mStartY;
			int action = event.getAction();
			switch (action) {
				case MotionEvent.ACTION_DOWN:
					mStartX = event.getX();
					mStartY = event.getY();
					break;
				case MotionEvent.ACTION_CANCEL:
				case MotionEvent.ACTION_UP:
					float time = event.getEventTime() - event.getDownTime();
					if (deltaX < mTouchSlop && deltaY < mTouchSlop && time < mClickTimeout) {
						if (mOnClickListener != null) {
							this.mOnClickListener.onClick(this);
						}
					}
					break;
				default:
					break;
			}
			return true;
		}
		return super.onTouchEvent(event);
	}
}
