package eu.siacs.conversations.xml;

public final class Namespace {
    public static final String ADDRESSING = "http://jabber.org/protocol/address";
    public static final String AXOLOTL = "eu.siacs.conversations.axolotl";
    public static final String PGP_SIGNED = "jabber:x:signed";
    public static final String PGP_ENCRYPTED = "jabber:x:encrypted";
    public static final String AXOLOTL_BUNDLES = AXOLOTL + ".bundles";
    public static final String AXOLOTL_DEVICE_LIST = AXOLOTL + ".devicelist";
    public static final String HINTS = "urn:xmpp:hints";
    public static final String MESSAGE_ARCHIVE_MANAGEMENT = "urn:xmpp:mam:2";
    public static final String VERSION = "jabber:iq:version";
    public static final String LAST_MESSAGE_CORRECTION = "urn:xmpp:message-correct:0";
    public static final String RESULT_SET_MANAGEMENT = "http://jabber.org/protocol/rsm";
    public static final String CHAT_MARKERS = "urn:xmpp:chat-markers:0";
    public static final String CHAT_STATES = "http://jabber.org/protocol/chatstates";
    public static final String DELIVERY_RECEIPTS = "urn:xmpp:receipts";
    public static final String REACTIONS = "urn:xmpp:reactions:0";
    public static final String VCARD_TEMP = "vcard-temp";
    public static final String VCARD_TEMP_UPDATE = "vcard-temp:x:update";
    public static final String DELAY = "urn:xmpp:delay";
    public static final String OCCUPANT_ID = "urn:xmpp:occupant-id:0";
    public static final String STREAMS = "http://etherx.jabber.org/streams";
    public static final String STANZAS = "urn:ietf:params:xml:ns:xmpp-stanzas";
    public static final String JABBER_CLIENT = "jabber:client";
    public static final String FORWARD = "urn:xmpp:forward:0";
    public static final String DISCO_ITEMS = "http://jabber.org/protocol/disco#items";
    public static final String DISCO_INFO = "http://jabber.org/protocol/disco#info";
    public static final String EXTERNAL_SERVICE_DISCOVERY = "urn:xmpp:extdisco:2";
    public static final String BLOCKING = "urn:xmpp:blocking";
    public static final String ROSTER = "jabber:iq:roster";
    public static final String REGISTER = "jabber:iq:register";
    public static final String REGISTER_STREAM_FEATURE = "http://jabber.org/features/iq-register";
    public static final String BYTE_STREAMS = "http://jabber.org/protocol/bytestreams";
    public static final String HTTP_UPLOAD = "urn:xmpp:http:upload:0";
    public static final String HTTP_UPLOAD_LEGACY = "urn:xmpp:http:upload";
    public static final String STANZA_IDS = "urn:xmpp:sid:0";
    public static final String IDLE = "urn:xmpp:idle:1";
    public static final String DATA = "jabber:x:data";
    public static final String OOB = "jabber:x:oob";
    public static final String SASL = "urn:ietf:params:xml:ns:xmpp-sasl";
    public static final String SASL_2 = "urn:xmpp:sasl:2";
    public static final String CHANNEL_BINDING = "urn:xmpp:sasl-cb:0";
    public static final String FAST = "urn:xmpp:fast:0";
    public static final String TLS = "urn:ietf:params:xml:ns:xmpp-tls";
    public static final String PUBSUB = "http://jabber.org/protocol/pubsub";
    public static final String PUBSUB_EVENT = PUBSUB + "#event";
    public static final String MUC = "http://jabber.org/protocol/muc";
    public static final String PUBSUB_PUBLISH_OPTIONS = PUBSUB + "#publish-options";
    public static final String PUBSUB_CONFIG_NODE_MAX = PUBSUB + "#config-node-max";
    public static final String PUBSUB_ERROR = PUBSUB + "#errors";
    public static final String PUBSUB_OWNER = PUBSUB + "#owner";
    public static final String NICK = "http://jabber.org/protocol/nick";
    public static final String FLEXIBLE_OFFLINE_MESSAGE_RETRIEVAL =
            "http://jabber.org/protocol/offline";
    public static final String BIND = "urn:ietf:params:xml:ns:xmpp-bind";
    public static final String BIND2 = "urn:xmpp:bind:0";
    public static final String STREAM_MANAGEMENT = "urn:xmpp:sm:3";
    public static final String CSI = "urn:xmpp:csi:0";
    public static final String CARBONS = "urn:xmpp:carbons:2";
    public static final String BOOKMARKS_CONVERSION = "urn:xmpp:bookmarks-conversion:0";
    public static final String BOOKMARKS = "storage:bookmarks";
    public static final String SYNCHRONIZATION = "im.quicksy.synchronization:0";
    public static final String AVATAR_DATA = "urn:xmpp:avatar:data";
    public static final String AVATAR_METADATA = "urn:xmpp:avatar:metadata";
    public static final String AVATAR_CONVERSION = "urn:xmpp:pep-vcard-conversion:0";
    public static final String JINGLE = "urn:xmpp:jingle:1";
    public static final String JINGLE_ERRORS = "urn:xmpp:jingle:errors:1";
    public static final String JINGLE_MESSAGE = "urn:xmpp:jingle-message:0";
    public static final String JINGLE_ENCRYPTED_TRANSPORT = "urn:xmpp:jingle:jet:0";
    public static final String JINGLE_ENCRYPTED_TRANSPORT_OMEMO = "urn:xmpp:jingle:jet-omemo:0";
    public static final String JINGLE_TRANSPORTS_S5B = "urn:xmpp:jingle:transports:s5b:1";
    public static final String JINGLE_TRANSPORTS_IBB = "urn:xmpp:jingle:transports:ibb:1";
    public static final String JINGLE_TRANSPORT_ICE_UDP = "urn:xmpp:jingle:transports:ice-udp:1";
    public static final String JINGLE_TRANSPORT_WEBRTC_DATA_CHANNEL =
            "urn:xmpp:jingle:transports:webrtc-datachannel:1";
    public static final String JINGLE_TRANSPORT = "urn:xmpp:jingle:transports:dtls-sctp:1";
    public static final String JINGLE_APPS_RTP = "urn:xmpp:jingle:apps:rtp:1";

