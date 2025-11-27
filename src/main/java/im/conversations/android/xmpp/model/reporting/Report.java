package im.conversations.android.xmpp.model.reporting;

import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(namespace = Namespace.REPORTING)
public class Report extends Extension {
    public Report() {
        super(Report.class);
    }

    public void setReason(final String reason) {
        this.setAttribute("reason", reason);
    }
}
