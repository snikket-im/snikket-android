package eu.siacs.conversations.xmpp.manager;

import android.content.Context;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.stanza.Iq;
import im.conversations.android.xmpp.model.vcard.VCard;

public class VCardManager extends AbstractManager {

    public VCardManager(final Context context, final XmppConnection connection) {
        super(context, connection);
    }

    public ListenableFuture<VCard> retrieve(final Jid address) {
        final var iq = new Iq(Iq.Type.GET, new VCard());
        iq.setTo(address);
        return Futures.transform(
                this.connection.sendIqPacket(iq),
                result -> {
                    final var vCard = result.getExtension(VCard.class);
                    if (vCard == null) {
                        throw new IllegalStateException("Result did not include vCard");
                    }
                    return vCard;
                },
                MoreExecutors.directExecutor());
    }

    public ListenableFuture<byte[]> retrievePhoto(final Jid address) {
        final var vCardFuture = retrieve(address);
        return Futures.transform(
                vCardFuture,
                vCard -> {
                    final var photo = vCard.getPhoto();
                    if (photo == null) {
                        throw new IllegalStateException(
                                String.format("No photo in vCard of %s", address));
                    }
                    final var binaryValue = photo.getBinaryValue();
                    if (binaryValue == null) {
                        throw new IllegalStateException(
                                String.format("Photo has no binary value in vCard of %s", address));
                    }
                    return binaryValue.asBytes();
                },
                MoreExecutors.directExecutor());
    }
}
