package eu.siacs.conversations.utils;

import android.app.Activity;
import android.content.Intent;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.ConversationsActivity;
import eu.siacs.conversations.ui.EditAccountActivity;
import eu.siacs.conversations.ui.ManageAccountActivity;
import eu.siacs.conversations.ui.PickServerActivity;
import eu.siacs.conversations.ui.StartConversationActivity;
import eu.siacs.conversations.ui.WelcomeActivity;

public class SignupUtils {

    public static Intent getSignUpIntent(final Activity activity) {
        return getSignUpIntent(activity, false);
    }

    public static Intent getSignUpIntent(final Activity activity, final boolean toServerChooser) {
        Intent intent;
        if (toServerChooser) {
            intent = new Intent(activity, PickServerActivity.class);
        } else {
            intent = new Intent(activity, WelcomeActivity.class);
        }
        StartConversationActivity.addInviteUri(intent, activity.getIntent());
        return intent;
    }

    public static Intent getRedirectionIntent(final ConversationsActivity activity) {
        final XmppConnectionService service = activity.xmppConnectionService;
        Account pendingAccount = AccountUtils.getPendingAccount(service);
        Intent intent;
        if (pendingAccount != null) {
            intent = new Intent(activity, EditAccountActivity.class);
            intent.putExtra("jid", pendingAccount.getJid().asBareJid().toString());
            if (!pendingAccount.isOptionSet(Account.OPTION_MAGIC_CREATE)) {
                intent.putExtra(EditAccountActivity.EXTRA_FORCE_REGISTER, pendingAccount.isOptionSet(Account.OPTION_REGISTER));
            }
        } else {
            if (service.getAccounts().size() == 0) {
                if (Config.X509_VERIFICATION) {
                    intent = new Intent(activity, ManageAccountActivity.class);
                } else if (Config.MAGIC_CREATE_DOMAIN != null) {
                    intent = getSignUpIntent(activity);
                } else {
                    intent = new Intent(activity, EditAccountActivity.class);
                }
            } else {
                intent = new Intent(activity, StartConversationActivity.class);
            }
        }
        intent.putExtra("init", true);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return intent;
    }
}