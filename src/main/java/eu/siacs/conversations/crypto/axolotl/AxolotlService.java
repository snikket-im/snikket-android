package eu.siacs.conversations.crypto.axolotl;

import android.util.Base64;
import android.util.Log;

import org.whispersystems.libaxolotl.AxolotlAddress;
import org.whispersystems.libaxolotl.DuplicateMessageException;
import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.IdentityKeyPair;
import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.InvalidKeyIdException;
import org.whispersystems.libaxolotl.InvalidMessageException;
import org.whispersystems.libaxolotl.InvalidVersionException;
import org.whispersystems.libaxolotl.LegacyMessageException;
import org.whispersystems.libaxolotl.NoSessionException;
import org.whispersystems.libaxolotl.SessionBuilder;
import org.whispersystems.libaxolotl.SessionCipher;
import org.whispersystems.libaxolotl.UntrustedIdentityException;
import org.whispersystems.libaxolotl.ecc.Curve;
import org.whispersystems.libaxolotl.ecc.ECKeyPair;
import org.whispersystems.libaxolotl.ecc.ECPublicKey;
import org.whispersystems.libaxolotl.protocol.CiphertextMessage;
import org.whispersystems.libaxolotl.protocol.PreKeyWhisperMessage;
import org.whispersystems.libaxolotl.protocol.WhisperMessage;
import org.whispersystems.libaxolotl.state.AxolotlStore;
import org.whispersystems.libaxolotl.state.PreKeyBundle;
import org.whispersystems.libaxolotl.state.PreKeyRecord;
import org.whispersystems.libaxolotl.state.SessionRecord;
import org.whispersystems.libaxolotl.state.SignedPreKeyRecord;
import org.whispersystems.libaxolotl.util.KeyHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.parser.IqParser;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.OnIqPacketReceived;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;

public class AxolotlService {

    public static final String PEP_PREFIX = "eu.siacs.conversations.axolotl";
    public static final String PEP_DEVICE_LIST = PEP_PREFIX + ".devicelist";
    public static final String PEP_PREKEYS = PEP_PREFIX + ".prekeys";
    public static final String PEP_BUNDLE = PEP_PREFIX + ".bundle";

    private final Account account;
    private final XmppConnectionService mXmppConnectionService;
    private final SQLiteAxolotlStore axolotlStore;
    private final SessionMap sessions;
    private final BundleMap bundleCache;
    private int ownDeviceId;

    public static class SQLiteAxolotlStore implements AxolotlStore {

        public static final String PREKEY_TABLENAME = "prekeys";
        public static final String SIGNED_PREKEY_TABLENAME = "signed_prekeys";
        public static final String SESSION_TABLENAME = "sessions";
        public static final String ACCOUNT = "account";
        public static final String DEVICE_ID = "device_id";
        public static final String ID = "id";
        public static final String KEY = "key";
        public static final String NAME = "name";
        public static final String TRUSTED = "trusted";

        public static final String JSONKEY_IDENTITY_KEY_PAIR = "axolotl_key";
        public static final String JSONKEY_REGISTRATION_ID = "axolotl_reg_id";
        public static final String JSONKEY_CURRENT_PREKEY_ID = "axolotl_cur_prekey_id";

        private final Account account;
        private final XmppConnectionService mXmppConnectionService;

        private final IdentityKeyPair identityKeyPair;
        private final int localRegistrationId;
        private int currentPreKeyId = 0;


        private static IdentityKeyPair generateIdentityKeyPair() {
            Log.d(Config.LOGTAG, "Generating axolotl IdentityKeyPair...");
            ECKeyPair identityKeyPairKeys = Curve.generateKeyPair();
            IdentityKeyPair ownKey = new IdentityKeyPair(new IdentityKey(identityKeyPairKeys.getPublicKey()),
                    identityKeyPairKeys.getPrivateKey());
            return ownKey;
        }

        private static int generateRegistrationId() {
            Log.d(Config.LOGTAG, "Generating axolotl registration ID...");
            int reg_id = KeyHelper.generateRegistrationId(false);
            return reg_id;
        }

        public SQLiteAxolotlStore(Account account, XmppConnectionService service) {
            this.account = account;
            this.mXmppConnectionService = service;
            this.identityKeyPair = loadIdentityKeyPair();
            this.localRegistrationId = loadRegistrationId();
            this.currentPreKeyId = loadCurrentPreKeyId();
            for( SignedPreKeyRecord record:loadSignedPreKeys()) {
                Log.d(Config.LOGTAG, "Got Axolotl signed prekey record:" + record.getId());
            }
        }

