package eu.siacs.conversations.xmpp;

import androidx.annotation.NonNull;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import eu.siacs.conversations.utils.IP;
import im.conversations.android.xmpp.model.stanza.Stanza;
import java.io.Serializable;
import java.net.IDN;
import java.util.regex.Pattern;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Domainpart;
import org.jxmpp.jid.parts.Localpart;
import org.jxmpp.jid.parts.Resourcepart;
import org.jxmpp.stringprep.XmppStringprepException;

public abstract class Jid implements Comparable<Jid>, Serializable, CharSequence {

    private static final Pattern HOSTNAME_PATTERN =
            Pattern.compile(
                    "^(?=.{1,253}$)(?!-)[\\p{L}\\p{N}](?:[\\p{L}\\p{N}-]{0,61}[\\p{L}\\p{N}])?(?:\\.(?!-)[\\p{L}\\p{N}](?:[\\p{L}\\p{N}-]{0,61}[\\p{L}\\p{N}])?)*\\.?$");

    public static Jid of(
            final CharSequence local, final CharSequence domain, final CharSequence resource) {
        if (local == null) {
            if (resource == null) {
                return ofDomain(domain);
            } else {
                return ofDomainAndResource(domain, resource);
            }
        }
        if (resource == null) {
            return ofLocalAndDomain(local, domain);
        }
        try {
            return new InternalRepresentation(
                    JidCreate.entityFullFrom(
                            Localpart.from(local.toString()),
                            Domainpart.from(domain.toString()),
                            Resourcepart.from(resource.toString())));
        } catch (final XmppStringprepException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static Jid ofDomain(final CharSequence domain) {
        try {
            return new InternalRepresentation(JidCreate.domainBareFrom(domain));
        } catch (final XmppStringprepException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static Jid ofLocalAndDomain(final CharSequence local, final CharSequence domain) {
        try {
            return new InternalRepresentation(
                    JidCreate.bareFrom(
                            Localpart.from(local.toString()), Domainpart.from(domain.toString())));
        } catch (final XmppStringprepException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static Jid ofDomainAndResource(CharSequence domain, CharSequence resource) {
        try {
            return new InternalRepresentation(
                    JidCreate.domainFullFrom(
                            Domainpart.from(domain.toString()),
                            Resourcepart.from(resource.toString())));
        } catch (final XmppStringprepException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static Jid of(final CharSequence input) {
        if (input instanceof Jid jid) {
            return jid;
        }
        try {
            return new InternalRepresentation(JidCreate.from(input));
        } catch (final XmppStringprepException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static Jid ofUserInput(final CharSequence input) {
        final var jid = of(input);
        final var domain = jid.getDomain().toString();
        if (domain.isEmpty()) {
            throw new IllegalArgumentException("Domain can not be empty");
        }
        if (HOSTNAME_PATTERN.matcher(domain).matches()) {
            final Jid bare;
            if (jid.isDomainJid()) {
                bare = Jid.ofDomain(IDN.toUnicode(domain));
            } else {
                bare = Jid.ofLocalAndDomain(jid.getLocal(), IDN.toUnicode(domain));
            }
            return jid.isBareJid() ? bare : bare.withResource(jid.getResource());
        } else if (IP.matches(domain)) {
            return jid;
        }
        throw new IllegalArgumentException("Invalid hostname");
    }

    public static Jid ofOrInvalid(final String input) {
        return ofOrInvalid(input, false);
    }

    /**
     * @param jid a string representation of the jid to parse
     * @param fallback indicates whether an attempt should be made to parse a bare version of the
     *     jid
     * @return an instance of Jid; may be Jid.Invalid
     */
    public static Jid ofOrInvalid(final String jid, final boolean fallback) {
        try {
            return Jid.of(jid);
        } catch (final IllegalArgumentException e) {
            return Jid.invalidOf(jid, fallback);
        }
    }

    private static Jid invalidOf(final String jid, boolean fallback) {
        final int pos = jid.indexOf('/');
        if (fallback && pos >= 0 && jid.length() >= pos + 1) {
            if (jid.substring(pos + 1).trim().isEmpty()) {
                return Jid.of(jid.substring(0, pos));
            }
        }
        return new Invalid(jid);
    }

    public abstract boolean isFullJid();

    public abstract boolean isBareJid();

    public abstract boolean isDomainJid();

    public abstract Jid asBareJid();

    public abstract Jid withResource(CharSequence resource);

    public abstract String getLocal();

    public abstract Jid getDomain();

    public abstract String getResource();

    private static class InternalRepresentation extends Jid {
        private final org.jxmpp.jid.Jid inner;

        private InternalRepresentation(final org.jxmpp.jid.Jid inner) {
            this.inner = inner;
        }

        @Override
        public boolean isFullJid() {
            return inner.isEntityFullJid() || inner.isDomainFullJid();
        }

        @Override
        public boolean isBareJid() {
            return inner.isDomainBareJid() || inner.isEntityBareJid();
        }

        @Override
        public boolean isDomainJid() {
            return inner.isDomainBareJid() || inner.isDomainFullJid();
        }

        @Override
        public Jid asBareJid() {
            return new InternalRepresentation(inner.asBareJid());
        }

        @Override
        public Jid withResource(CharSequence resource) {
            final Localpart localpart = inner.getLocalpartOrNull();
            try {
                final Resourcepart resourcepart = Resourcepart.from(resource.toString());
                if (localpart == null) {
                    return new InternalRepresentation(
                            JidCreate.domainFullFrom(inner.getDomain(), resourcepart));
                } else {
                    return new InternalRepresentation(
                            JidCreate.fullFrom(localpart, inner.getDomain(), resourcepart));
                }
            } catch (XmppStringprepException e) {
                throw new IllegalArgumentException(e);
            }
        }

        @Override
        public String getLocal() {
            final Localpart localpart = inner.getLocalpartOrNull();
            return localpart == null ? null : localpart.toString();
        }

        @Override
        public Jid getDomain() {
            return new InternalRepresentation(inner.asDomainBareJid());
        }

        @Override
        public String getResource() {
            final Resourcepart resourcepart = inner.getResourceOrNull();
            return resourcepart == null ? null : resourcepart.toString();
        }

        @NonNull
        @Override
        public String toString() {
            return inner.toString();
        }

        @Override
        public int length() {
            return inner.length();
        }

        @Override
        public char charAt(int i) {
            return inner.charAt(i);
        }

        @NonNull
        @Override
        public CharSequence subSequence(int i, int i1) {
            return inner.subSequence(i, i1);
        }

        @Override
        public int compareTo(Jid jid) {
            if (jid instanceof InternalRepresentation) {
                return inner.compareTo(((InternalRepresentation) jid).inner);
            } else {
                return 0;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            InternalRepresentation that = (InternalRepresentation) o;
            return inner.equals(that.inner);
        }

        @Override
        public int hashCode() {
            return inner.hashCode();
        }
    }

    public static class Invalid extends Jid {

        private final String value;

        private Invalid(final String jid) {
            this.value = jid;
        }

        @Override
        @NonNull
        public String toString() {
            return value;
        }

        @Override
        public boolean isFullJid() {
            throw new AssertionError("Not implemented");
        }

        @Override
        public boolean isBareJid() {
            throw new AssertionError("Not implemented");
        }

        @Override
        public boolean isDomainJid() {
            throw new AssertionError("Not implemented");
        }

        @Override
        public Jid asBareJid() {
            final var bare = Iterables.getFirst(Splitter.on('/').split(value), null);
            if (bare == null) {
                return null;
            }
            try {
                return Jid.of(bare).asBareJid();
            } catch (final IllegalArgumentException e) {
                return this;
            }
        }

        @Override
        public Jid withResource(CharSequence charSequence) {
            throw new AssertionError("Not implemented");
        }

        @Override
        public String getLocal() {
            throw new AssertionError("Not implemented");
        }

        @Override
        public Jid getDomain() {
            throw new AssertionError("Not implemented");
        }

        @Override
        public String getResource() {
            throw new AssertionError("Not implemented");
        }

        @Override
        public int length() {
            return value.length();
        }

        @Override
        public char charAt(int index) {
            return value.charAt(index);
        }

        @NonNull
        @Override
        public CharSequence subSequence(int start, int end) {
            return value.subSequence(start, end);
        }

        @Override
        public int compareTo(@NonNull Jid o) {
            throw new AssertionError("Not implemented");
        }

        public static Jid getNullForInvalid(final Jid jid) {
            if (jid instanceof Invalid) {
                return null;
            } else {
                return jid;
            }
        }

        public static boolean isValid(Jid jid) {
            return !(jid instanceof Invalid);
        }

        public static boolean hasValidFrom(final Stanza stanza) {
            final String from = stanza.getAttribute("from");
            if (from == null) {
                return false;
            }
            try {
                Jid.of(from);
                return true;
            } catch (final IllegalArgumentException e) {
                return false;
            }
        }
    }
}
