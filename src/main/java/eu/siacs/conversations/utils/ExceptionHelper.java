package eu.siacs.conversations.utils;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.preference.PreferenceManager;
import android.text.format.DateUtils;
import android.util.Log;

public class ExceptionHelper {
	public static void init(Context context) {
		if (!(Thread.getDefaultUncaughtExceptionHandler() instanceof ExceptionHandler)) {
			Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler(
					context));
		}
	}

	public static void checkForCrash(Context context,
			final XmppConnectionService service) {
		try {
			final SharedPreferences preferences = PreferenceManager
					.getDefaultSharedPreferences(context);
			boolean neverSend = preferences.getBoolean("never_send", false);
			if (neverSend) {
				return;
			}
			List<Account> accounts = service.getAccounts();
			Account account = null;
			for (int i = 0; i < accounts.size(); ++i) {
				if (!accounts.get(i).isOptionSet(Account.OPTION_DISABLED)) {
					account = accounts.get(i);
					break;
				}
			}
			if (account == null) {
				return;
			}
			final Account finalAccount = account;
			FileInputStream file = context.openFileInput("stacktrace.txt");
			InputStreamReader inputStreamReader = new InputStreamReader(file);
			BufferedReader stacktrace = new BufferedReader(inputStreamReader);
			final StringBuilder report = new StringBuilder();
			PackageManager pm = context.getPackageManager();
			PackageInfo packageInfo = null;
			try {
				packageInfo = pm.getPackageInfo(context.getPackageName(), 0);
				report.append("Version: " + packageInfo.versionName + '\n');
				report.append("Last Update: "
						+ DateUtils.formatDateTime(context,
								packageInfo.lastUpdateTime,
								DateUtils.FORMAT_SHOW_TIME
										| DateUtils.FORMAT_SHOW_DATE) + '\n');
			} catch (NameNotFoundException e) {
			}
			String line;
			while ((line = stacktrace.readLine()) != null) {
				report.append(line);
				report.append('\n');
			}
			file.close();
			context.deleteFile("stacktrace.txt");
			AlertDialog.Builder builder = new AlertDialog.Builder(context);
			builder.setTitle(context.getString(R.string.crash_report_title));
			builder.setMessage(context.getText(R.string.crash_report_message));
			builder.setPositiveButton(context.getText(R.string.send_now),
					new OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {

							Log.d(Config.LOGTAG, "using account="
									+ finalAccount.getJid().toBareJid()
									+ " to send in stack trace");
                            Conversation conversation = null;
                            try {
                                conversation = service.findOrCreateConversation(finalAccount,
                                        Jid.fromString("bugs@siacs.eu"), false);
                            } catch (final InvalidJidException ignored) {
                            }
                            Message message = new Message(conversation, report
									.toString(), Message.ENCRYPTION_NONE);
							service.sendMessage(message);
						}
					});
			builder.setNegativeButton(context.getText(R.string.send_never),
					new OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {
							preferences.edit().putBoolean("never_send", true)
									.apply();
						}
					});
			builder.create().show();
		} catch (final IOException ignored) {
        }

	}
}
