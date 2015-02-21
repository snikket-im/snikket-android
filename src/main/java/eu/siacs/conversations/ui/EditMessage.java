package eu.siacs.conversations.ui;

import android.content.Context;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.widget.EditText;

import eu.siacs.conversations.Config;

public class EditMessage extends EditText {

	public EditMessage(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public EditMessage(Context context) {
		super(context);
	}

	protected Handler mTypingHandler = new Handler();

	protected Runnable mTypingTimeout = new Runnable() {
		@Override
		public void run() {
			if (isUserTyping && keyboardListener != null) {
				keyboardListener.onTypingStopped();
				isUserTyping = false;
			}
		}
	};

	private boolean isUserTyping = false;

	protected KeyboardListener keyboardListener;

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_ENTER) {
			if (keyboardListener != null) {
				keyboardListener.onEnterPressed();
			}
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
		super.onTextChanged(text,start,lengthBefore,lengthAfter);
		if (this.mTypingHandler != null && this.keyboardListener != null) {
			this.mTypingHandler.removeCallbacks(mTypingTimeout);
			this.mTypingHandler.postDelayed(mTypingTimeout, Config.TYPING_TIMEOUT * 1000);
			final int length = text.length();
			if (!isUserTyping && length > 0) {
				this.isUserTyping = true;
				this.keyboardListener.onTypingStarted();
			} else if (length == 0) {
				this.isUserTyping = false;
				this.keyboardListener.onTextDeleted();
			}
		}
	}

	public void setKeyboardListener(KeyboardListener listener) {
		this.keyboardListener = listener;
		if (listener != null) {
			this.isUserTyping = false;
		}
	}

	public interface KeyboardListener {
		public void onEnterPressed();
		public void onTypingStarted();
		public void onTypingStopped();
		public void onTextDeleted();
	}

}