        public int getCurrentPreKeyId() {
            return currentPreKeyId;
        }

        // --------------------------------------
        // IdentityKeyStore
        // --------------------------------------

        private IdentityKeyPair loadIdentityKeyPair() {
            String serializedKey = this.account.getKey(JSONKEY_IDENTITY_KEY_PAIR);
            IdentityKeyPair ownKey;
            if( serializedKey != null ) {
                try {
                    ownKey = new IdentityKeyPair(Base64.decode(serializedKey,Base64.DEFAULT));
                    return ownKey;
                } catch (InvalidKeyException e) {
                    Log.d(Config.LOGTAG, "Invalid key stored for account " + account.getJid() + ": " + e.getMessage());
//                    return null;
                }
            } //else {
                Log.d(Config.LOGTAG, "Could not retrieve axolotl key for account " + account.getJid());
                ownKey = generateIdentityKeyPair();
                boolean success = this.account.setKey(JSONKEY_IDENTITY_KEY_PAIR, Base64.encodeToString(ownKey.serialize(), Base64.DEFAULT));
                if(success) {
                    mXmppConnectionService.databaseBackend.updateAccount(account);
                } else {
                    Log.e(Config.LOGTAG, "Failed to write new key to the database!");
                }
            //}
            return ownKey;
        }

        private int loadRegistrationId() {
            String regIdString = this.account.getKey(JSONKEY_REGISTRATION_ID);
            int reg_id;
            if (regIdString != null) {
                reg_id = Integer.valueOf(regIdString);
            } else {
                Log.d(Config.LOGTAG, "Could not retrieve axolotl registration id for account " + account.getJid());
                reg_id = generateRegistrationId();
                boolean success = this.account.setKey(JSONKEY_REGISTRATION_ID,""+reg_id);
                if(success) {
                    mXmppConnectionService.databaseBackend.updateAccount(account);
                } else {
                    Log.e(Config.LOGTAG, "Failed to write new key to the database!");
                }
            }
            return reg_id;
        }

        private int loadCurrentPreKeyId() {
            String regIdString = this.account.getKey(JSONKEY_CURRENT_PREKEY_ID);
            int reg_id;
            if (regIdString != null) {
                reg_id = Integer.valueOf(regIdString);
            } else {
                Log.d(Config.LOGTAG, "Could not retrieve current prekey id for account " + account.getJid());
                reg_id = 0;
            }
            return reg_id;
        }


        /**
         * Get the local client's identity key pair.
         *
         * @return The local client's persistent identity key pair.
         */
        @Override
        public IdentityKeyPair getIdentityKeyPair() {
            return identityKeyPair;
        }

        /**
         * Return the local client's registration ID.
         * <p/>
         * Clients should maintain a registration ID, a random number
         * between 1 and 16380 that's generated once at install time.
         *
         * @return the local client's registration ID.
         */
        @Override
        public int getLocalRegistrationId() {
            return localRegistrationId;
        }

        /**
         * Save a remote client's identity key
         * <p/>
         * Store a remote client's identity key as trusted.
         *
         * @param name        The name of the remote client.
         * @param identityKey The remote client's identity key.
         */
        @Override
        public void saveIdentity(String name, IdentityKey identityKey) {
            try {
                Jid contactJid = Jid.fromString(name);
                Conversation conversation = this.mXmppConnectionService.find(this.account, contactJid);
                if (conversation != null) {
                    conversation.getContact().addAxolotlIdentityKey(identityKey);
                    mXmppConnectionService.updateConversationUi();
                    mXmppConnectionService.syncRosterToDisk(conversation.getAccount());
                }
            } catch (final InvalidJidException e) {
                Log.e(Config.LOGTAG, "Failed to save identityKey for contact name " + name + ": " + e.toString());
            }
        }

