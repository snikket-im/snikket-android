package eu.siacs.conversations.utils;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.ui.ConversationsActivity;
import eu.siacs.conversations.ui.EnterPhoneNumberActivity;
import eu.siacs.conversations.ui.StartConversationActivity;
import eu.siacs.conversations.ui.VerifyActivity;

public class SignupUtils {

    public static Intent getSignUpIntent(Activity activity) {
        final Intent intent = new Intent(activity, EnterPhoneNumberActivity.class);
        return intent;
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
            intent = getSignUpIntent(activity);

        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return intent;
    }
}