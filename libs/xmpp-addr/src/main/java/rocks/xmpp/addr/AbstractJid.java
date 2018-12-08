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

import java.text.Collator;
import java.util.Objects;

/**
 * Abstract Jid implementation for both full and bare JIDs.
 *
 * @author Christian Schudt
 */
abstract class AbstractJid implements Jid {

    /**
     * Checks if the JID is a full JID.
     * <blockquote>
     * <p>The term "full JID" refers to an XMPP address of the form &lt;localpart@domainpart/resourcepart&gt; (for a particular authorized client or device associated with an account) or of the form &lt;domainpart/resourcepart&gt; (for a particular resource or script associated with a server).</p>
     * </blockquote>
     *
     * @return True, if the JID is a full JID; otherwise false.
     */
    @Override
    public final boolean isFullJid() {
        return getResource() != null;
    }

    /**
     * Checks if the JID is a bare JID.
     * <blockquote>
     * <p>The term "bare JID" refers to an XMPP address of the form &lt;localpart@domainpart&gt; (for an account at a server) or of the form &lt;domainpart&gt; (for a server).</p>
     * </blockquote>
     *
     * @return True, if the JID is a bare JID; otherwise false.
     */
    @Override
    public final boolean isBareJid() {
        return getResource() == null;
    }

    @Override
    public final boolean isDomainJid() {
        return getLocal() == null;
    }

    @Override
    public final boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Jid)) {
            return false;
        }
        Jid other = (Jid) o;

        return Objects.equals(getLocal(), other.getLocal())
                && Objects.equals(getDomain(), other.getDomain())
                && Objects.equals(getResource(), other.getResource());
    }

    @Override
    public final int hashCode() {
        return Objects.hash(getLocal(), getDomain(), getResource());
    }

    /**
     * Compares this JID with another JID. First domain parts are compared. If these are equal, local parts are compared
     * and if these are equal, too, resource parts are compared.
     *
     * @param o The other JID.
     * @return The comparison result.
     */
    @Override
    public final int compareTo(Jid o) {

        if (this == o) {
            return 0;
        }

        if (o != null) {
            final Collator collator = Collator.getInstance();
            int result;
            // First compare domain parts.
            if (getDomain() != null) {
                result = o.getDomain() != null ? collator.compare(getDomain(), o.getDomain()) : -1;
            } else {
                result = o.getDomain() != null ? 1 : 0;
            }
            // If the domains are equal, compare local parts.
            if (result == 0) {
                if (getLocal() != null) {
                    // If this local part is not null, but the other is null, move this down (1).
                    result = o.getLocal() != null ? collator.compare(getLocal(), o.getLocal()) : 1;
                } else {
                    // If this local part is null, but the other is not, move this up (-1).
                    result = o.getLocal() != null ? -1 : 0;
                }
            }
            // If the local parts are equal, compare resource parts.
            if (result == 0) {
                if (getResource() != null) {
                    // If this resource part is not null, but the other is null, move this down (1).
                    return o.getResource() != null ? collator.compare(getResource(), o.getResource()) : 1;
                } else {
                    // If this resource part is null, but the other is not, move this up (-1).
                    return o.getResource() != null ? -1 : 0;
                }
            }
            return result;
        } else {
            return -1;
        }
    }

    @Override
    public final int length() {
        return toString().length();
    }

    @Override
    public final char charAt(int index) {
        return toString().charAt(index);
    }

    @Override
    public final CharSequence subSequence(int start, int end) {
        return toString().subSequence(start, end);
    }

    /**
     * Returns the JID in its string representation, i.e. [ localpart "@" ] domainpart [ "/" resourcepart ].
     *
     * @return The JID.
     * @see #toEscapedString()
     */
    @Override
    public final String toString() {
        return toString(getLocal(), getDomain(), getResource());
    }

    @Override
    public final String toEscapedString() {
        return toString(getEscapedLocal(), getDomain(), getResource());
    }

    static String toString(String local, String domain, String resource) {
        StringBuilder sb = new StringBuilder();
        if (local != null) {
            sb.append(local).append('@');
        }
        sb.append(domain);
        if (resource != null) {
            sb.append('/').append(resource);
        }
        return sb.toString();
    }
}