        /**
         * Verify a remote client's identity key.
         * <p/>
         * Determine whether a remote client's identity is trusted.  Convention is
         * that the TextSecure protocol is 'trust on first use.'  This means that
         * an identity key is considered 'trusted' if there is no entry for the recipient
         * in the local store, or if it matches the saved key for a recipient in the local
         * store.  Only if it mismatches an entry in the local store is it considered
         * 'untrusted.'
         *
         * @param name        The name of the remote client.
         * @param identityKey The identity key to verify.
         * @return true if trusted, false if untrusted.
         */
        @Override
        public boolean isTrustedIdentity(String name, IdentityKey identityKey) {
            try {
                Jid contactJid = Jid.fromString(name);
                Conversation conversation = this.mXmppConnectionService.find(this.account, contactJid);
                if (conversation != null) {
                    List<IdentityKey> trustedKeys = conversation.getContact().getAxolotlIdentityKeys();
                    return trustedKeys.isEmpty() || trustedKeys.contains(identityKey);
                } else {
                    return false;
                }
            } catch (final InvalidJidException e) {
                Log.e(Config.LOGTAG, "Failed to save identityKey for contact name" + name + ": " + e.toString());
                return false;
            }
        }

        // --------------------------------------
        // SessionStore
        // --------------------------------------

        /**
         * Returns a copy of the {@link SessionRecord} corresponding to the recipientId + deviceId tuple,
         * or a new SessionRecord if one does not currently exist.
         * <p/>
         * It is important that implementations return a copy of the current durable information.  The
         * returned SessionRecord may be modified, but those changes should not have an effect on the
         * durable session state (what is returned by subsequent calls to this method) without the
         * store method being called here first.
         *
         * @param address The name and device ID of the remote client.
         * @return a copy of the SessionRecord corresponding to the recipientId + deviceId tuple, or
         * a new SessionRecord if one does not currently exist.
         */
        @Override
        public SessionRecord loadSession(AxolotlAddress address) {
            SessionRecord session = mXmppConnectionService.databaseBackend.loadSession(this.account, address);
            return (session!=null)?session:new SessionRecord();
        }

        /**
         * Returns all known devices with active sessions for a recipient
         *
         * @param name the name of the client.
         * @return all known sub-devices with active sessions.
         */
        @Override
        public List<Integer> getSubDeviceSessions(String name) {
            return mXmppConnectionService.databaseBackend.getSubDeviceSessions(account,
                    new AxolotlAddress(name,0));
        }

        /**
         * Commit to storage the {@link SessionRecord} for a given recipientId + deviceId tuple.
         *
         * @param address the address of the remote client.
         * @param record  the current SessionRecord for the remote client.
         */
        @Override
        public void storeSession(AxolotlAddress address, SessionRecord record) {
            mXmppConnectionService.databaseBackend.storeSession(account, address, record);
        }

        /**
         * Determine whether there is a committed {@link SessionRecord} for a recipientId + deviceId tuple.
         *
         * @param address the address of the remote client.
         * @return true if a {@link SessionRecord} exists, false otherwise.
         */
        @Override
        public boolean containsSession(AxolotlAddress address) {
            return mXmppConnectionService.databaseBackend.containsSession(account, address);
        }

        /**
         * Remove a {@link SessionRecord} for a recipientId + deviceId tuple.
         *
         * @param address the address of the remote client.
         */
        @Override
        public void deleteSession(AxolotlAddress address) {
            mXmppConnectionService.databaseBackend.deleteSession(account, address);
        }

        /**
         * Remove the {@link SessionRecord}s corresponding to all devices of a recipientId.
         *
         * @param name the name of the remote client.
         */
        @Override
        public void deleteAllSessions(String name) {
            mXmppConnectionService.databaseBackend.deleteAllSessions(account,
                    new AxolotlAddress(name, 0));
        }

        public boolean isTrustedSession(AxolotlAddress address) {
            return mXmppConnectionService.databaseBackend.isTrustedSession(this.account, address);
        }

        public void setTrustedSession(AxolotlAddress address, boolean trusted) {
            mXmppConnectionService.databaseBackend.setTrustedSession(this.account, address,trusted);
        }

        // --------------------------------------
        // PreKeyStore
        // --------------------------------------

        /**
         * Load a local PreKeyRecord.
         *
         * @param preKeyId the ID of the local PreKeyRecord.
         * @return the corresponding PreKeyRecord.
         * @throws InvalidKeyIdException when there is no corresponding PreKeyRecord.
         */
        @Override
        public PreKeyRecord loadPreKey(int preKeyId) throws InvalidKeyIdException {
            PreKeyRecord record = mXmppConnectionService.databaseBackend.loadPreKey(account, preKeyId);
            if(record == null) {
                throw new InvalidKeyIdException("No such PreKeyRecord: " + preKeyId);
            }
            return record;
        }

