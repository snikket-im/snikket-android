package eu.siacs.conversations.ui.widget;

import android.content.Context;
import android.support.text.emoji.widget.EmojiAppCompatEditText;
import android.util.AttributeSet;

public abstract class AbstractEditMessage extends EmojiAppCompatEditText {

    public AbstractEditMessage(Context context) {
        super(context);
    }

    public AbstractEditMessage(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

}