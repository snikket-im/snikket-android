package eu.siacs.conversations.ui.widget;

import android.text.Layout;
import android.text.Spannable;
import android.text.method.ArrowKeyMovementMethod;
import android.text.style.ClickableSpan;
import android.view.MotionEvent;
import android.widget.TextView;

public class ClickableMovementMethod extends ArrowKeyMovementMethod {

	@Override
	public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
		// Just copied from android.text.method.LinkMovementMethod
		if (event.getAction() == MotionEvent.ACTION_UP) {
			int x = (int) event.getX();
			int y = (int) event.getY();
			x -= widget.getTotalPaddingLeft();
			y -= widget.getTotalPaddingTop();
			x += widget.getScrollX();
			y += widget.getScrollY();
			Layout layout = widget.getLayout();
			int line = layout.getLineForVertical(y);
			int off = layout.getOffsetForHorizontal(line, x);
			ClickableSpan[] link = buffer.getSpans(off, off, ClickableSpan.class);
			if (link.length != 0) {
				link[0].onClick(widget);
				return true;
			}
		}
		return super.onTouchEvent(widget, buffer, event);
	}

	public static ClickableMovementMethod getInstance() {
		if (sInstance == null) {
			sInstance = new ClickableMovementMethod();
		}
		return sInstance;
	}

	private static ClickableMovementMethod sInstance;
}