        /**
         * Store a local PreKeyRecord.
         *
         * @param preKeyId the ID of the PreKeyRecord to store.
         * @param record   the PreKeyRecord.
         */
        @Override
        public void storePreKey(int preKeyId, PreKeyRecord record) {
            mXmppConnectionService.databaseBackend.storePreKey(account, record);
            currentPreKeyId = preKeyId;
            boolean success = this.account.setKey(JSONKEY_CURRENT_PREKEY_ID,Integer.toString(preKeyId));
            if(success) {
                mXmppConnectionService.databaseBackend.updateAccount(account);
            } else {
                Log.e(Config.LOGTAG, "Failed to write new prekey id to the database!");
            }
        }

        /**
         * @param preKeyId A PreKeyRecord ID.
         * @return true if the store has a record for the preKeyId, otherwise false.
         */
        @Override
        public boolean containsPreKey(int preKeyId) {
            return mXmppConnectionService.databaseBackend.containsPreKey(account, preKeyId);
        }

        /**
         * Delete a PreKeyRecord from local storage.
         *
         * @param preKeyId The ID of the PreKeyRecord to remove.
         */
        @Override
        public void removePreKey(int preKeyId) {
            mXmppConnectionService.databaseBackend.deletePreKey(account, preKeyId);
        }

        // --------------------------------------
        // SignedPreKeyStore
        // --------------------------------------

        /**
         * Load a local SignedPreKeyRecord.
         *
         * @param signedPreKeyId the ID of the local SignedPreKeyRecord.
         * @return the corresponding SignedPreKeyRecord.
         * @throws InvalidKeyIdException when there is no corresponding SignedPreKeyRecord.
         */
        @Override
        public SignedPreKeyRecord loadSignedPreKey(int signedPreKeyId) throws InvalidKeyIdException {
            SignedPreKeyRecord record = mXmppConnectionService.databaseBackend.loadSignedPreKey(account, signedPreKeyId);
            if(record == null) {
                throw new InvalidKeyIdException("No such SignedPreKeyRecord: " + signedPreKeyId);
            }
            return record;
        }

        /**
         * Load all local SignedPreKeyRecords.
         *
         * @return All stored SignedPreKeyRecords.
         */
        @Override
        public List<SignedPreKeyRecord> loadSignedPreKeys() {
            return mXmppConnectionService.databaseBackend.loadSignedPreKeys(account);
        }

        /**
         * Store a local SignedPreKeyRecord.
         *
         * @param signedPreKeyId the ID of the SignedPreKeyRecord to store.
         * @param record         the SignedPreKeyRecord.
         */
        @Override
        public void storeSignedPreKey(int signedPreKeyId, SignedPreKeyRecord record) {
            mXmppConnectionService.databaseBackend.storeSignedPreKey(account, record);
        }

        /**
         * @param signedPreKeyId A SignedPreKeyRecord ID.
         * @return true if the store has a record for the signedPreKeyId, otherwise false.
         */
        @Override
        public boolean containsSignedPreKey(int signedPreKeyId) {
            return mXmppConnectionService.databaseBackend.containsSignedPreKey(account, signedPreKeyId);
        }

        /**
         * Delete a SignedPreKeyRecord from local storage.
         *
         * @param signedPreKeyId The ID of the SignedPreKeyRecord to remove.
         */
        @Override
        public void removeSignedPreKey(int signedPreKeyId) {
            mXmppConnectionService.databaseBackend.deleteSignedPreKey(account, signedPreKeyId);
        }
    }

    public static class XmppAxolotlSession {
        private SessionCipher cipher;
        private boolean isTrusted = false;
        private SQLiteAxolotlStore sqLiteAxolotlStore;
        private AxolotlAddress remoteAddress;

        public XmppAxolotlSession(SQLiteAxolotlStore store, AxolotlAddress remoteAddress) {
            this.cipher = new SessionCipher(store, remoteAddress);
            this.remoteAddress = remoteAddress;
            this.sqLiteAxolotlStore = store;
            this.isTrusted = sqLiteAxolotlStore.isTrustedSession(remoteAddress);
        }

        public void trust() {
            sqLiteAxolotlStore.setTrustedSession(remoteAddress, true);
            this.isTrusted = true;
        }

        public boolean isTrusted() {
            return this.isTrusted;
        }

