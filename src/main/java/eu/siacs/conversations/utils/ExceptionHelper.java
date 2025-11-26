package eu.siacs.conversations.utils;

import android.content.Context;
import android.util.Log;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.base.Charsets;
import com.google.common.io.CharSink;
import com.google.common.io.Files;

import eu.siacs.conversations.AppSettings;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.XmppActivity;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class ExceptionHelper {

    private static final String FILENAME = "stacktrace.txt";

    public static void init(final Context context) {
        if (Thread.getDefaultUncaughtExceptionHandler() instanceof ExceptionHandler) {
            return;
        }
        Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler(context));
    }

    public static boolean checkForCrash(final XmppActivity activity) {
        final XmppConnectionService service =
                activity == null ? null : activity.xmppConnectionService;
        if (service == null) {
            return false;
        }
        final AppSettings appSettings = new AppSettings(activity);
        if (!appSettings.isSendCrashReports() || Config.BUG_REPORTS == null) {
            return false;
        }
        final Account account = AccountUtils.getFirstEnabled(service);
        if (account == null) {
            return false;
        }
        final var file = new File(activity.getCacheDir(), FILENAME);
        if (!file.exists()) {
            return false;
        }
        final String report;
        try {
            report = Files.asCharSource(file, Charsets.UTF_8).read();
        } catch (final IOException e) {
            return false;
        }
        if (file.delete()) {
            Log.d(Config.LOGTAG, "deleted crash report file");
        }
        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
        builder.setTitle(
                activity.getString(
                        R.string.crash_report_title, activity.getString(R.string.app_name)));
        builder.setMessage(
                activity.getString(
                        R.string.crash_report_message, activity.getString(R.string.app_name)));
        builder.setPositiveButton(
                activity.getText(R.string.send_now),
                (dialog, which) -> {
                    Log.d(
                            Config.LOGTAG,
                            "using account="
                                    + account.getJid().asBareJid()
                                    + " to send in stack trace");
                    Conversation conversation =
                            service.findOrCreateConversation(
                                    account, Config.BUG_REPORTS, false, true);
                    Message message = new Message(conversation, report, Message.ENCRYPTION_NONE);
                    service.sendMessage(message);
                });
        builder.setNegativeButton(
                activity.getText(R.string.send_never),
                (dialog, which) -> appSettings.setSendCrashReports(false));
        builder.create().show();
        return true;
    }

    static void writeToStacktraceFile(final Context context, final String msg) {
        try {
            Files.asCharSink(new File(context.getCacheDir(), FILENAME), Charsets.UTF_8).write(msg);
        } catch (IOException e) {
            Log.w(Config.LOGTAG, "could not write stack trace to file", e);
        }
    }
}
