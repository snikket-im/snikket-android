package eu.siacs.conversations.xmpp.jingle.stanzas;

import android.util.Base64;

import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import rocks.xmpp.addr.Jid;

public class JinglePacket extends IqPacket {


	//get rid of that BS and set/get directly
    Content content = null;
    Reason reason = null;
    Element checksum = null;
    Element jingle = new Element("jingle");

    //get rid of what ever that is; maybe throw illegal state to ensure we are only calling setContent etc
    @Override
    public Element addChild(Element child) {
        if ("jingle".equals(child.getName())) {
            Element contentElement = child.findChild("content");
            if (contentElement != null) {
                this.content = new Content();
                this.content.setChildren(contentElement.getChildren());
                this.content.setAttributes(contentElement.getAttributes());
            }
            Element reasonElement = child.findChild("reason");
            if (reasonElement != null) {
                this.reason = new Reason();
                this.reason.setChildren(reasonElement.getChildren());
                this.reason.setAttributes(reasonElement.getAttributes());
            }
            this.checksum = child.findChild("checksum");
            this.jingle.setAttributes(child.getAttributes());
        }
        return child;
    }

    public JinglePacket setContent(Content content) { //take content interface
        this.content = content;
        return this;
    }

    public Content getJingleContent() {
        if (this.content == null) {
            this.content = new Content();
        }
        return this.content;
    }

    public Reason getReason() {
        return this.reason;
    }

    public JinglePacket setReason(Reason reason) {
        this.reason = reason;
        return this;
    }

    public Element getChecksum() {
        return this.checksum;
    }

    //should be unnecessary if we set and get directly
    private void build() {
        this.children.clear();
        this.jingle.clearChildren();
        this.jingle.setAttribute("xmlns", "urn:xmpp:jingle:1");
        if (this.content != null) {
            jingle.addChild(this.content);
        }
        if (this.reason != null) {
            jingle.addChild(this.reason);
        }
        if (this.checksum != null) {
            jingle.addChild(checksum);
        }
        this.children.add(jingle);
        this.setAttribute("type", "set");
    }

    public String getSessionId() {
        return this.jingle.getAttribute("sid");
    }

    public void setSessionId(String sid) {
        this.jingle.setAttribute("sid", sid);
    }

    @Override
    public String toString() {
        this.build();
        return super.toString();
    }

    //use enum for action
    public String getAction() {
        return this.jingle.getAttribute("action");
    }

    public void setAction(String action) {
        this.jingle.setAttribute("action", action);
    }

    public void setInitiator(final Jid initiator) {
        this.jingle.setAttribute("initiator", initiator.toString());
    }

    public boolean isAction(String action) {
        return action.equalsIgnoreCase(this.getAction());
    }

    public void addChecksum(byte[] sha1Sum, String namespace) {
        this.checksum = new Element("checksum", namespace);
        checksum.setAttribute("creator", "initiator");
        checksum.setAttribute("name", "a-file-offer");
        Element hash = checksum.addChild("file").addChild("hash", "urn:xmpp:hashes:2");
        hash.setAttribute("algo", "sha-1").setContent(Base64.encodeToString(sha1Sum, Base64.NO_WRAP));
    }
}