        public byte[] processReceiving(XmppAxolotlMessage.XmppAxolotlMessageHeader incomingHeader) {
            byte[] plaintext = null;
            try {
                try {
                    PreKeyWhisperMessage message = new PreKeyWhisperMessage(incomingHeader.getContents());
                    Log.d(Config.LOGTAG,"PreKeyWhisperMessage ID:" + message.getSignedPreKeyId() + "/" + message.getPreKeyId());
                    plaintext = cipher.decrypt(message);
                } catch (InvalidMessageException|InvalidVersionException e) {
                    WhisperMessage message = new WhisperMessage(incomingHeader.getContents());
                    plaintext = cipher.decrypt(message);
                } catch (InvalidKeyException|InvalidKeyIdException| UntrustedIdentityException e) {
                    Log.d(Config.LOGTAG, "Error decrypting axolotl header: " + e.getMessage());
                }
            } catch (LegacyMessageException|InvalidMessageException e) {
                Log.d(Config.LOGTAG, "Error decrypting axolotl header: " + e.getMessage());
            } catch (DuplicateMessageException|NoSessionException e) {
                Log.d(Config.LOGTAG, "Error decrypting axolotl header: " + e.getMessage());
            }
            return plaintext;
        }

        public XmppAxolotlMessage.XmppAxolotlMessageHeader processSending(byte[] outgoingMessage) {
            CiphertextMessage ciphertextMessage = cipher.encrypt(outgoingMessage);
            XmppAxolotlMessage.XmppAxolotlMessageHeader header =
                    new XmppAxolotlMessage.XmppAxolotlMessageHeader(remoteAddress.getDeviceId(),
                            ciphertextMessage.serialize());
            return header;
        }
    }

    private static class AxolotlAddressMap<T> {
        protected Map<String, Map<Integer,T>> map;
        protected final Object MAP_LOCK = new Object();

        public AxolotlAddressMap() {
            this.map = new HashMap<>();
        }

        public void put(AxolotlAddress address, T value) {
            synchronized (MAP_LOCK) {
                Map<Integer, T> devices = map.get(address.getName());
                if (devices == null) {
                    devices = new HashMap<>();
                    map.put(address.getName(), devices);
                }
                devices.put(address.getDeviceId(), value);
            }
        }

        public T get(AxolotlAddress address) {
            synchronized (MAP_LOCK) {
                Map<Integer, T> devices = map.get(address.getName());
                if(devices == null) {
                    return null;
                }
                return devices.get(address.getDeviceId());
            }
        }

        public Map<Integer, T> getAll(AxolotlAddress address) {
            synchronized (MAP_LOCK) {
                Map<Integer, T> devices = map.get(address.getName());
                if(devices == null) {
                    return new HashMap<>();
                }
                return devices;
            }
        }

        public boolean hasAny(AxolotlAddress address) {
            synchronized (MAP_LOCK) {
                Map<Integer, T> devices = map.get(address.getName());
                return devices != null && !devices.isEmpty();
            }
        }


    }

    private static class SessionMap extends AxolotlAddressMap<XmppAxolotlSession> {

        public SessionMap(SQLiteAxolotlStore store, Account account) {
            super();
            this.fillMap(store, account);
        }

        private void fillMap(SQLiteAxolotlStore store, Account account) {
            for(Contact contact:account.getRoster().getContacts()){
                Jid bareJid = contact.getJid().toBareJid();
                if(bareJid == null) {
                    continue; // FIXME: handle this?
                }
                String address = bareJid.toString();
                List<Integer> deviceIDs = store.getSubDeviceSessions(address);
                for(Integer deviceId:deviceIDs) {
                    AxolotlAddress axolotlAddress = new AxolotlAddress(address, deviceId);
                    this.put(axolotlAddress, new XmppAxolotlSession(store, axolotlAddress));
                }
            }
        }

    }

    private static class BundleMap extends AxolotlAddressMap<PreKeyBundle> {

    }

    public AxolotlService(Account account, XmppConnectionService connectionService) {
        this.mXmppConnectionService = connectionService;
        this.account = account;
        this.axolotlStore = new SQLiteAxolotlStore(this.account, this.mXmppConnectionService);
        this.sessions = new SessionMap(axolotlStore, account);
        this.bundleCache = new BundleMap();
        this.ownDeviceId = axolotlStore.getLocalRegistrationId();
    }

    public void trustSession(AxolotlAddress counterpart) {
        XmppAxolotlSession session = sessions.get(counterpart);
        if(session != null) {
            session.trust();
        }
    }

    public boolean isTrustedSession(AxolotlAddress counterpart) {
        XmppAxolotlSession session = sessions.get(counterpart);
        return session != null && session.isTrusted();
    }

