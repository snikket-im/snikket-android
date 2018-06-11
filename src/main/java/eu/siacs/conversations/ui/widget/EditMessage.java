package eu.siacs.conversations.ui.widget;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.text.emoji.widget.EmojiAppCompatEditText;
import android.support.v13.view.inputmethod.EditorInfoCompat;
import android.support.v13.view.inputmethod.InputConnectionCompat;
import android.support.v13.view.inputmethod.InputContentInfoCompat;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;

public class EditMessage extends EmojiAppCompatEditText {

	private static final InputFilter SPAN_FILTER = (source, start, end, dest, dstart, dend) -> source instanceof Spanned ? source.toString() : source;
	protected Handler mTypingHandler = new Handler();
	protected KeyboardListener keyboardListener;
	private OnCommitContentListener mCommitContentListener = null;
	private String[] mimeTypes = null;
	private boolean isUserTyping = false;
	protected Runnable mTypingTimeout = new Runnable() {
		@Override
		public void run() {
			if (isUserTyping && keyboardListener != null) {
				keyboardListener.onTypingStopped();
				isUserTyping = false;
			}
		}
	};
	private boolean lastInputWasTab = false;

	public EditMessage(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public EditMessage(Context context) {
		super(context);
	}

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
	public int getAutofillType() {
		return AUTOFILL_TYPE_NONE;
	}

	@Override
	public void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
		super.onTextChanged(text, start, lengthBefore, lengthAfter);
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

	public void setRichContentListener(String[] mimeTypes, OnCommitContentListener listener) {
		this.mimeTypes = mimeTypes;
		this.mCommitContentListener = listener;
	}

	public void insertAsQuote(String text) {
		text = text.replaceAll("(\n *){2,}", "\n").replaceAll("(^|\n)", "$1> ").replaceAll("\n$", "");
		Editable editable = getEditableText();
		int position = getSelectionEnd();
		if (position == -1) position = editable.length();
		if (position > 0 && editable.charAt(position - 1) != '\n') {
			editable.insert(position++, "\n");
		}
		editable.insert(position, text);
		position += text.length();
		editable.insert(position++, "\n");
		if (position < editable.length() && editable.charAt(position) != '\n') {
			editable.insert(position, "\n");
		}
		setSelection(position);
	}

	@Override
	public InputConnection onCreateInputConnection(EditorInfo editorInfo) {
		final InputConnection ic = super.onCreateInputConnection(editorInfo);

		if (mimeTypes != null && mCommitContentListener != null) {
			EditorInfoCompat.setContentMimeTypes(editorInfo, mimeTypes);
			return InputConnectionCompat.createWrapper(ic, editorInfo, (inputContentInfo, flags, opts) -> EditMessage.this.mCommitContentListener.onCommitContent(inputContentInfo, flags, opts, mimeTypes));
		} else {
			return ic;
		}
	}

	public void refreshIme() {
		SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(getContext());
		final boolean usingEnterKey = p.getBoolean("display_enter_key", getResources().getBoolean(R.bool.display_enter_key));
		final boolean enterIsSend = p.getBoolean("enter_is_send", getResources().getBoolean(R.bool.enter_is_send));

		if (usingEnterKey && enterIsSend) {
			setInputType(getInputType() & (~InputType.TYPE_TEXT_FLAG_MULTI_LINE));
			setInputType(getInputType() & (~InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE));
		} else if (usingEnterKey) {
			setInputType(getInputType() | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
			setInputType(getInputType() & (~InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE));
		} else {
			setInputType(getInputType() | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
			setInputType(getInputType() | InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE);
		}
	}

	public interface OnCommitContentListener {
		boolean onCommitContent(InputContentInfoCompat inputContentInfo, int flags, Bundle opts, String[] mimeTypes);
	}

	public interface KeyboardListener {
		boolean onEnterPressed();

		void onTypingStarted();

		void onTypingStopped();

		void onTextDeleted();

		void onTextChanged();

		boolean onTabPressed(boolean repeated);
	}
}
