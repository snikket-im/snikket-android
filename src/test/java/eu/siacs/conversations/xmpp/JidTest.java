package eu.siacs.conversations.xmpp;

import org.junit.Test;

public class JidTest {

    @Test
    public void testDoubleDash() {
        Jid.ofUserInput("user@a--z.com");
    }

    @Test
    public void testUnicode() {
        Jid.ofUserInput("test@գծոոոց.հայ");
    }
}