    private AxolotlAddress getAddressForJid(Jid jid) {
        return new AxolotlAddress(jid.toString(), 0);
    }

    private Set<XmppAxolotlSession> findOwnSessions() {
        AxolotlAddress ownAddress = getAddressForJid(account.getJid());
        Set<XmppAxolotlSession> ownDeviceSessions = new HashSet<>(this.sessions.getAll(ownAddress).values());
        return ownDeviceSessions;
    }

    private Set<XmppAxolotlSession> findSessionsforContact(Contact contact) {
        AxolotlAddress contactAddress = getAddressForJid(contact.getJid());
        Set<XmppAxolotlSession> sessions = new HashSet<>(this.sessions.getAll(contactAddress).values());
        return sessions;
    }

    private boolean hasAny(Contact contact) {
        AxolotlAddress contactAddress = getAddressForJid(contact.getJid());
        return sessions.hasAny(contactAddress);
    }

    public int getOwnDeviceId() {
        return ownDeviceId;
    }

    public void fetchBundleIfNeeded(final Contact contact, final Integer deviceId) {
        final AxolotlAddress address = new AxolotlAddress(contact.getJid().toString(), deviceId);
        if (sessions.get(address) != null) {
            return;
        }

        synchronized (bundleCache) {
            PreKeyBundle bundle = bundleCache.get(address);
            if (bundle == null) {
                bundle = new PreKeyBundle(0, deviceId, 0, null, 0, null, null, null);
                bundleCache.put(address, bundle);
            }

            if(bundle.getPreKey() == null) {
                Log.d(Config.LOGTAG, "No preKey in cache, fetching...");
                IqPacket prekeysPacket = mXmppConnectionService.getIqGenerator().retrievePreKeysForDevice(contact.getJid(), deviceId);
                mXmppConnectionService.sendIqPacket(account, prekeysPacket, new OnIqPacketReceived() {
                    @Override
                    public void onIqPacketReceived(Account account, IqPacket packet) {
                        synchronized (bundleCache) {
                            Log.d(Config.LOGTAG, "Received preKey IQ packet, processing...");
                            final IqParser parser = mXmppConnectionService.getIqParser();
                            final PreKeyBundle bundle = bundleCache.get(address);
                            final List<PreKeyBundle> preKeyBundleList = parser.preKeys(packet);
                            if (preKeyBundleList.isEmpty()) {
                                Log.d(Config.LOGTAG, "preKey IQ packet invalid: " + packet);
                                return;
                            }
                            Random random = new Random();
                            final PreKeyBundle newBundle = preKeyBundleList.get(random.nextInt(preKeyBundleList.size()));
                            if (bundle == null || newBundle == null) {
                                //should never happen
                                return;
                            }

                            final PreKeyBundle mergedBundle = new PreKeyBundle(bundle.getRegistrationId(),
                                    bundle.getDeviceId(), newBundle.getPreKeyId(), newBundle.getPreKey(),
                                    bundle.getSignedPreKeyId(), bundle.getSignedPreKey(),
                                    bundle.getSignedPreKeySignature(), bundle.getIdentityKey());

                            bundleCache.put(address, mergedBundle);
                        }
                    }
                });
            }
            if(bundle.getIdentityKey() == null) {
                Log.d(Config.LOGTAG, "No bundle in cache, fetching...");
                IqPacket bundlePacket = mXmppConnectionService.getIqGenerator().retrieveBundleForDevice(contact.getJid(), deviceId);
                mXmppConnectionService.sendIqPacket(account, bundlePacket, new OnIqPacketReceived() {
                    @Override
                    public void onIqPacketReceived(Account account, IqPacket packet) {
                        synchronized (bundleCache) {
                            Log.d(Config.LOGTAG, "Received bundle IQ packet, processing...");
                            final IqParser parser = mXmppConnectionService.getIqParser();
                            final PreKeyBundle bundle = bundleCache.get(address);
                            final PreKeyBundle newBundle = parser.bundle(packet);
                            if( bundle == null || newBundle == null ) {
                                Log.d(Config.LOGTAG, "bundle IQ packet invalid: " + packet);
                                //should never happen
                                return;
                            }

                            final PreKeyBundle mergedBundle = new PreKeyBundle(bundle.getRegistrationId(),
                                    bundle.getDeviceId(), bundle.getPreKeyId(), bundle.getPreKey(),
                                    newBundle.getSignedPreKeyId(), newBundle.getSignedPreKey(),
                                    newBundle.getSignedPreKeySignature(), newBundle.getIdentityKey());

                            axolotlStore.saveIdentity(contact.getJid().toBareJid().toString(), newBundle.getIdentityKey());
                            bundleCache.put(address, mergedBundle);
                        }
                    }
                });
            }
        }
    }

