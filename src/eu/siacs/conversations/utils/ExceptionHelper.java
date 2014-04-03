package eu.siacs.conversations.utils;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.preference.PreferenceManager;
import android.util.Log;

public class ExceptionHelper {
	public static void init(Context context) {
		if(!(Thread.getDefaultUncaughtExceptionHandler() instanceof ExceptionHandler)) {
		    Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler(context));
		}
	}
	
	public static void checkForCrash(Context context, final XmppConnectionService service) {
		try {
			final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
			boolean neverSend = preferences.getBoolean("never_send",false);
			if (neverSend) {
				return;
			}
			FileInputStream file = context.openFileInput("stacktrace.txt");
			InputStreamReader inputStreamReader = new InputStreamReader(
                    file);
            BufferedReader bufferedReader = new BufferedReader(
                    inputStreamReader);
            final StringBuilder stacktrace = new StringBuilder();
            String line;
            while((line = bufferedReader.readLine()) != null) {
            	stacktrace.append(line);
            	stacktrace.append('\n');
            }
            file.close();
            context.deleteFile("stacktrace.txt");
			AlertDialog.Builder builder = new AlertDialog.Builder(context);
			builder.setTitle(context.getString(R.string.crash_report_title));
			builder.setMessage(context.getText(R.string.crash_report_message));
			builder.setPositiveButton(context.getText(R.string.send_now), new OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					List<Account> accounts = service.getAccounts();
					Account account = null;
					for(int i = 0; i < accounts.size(); ++i) {
						if (!accounts.get(i).isOptionSet(Account.OPTION_DISABLED)) {
							account = accounts.get(i);
							break;
						}
					}
					if (account!=null) {
						Log.d("xmppService","using account="+account.getJid()+" to send in stack trace");
						Conversation conversation = service.findOrCreateConversation(account, "bugs@siacs.eu", false);
						Message message = new Message(conversation, stacktrace.toString(), Message.ENCRYPTION_NONE);
						service.sendMessage(message, null);
					}
				}
			});
			builder.setNegativeButton(context.getText(R.string.send_never),new OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					preferences.edit().putBoolean("never_send", true).commit();
				}
			});
			builder.create().show();
		} catch (FileNotFoundException e) {
			return;
		} catch (IOException e) {
			return;
		}
		
	}
}
