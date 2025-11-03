package eu.siacs.conversations.parser;

import android.util.Log;
import android.util.Pair;
import androidx.annotation.NonNull;
import com.google.common.base.CharMatcher;
import com.google.common.io.BaseEncoding;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.manager.BlockingManager;
import eu.siacs.conversations.xmpp.manager.DiscoManager;
import eu.siacs.conversations.xmpp.manager.EntityTimeManager;
import eu.siacs.conversations.xmpp.manager.MultiUserChatManager;
import eu.siacs.conversations.xmpp.manager.PingManager;
import eu.siacs.conversations.xmpp.manager.RosterManager;
import eu.siacs.conversations.xmpp.manager.UnifiedPushManager;
import im.conversations.android.xmpp.model.blocking.Block;
import im.conversations.android.xmpp.model.blocking.Unblock;
import im.conversations.android.xmpp.model.disco.info.InfoQuery;
import im.conversations.android.xmpp.model.error.Condition;
import im.conversations.android.xmpp.model.error.Error;
import im.conversations.android.xmpp.model.ibb.InBandByteStream;
import im.conversations.android.xmpp.model.jingle.Jingle;
import im.conversations.android.xmpp.model.ping.Ping;
import im.conversations.android.xmpp.model.roster.Query;
import im.conversations.android.xmpp.model.stanza.Iq;
import im.conversations.android.xmpp.model.time.Time;
import im.conversations.android.xmpp.model.up.Push;
import im.conversations.android.xmpp.model.version.Version;
import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.state.PreKeyBundle;

public class IqParser extends AbstractParser implements Consumer<Iq> {

    public IqParser(final XmppConnectionService service, final XmppConnection connection) {
        super(service, connection);
    }

    public static Element getItem(final Iq packet) {
        final Element pubsub = packet.findChild("pubsub", Namespace.PUB_SUB);
        if (pubsub == null) {
            return null;
        }
        final Element items = pubsub.findChild("items");
        if (items == null) {
            return null;
        }
        return items.findChild("item");
    }

    @NonNull
    public static Set<Integer> deviceIds(final Element item) {
        Set<Integer> deviceIds = new HashSet<>();
        if (item != null) {
            final Element list = item.findChild("list");
            if (list != null) {
                for (Element device : list.getChildren()) {
                    if (!device.getName().equals("device")) {
                        continue;
                    }
                    try {
                        Integer id = Integer.valueOf(device.getAttribute("id"));
                        deviceIds.add(id);
                    } catch (NumberFormatException e) {
                        Log.e(
                                Config.LOGTAG,
                                AxolotlService.LOGPREFIX
                                        + " : "
                                        + "Encountered invalid <device> node in PEP ("
                                        + e.getMessage()
                                        + "):"
                                        + device
                                        + ", skipping...");
                    }
                }
            }
        }
        return deviceIds;
    }