    public void publishOwnDeviceIdIfNeeded() {
        IqPacket packet = mXmppConnectionService.getIqGenerator().retrieveDeviceIds(account.getJid().toBareJid());
        mXmppConnectionService.sendIqPacket(account, packet, new OnIqPacketReceived() {
            @Override
            public void onIqPacketReceived(Account account, IqPacket packet) {
                Element item = mXmppConnectionService.getIqParser().getItem(packet);
                List<Integer> deviceIds = mXmppConnectionService.getIqParser().deviceIds(item);
                if(deviceIds == null) {
                    deviceIds = new ArrayList<>();
                }
                if(!deviceIds.contains(getOwnDeviceId())) {
                    Log.d(Config.LOGTAG, "Own device " + getOwnDeviceId() + " not in PEP devicelist. Publishing...");
                    deviceIds.add(getOwnDeviceId());
                    IqPacket publish = mXmppConnectionService.getIqGenerator().publishDeviceIds(deviceIds);
                    mXmppConnectionService.sendIqPacket(account, publish, new OnIqPacketReceived() {
                        @Override
                        public void onIqPacketReceived(Account account, IqPacket packet) {
                            // TODO: implement this!
                        }
                    });
                }
            }
        });
    }

    public void publishBundleIfNeeded() {
        IqPacket packet = mXmppConnectionService.getIqGenerator().retrieveBundleForDevice(account.getJid().toBareJid(), ownDeviceId);
        mXmppConnectionService.sendIqPacket(account, packet, new OnIqPacketReceived() {
            @Override
            public void onIqPacketReceived(Account account, IqPacket packet) {
                PreKeyBundle bundle = mXmppConnectionService.getIqParser().bundle(packet);
                if(bundle == null) {
                    Log.d(Config.LOGTAG, "Bundle " + getOwnDeviceId() + " not in PEP. Publishing...");
                    int numSignedPreKeys = axolotlStore.loadSignedPreKeys().size();
                    try {
                        SignedPreKeyRecord signedPreKeyRecord = KeyHelper.generateSignedPreKey(
                                axolotlStore.getIdentityKeyPair(), numSignedPreKeys + 1);
                        axolotlStore.storeSignedPreKey(signedPreKeyRecord.getId(), signedPreKeyRecord);
                        IqPacket publish = mXmppConnectionService.getIqGenerator().publishBundle(
                                signedPreKeyRecord, axolotlStore.getIdentityKeyPair().getPublicKey(),
                                ownDeviceId);
                        mXmppConnectionService.sendIqPacket(account, publish, new OnIqPacketReceived() {
                            @Override
                            public void onIqPacketReceived(Account account, IqPacket packet) {
                                // TODO: implement this!
                                Log.d(Config.LOGTAG, "Published bundle, got: " + packet);
                            }
                        });
                    } catch (InvalidKeyException e) {
                        Log.e(Config.LOGTAG, "Failed to publish bundle " + getOwnDeviceId() + ", reason: " + e.getMessage());
                    }
                }
            }
        });
    }

    public void publishPreKeysIfNeeded() {
        IqPacket packet = mXmppConnectionService.getIqGenerator().retrievePreKeysForDevice(account.getJid().toBareJid(), ownDeviceId);
        mXmppConnectionService.sendIqPacket(account, packet, new OnIqPacketReceived() {
            @Override
            public void onIqPacketReceived(Account account, IqPacket packet) {
                Map<Integer, ECPublicKey> keys = mXmppConnectionService.getIqParser().preKeyPublics(packet);
                if(keys == null || keys.isEmpty()) {
                    Log.d(Config.LOGTAG, "Prekeys " + getOwnDeviceId() + " not in PEP. Publishing...");
                    List<PreKeyRecord> preKeyRecords = KeyHelper.generatePreKeys(
                            axolotlStore.getCurrentPreKeyId(), 100);
                    for(PreKeyRecord record : preKeyRecords) {
                        axolotlStore.storePreKey(record.getId(), record);
                    }
                    IqPacket publish = mXmppConnectionService.getIqGenerator().publishPreKeys(
                            preKeyRecords, ownDeviceId);

                    mXmppConnectionService.sendIqPacket(account, publish, new OnIqPacketReceived() {
                        @Override
                        public void onIqPacketReceived(Account account, IqPacket packet) {
                            Log.d(Config.LOGTAG, "Published prekeys, got: " + packet);
                            // TODO: implement this!
                        }
                    });
                }
            }
        });
    }


