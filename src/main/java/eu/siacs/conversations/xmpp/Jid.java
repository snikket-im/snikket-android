package eu.siacs.conversations.xmpp;

import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Domainpart;
import org.jxmpp.jid.parts.Localpart;
import org.jxmpp.jid.parts.Resourcepart;
import org.jxmpp.stringprep.XmppStringprepException;

import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface Jid extends Comparable<Jid>, Serializable, CharSequence {

    Pattern JID = Pattern.compile("^((.*?)@)?([^/@]+)(/(.*))?$");

    static Jid of(CharSequence local, CharSequence domain, CharSequence resource) {
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
            return new WrappedJid(JidCreate.entityFullFrom(
                    Localpart.fromUnescaped(local.toString()),
                    Domainpart.from(domain.toString()),
                    Resourcepart.from(resource.toString())
            ));
        } catch (XmppStringprepException e) {
            throw new IllegalArgumentException(e);
        }
    }

    static Jid ofEscaped(CharSequence local, CharSequence domain, CharSequence resource) {
        try {
            if (resource == null) {
                return new WrappedJid(
                        JidCreate.bareFrom(
                                Localpart.from(local.toString()),
                                Domainpart.from(domain.toString())
                        )
                );
            }
            return new WrappedJid(JidCreate.entityFullFrom(
                    Localpart.from(local.toString()),
                    Domainpart.from(domain.toString()),
                    Resourcepart.from(resource.toString())
            ));
        } catch (XmppStringprepException e) {
            throw new IllegalArgumentException(e);
        }
    }


    static Jid ofDomain(CharSequence domain) {
        try {
            return new WrappedJid(JidCreate.domainBareFrom(domain));
        } catch (XmppStringprepException e) {
            throw new IllegalArgumentException(e);
        }
    }

    static Jid ofLocalAndDomain(CharSequence local, CharSequence domain) {
        try {
            return new WrappedJid(
                    JidCreate.bareFrom(
                            Localpart.fromUnescaped(local.toString()),
                            Domainpart.from(domain.toString())
                    )
            );
        } catch (XmppStringprepException e) {
            throw new IllegalArgumentException(e);
        }
    }

    static Jid ofDomainAndResource(CharSequence domain, CharSequence resource) {
        try {
            return new WrappedJid(
                    JidCreate.domainFullFrom(
                            Domainpart.from(domain.toString()),
                            Resourcepart.from(resource.toString())
                    ));
        } catch (XmppStringprepException e) {
            throw new IllegalArgumentException(e);
        }
    }

    static Jid ofLocalAndDomainEscaped(CharSequence local, CharSequence domain) {
        try {
            return new WrappedJid(
                    JidCreate.bareFrom(
                            Localpart.from(local.toString()),
                            Domainpart.from(domain.toString())
                    )
            );
        } catch (XmppStringprepException e) {
            throw new IllegalArgumentException(e);
        }
    }

    static Jid of(CharSequence jid) {
        if (jid instanceof Jid) {
            return (Jid) jid;
        }
        Matcher matcher = JID.matcher(jid);
        if (matcher.matches()) {
            return of(matcher.group(2), matcher.group(3), matcher.group(5));
        } else {
            throw new IllegalArgumentException("Could not parse JID: " + jid);
        }
    }

    static Jid ofEscaped(CharSequence jid) {
        try {
            return new WrappedJid(JidCreate.from(jid));
        } catch (final XmppStringprepException e) {
            throw new IllegalArgumentException(e);
        }
    }

    boolean isFullJid();

    boolean isBareJid();

    boolean isDomainJid();

    Jid asBareJid();

    Jid withResource(CharSequence resource);

    String getLocal();

    String getEscapedLocal();

    Jid getDomain();

    String getResource();

    String toEscapedString();
}
