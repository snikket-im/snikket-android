package eu.siacs.conversations.ui.widget;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.Selection;
import android.text.Spannable;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ListSelectionManager {

    private static final int MESSAGE_SEND_RESET = 1;
    private static final int MESSAGE_RESET = 2;
    private static final int MESSAGE_START_SELECTION = 3;
    private static final Field FIELD_EDITOR;
    private static final Method METHOD_START_SELECTION;
    private static final boolean SUPPORTED;
    private static final Handler HANDLER = new Handler(Looper.getMainLooper(), new Handler.Callback() {

        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_SEND_RESET: {
                    // Skip one more message queue loop
                    HANDLER.obtainMessage(MESSAGE_RESET, msg.obj).sendToTarget();
                    return true;
                }
                case MESSAGE_RESET: {
                    final ListSelectionManager listSelectionManager = (ListSelectionManager) msg.obj;
                    listSelectionManager.futureSelectionIdentifier = null;
                    return true;
                }
                case MESSAGE_START_SELECTION: {
                    final StartSelectionHolder holder = (StartSelectionHolder) msg.obj;
                    holder.listSelectionManager.futureSelectionIdentifier = null;
                    startSelection(holder.textView, holder.start, holder.end);
                    return true;
                }
            }
            return false;
        }
    });

    static {
        Field editor;
        try {
            editor = TextView.class.getDeclaredField("mEditor");
            editor.setAccessible(true);
        } catch (Exception e) {
            editor = null;
        }
        FIELD_EDITOR = editor;
        Method startSelection = null;
        if (editor != null) {
            String[] startSelectionNames = {"startSelectionActionMode", "startSelectionActionModeWithSelection"};
            for (String startSelectionName : startSelectionNames) {
                try {
                    startSelection = editor.getType().getDeclaredMethod(startSelectionName);
                    startSelection.setAccessible(true);
                    break;
                } catch (Exception e) {
                    startSelection = null;
                }
            }
        }
        METHOD_START_SELECTION = startSelection;
        SUPPORTED = FIELD_EDITOR != null && METHOD_START_SELECTION != null;
    }

    private ActionMode selectionActionMode;
    private Object selectionIdentifier;
    private TextView selectionTextView;
    private Object futureSelectionIdentifier;
    private int futureSelectionStart;
    private int futureSelectionEnd;

    public static boolean isSupported() {
        return SUPPORTED;
    }

    private static void startSelection(TextView textView, int start, int end) {
        final CharSequence text = textView.getText();
        if (SUPPORTED && start >= 0 && end > start && textView.isTextSelectable() && text instanceof Spannable) {
            final Spannable spannable = (Spannable) text;
            start = Math.min(start, spannable.length());
            end = Math.min(end, spannable.length());
            Selection.setSelection(spannable, start, end);
            try {
                final Object editor = FIELD_EDITOR != null ? FIELD_EDITOR.get(textView) : textView;
                METHOD_START_SELECTION.invoke(editor);
            } catch (Exception e) {
            }
        }
    }

    public void onCreate(TextView textView, ActionMode.Callback additionalCallback) {
        final CustomCallback callback = new CustomCallback(textView, additionalCallback);
        textView.setCustomSelectionActionModeCallback(callback);
    }

    public void onUpdate(TextView textView, Object identifier) {
        if (SUPPORTED) {
            final ActionMode.Callback callback = textView.getCustomSelectionActionModeCallback();
            if (callback instanceof CustomCallback) {
                final CustomCallback customCallback = (CustomCallback) textView.getCustomSelectionActionModeCallback();
                customCallback.identifier = identifier;
                if (futureSelectionIdentifier == identifier) {
                    HANDLER.obtainMessage(MESSAGE_START_SELECTION, new StartSelectionHolder(this,
                            textView, futureSelectionStart, futureSelectionEnd)).sendToTarget();
                }
            }
        }
    }

    public void onBeforeNotifyDataSetChanged() {
        if (SUPPORTED) {
            HANDLER.removeMessages(MESSAGE_SEND_RESET);
            HANDLER.removeMessages(MESSAGE_RESET);
            HANDLER.removeMessages(MESSAGE_START_SELECTION);
            if (selectionActionMode != null) {
                final CharSequence text = selectionTextView.getText();
                futureSelectionIdentifier = selectionIdentifier;
                futureSelectionStart = Selection.getSelectionStart(text);
                futureSelectionEnd = Selection.getSelectionEnd(text);
                selectionActionMode.finish();
                selectionActionMode = null;
                selectionIdentifier = null;
                selectionTextView = null;
            }
        }
    }

    public void onAfterNotifyDataSetChanged() {
        if (SUPPORTED && futureSelectionIdentifier != null) {
            HANDLER.obtainMessage(MESSAGE_SEND_RESET, this).sendToTarget();
        }
    }

    private static class StartSelectionHolder {

        final ListSelectionManager listSelectionManager;
        final TextView textView;
        public final int start;
        public final int end;

        StartSelectionHolder(ListSelectionManager listSelectionManager, TextView textView,
                                    int start, int end) {
            this.listSelectionManager = listSelectionManager;
            this.textView = textView;
            this.start = start;
            this.end = end;
        }
    }

    private class CustomCallback implements ActionMode.Callback {

        private final TextView textView;
        private final ActionMode.Callback additionalCallback;
        Object identifier;

        CustomCallback(TextView textView, ActionMode.Callback additionalCallback) {
            this.textView = textView;
            this.additionalCallback = additionalCallback;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            selectionActionMode = mode;
            selectionIdentifier = identifier;
            selectionTextView = textView;
            if (additionalCallback != null) {
                additionalCallback.onCreateActionMode(mode, menu);
            }
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            if (additionalCallback != null) {
                additionalCallback.onPrepareActionMode(mode, menu);
            }
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            if (additionalCallback != null && additionalCallback.onActionItemClicked(mode, item)) {
                return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            if (additionalCallback != null) {
                additionalCallback.onDestroyActionMode(mode);
            }
            if (selectionActionMode == mode) {
                selectionActionMode = null;
                selectionIdentifier = null;
                selectionTextView = null;
            }
        }
    }
}