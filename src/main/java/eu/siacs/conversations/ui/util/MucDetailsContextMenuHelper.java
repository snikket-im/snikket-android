package eu.siacs.conversations.ui.util;

import android.support.v7.app.AlertDialog;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.MucOptions.User;
import eu.siacs.conversations.R;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.XmppActivity;
import rocks.xmpp.addr.Jid;


public final class MucDetailsContextMenuHelper {
    public static void configureMucDetailsContextMenu(Menu menu, Conversation conversation, User user, boolean advancedMode) {
        if (user != null) {
            if (user.getRealJid() != null) {
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
                if (user.getRole() == MucOptions.Role.NONE) {
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
            } else {
                MenuItem sendPrivateMessage = menu.findItem(R.id.send_private_message);
                sendPrivateMessage.setVisible(true);
                sendPrivateMessage.setEnabled(user.getRole().ranks(MucOptions.Role.VISITOR));
            }
        } else {
            MenuItem sendPrivateMessage = menu.findItem(R.id.send_private_message);
            sendPrivateMessage.setVisible(true);
            sendPrivateMessage.setEnabled(false);
        }
    }

    public static boolean onContextItemSelected(MenuItem item, User user, Conversation conversation, XmppActivity activity, XmppConnectionService.OnAffiliationChanged onAffiliationChanged, XmppConnectionService.OnRoleChanged onRoleChanged) {
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
                if (conversation.getMucOptions().allowPm()) {
                    activity.privateMsgInMuc(conversation, user.getName());
                } else {
                    Toast.makeText(activity, R.string.private_messages_are_disabled, Toast.LENGTH_SHORT).show();
                }
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
            builder.setMessage(activity.getString(R.string.removing_from_public_conference, user.getName()));
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