    public static final String JINGLE_APPS_FILE_TRANSFER = "urn:xmpp:jingle:apps:file-transfer:5";
    public static final String JINGLE_APPS_DTLS = "urn:xmpp:jingle:apps:dtls:0";
    public static final String JINGLE_APPS_GROUPING = "urn:xmpp:jingle:apps:grouping:0";
    public static final String JINGLE_FEATURE_AUDIO = "urn:xmpp:jingle:apps:rtp:audio";
    public static final String JINGLE_FEATURE_VIDEO = "urn:xmpp:jingle:apps:rtp:video";
    public static final String JINGLE_RTP_HEADER_EXTENSIONS =
            "urn:xmpp:jingle:apps:rtp:rtp-hdrext:0";
    public static final String JINGLE_RTP_FEEDBACK_NEGOTIATION =
            "urn:xmpp:jingle:apps:rtp:rtcp-fb:0";
    public static final String JINGLE_RTP_SOURCE_SPECIFIC_MEDIA_ATTRIBUTES =
            "urn:xmpp:jingle:apps:rtp:ssma:0";
    public static final String IBB = "http://jabber.org/protocol/ibb";
    public static final String PING = "urn:xmpp:ping";
    public static final String PUSH = "urn:xmpp:push:0";
    public static final String COMMANDS = "http://jabber.org/protocol/commands";
    public static final String MUC_USER = "http://jabber.org/protocol/muc#user";
    public static final String BOOKMARKS2 = "urn:xmpp:bookmarks:1";
    public static final String BOOKMARKS2_COMPAT = BOOKMARKS2 + "#compat";
    public static final String INVITE = "urn:xmpp:invite";
    public static final String PARS = "urn:xmpp:pars:0";
    public static final String EASY_ONBOARDING_INVITE = "urn:xmpp:invite#invite";
    public static final String OMEMO_DTLS_SRTP_VERIFICATION =
            "http://gultsch.de/xmpp/drafts/omemo/dlts-srtp-verification";
    public static final String JINGLE_TRANSPORT_ICE_OPTION =
            "http://gultsch.de/xmpp/drafts/jingle/transports/ice-udp/option";
    public static final String UNIFIED_PUSH = "http://gultsch.de/xmpp/drafts/unified-push";
    public static final String REPORTING = "urn:xmpp:reporting:1";
    public static final String REPORTING_REASON_SPAM = "urn:xmpp:reporting:spam";
    public static final String SDP_OFFER_ANSWER = "urn:ietf:rfc:3264";
    public static final String HASHES = "urn:xmpp:hashes:2";
    public static final String MDS_DISPLAYED = "urn:xmpp:mds:displayed:0";
    public static final String MDS_SERVER_ASSIST = "urn:xmpp:mds:server-assist:0";

    public static final String ENTITY_CAPABILITIES = "http://jabber.org/protocol/caps";
    public static final String ENTITY_CAPABILITIES_2 = "urn:xmpp:caps";
}
