/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Christian Schudt
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package rocks.xmpp.addr;

import rocks.xmpp.precis.PrecisProfile;
import rocks.xmpp.precis.PrecisProfiles;
import rocks.xmpp.util.cache.LruCache;

import java.net.IDN;
import java.nio.charset.Charset;
import java.text.Normalizer;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The implementation of the JID as described in <a href="https://tools.ietf.org/html/rfc7622">Extensible Messaging and Presence Protocol (XMPP): Address Format</a>.
 * <p>
 * This class is thread-safe and immutable.
 *
 * @author Christian Schudt
 * @see <a href="https://tools.ietf.org/html/rfc7622">RFC 7622 - Extensible Messaging and Presence Protocol (XMPP): Address Format</a>
 */
final class FullJid extends AbstractJid {

    /**
     * Escapes all disallowed characters and also backslash, when followed by a defined hex code for escaping. See 4. Business Rules.
     */
    private static final Pattern ESCAPE_PATTERN = Pattern.compile("[ \"&'/:<>@]|\\\\(?=20|22|26|27|2f|3a|3c|3e|40|5c)");

    private static final Pattern UNESCAPE_PATTERN = Pattern.compile("\\\\(20|22|26|27|2f|3a|3c|3e|40|5c)");

    private static final Pattern JID = Pattern.compile("^((.*?)@)?([^/@]+)(/(.*))?$");

    private static final IDNProfile IDN_PROFILE = new IDNProfile();

    /**
     * Whenever dots are used as label separators, the following characters MUST be recognized as dots: U+002E (full stop), U+3002 (ideographic full stop), U+FF0E (fullwidth full stop), U+FF61 (halfwidth ideographic full stop).
     */
    private static final String DOTS = "[.\u3002\uFF0E\uFF61]";

    /**
     * Label separators for domain labels, which should be mapped to "." (dot): IDEOGRAPHIC FULL STOP character (U+3002)
     */
    private static final Pattern LABEL_SEPARATOR = Pattern.compile(DOTS);

    private static final Pattern LABEL_SEPARATOR_FINAL = Pattern.compile(DOTS + "$");

    /**
     * Caches the escaped JIDs.
     */
    private static final Map<CharSequence, Jid> ESCAPED_CACHE = new LruCache<>(5000);

    /**
     * Caches the unescaped JIDs.
     */
    private static final Map<CharSequence, Jid> UNESCAPED_CACHE = new LruCache<>(5000);

    private static final long serialVersionUID = -3824234106101731424L;

    private final String escapedLocal;

    private final String local;

    private final String domain;

    private final String resource;

    private final Jid bareJid;

    /**
     * Creates a full JID with local, domain and resource part.
     *
     * @param local    The local part.
     * @param domain   The domain part.
     * @param resource The resource part.
     */
    FullJid(CharSequence local, CharSequence domain, CharSequence resource) {
        this(local, domain, resource, false, null);
    }

    private FullJid(final CharSequence local, final CharSequence domain, final CharSequence resource, final boolean doUnescape, Jid bareJid) {
        final String enforcedLocalPart;
        final String enforcedDomainPart;
        final String enforcedResource;

        final String unescapedLocalPart;

        if (domain == null) {
            throw new NullPointerException();
        }

        if (doUnescape) {
            unescapedLocalPart = unescape(local);
        } else {
            unescapedLocalPart = local != null ? local.toString() : null;
        }

        // Escape the local part, so that disallowed characters like the space characters pass the UsernameCaseMapped profile.
        final String escapedLocalPart = escape(unescapedLocalPart);

        // If the domainpart includes a final character considered to be a label
        // separator (dot) by [RFC1034], this character MUST be stripped from
        // the domainpart before the JID of which it is a part is used for the
        // purpose of routing an XML stanza, comparing against another JID, or
        // constructing an XMPP URI or IRI [RFC5122].  In particular, such a
        // character MUST be stripped before any other canonicalization steps
        // are taken.
        // Also validate, that the domain name can be converted to ASCII, i.e. validate the domain name (e.g. must not start with "_").
        final String strDomain = IDN.toASCII(LABEL_SEPARATOR_FINAL.matcher(domain).replaceAll(""), IDN.USE_STD3_ASCII_RULES);
        enforcedLocalPart = escapedLocalPart != null ? PrecisProfiles.USERNAME_CASE_MAPPED.enforce(escapedLocalPart) : null;
        enforcedResource = resource != null ? PrecisProfiles.OPAQUE_STRING.enforce(resource) : null;
        // See https://tools.ietf.org/html/rfc5895#section-2
        enforcedDomainPart = IDN_PROFILE.enforce(strDomain);

        validateLength(enforcedLocalPart, "local");
        validateLength(enforcedResource, "resource");
        validateDomain(strDomain);

        this.local = unescape(enforcedLocalPart);
        this.escapedLocal = enforcedLocalPart;
        this.domain = enforcedDomainPart;
        this.resource = enforcedResource;
        if (bareJid != null) {
            this.bareJid = bareJid;
        } else {
            this.bareJid = isBareJid() ? this : new AbstractJid() {

                @Override
                public Jid asBareJid() {
                    return this;
                }

                @Override
                public Jid withLocal(CharSequence local) {
                    if (local == this.getLocal() || local != null && local.equals(this.getLocal())) {
                        return this;
                    }
                    return new FullJid(local, getDomain(), getResource(), false, null);
                }

                @Override
                public Jid withResource(CharSequence resource) {
                    if (resource == this.getResource() || resource != null && resource.equals(this.getResource())) {
                        return this;
                    }
                    return new FullJid(getLocal(), getDomain(), resource, false, asBareJid());
                }

                @Override
                public Jid atSubdomain(CharSequence subdomain) {
                    if (subdomain == null) {
                        throw new NullPointerException();
                    }
                    return new FullJid(getLocal(), subdomain + "." + getDomain(), getResource(), false, null);
                }

                @Override
                public String getLocal() {
                    return FullJid.this.getLocal();
                }

                @Override
                public String getEscapedLocal() {
                    return FullJid.this.getEscapedLocal();
                }

                @Override
                public String getDomain() {
                    return FullJid.this.getDomain();
                }

                @Override
                public String getResource() {
                    return null;
                }
            };
        }
    }

