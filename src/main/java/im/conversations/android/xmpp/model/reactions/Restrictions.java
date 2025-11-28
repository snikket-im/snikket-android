package im.conversations.android.xmpp.model.reactions;

import com.google.common.collect.Collections2;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Reaction;
import java.util.Collection;

public record Restrictions(Integer maxReactionsPerUser, Collection<String> allowList) {

    public boolean reactionsPerUserRemaining(final Collection<Reaction> reactions) {
        if (this.maxReactionsPerUser == null) {
            return true;
        }
        final var ours = Collections2.filter(reactions, r -> !r.received).size();
        return ours < this.maxReactionsPerUser;
    }

    public static boolean reactionsPerUserRemaining(final Message message) {
        if (message.getConversation().getMode() == Conversation.MODE_MULTI
                && message.getConversation() instanceof Conversation c) {
            final var mucOptions = c.getMucOptions();
            final var restrictions = mucOptions.getReactionsRestrictions();
            return restrictions.reactionsPerUserRemaining(message.getReactions());
        } else {
            return true;
        }
    }
}