    private static Integer signedPreKeyId(final Element bundle) {
        final Element signedPreKeyPublic = bundle.findChild("signedPreKeyPublic");
        if (signedPreKeyPublic == null) {
            return null;
        }
        try {
            return Integer.valueOf(signedPreKeyPublic.getAttribute("signedPreKeyId"));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static ECPublicKey signedPreKeyPublic(final Element bundle) {
        ECPublicKey publicKey = null;
        final String signedPreKeyPublic = bundle.findChildContent("signedPreKeyPublic");
        if (signedPreKeyPublic == null) {
            return null;
        }
        try {
            publicKey = Curve.decodePoint(base64decode(signedPreKeyPublic), 0);
        } catch (final IllegalArgumentException | InvalidKeyException e) {
            Log.e(
                    Config.LOGTAG,
                    AxolotlService.LOGPREFIX
                            + " : "
                            + "Invalid signedPreKeyPublic in PEP: "
                            + e.getMessage());
        }
        return publicKey;
    }

    private static byte[] signedPreKeySignature(final Element bundle) {
        final String signedPreKeySignature = bundle.findChildContent("signedPreKeySignature");
        if (signedPreKeySignature == null) {
            return null;
        }
        try {
            return base64decode(signedPreKeySignature);
        } catch (final IllegalArgumentException e) {
            Log.e(
                    Config.LOGTAG,
                    AxolotlService.LOGPREFIX + " : Invalid base64 in signedPreKeySignature");
            return null;
        }
    }

    private static IdentityKey identityKey(final Element bundle) {
        final String identityKey = bundle.findChildContent("identityKey");
        if (identityKey == null) {
            return null;
        }
        try {
            return new IdentityKey(base64decode(identityKey), 0);
        } catch (final IllegalArgumentException | InvalidKeyException e) {
            Log.e(
                    Config.LOGTAG,
                    AxolotlService.LOGPREFIX
                            + " : "
                            + "Invalid identityKey in PEP: "
                            + e.getMessage());
            return null;
        }
    }

    public static Map<Integer, ECPublicKey> preKeyPublics(final Iq packet) {
        Map<Integer, ECPublicKey> preKeyRecords = new HashMap<>();
        Element item = getItem(packet);
        if (item == null) {
            Log.d(
                    Config.LOGTAG,
                    AxolotlService.LOGPREFIX
                            + " : "
                            + "Couldn't find <item> in bundle IQ packet: "
                            + packet);
            return null;
        }
        final Element bundleElement = item.findChild("bundle");
        if (bundleElement == null) {
            return null;
        }
        final Element prekeysElement = bundleElement.findChild("prekeys");
        if (prekeysElement == null) {
            Log.d(
                    Config.LOGTAG,
                    AxolotlService.LOGPREFIX
                            + " : "
                            + "Couldn't find <prekeys> in bundle IQ packet: "
                            + packet);
            return null;
        }
        for (Element preKeyPublicElement : prekeysElement.getChildren()) {
            if (!preKeyPublicElement.getName().equals("preKeyPublic")) {
                Log.d(
                        Config.LOGTAG,
                        AxolotlService.LOGPREFIX
                                + " : "
                                + "Encountered unexpected tag in prekeys list: "
                                + preKeyPublicElement);
                continue;
            }
            final String preKey = preKeyPublicElement.getContent();
            if (preKey == null) {
                continue;
            }
            Integer preKeyId = null;
            try {
                preKeyId = Integer.valueOf(preKeyPublicElement.getAttribute("preKeyId"));
                final ECPublicKey preKeyPublic = Curve.decodePoint(base64decode(preKey), 0);
                preKeyRecords.put(preKeyId, preKeyPublic);
            } catch (NumberFormatException e) {
                Log.e(
                        Config.LOGTAG,
                        AxolotlService.LOGPREFIX
                                + " : "
                                + "could not parse preKeyId from preKey "
                                + preKeyPublicElement);
            } catch (Throwable e) {
                Log.e(
                        Config.LOGTAG,
                        AxolotlService.LOGPREFIX
                                + " : "
                                + "Invalid preKeyPublic (ID="
                                + preKeyId
                                + ") in PEP: "
                                + e.getMessage()
                                + ", skipping...");
            }
        }
        return preKeyRecords;
    }

    private static byte[] base64decode(String input) {
        return BaseEncoding.base64().decode(CharMatcher.whitespace().removeFrom(input));
    }

    public static Pair<X509Certificate[], byte[]> verification(final Iq packet) {
        Element item = getItem(packet);
        Element verification =
                item != null ? item.findChild("verification", AxolotlService.PEP_PREFIX) : null;
        Element chain = verification != null ? verification.findChild("chain") : null;
        String signature = verification != null ? verification.findChildContent("signature") : null;
        if (chain != null && signature != null) {
            List<Element> certElements = chain.getChildren();
            X509Certificate[] certificates = new X509Certificate[certElements.size()];
            try {
                CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
                int i = 0;
                for (final Element certElement : certElements) {
                    final String cert = certElement.getContent();
                    if (cert == null) {
                        continue;
                    }
                    certificates[i] =
                            (X509Certificate)
                                    certificateFactory.generateCertificate(
                                            new ByteArrayInputStream(
                                                    BaseEncoding.base64().decode(cert)));
                    ++i;
                }
                return new Pair<>(certificates, BaseEncoding.base64().decode(signature));
            } catch (CertificateException e) {
                return null;
            }
        } else {
            return null;
        }
    }

    public static PreKeyBundle bundle(final Iq bundle) {
        final Element bundleItem = getItem(bundle);
        if (bundleItem == null) {
            return null;
        }
        final Element bundleElement = bundleItem.findChild("bundle");
        if (bundleElement == null) {
            return null;
        }
        final ECPublicKey signedPreKeyPublic = signedPreKeyPublic(bundleElement);
        final Integer signedPreKeyId = signedPreKeyId(bundleElement);
        final byte[] signedPreKeySignature = signedPreKeySignature(bundleElement);
        final IdentityKey identityKey = identityKey(bundleElement);
        if (signedPreKeyId == null
                || signedPreKeyPublic == null
                || identityKey == null
                || signedPreKeySignature == null
                || signedPreKeySignature.length == 0) {
            return null;
        }
        return new PreKeyBundle(
                0,
                0,
                0,
                null,
                signedPreKeyId,
                signedPreKeyPublic,
                signedPreKeySignature,
                identityKey);
    }

    public static List<PreKeyBundle> preKeys(final Iq preKeys) {
        List<PreKeyBundle> bundles = new ArrayList<>();
        Map<Integer, ECPublicKey> preKeyPublics = preKeyPublics(preKeys);
        if (preKeyPublics != null) {
            for (Integer preKeyId : preKeyPublics.keySet()) {
                ECPublicKey preKeyPublic = preKeyPublics.get(preKeyId);
                bundles.add(new PreKeyBundle(0, 0, preKeyId, preKeyPublic, 0, null, null, null));
            }
        }

        return bundles;
    }

    @Override
    public void accept(final Iq packet) {
        final var type = packet.getType();
        switch (type) {
            case SET -> acceptPush(packet);
            case GET -> acceptRequest(packet);
            default ->
                    throw new AssertionError(
                            "IQ results and errors should are handled in callbacks");
        }
    }

    private void acceptPush(final Iq packet) {
        // there is rarely a good reason to respond to IQs from MUCs
        if (getManager(MultiUserChatManager.class).isMuc(packet)) {
            this.connection.sendErrorFor(
                    packet, Error.Type.CANCEL, new Condition.ServiceUnavailable());
            return;
        }
        final var jingleConnectionManager =
                this.mXmppConnectionService.getJingleConnectionManager();
        if (packet.hasExtension(Jingle.class)) {
            jingleConnectionManager.deliverPacket(getAccount(), packet);
        } else if (packet.hasExtension(Query.class)) {
            this.getManager(RosterManager.class).push(packet);
        } else if (packet.hasExtension(Block.class)) {
            this.getManager(BlockingManager.class).pushBlock(packet);
        } else if (packet.hasExtension(Unblock.class)) {
            this.getManager(BlockingManager.class).pushUnblock(packet);
        } else if (packet.hasExtension(InBandByteStream.class)) {
            jingleConnectionManager.deliverIbbPacket(getAccount(), packet);
        } else if (packet.hasExtension(Push.class)) {
            this.getManager(UnifiedPushManager.class).push(packet);
        } else {
            this.connection.sendErrorFor(
                    packet, Error.Type.CANCEL, new Condition.FeatureNotImplemented());
        }
    }

    private void acceptRequest(final Iq packet) {
        // responding to pings in MUCs is fine. this does not reveal more info than responding with
        // service unavailable
        if (packet.hasExtension(Ping.class)) {
            this.getManager(PingManager.class).pong(packet);
            return;
        }

        // there is rarely a good reason to respond to IQs from MUCs
        if (getManager(MultiUserChatManager.class).isMuc(packet)) {
            this.connection.sendErrorFor(
                    packet, Error.Type.CANCEL, new Condition.ServiceUnavailable());
        } else if (packet.hasExtension(InfoQuery.class)) {
            this.getManager(DiscoManager.class).handleInfoQuery(packet);
        } else if (packet.hasExtension(Version.class)) {
            this.getManager(DiscoManager.class).handleVersionRequest(packet);
        } else if (packet.hasExtension(Time.class)) {
            this.getManager(EntityTimeManager.class).request(packet);
        } else {
            this.connection.sendErrorFor(
                    packet, Error.Type.CANCEL, new Condition.FeatureNotImplemented());
        }
    }
}
