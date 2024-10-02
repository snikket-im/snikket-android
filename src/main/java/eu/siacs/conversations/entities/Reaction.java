package eu.siacs.conversations.entities;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Ordering;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.xmpp.Jid;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Reaction {

    public static final List<String> SUGGESTIONS =
            Arrays.asList(
                    "\u2764\uFE0F",
                    "\uD83D\uDC4D",
                    "\uD83D\uDC4E",
                    "\uD83D\uDE02",
                    "\uD83D\uDE2E",
                    "\uD83D\uDE22");

    private static final Gson GSON;

    static {
        GSON = new GsonBuilder().registerTypeAdapter(Jid.class, new JidTypeAdapter()).create();
    }

    public final String reaction;
    public final boolean received;
    public final Jid from;
    public final Jid trueJid;
    public final String occupantId;

    public Reaction(
            final String reaction,
            boolean received,
            final Jid from,
            final Jid trueJid,
            final String occupantId) {
        this.reaction = reaction;
        this.received = received;
        this.from = from;
        this.trueJid = trueJid;
        this.occupantId = occupantId;
    }

    public static String toString(final Collection<Reaction> reactions) {
        return (reactions == null || reactions.isEmpty()) ? null : GSON.toJson(reactions);
    }

    public static Collection<Reaction> fromString(final String asString) {
        if (Strings.isNullOrEmpty(asString)) {
            return Collections.emptyList();
        }
        try {
            return GSON.fromJson(asString, new TypeToken<List<Reaction>>() {}.getType());
        } catch (final JsonSyntaxException e) {
            Log.e(Config.LOGTAG,"could not restore reactions", e);
            return Collections.emptyList();
        }
    }

    public static Collection<Reaction> withOccupantId(
            final Collection<Reaction> existing,
            final Collection<String> reactions,
            final boolean received,
            final Jid from,
            final Jid trueJid,
            final String occupantId) {
        final ImmutableList.Builder<Reaction> builder = new ImmutableList.Builder<>();
        builder.addAll(Collections2.filter(existing, e -> !occupantId.equals(e.occupantId)));
        builder.addAll(
                Collections2.transform(
                        reactions, r -> new Reaction(r, received, from, trueJid, occupantId)));
        return builder.build();
    }

    @NonNull
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("reaction", reaction)
                .add("received", received)
                .add("from", from)
                .add("trueJid", trueJid)
                .add("occupantId", occupantId)
                .toString();
    }

    public static Collection<Reaction> withFrom(
            final Collection<Reaction> existing,
            final Collection<String> reactions,
            final boolean received,
            final Jid from) {
        final ImmutableList.Builder<Reaction> builder = new ImmutableList.Builder<>();
        builder.addAll(
                Collections2.filter(existing, e -> !from.asBareJid().equals(e.from.asBareJid())));
        builder.addAll(
                Collections2.transform(
                        reactions, r -> new Reaction(r, received, from, null, null)));
        return builder.build();
    }

    private static class JidTypeAdapter extends TypeAdapter<Jid> {
        @Override
        public void write(final JsonWriter out, final Jid value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.toEscapedString());
            }
        }

        @Override
        public Jid read(final JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            } else if (in.peek() == JsonToken.STRING) {
                final String value = in.nextString();
                return Jid.ofEscaped(value);
            }
            throw new IOException("Unexpected token");
        }
    }

    public static Aggregated aggregated(final Collection<Reaction> reactions) {
        final Map<String, Integer> aggregatedReactions =
                Maps.transformValues(
                        Multimaps.index(reactions, r -> r.reaction).asMap(), Collection::size);
        final List<Map.Entry<String, Integer>> sortedList =
                Ordering.from(
                                Comparator.comparingInt(
                                        (Map.Entry<String, Integer> o) -> o.getValue()))
                        .reverse()
                        .immutableSortedCopy(aggregatedReactions.entrySet());
        return new Aggregated(
                sortedList,
                ImmutableSet.copyOf(
                        Collections2.transform(
                                Collections2.filter(reactions, r -> !r.received),
                                r -> r.reaction)));
    }

    public static final class Aggregated {

        public final List<Map.Entry<String, Integer>> reactions;
        public final Set<String> ourReactions;

        private Aggregated(
                final List<Map.Entry<String, Integer>> reactions, Set<String> ourReactions) {
            this.reactions = reactions;
            this.ourReactions = ourReactions;
        }
    }
}
