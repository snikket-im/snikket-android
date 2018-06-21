package eu.siacs.conversations.ui.widget;

import android.content.Context;
import android.support.v7.widget.AppCompatEditText;
import android.util.AttributeSet;

public abstract class AbstractEditMessage extends AppCompatEditText {

    public AbstractEditMessage(Context context) {
        super(context);
    }

    public AbstractEditMessage(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
}