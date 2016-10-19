package eu.siacs.conversations.ui;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
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

	private boolean lastInputWasTab = false;

	protected KeyboardListener keyboardListener;

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent e) {
		if (keyCode == KeyEvent.KEYCODE_ENTER && !e.isShiftPressed()) {
			lastInputWasTab = false;
			if (keyboardListener != null && keyboardListener.onEnterPressed()) {
				return true;
			}
		} else if (keyCode == KeyEvent.KEYCODE_TAB && !e.isAltPressed() && !e.isCtrlPressed()) {
			if (keyboardListener != null && keyboardListener.onTabPressed(this.lastInputWasTab)) {
				lastInputWasTab = true;
				return true;
			}
		} else {
			lastInputWasTab = false;
		}
		return super.onKeyDown(keyCode, e);
	}

	@Override
	public void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
		super.onTextChanged(text,start,lengthBefore,lengthAfter);
		lastInputWasTab = false;
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
			this.keyboardListener.onTextChanged();
		}
	}

	public void setKeyboardListener(KeyboardListener listener) {
		this.keyboardListener = listener;
		if (listener != null) {
			this.isUserTyping = false;
		}
	}

	public interface KeyboardListener {
		boolean onEnterPressed();
		void onTypingStarted();
		void onTypingStopped();
		void onTextDeleted();
		void onTextChanged();
		boolean onTabPressed(boolean repeated);
	}

	private static final InputFilter SPAN_FILTER = new InputFilter() {

		@Override
		public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
			return source instanceof Spanned ? source.toString() : source;
		}
	};

	@Override
	public boolean onTextContextMenuItem(int id) {
		if (id == android.R.id.paste) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				return super.onTextContextMenuItem(android.R.id.pasteAsPlainText);
			} else {
				Editable editable = getEditableText();
				InputFilter[] filters = editable.getFilters();
				InputFilter[] tempFilters = new InputFilter[filters != null ? filters.length + 1 : 1];
				if (filters != null) {
					System.arraycopy(filters, 0, tempFilters, 1, filters.length);
				}
				tempFilters[0] = SPAN_FILTER;
				editable.setFilters(tempFilters);
				try {
					return super.onTextContextMenuItem(id);
				} finally {
					editable.setFilters(filters);
				}
			}
		} else {
			return super.onTextContextMenuItem(id);
		}
	}
}
