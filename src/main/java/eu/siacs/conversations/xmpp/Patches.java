package eu.siacs.conversations.xmpp;


import java.util.Arrays;
import java.util.List;

public class Patches {
    public static final List<String> DISCO_EXCEPTIONS = Arrays.asList(
            "nimbuzz.com"
    );
    public static final List<XmppConnection.Identity> BAD_MUC_REFLECTION = Arrays.asList(
            XmppConnection.Identity.SLACK
    );
}
