package eu.siacs.conversations.utils;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.ui.ConversationsActivity;
import eu.siacs.conversations.ui.EnterPhoneNumberActivity;
import eu.siacs.conversations.ui.StartConversationActivity;
import eu.siacs.conversations.ui.TosActivity;
import eu.siacs.conversations.ui.VerifyActivity;

public class SignupUtils {

    public static Intent getSignUpIntent(Activity activity, boolean ignored) {
        return getSignUpIntent(activity);
    }

    public static Intent getSignUpIntent(Activity activity) {
        return new Intent(activity, EnterPhoneNumberActivity.class);
    }

    public static Intent getRedirectionIntent(ConversationsActivity activity) {
        final Intent intent;
        final Account account = AccountUtils.getFirst(activity.xmppConnectionService);
        if (account != null) {
            if (account.isOptionSet(Account.OPTION_UNVERIFIED)) {
                intent = new Intent(activity, VerifyActivity.class);
            } else {
                intent = new Intent(activity, StartConversationActivity.class);
            }
        } else {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
            if (preferences.getBoolean("tos",false)) {
                intent = getSignUpIntent(activity);
            } else {
                intent = new Intent(activity, TosActivity.class);
            }

        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return intent;
    }
}