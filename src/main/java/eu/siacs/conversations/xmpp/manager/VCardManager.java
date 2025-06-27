package eu.siacs.conversations.xmpp.manager;

import android.content.Context;
import android.util.Log;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.xmpp.IqErrorException;
import im.conversations.android.xmpp.model.error.Condition;
import im.conversations.android.xmpp.model.stanza.Iq;
import im.conversations.android.xmpp.model.vcard.BinaryValue;
import im.conversations.android.xmpp.model.vcard.Photo;
import im.conversations.android.xmpp.model.vcard.VCard;
import java.util.Objects;

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

        // TODO add a caching variant

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

    public ListenableFuture<Void> publish(final VCard vCard) {
        return publish(getAccount().getJid().asBareJid(), vCard);
    }

    public ListenableFuture<Void> publish(final Jid address, final VCard vCard) {
        final var iq = new Iq(Iq.Type.SET, vCard);
        iq.setTo(address);
        return Futures.transform(
                connection.sendIqPacket(iq), result -> null, MoreExecutors.directExecutor());
    }

    public ListenableFuture<Void> deletePhoto() {
        final var vCardFuture = retrieve(getAccount().getJid().asBareJid());
        return Futures.transformAsync(
                vCardFuture,
                vCard -> {
                    final var photo = vCard.getPhoto();
                    if (photo == null) {
                        return Futures.immediateFuture(null);
                    }
                    Log.d(
                            Config.LOGTAG,
                            "deleting photo from vCard. binaryValue="
                                    + Objects.nonNull(photo.getBinaryValue()));
                    photo.clearChildren();
                    return publish(vCard);
                },
                MoreExecutors.directExecutor());
    }

    public ListenableFuture<Void> publishPhoto(
            final Jid address, final String type, final byte[] image) {
        final var retrieveFuture = this.retrieve(address);

        final var caughtFuture =
                Futures.catchingAsync(
                        retrieveFuture,
                        IqErrorException.class,
                        ex -> {
                            final var error = ex.getError();
                            if (error != null
                                    && error.getCondition() instanceof Condition.ItemNotFound) {
                                return Futures.immediateFuture(null);
                            } else {
                                return Futures.immediateFailedFuture(ex);
                            }
                        },
                        MoreExecutors.directExecutor());

        return Futures.transformAsync(
                caughtFuture,
                existing -> {
                    final VCard vCard;
                    if (existing == null) {
                        Log.d(Config.LOGTAG, "item-not-found. created fresh vCard");
                        vCard = new VCard();
                    } else {
                        vCard = existing;
                    }
                    final var photo = new Photo();
                    photo.setType(type);
                    photo.addExtension(new BinaryValue()).setContent(image);
                    vCard.setExtension(photo);
                    return publish(address, vCard);
                },
                MoreExecutors.directExecutor());
    }
}
