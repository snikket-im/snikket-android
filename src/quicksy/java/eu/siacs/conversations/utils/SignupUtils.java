package eu.siacs.conversations.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;
import android.util.Log;
import com.google.common.collect.Iterables;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.Conversations;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.ui.ConversationsActivity;
import eu.siacs.conversations.ui.EnterPhoneNumberActivity;
import eu.siacs.conversations.ui.TosActivity;
import eu.siacs.conversations.ui.VerifyActivity;
import eu.siacs.conversations.xmpp.Jid;

public class SignupUtils {

    public static Intent getSignUpIntent(Activity activity, boolean ignored) {
        return getSignUpIntent(activity);
    }

    public static Intent getSignUpIntent(final Context context) {
        final var preferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (preferences.getBoolean("tos", false)) {
            return new Intent(context, EnterPhoneNumberActivity.class);
        } else {
            return new Intent(context, TosActivity.class);
        }
    }

    public static Intent getRedirectionIntent(final Context context) {
        final var accounts = Conversations.getInstance(context).getAccounts();
        Log.d(Config.LOGTAG, "getRedirection intent " + accounts);
        final Intent intent;
        final var account = Iterables.getFirst(accounts, null);
        if (account != null) {
            if (account.isOptionSet(Account.OPTION_UNVERIFIED)) {
                intent = new Intent(context, VerifyActivity.class);
            } else {
                intent = new Intent(context, ConversationsActivity.class);
            }
        } else {
            intent = getSignUpIntent(context);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return intent;
    }

    public static boolean isSupportTokenRegistry() {
        return false;
    }

    public static Intent getTokenRegistrationIntent(Activity activity, Jid preset, String key) {
        return null;
    }
}
