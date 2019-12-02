package eu.siacs.conversations.utils;

import android.database.AbstractWindowedCursor;
import android.database.Cursor;
import android.database.CursorWindow;
import android.database.sqlite.SQLiteCursor;

public class CursorUtils {

    public static void upgradeCursorWindowSize(final Cursor cursor) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            if (cursor instanceof AbstractWindowedCursor) {
                final AbstractWindowedCursor windowedCursor = (AbstractWindowedCursor) cursor;
                windowedCursor.setWindow(new CursorWindow("8k", 8 * 1024 * 1024));
            }
            if (cursor instanceof SQLiteCursor) {
                ((SQLiteCursor) cursor).setFillWindowForwardOnly(true);
            }
        }
    }

}
