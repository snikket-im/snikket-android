package eu.siacs.conversations.xmpp.pep;

import android.os.Bundle;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.xmpp.model.stanza.Iq;

public class PublishOptions {

    private PublishOptions() {}

    public static Bundle openAccess() {
        final Bundle options = new Bundle();
        options.putString("pubsub#access_model", "open");
        return options;
    }

    public static boolean preconditionNotMet(Iq response) {
        final Element error =
                response.getType() == Iq.Type.ERROR ? response.findChild("error") : null;
        return error != null && error.hasChild("precondition-not-met", Namespace.PUB_SUB_ERROR);
    }
}
