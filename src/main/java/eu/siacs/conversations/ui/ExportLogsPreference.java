package eu.siacs.conversations.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
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
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
				&& getContext().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
			return;
		}
		final Intent startIntent = new Intent(getContext(), ExportLogsService.class);
		getContext().startService(startIntent);
		super.onClick();
    }
}