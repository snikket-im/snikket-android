package de.gultsch.common;

import com.google.common.collect.ImmutableMap;
import org.junit.Assert;
import org.junit.Test;

public class MiniUriTest {

    @Test
    public void httpsUrl() {
        final var miniUri = new MiniUri("https://example.com");
        Assert.assertEquals("https", miniUri.getScheme());
        Assert.assertEquals("example.com", miniUri.getAuthority());
        Assert.assertNull(miniUri.getPath());
    }

    @Test
    public void httpsUrlHtml() {
        final var miniUri = new MiniUri("https://example.com/test.html");
        Assert.assertEquals("https", miniUri.getScheme());
        Assert.assertEquals("example.com", miniUri.getAuthority());
        Assert.assertEquals("/test.html", miniUri.getPath());
    }

    @Test
    public void httpsUrlCgiFooBar() {
        final var miniUri = new MiniUri("https://example.com/test.cgi?foo=bar");
        Assert.assertEquals("https", miniUri.getScheme());
        Assert.assertEquals("example.com", miniUri.getAuthority());
        Assert.assertEquals("/test.cgi", miniUri.getPath());
        Assert.assertEquals(ImmutableMap.of("foo", "bar"), miniUri.getParameter());
    }

    @Test
    public void xmppUri() {
        final var miniUri = new MiniUri("xmpp:user@example.com");
        Assert.assertEquals("xmpp", miniUri.getScheme());
        Assert.assertNull(miniUri.getAuthority());
        Assert.assertEquals("user@example.com", miniUri.getPath());
    }

    @Test
    public void xmppUriJoin() {
        final var miniUri = new MiniUri("xmpp:room@chat.example.com?join");
        Assert.assertEquals("xmpp", miniUri.getScheme());
        Assert.assertNull(miniUri.getAuthority());
        Assert.assertEquals("room@chat.example.com", miniUri.getPath());
        Assert.assertEquals(ImmutableMap.of("join", ""), miniUri.getParameter());
    }

    @Test
    public void xmppUriMessage() {
        final var miniUri =
                new MiniUri("xmpp:romeo@montague.net?message;body=Here%27s%20a%20test%20message");
        Assert.assertEquals("xmpp", miniUri.getScheme());
        Assert.assertNull(miniUri.getAuthority());
        Assert.assertEquals("romeo@montague.net", miniUri.getPath());
        Assert.assertEquals(
                ImmutableMap.of("message", "", "body", "Here's a test message"),
                miniUri.getParameter());
    }
}
