package eu.siacs.conversations.crypto.axolotl;

import android.util.Log;

import org.whispersystems.libaxolotl.AxolotlAddress;
import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.IdentityKeyPair;
import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.InvalidKeyIdException;
import org.whispersystems.libaxolotl.ecc.Curve;
import org.whispersystems.libaxolotl.ecc.ECKeyPair;
import org.whispersystems.libaxolotl.state.AxolotlStore;
import org.whispersystems.libaxolotl.state.PreKeyRecord;
import org.whispersystems.libaxolotl.state.SessionRecord;
import org.whispersystems.libaxolotl.state.SignedPreKeyRecord;
import org.whispersystems.libaxolotl.util.KeyHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;

public class AxolotlService {

    private Account account;
    private XmppConnectionService mXmppConnectionService;
    private SQLiteAxolotlStore axolotlStore;
    private Map<Jid,XmppAxolotlSession> sessions;

    public static class SQLiteAxolotlStore implements AxolotlStore {

        public static final String PREKEY_TABLENAME = "prekeys";
        public static final String SIGNED_PREKEY_TABLENAME = "signed_prekeys";
        public static final String SESSION_TABLENAME = "signed_prekeys";
        public static final String NAME = "name";
        public static final String DEVICE_ID = "device_id";
        public static final String ID = "id";
        public static final String KEY = "key";
        public static final String ACCOUNT = "account";

        public static final String JSONKEY_IDENTITY_KEY_PAIR = "axolotl_key";
        public static final String JSONKEY_REGISTRATION_ID = "axolotl_reg_id";

        private final Account account;
        private final XmppConnectionService mXmppConnectionService;

        private final IdentityKeyPair identityKeyPair;
        private final int localRegistrationId;


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
        }

        // --------------------------------------
        // IdentityKeyStore
        // --------------------------------------

        private IdentityKeyPair loadIdentityKeyPair() {
            String serializedKey = this.account.getKey(JSONKEY_IDENTITY_KEY_PAIR);
            IdentityKeyPair ownKey;
            if( serializedKey != null ) {
                try {
                    ownKey = new IdentityKeyPair(serializedKey.getBytes());
                } catch (InvalidKeyException e) {
                    Log.d(Config.LOGTAG, "Invalid key stored for account " + account.getJid() + ": " + e.getMessage());
                    return null;
                }
            } else {
                Log.d(Config.LOGTAG, "Could not retrieve axolotl key for account " + account.getJid());
                ownKey = generateIdentityKeyPair();
                boolean success = this.account.setKey(JSONKEY_IDENTITY_KEY_PAIR, new String(ownKey.serialize()));
                if(success) {
                    mXmppConnectionService.databaseBackend.updateAccount(account);
                } else {
                    Log.e(Config.LOGTAG, "Failed to write new key to the database!");
                }
            }
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
                    conversation.getContact().addAxolotlIdentityKey(identityKey, false);
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
                    List<IdentityKey> trustedKeys = conversation.getContact().getTrustedAxolotlIdentityKeys();
                    return trustedKeys.contains(identityKey);
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
                    new AxolotlAddress(name,0));
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
                throw new InvalidKeyIdException("No such PreKeyRecord!");
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
                throw new InvalidKeyIdException("No such PreKeyRecord!");
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

    private static class XmppAxolotlSession {
        private List<Message> untrustedMessages;
        private AxolotlStore axolotlStore;

        public XmppAxolotlSession(SQLiteAxolotlStore axolotlStore) {
            this.untrustedMessages = new ArrayList<>();
            this.axolotlStore = axolotlStore;
        }

        public void trust() {
            for (Message message : this.untrustedMessages) {
                message.trust();
            }
            this.untrustedMessages = null;
        }

        public boolean isTrusted() {
            return (this.untrustedMessages == null);
        }

        public String processReceiving(XmppAxolotlMessage incomingMessage) {
            return null;
        }

        public XmppAxolotlMessage processSending(String outgoingMessage) {
            return null;
        }
    }

    public AxolotlService(Account account, XmppConnectionService connectionService) {
        this.mXmppConnectionService = connectionService;
        this.account = account;
        this.axolotlStore = new SQLiteAxolotlStore(this.account, this.mXmppConnectionService);
        this.sessions = new HashMap<>();
    }

    public void trustSession(Jid counterpart) {
        XmppAxolotlSession session = sessions.get(counterpart);
        if(session != null) {
            session.trust();
        }
    }

    public boolean isTrustedSession(Jid counterpart) {
        XmppAxolotlSession session = sessions.get(counterpart);
        return session != null && session.isTrusted();
    }


}
