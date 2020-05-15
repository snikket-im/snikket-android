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

    /**
     * Creates a bare JID with only the domain part, e.g. <code>capulet.com</code>
     *
     * @param domain The domain.
     * @return The JID.
     * @throws NullPointerException     If the domain is null.
     * @throws IllegalArgumentException If the domain or local part are not valid.
     */
    static Jid ofDomain(CharSequence domain) {
        try {
            return new WrappedJid(JidCreate.domainBareFrom(domain));
        } catch (XmppStringprepException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Creates a bare JID with a local and domain part, e.g. <code>juliet@capulet.com</code>
     *
     * @param local  The local part.
     * @param domain The domain.
     * @return The JID.
     * @throws NullPointerException     If the domain is null.
     * @throws IllegalArgumentException If the domain or local part are not valid.
     */
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

    /**
     * Creates a JID from an unescaped string. The format must be
     * <blockquote><p>[ localpart "@" ] domainpart [ "/" resourcepart ]</p></blockquote>
     * The input string will be escaped.
     *
     * @param jid The JID.
     * @return The JID.
     * @throws NullPointerException     If the jid is null.
     * @throws IllegalArgumentException If the jid could not be parsed or is not valid.
     * @see <a href="https://xmpp.org/extensions/xep-0106.html">XEP-0106: JID Escaping</a>
     */
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

    /**
     * Creates a JID from a escaped JID string. The format must be
     * <blockquote><p>[ localpart "@" ] domainpart [ "/" resourcepart ]</p></blockquote>
     * This method should be used, when parsing JIDs from the XMPP stream.
     *
     * @param jid The JID.
     * @return The JID.
     * @throws NullPointerException     If the jid is null.
     * @throws IllegalArgumentException If the jid could not be parsed or is not valid.
     * @see <a href="https://xmpp.org/extensions/xep-0106.html">XEP-0106: JID Escaping</a>
     */
    static Jid ofEscaped(CharSequence jid) {
        try {
            return new WrappedJid(JidCreate.from(jid));
        } catch (XmppStringprepException e) {
            e.printStackTrace();
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Checks if the JID is a full JID.
     * <blockquote>
     * <p>The term "full JID" refers to an XMPP address of the form &lt;localpart@domainpart/resourcepart&gt; (for a particular authorized client or device associated with an account) or of the form &lt;domainpart/resourcepart&gt; (for a particular resource or script associated with a server).</p>
     * </blockquote>
     *
     * @return True, if the JID is a full JID; otherwise false.
     */
    boolean isFullJid();

    /**
     * Checks if the JID is a bare JID.
     * <blockquote>
     * <p>The term "bare JID" refers to an XMPP address of the form &lt;localpart@domainpart&gt; (for an account at a server) or of the form &lt;domainpart&gt; (for a server).</p>
     * </blockquote>
     *
     * @return True, if the JID is a bare JID; otherwise false.
     */
    boolean isBareJid();

    /**
     * Checks if the JID is a domain JID, i.e. if it has no local part.
     *
     * @return True, if the JID is a domain JID, i.e. if it has no local part.
     */
    boolean isDomainJid();

    /**
     * Gets the bare JID representation of this JID, i.e. removes the resource part.
     * <blockquote>
     * <p>The term "bare JID" refers to an XMPP address of the form &lt;localpart@domainpart&gt; (for an account at a server) or of the form &lt;domainpart&gt; (for a server).</p>
     * </blockquote>
     *
     * @return The bare JID.
     * @see #withResource(CharSequence)
     */
    Jid asBareJid();

    /**
     * Creates a new full JID with a resource and the same local and domain part of the current JID.
     *
     * @param resource The resource.
     * @return The full JID with a resource.
     * @throws IllegalArgumentException If the resource is not a valid resource part.
     * @see #asBareJid()
     */
    Jid withResource(CharSequence resource);


    /**
     * Gets the local part of the JID, also known as the name or node.
     * <blockquote>
     * <p><cite><a href="https://tools.ietf.org/html/rfc7622#section-3.3">3.3.  Localpart</a></cite></p>
     * <p>The localpart of a JID is an optional identifier placed before the
     * domainpart and separated from the latter by the '@' character.
     * Typically, a localpart uniquely identifies the entity requesting and
     * using network access provided by a server (i.e., a local account),
     * although it can also represent other kinds of entities (e.g., a
     * chatroom associated with a multi-user chat service [XEP-0045]).  The
     * entity represented by an XMPP localpart is addressed within the
     * context of a specific domain (i.e., &lt;localpart@domainpart&gt;).</p>
     * </blockquote>
     *
     * @return The local part or null.
     * @see #getEscapedLocal()
     */
    String getLocal();

    /**
     * Gets the escaped local part of the JID.
     *
     * @return The escaped local part or null.
     * @see #getLocal()
     * @since 0.8.0
     */
    String getEscapedLocal();

    /**
     * Gets the domain part.
     * <blockquote>
     * <p><cite><a href="https://tools.ietf.org/html/rfc7622#section-3.2">3.2.  Domainpart</a></cite></p>
     * <p>The domainpart is the primary identifier and is the only REQUIRED
     * element of a JID (a mere domainpart is a valid JID).  Typically,
     * a domainpart identifies the "home" server to which clients connect
     * for XML routing and data management functionality.</p>
     * </blockquote>
     *
     * @return The domain part.
     */
    String getDomain();

    /**
     * Gets the resource part.
     * <blockquote>
     * <p><cite><a href="https://tools.ietf.org/html/rfc7622#section-3.4">3.4.  Resourcepart</a></cite></p>
     * <p>The resourcepart of a JID is an optional identifier placed after the
     * domainpart and separated from the latter by the '/' character.  A
     * resourcepart can modify either a &lt;localpart@domainpart&gt; address or a
     * mere &lt;domainpart&gt; address.  Typically, a resourcepart uniquely
     * identifies a specific connection (e.g., a device or location) or
     * object (e.g., an occupant in a multi-user chatroom [XEP-0045])
     * belonging to the entity associated with an XMPP localpart at a domain
     * (i.e., &lt;localpart@domainpart/resourcepart&gt;).</p>
     * </blockquote>
     *
     * @return The resource part or null.
     */
    String getResource();

    /**
     * Returns the JID in escaped form as described in <a href="https://xmpp.org/extensions/xep-0106.html">XEP-0106: JID Escaping</a>.
     *
     * @return The escaped JID.
     * @see #toString()
     */
    String toEscapedString();
}