    /**
     * Creates a JID from a string. The format must be
     * <blockquote><p>[ localpart "@" ] domainpart [ "/" resourcepart ]</p></blockquote>
     *
     * @param jid        The JID.
     * @param doUnescape If the jid parameter will be unescaped.
     * @return The JID.
     * @throws NullPointerException     If the jid is null.
     * @throws IllegalArgumentException If the jid could not be parsed or is not valid.
     * @see <a href="https://xmpp.org/extensions/xep-0106.html">XEP-0106: JID Escaping</a>
     */
    static Jid of(String jid, final boolean doUnescape) {
        if (jid == null) {
            throw new NullPointerException("jid must not be null.");
        }

        jid = jid.trim();

        if (jid.isEmpty()) {
            throw new IllegalArgumentException("jid must not be empty.");
        }

        Jid result;
        if (doUnescape) {
            result = UNESCAPED_CACHE.get(jid);
        } else {
            result = ESCAPED_CACHE.get(jid);
        }

        if (result != null) {
            return result;
        }

        Matcher matcher = JID.matcher(jid);
        if (matcher.matches()) {
            Jid jidValue = new FullJid(matcher.group(2), matcher.group(3), matcher.group(5), doUnescape, null);
            if (doUnescape) {
                UNESCAPED_CACHE.put(jid, jidValue);
            } else {
                ESCAPED_CACHE.put(jid, jidValue);
            }
            return jidValue;
        } else {
            throw new IllegalArgumentException("Could not parse JID: " + jid);
        }
    }

    /**
     * Escapes a local part. The characters {@code "&'/:<>@} (+ whitespace) are replaced with their respective escape characters.
     *
     * @param localPart The local part.
     * @return The escaped local part or null.
     * @see <a href="https://xmpp.org/extensions/xep-0106.html">XEP-0106: JID Escaping</a>
     */
    private static String escape(final CharSequence localPart) {
        if (localPart != null) {
            final Matcher matcher = ESCAPE_PATTERN.matcher(localPart);
            final StringBuffer sb = new StringBuffer();
            while (matcher.find()) {
                matcher.appendReplacement(sb, "\\\\" + Integer.toHexString(matcher.group().charAt(0)));
            }
            matcher.appendTail(sb);
            return sb.toString();
        }
        return null;
    }

    private static String unescape(final CharSequence localPart) {
        if (localPart != null) {
            final Matcher matcher = UNESCAPE_PATTERN.matcher(localPart);
            final StringBuffer sb = new StringBuffer();
            while (matcher.find()) {
                final char c = (char) Integer.parseInt(matcher.group(1), 16);
                if (c == '\\') {
                    matcher.appendReplacement(sb, "\\\\");
                } else {
                    matcher.appendReplacement(sb, String.valueOf(c));
                }
            }
            matcher.appendTail(sb);
            return sb.toString();
        }
        return null;
    }

    private static void validateDomain(String domain) {
        if (domain == null) {
            throw new NullPointerException("domain must not be null.");
        }
        if (domain.contains("@")) {
            // Prevent misuse of API.
            throw new IllegalArgumentException("domain must not contain a '@' sign");
        }
        validateLength(domain, "domain");
    }