    public boolean isContactAxolotlCapable(Contact contact) {
        AxolotlAddress address = new AxolotlAddress(contact.getJid().toBareJid().toString(), 0);
        return sessions.hasAny(address) || bundleCache.hasAny(address);
    }

    public void initiateSynchronousSession(Contact contact) {

    }

    private void createSessionsIfNeeded(Contact contact) throws NoSessionsCreatedException {
        Log.d(Config.LOGTAG, "Creating axolotl sessions if needed...");
        AxolotlAddress address = new AxolotlAddress(contact.getJid().toBareJid().toString(), 0);
        for(Integer deviceId: bundleCache.getAll(address).keySet()) {
            Log.d(Config.LOGTAG, "Processing device ID: " + deviceId);
            AxolotlAddress remoteAddress = new AxolotlAddress(contact.getJid().toBareJid().toString(), deviceId);
            if(sessions.get(remoteAddress) == null) {
                Log.d(Config.LOGTAG, "Building new sesstion for " + deviceId);
                SessionBuilder builder = new SessionBuilder(this.axolotlStore, remoteAddress);
                try {
                    builder.process(bundleCache.get(remoteAddress));
                    XmppAxolotlSession session = new XmppAxolotlSession(this.axolotlStore, remoteAddress);
                    sessions.put(remoteAddress, session);
                } catch (InvalidKeyException e) {
                    Log.d(Config.LOGTAG, "Error building session for " + deviceId+ ": InvalidKeyException, " +e.getMessage());
                } catch (UntrustedIdentityException e) {
                    Log.d(Config.LOGTAG, "Error building session for " + deviceId+ ": UntrustedIdentityException, " +e.getMessage());
                }
            } else {
                Log.d(Config.LOGTAG, "Already have session for " + deviceId);
            }
        }
        if(!this.hasAny(contact)) {
            Log.e(Config.LOGTAG, "No Axolotl sessions available!");
            throw new NoSessionsCreatedException(); // FIXME: proper error handling
        }
    }

    public XmppAxolotlMessage processSending(Contact contact, String outgoingMessage) throws NoSessionsCreatedException {
        XmppAxolotlMessage message = new XmppAxolotlMessage(contact, ownDeviceId, outgoingMessage);
        createSessionsIfNeeded(contact);
        Log.d(Config.LOGTAG, "Building axolotl foreign headers...");

        for(XmppAxolotlSession session : findSessionsforContact(contact)) {
//            if(!session.isTrusted()) {
                // TODO: handle this properly
  //              continue;
    //        }
            message.addHeader(session.processSending(message.getInnerKey()));
        }
        Log.d(Config.LOGTAG, "Building axolotl own headers...");
        for(XmppAxolotlSession session : findOwnSessions()) {
    //        if(!session.isTrusted()) {
                // TODO: handle this properly
      //          continue;
        //    }
            message.addHeader(session.processSending(message.getInnerKey()));
        }

        return message;
    }

    public XmppAxolotlMessage.XmppAxolotlPlaintextMessage processReceiving(XmppAxolotlMessage message) {
        XmppAxolotlMessage.XmppAxolotlPlaintextMessage plaintextMessage = null;
        AxolotlAddress senderAddress = new AxolotlAddress(message.getContact().getJid().toBareJid().toString(),
                message.getSenderDeviceId());

        XmppAxolotlSession session = sessions.get(senderAddress);
        if (session == null) {
            Log.d(Config.LOGTAG, "No axolotl session found while parsing received message " + message);
            // TODO: handle this properly
            session = new XmppAxolotlSession(axolotlStore, senderAddress);

        }

        for(XmppAxolotlMessage.XmppAxolotlMessageHeader header : message.getHeaders()) {
            if (header.getRecipientDeviceId() == ownDeviceId) {
                Log.d(Config.LOGTAG, "Found axolotl header matching own device ID, processing...");
                byte[] payloadKey = session.processReceiving(header);
                if (payloadKey != null) {
                    Log.d(Config.LOGTAG, "Got payload key from axolotl header. Decrypting message...");
                    plaintextMessage = message.decrypt(session, payloadKey);
                }
            }
        }

        return plaintextMessage;
    }
}
