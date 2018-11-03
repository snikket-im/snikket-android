package eu.siacs.conversations.ui.util;

import android.app.Activity;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.TypefaceSpan;
import android.view.Menu;
import android.view.MenuItem;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.MucOptions.User;
import eu.siacs.conversations.R;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.ConferenceDetailsActivity;
import eu.siacs.conversations.ui.ConversationFragment;
import eu.siacs.conversations.ui.ConversationsActivity;
import eu.siacs.conversations.ui.XmppActivity;
import rocks.xmpp.addr.Jid;


public final class MucDetailsContextMenuHelper {
    public static void configureMucDetailsContextMenu(Activity activity, Menu menu, Conversation conversation, User user) {
        final MucOptions mucOptions = conversation.getMucOptions();
        final boolean advancedMode = PreferenceManager.getDefaultSharedPreferences(activity).getBoolean("advanced_muc_mode", false);
        MenuItem sendPrivateMessage = menu.findItem(R.id.send_private_message);
        if (user != null && user.getRealJid() != null) {
            MenuItem showContactDetails = menu.findItem(R.id.action_contact_details);
            MenuItem startConversation = menu.findItem(R.id.start_conversation);
            MenuItem giveMembership = menu.findItem(R.id.give_membership);
            MenuItem removeMembership = menu.findItem(R.id.remove_membership);
            MenuItem giveAdminPrivileges = menu.findItem(R.id.give_admin_privileges);
            MenuItem removeAdminPrivileges = menu.findItem(R.id.remove_admin_privileges);
            MenuItem removeFromRoom = menu.findItem(R.id.remove_from_room);
            MenuItem banFromConference = menu.findItem(R.id.ban_from_conference);
            MenuItem invite = menu.findItem(R.id.invite);
            startConversation.setVisible(true);
            final Contact contact = user.getContact();
            final User self = conversation.getMucOptions().getSelf();
            if (contact != null && contact.showInRoster()) {
                showContactDetails.setVisible(!contact.isSelf());
            }
            if (activity instanceof ConferenceDetailsActivity && user.getRole() == MucOptions.Role.NONE) {
                invite.setVisible(true);
            }
            if (self.getAffiliation().ranks(MucOptions.Affiliation.ADMIN) &&
                    self.getAffiliation().outranks(user.getAffiliation())) {
                if (advancedMode) {
                    if (user.getAffiliation() == MucOptions.Affiliation.NONE) {
                        giveMembership.setVisible(true);
                    } else {
                        removeMembership.setVisible(true);
                    }
                    if (!Config.DISABLE_BAN) {
                        banFromConference.setVisible(true);
                    }
                } else {
                    if (!Config.DISABLE_BAN || conversation.getMucOptions().membersOnly()) {
                        removeFromRoom.setVisible(true);
                    }
                }
                if (user.getAffiliation() != MucOptions.Affiliation.ADMIN) {
                    giveAdminPrivileges.setVisible(true);
                } else {
                    removeAdminPrivileges.setVisible(true);
                }
            }
            sendPrivateMessage.setVisible(!mucOptions.isPrivateAndNonAnonymous() && mucOptions.allowPm() && user.getRole().ranks(MucOptions.Role.VISITOR));
        } else {
            sendPrivateMessage.setVisible(true);
            sendPrivateMessage.setEnabled(user != null && mucOptions.allowPm() && user.getRole().ranks(MucOptions.Role.VISITOR));
        }
    }