    /**
     * Validates that the length of a local, domain or resource part is not longer than 1023 characters.
     *
     * @param value The value.
     * @param part  The part, only used to produce an exception message.
     */
    private static void validateLength(CharSequence value, CharSequence part) {
        if (value != null) {
            if (value.length() == 0) {
                throw new IllegalArgumentException(part + " must not be empty.");
            }
            if (value.toString().getBytes(Charset.forName("UTF-8")).length > 1023) {
                throw new IllegalArgumentException(part + " must not be greater than 1023 bytes.");
            }
        }
    }

    /**
     * Converts this JID into a bare JID, i.e. removes the resource part.
     * <blockquote>
     * <p>The term "bare JID" refers to an XMPP address of the form &lt;localpart@domainpart&gt; (for an account at a server) or of the form &lt;domainpart&gt; (for a server).</p>
     * </blockquote>
     *
     * @return The bare JID.
     * @see #withResource(CharSequence)
     */
    @Override
    public final Jid asBareJid() {
        return bareJid;
    }

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
     */
    @Override
    public final String getLocal() {
        return local;
    }

    @Override
    public final String getEscapedLocal() {
        return escapedLocal;
    }

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
    @Override
    public final String getDomain() {
        return domain;
    }

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
    @Override
    public final String getResource() {
        return resource;
    }

    /**
     * Creates a new JID with a new local part and the same domain and resource part of the current JID.
     *
     * @param local The local part.
     * @return The JID with a new local part.
     * @throws IllegalArgumentException If the local is not a valid local part.
     * @see #withResource(CharSequence)
     */
    @Override
    public final Jid withLocal(CharSequence local) {
        if (local == this.getLocal() || local != null && local.equals(this.getLocal())) {
            return this;
        }
        return new FullJid(local, getDomain(), getResource(), false, null);
    }

    /**
     * Creates a new full JID with a resource and the same local and domain part of the current JID.
     *
     * @param resource The resource.
     * @return The full JID with a resource.
     * @throws IllegalArgumentException If the resource is not a valid resource part.
     * @see #asBareJid()
     * @see #withLocal(CharSequence)
     */
    @Override
    public final Jid withResource(CharSequence resource) {
        if (resource == this.getResource() || resource != null && resource.equals(this.getResource())) {
            return this;
        }
        return new FullJid(getLocal(), getDomain(), resource, false, asBareJid());
    }

    /**
     * Creates a new JID at a subdomain and at the same domain as this JID.
     *
     * @param subdomain The subdomain.
     * @return The JID at a subdomain.
     * @throws NullPointerException     If subdomain is null.
     * @throws IllegalArgumentException If subdomain is not a valid subdomain name.
     */
    @Override
    public final Jid atSubdomain(CharSequence subdomain) {
        if (subdomain != null) {
            throw new NullPointerException();
        }
        return new FullJid(getLocal(), subdomain + "." + getDomain(), getResource(), false, null);
    }

    /**
     * A profile for applying the rules for IDN as in RFC 5895. Although IDN doesn't use Precis, it's still very similar so that we can use the base class.
     *
     * @see <a href="https://tools.ietf.org/html/rfc5895#section-2">RFC 5895</a>
     */
    private static final class IDNProfile extends PrecisProfile {

        private IDNProfile() {
            super(false);
        }

        @Override
        public String prepare(CharSequence input) {
            return IDN.toUnicode(input.toString(), IDN.USE_STD3_ASCII_RULES);
        }

        @Override
        public String enforce(CharSequence input) {
            // 4. Map IDEOGRAPHIC FULL STOP character (U+3002) to dot.
            return applyAdditionalMappingRule(
                    // 3.  All characters are mapped using Unicode Normalization Form C (NFC).
                    applyNormalizationRule(
                            // 2. Fullwidth and halfwidth characters (those defined with
                            // Decomposition Types <wide> and <narrow>) are mapped to their
                            // decomposition mappings
                            applyWidthMappingRule(
                                    // 1. Uppercase characters are mapped to their lowercase equivalents
                                    applyCaseMappingRule(prepare(input))))).toString();
        }

        @Override
        protected CharSequence applyWidthMappingRule(CharSequence charSequence) {
            return widthMap(charSequence);
        }

        @Override
        protected CharSequence applyAdditionalMappingRule(CharSequence charSequence) {
            return LABEL_SEPARATOR.matcher(charSequence).replaceAll(".");
        }

        @Override
        protected CharSequence applyCaseMappingRule(CharSequence charSequence) {
            return charSequence.toString().toLowerCase();
        }

        @Override
        protected CharSequence applyNormalizationRule(CharSequence charSequence) {
            return Normalizer.normalize(charSequence, Normalizer.Form.NFC);
        }

        @Override
        protected CharSequence applyDirectionalityRule(CharSequence charSequence) {
            return charSequence;
        }
    }
}
