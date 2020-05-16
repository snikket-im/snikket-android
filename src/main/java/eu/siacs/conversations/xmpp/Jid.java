package eu.siacs.conversations.xmpp;

import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Domainpart;
import org.jxmpp.jid.parts.Localpart;
import org.jxmpp.jid.parts.Resourcepart;
import org.jxmpp.stringprep.XmppStringprepException;

import java.io.Serializable;

public interface Jid extends Comparable<Jid>, Serializable, CharSequence {

    static Jid of(CharSequence local, CharSequence domain, CharSequence resource) {
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
        try {
            return new WrappedJid(JidCreate.fromUnescaped(jid));
        } catch (XmppStringprepException e) {
            throw new IllegalArgumentException(e);
        }
    }

    static Jid ofEscaped(CharSequence jid) {
        try {
            return new WrappedJid(JidCreate.from(jid));
        } catch (XmppStringprepException e) {
            e.printStackTrace();
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

    String getDomain();

    String getResource();

    String toEscapedString();
}
