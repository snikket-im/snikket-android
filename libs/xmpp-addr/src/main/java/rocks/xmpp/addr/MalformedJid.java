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

/**
 * Represents a malformed JID in order to handle the <code>jid-malformed</code> error.
 * <p>
 * This class is not intended to be publicly instantiable, but is used for malformed JIDs during parsing automatically.
 *
 * @author Christian Schudt
 * @see <a href="https://xmpp.org/rfcs/rfc6120.html#stanzas-error-conditions-jid-malformed">RFC 6120, 8.3.3.8.  jid-malformed</a>
 */
public final class MalformedJid extends AbstractJid {

    private static final long serialVersionUID = -2896737611021417985L;

    private final String localPart;

    private final String domainPart;

    private final String resourcePart;

    private final Throwable cause;

    static MalformedJid of(final String jid, final Throwable cause) {
        // Do some basic parsing without any further checks or validation.
        final StringBuilder sb = new StringBuilder(jid);
        // 1.  Remove any portion from the first '/' character to the end of the
        // string (if there is a '/' character present).
        final int indexOfResourceDelimiter = jid.indexOf('/');
        final String resourcePart;
        if (indexOfResourceDelimiter > -1) {
            resourcePart = sb.substring(indexOfResourceDelimiter + 1);
            sb.delete(indexOfResourceDelimiter, sb.length());
        } else {
            resourcePart = null;
        }
        // 2.  Remove any portion from the beginning of the string to the first
        // '@' character (if there is an '@' character present).
        final int indexOfAt = jid.indexOf('@');
        final String localPart;
        if (indexOfAt > -1) {
            localPart = sb.substring(0, indexOfAt);
            sb.delete(0, indexOfAt + 1);
        } else {
            localPart = null;
        }
        return new MalformedJid(localPart, sb.toString(), resourcePart, cause);
    }

    private MalformedJid(final String localPart, final String domainPart, final String resourcePart, final Throwable cause) {
        this.localPart = localPart;
        this.domainPart = domainPart;
        this.resourcePart = resourcePart;
        this.cause = cause;
    }

    @Override
    public final Jid asBareJid() {
        return new MalformedJid(localPart, domainPart, null, cause);
    }

    @Override
    public Jid withLocal(CharSequence local) {
        return new MalformedJid(local.toString(), domainPart, resourcePart, cause);
    }

    @Override
    public Jid withResource(CharSequence resource) {
        return new MalformedJid(localPart, domainPart, resource.toString(), cause);
    }

    @Override
    public Jid atSubdomain(CharSequence subdomain) {
        if (subdomain == null) {
            throw new NullPointerException();
        }
        return new MalformedJid(localPart, subdomain + "." + domainPart, resourcePart, cause);
    }

    @Override
    public final String getLocal() {
        return localPart;
    }

    @Override
    public final String getEscapedLocal() {
        return localPart;
    }

    @Override
    public final String getDomain() {
        return domainPart;
    }

    @Override
    public final String getResource() {
        return resourcePart;
    }

    /**
     * Gets the cause why the JID is malformed.
     *
     * @return The cause.
     */
    public final Throwable getCause() {
        return cause;
    }
}
