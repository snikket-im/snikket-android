package eu.siacs.conversations.entities;

import com.google.common.base.Strings;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import eu.siacs.conversations.xmpp.Jid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class Reaction {

    private static final Gson GSON = new Gson();

    public final String reaction;
    public final Jid jid;
    public final String occupantId;

    public Reaction(final String reaction, final Jid jid, final String occupantId) {
        this.reaction = reaction;
        this.jid = jid;
        this.occupantId = occupantId;
    }


    public static String toString(final Collection<Reaction> reactions) {
        return (reactions == null || reactions.isEmpty()) ? null : GSON.toJson(reactions);
    }

    public static Collection<Reaction> fromString(final String asString) {
        if ( Strings.isNullOrEmpty(asString)) {
            return Collections.emptyList();
        }
        try {
            return GSON.fromJson(asString,new TypeToken<ArrayList<Reaction>>(){}.getType());
        } catch (final JsonSyntaxException e) {
            return Collections.emptyList();
        }
    }
}
