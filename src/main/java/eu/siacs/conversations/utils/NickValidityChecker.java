package eu.siacs.conversations.utils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;

public class NickValidityChecker {

    private static boolean check(final Conversation conversation, final String nick) {
        Jid room = conversation.getJid();
        try {
            Jid full = Jid.fromParts(room.getLocalpart(), room.getDomainpart(), nick);
            return conversation.hasMessageWithCounterpart(full)
                    || conversation.getMucOptions().findUserByFullJid(full) != null;
        } catch (InvalidJidException e) {
            return false;
        }
    }

    public static boolean check(final Conversation conversation, final List<String> nicks) {
        Set<String> previousNicks = new HashSet<>(nicks);
        for(String previousNick : previousNicks) {
            if (!NickValidityChecker.check(conversation,previousNick)) {
                return false;
            }
        }
        return true;
    }
}
