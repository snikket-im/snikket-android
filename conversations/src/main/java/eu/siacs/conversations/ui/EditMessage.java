package eu.siacs.conversations.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.widget.EditText;

public class EditMessage extends EditText {

	public EditMessage(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public EditMessage(Context context) {
		super(context);
	}

	protected OnEnterPressed mOnEnterPressed;

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_ENTER) {
			if (mOnEnterPressed != null) {
				mOnEnterPressed.onEnterPressed();
			}
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	public void setOnEnterPressedListener(OnEnterPressed listener) {
		this.mOnEnterPressed = listener;
	}

	public interface OnEnterPressed {
		public void onEnterPressed();
	}

}