    public static boolean onContextItemSelected(MenuItem item, User user, Conversation conversation, XmppActivity activity) {
        final XmppConnectionService.OnAffiliationChanged onAffiliationChanged = activity instanceof XmppConnectionService.OnAffiliationChanged ? (XmppConnectionService.OnAffiliationChanged) activity : null;
        final XmppConnectionService.OnRoleChanged onRoleChanged = activity instanceof XmppConnectionService.OnRoleChanged ? (XmppConnectionService.OnRoleChanged) activity : null;
        Jid jid = user.getRealJid();
        switch (item.getItemId()) {
            case R.id.action_contact_details:
                Contact contact = user.getContact();
                if (contact != null) {
                    activity.switchToContactDetails(contact);
                }
                return true;
            case R.id.start_conversation:
                startConversation(user, conversation, activity);
                return true;
            case R.id.give_admin_privileges:
                activity.xmppConnectionService.changeAffiliationInConference(conversation, jid, MucOptions.Affiliation.ADMIN, onAffiliationChanged);
                return true;
            case R.id.give_membership:
                activity.xmppConnectionService.changeAffiliationInConference(conversation, jid, MucOptions.Affiliation.MEMBER, onAffiliationChanged);
                return true;
            case R.id.remove_membership:
                activity.xmppConnectionService.changeAffiliationInConference(conversation, jid, MucOptions.Affiliation.NONE, onAffiliationChanged);
                return true;
            case R.id.remove_admin_privileges:
                activity.xmppConnectionService.changeAffiliationInConference(conversation, jid, MucOptions.Affiliation.MEMBER, onAffiliationChanged);
                return true;
            case R.id.remove_from_room:
                removeFromRoom(user, conversation, activity, onAffiliationChanged, onRoleChanged);
                return true;
            case R.id.ban_from_conference:
                activity.xmppConnectionService.changeAffiliationInConference(conversation, jid, MucOptions.Affiliation.OUTCAST, onAffiliationChanged);
                if (user.getRole() != MucOptions.Role.NONE) {
                    activity.xmppConnectionService.changeRoleInConference(conversation, user.getName(), MucOptions.Role.NONE, onRoleChanged);
                }
                return true;
            case R.id.send_private_message:
                if (activity instanceof ConversationsActivity) {
                    ConversationFragment conversationFragment = ConversationFragment.get(activity);
                    if (conversationFragment != null) {
                        conversationFragment.privateMessageWith(user.getFullJid());
                        return true;
                    }
                }
                activity.privateMsgInMuc(conversation, user.getName());
                return true;
            case R.id.invite:
                activity.xmppConnectionService.directInvite(conversation, jid);
                return true;
            default:
                return false;
        }
    }

    public static void removeFromRoom(final User user, Conversation conversation, XmppActivity activity, XmppConnectionService.OnAffiliationChanged onAffiliationChanged, XmppConnectionService.OnRoleChanged onRoleChanged) {
        if (conversation.getMucOptions().membersOnly()) {
            activity.xmppConnectionService.changeAffiliationInConference(conversation, user.getRealJid(), MucOptions.Affiliation.NONE, onAffiliationChanged);
            if (user.getRole() != MucOptions.Role.NONE) {
                activity.xmppConnectionService.changeRoleInConference(conversation, user.getName(), MucOptions.Role.NONE, onRoleChanged);
            }
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle(R.string.ban_from_conference);
            String jid = user.getRealJid().asBareJid().toString();
            SpannableString message = new SpannableString(activity.getString(R.string.removing_from_public_conference, jid));
            int start = message.toString().indexOf(jid);
            if (start >= 0) {
                message.setSpan(new TypefaceSpan("monospace"), start, start + jid.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            builder.setMessage(message);
            builder.setNegativeButton(R.string.cancel, null);
            builder.setPositiveButton(R.string.ban_now, (dialog, which) -> {
                activity.xmppConnectionService.changeAffiliationInConference(conversation, user.getRealJid(), MucOptions.Affiliation.OUTCAST, onAffiliationChanged);
                if (user.getRole() != MucOptions.Role.NONE) {
                    activity.xmppConnectionService.changeRoleInConference(conversation, user.getName(), MucOptions.Role.NONE, onRoleChanged);
                }
            });
            builder.create().show();
        }
    }

    public static void startConversation(User user, Conversation conversation, XmppActivity activity) {
        if (user.getRealJid() != null) {
            Conversation newConversation = activity.xmppConnectionService.findOrCreateConversation(conversation.getAccount(), user.getRealJid().asBareJid(), false, true);
            activity.switchToConversation(newConversation);
        }
    }
}