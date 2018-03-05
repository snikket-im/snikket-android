package eu.siacs.conversations.utils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import eu.siacs.conversations.entities.Conversation;
import rocks.xmpp.addr.Jid;

public class NickValidityChecker {

    private static boolean check(final Conversation conversation, final String nick) {
        Jid room = conversation.getJid();
        try {
            Jid full = Jid.of(room.getLocal(), room.getDomain(), nick);
            return conversation.hasMessageWithCounterpart(full)
                    || conversation.getMucOptions().findUserByFullJid(full) != null;
        } catch (IllegalArgumentException e) {
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
