package eu.siacs.conversations.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.work.WorkManager;

import com.google.common.base.Strings;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.ui.fragment.settings.BackupSettingsFragment;

public class WorkManagerEventReceiver extends BroadcastReceiver {

    public static final String ACTION_STOP_BACKUP = "eu.siacs.conversations.receiver.STOP_BACKUP";

    @Override
    public void onReceive(final Context context, final Intent intent) {
        final var action = Strings.nullToEmpty(intent == null ? null : intent.getAction());
        if (action.equals(ACTION_STOP_BACKUP)) {
            stopBackup(context);
        }
    }

    private void stopBackup(final Context context) {
        Log.d(Config.LOGTAG, "trying to stop one-off backup worker");
        final var workManager = WorkManager.getInstance(context);
        workManager.cancelUniqueWork(BackupSettingsFragment.CREATE_ONE_OFF_BACKUP);
    }
}
