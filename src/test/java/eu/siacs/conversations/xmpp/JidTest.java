package eu.siacs.conversations.xmpp;

import org.junit.Assert;
import org.junit.Test;

public class JidTest {

    @Test(expected = IllegalArgumentException.class)
    public void invalidDomain() {
        Jid.ofUserInput("test@something invalid.tld");
    }

    @Test
    public void ipAndResource() {
        Jid.ofUserInput("test@127.0.0.1/home");
    }

    @Test
    public void testDoubleDash() {
        Jid.ofUserInput("user@a--z.com");
    }

    @Test
    public void testUnicode() {
        Jid.ofUserInput("test@գծոոոց.հայ");
    }

    @Test
    public void testPunyCode() {
        final var jid = Jid.ofUserInput("Test@xn--kxae4bafwg.xn--pxaix.gr");
        Assert.assertEquals("test@ουτοπία.δπθ.gr", jid.toString());
    }

    @Test
    public void testPunyCodeDomain() {
        final var jid = Jid.ofUserInput("xn--kxae4bafwg.xn--pxaix.gr");
        Assert.assertEquals("ουτοπία.δπθ.gr", jid.toString());
    }

    @Test
    public void testPunyCodeResource() {
        final var jid = Jid.ofUserInput("Test@xn--kxae4bafwg.xn--pxaix.gr/foo");
        Assert.assertEquals("test@ουτοπία.δπθ.gr/foo", jid.toString());
    }
}
