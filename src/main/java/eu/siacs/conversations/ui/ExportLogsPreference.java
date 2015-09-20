package eu.siacs.conversations.ui;

import android.content.Context;
import android.content.Intent;
import android.preference.Preference;
import android.util.AttributeSet;

import eu.siacs.conversations.services.ExportLogsService;

public class ExportLogsPreference extends Preference {

    public ExportLogsPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ExportLogsPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ExportLogsPreference(Context context) {
        super(context);
    }

    protected void onClick() {
		final Intent startIntent = new Intent(getContext(), ExportLogsService.class);
		getContext().startService(startIntent);
		super.onClick();
    }
}