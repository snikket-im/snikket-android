package eu.siacs.conversations.services;


import android.content.SharedPreferences;
import android.net.Uri;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLHandshakeException;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.android.JabberIdContact;
import eu.siacs.conversations.android.PhoneNumberContact;
import eu.siacs.conversations.crypto.sasl.Plain;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.utils.AccountUtils;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.PhoneNumberUtilWrapper;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.OnIqPacketReceived;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import io.michaelrocks.libphonenumber.android.Phonenumber;
import rocks.xmpp.addr.Jid;

public class QuickConversationsService extends AbstractQuickConversationsService {


    public static final int API_ERROR_OTHER = -1;
    public static final int API_ERROR_UNKNOWN_HOST = -2;
    public static final int API_ERROR_CONNECT = -3;
    public static final int API_ERROR_SSL_HANDSHAKE = -4;
    public static final int API_ERROR_AIRPLANE_MODE = -5;

    private static final String BASE_URL = "http://venus.fritz.box:4567";

    private static final String INSTALLATION_ID = "eu.siacs.conversations.installation-id";

    private final Set<OnVerificationRequested> mOnVerificationRequested = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<OnVerification> mOnVerification = Collections.newSetFromMap(new WeakHashMap<>());

    private final AtomicBoolean mVerificationInProgress = new AtomicBoolean(false);
    private final AtomicBoolean mVerificationRequestInProgress = new AtomicBoolean(false);

    private Attempt mLastSyncAttempt = new Attempt(0,0);

    QuickConversationsService(XmppConnectionService xmppConnectionService) {
        super(xmppConnectionService);
    }

    private static long retryAfter(HttpURLConnection connection) {
        try {
            return SystemClock.elapsedRealtime() + (Long.parseLong(connection.getHeaderField("Retry-After")) * 1000L);
        } catch (Exception e) {
            return 0;
        }
    }

    public void addOnVerificationRequestedListener(OnVerificationRequested onVerificationRequested) {
        synchronized (mOnVerificationRequested) {
            mOnVerificationRequested.add(onVerificationRequested);
        }
    }

    public void removeOnVerificationRequestedListener(OnVerificationRequested onVerificationRequested) {
        synchronized (mOnVerificationRequested) {
            mOnVerificationRequested.remove(onVerificationRequested);
        }
    }

    public void addOnVerificationListener(OnVerification onVerification) {
        synchronized (mOnVerification) {
            mOnVerification.add(onVerification);
        }
    }

    public void removeOnVerificationListener(OnVerification onVerification) {
        synchronized (mOnVerification) {
            mOnVerification.remove(onVerification);
        }
    }

    public void requestVerification(Phonenumber.PhoneNumber phoneNumber) {
        final String e164 = PhoneNumberUtilWrapper.normalize(service, phoneNumber);
        if (mVerificationRequestInProgress.compareAndSet(false, true)) {
            new Thread(() -> {
                try {

                    Thread.sleep(5000);

                    final URL url = new URL(BASE_URL + "/authentication/" + e164);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(Config.SOCKET_TIMEOUT * 1000);
                    connection.setReadTimeout(Config.SOCKET_TIMEOUT * 1000);
                    setHeader(connection);
                    final int code = connection.getResponseCode();
                    if (code == 200) {
                        createAccountAndWait(phoneNumber, 0L);
                    } else if (code == 429) {
                        createAccountAndWait(phoneNumber, retryAfter(connection));
                    } else {
                        synchronized (mOnVerificationRequested) {
                            for (OnVerificationRequested onVerificationRequested : mOnVerificationRequested) {
                                onVerificationRequested.onVerificationRequestFailed(code);
                            }
                        }
                    }
                } catch (Exception e) {
                    final int code = getApiErrorCode(e);
                    synchronized (mOnVerificationRequested) {
                        for (OnVerificationRequested onVerificationRequested : mOnVerificationRequested) {
                            onVerificationRequested.onVerificationRequestFailed(code);
                        }
                    }
                } finally {
                    mVerificationRequestInProgress.set(false);
                }
            }).start();
        }


    }

    private void createAccountAndWait(Phonenumber.PhoneNumber phoneNumber, final long timestamp) {
        String local = PhoneNumberUtilWrapper.normalize(service, phoneNumber);
        Log.d(Config.LOGTAG, "requesting verification for " + PhoneNumberUtilWrapper.normalize(service, phoneNumber));
        Jid jid = Jid.of(local, Config.QUICKSY_DOMAIN, null);
        Account account = AccountUtils.getFirst(service);
        if (account == null || !account.getJid().asBareJid().equals(jid.asBareJid())) {
            if (account != null) {
                service.deleteAccount(account);
            }
            account = new Account(jid, CryptoHelper.createPassword(new SecureRandom()));
            account.setOption(Account.OPTION_DISABLED, true);
            account.setOption(Account.OPTION_UNVERIFIED, true);
            service.createAccount(account);
        }
        synchronized (mOnVerificationRequested) {
            for (OnVerificationRequested onVerificationRequested : mOnVerificationRequested) {
                if (timestamp <= 0) {
                    onVerificationRequested.onVerificationRequested();
                } else {
                    onVerificationRequested.onVerificationRequestedRetryAt(timestamp);
                }
            }
        }
    }

    public void verify(final Account account, String pin) {
        if (mVerificationInProgress.compareAndSet(false, true)) {
            new Thread(() -> {
                try {

                    Thread.sleep(5000);

                    final URL url = new URL(BASE_URL + "/password");
                    final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(Config.SOCKET_TIMEOUT * 1000);
                    connection.setReadTimeout(Config.SOCKET_TIMEOUT * 1000);
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Authorization", Plain.getMessage(account.getUsername(), pin));
                    setHeader(connection);
                    final OutputStream os = connection.getOutputStream();
                    final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
                    writer.write(account.getPassword());
                    writer.flush();
                    writer.close();
                    os.close();
                    connection.connect();
                    final int code = connection.getResponseCode();
                    if (code == 200) {
                        account.setOption(Account.OPTION_UNVERIFIED, false);
                        account.setOption(Account.OPTION_DISABLED, false);
                        service.updateAccount(account);
                        synchronized (mOnVerification) {
                            for (OnVerification onVerification : mOnVerification) {
                                onVerification.onVerificationSucceeded();
                            }
                        }
                    } else if (code == 429) {
                        final long retryAfter = retryAfter(connection);
                        synchronized (mOnVerification) {
                            for (OnVerification onVerification : mOnVerification) {
                                onVerification.onVerificationRetryAt(retryAfter);
                            }
                        }
                    } else {
                        synchronized (mOnVerification) {
                            for (OnVerification onVerification : mOnVerification) {
                                onVerification.onVerificationFailed(code);
                            }
                        }
                    }
                } catch (Exception e) {
                    final int code = getApiErrorCode(e);
                    synchronized (mOnVerification) {
                        for (OnVerification onVerification : mOnVerification) {
                            onVerification.onVerificationFailed(code);
                        }
                    }
                } finally {
                    mVerificationInProgress.set(false);
                }
            }).start();
        }
    }

    private void setHeader(HttpURLConnection connection) {
        connection.setRequestProperty("User-Agent", service.getIqGenerator().getUserAgent());
        connection.setRequestProperty("Installation-Id", getInstallationId());
        connection.setRequestProperty("Accept-Language", Locale.getDefault().getLanguage());
    }

    private String getInstallationId() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(service);
        String id = preferences.getString(INSTALLATION_ID, null);
        if (id != null) {
            return id;
        } else {
            id = UUID.randomUUID().toString();
            preferences.edit().putString(INSTALLATION_ID, id).apply();
            return id;
        }

    }

    private int getApiErrorCode(Exception e) {
        if (!service.hasInternetConnection()) {
            return API_ERROR_AIRPLANE_MODE;
        } else if (e instanceof UnknownHostException) {
            return API_ERROR_UNKNOWN_HOST;
        } else if (e instanceof ConnectException) {
            return API_ERROR_CONNECT;
        } else if (e instanceof SSLHandshakeException) {
            return API_ERROR_SSL_HANDSHAKE;
        } else {
            Log.d(Config.LOGTAG, e.getClass().getName());
            return API_ERROR_OTHER;
        }
    }

    public boolean isVerifying() {
        return mVerificationInProgress.get();
    }

    public boolean isRequestingVerification() {
        return mVerificationRequestInProgress.get();
    }

    @Override
    public void considerSync() {
        Map<String, PhoneNumberContact> contacts = PhoneNumberContact.load(service);
        for (Account account : service.getAccounts()) {
            refresh(account, contacts.values());
            if (!considerSync(account, contacts)) {
                service.syncRoster(account);
            }
        }
    }

    private void refresh(Account account, Collection<PhoneNumberContact> contacts) {
        for(Contact contact : account.getRoster().getWithSystemAccounts(PhoneNumberContact.class)) {
            final Uri uri = contact.getSystemAccount();
            if (uri == null) {
                continue;
            }
            PhoneNumberContact phoneNumberContact = findByUri(contacts, uri);
            final boolean needsCacheClean;
            if (phoneNumberContact != null) {
                needsCacheClean = contact.setPhoneContact(phoneNumberContact);
            } else {
                needsCacheClean = contact.unsetPhoneContact(PhoneNumberContact.class);
                Log.d(Config.LOGTAG,uri.toString()+" vanished from address book");
            }
            if (needsCacheClean) {
                service.getAvatarService().clear(contact);
            }
        }
    }

    private boolean considerSync(Account account, final Map<String, PhoneNumberContact> contacts) {
        final int hash = contacts.keySet().hashCode();
        Log.d(Config.LOGTAG,account.getJid().asBareJid()+": consider sync of "+hash);
        if (!mLastSyncAttempt.retry(hash)) {
            Log.d(Config.LOGTAG,account.getJid().asBareJid()+": do not attempt sync");
            return false;
        }
        XmppConnection xmppConnection = account.getXmppConnection();
        Jid syncServer = xmppConnection == null ? null : xmppConnection.findDiscoItemByFeature(Namespace.SYNCHRONIZATION);
        if (syncServer == null) {
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": skipping sync. no sync server found");
            return false;
        }
        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": sending phone list to " + syncServer);
        List<Element> entries = new ArrayList<>();
        for (PhoneNumberContact c : contacts.values()) {
            entries.add(new Element("entry").setAttribute("number", c.getPhoneNumber()));
        }
        IqPacket query = new IqPacket(IqPacket.TYPE.GET);
        query.setTo(syncServer);
        query.addChild(new Element("phone-book", Namespace.SYNCHRONIZATION).setChildren(entries));
        mLastSyncAttempt = Attempt.create(hash);
        service.sendIqPacket(account, query, (a, response) -> {
            if (response.getType() == IqPacket.TYPE.RESULT) {
                List<Contact> withSystemAccounts = account.getRoster().getWithSystemAccounts(PhoneNumberContact.class);
                final Element phoneBook = response.findChild("phone-book", Namespace.SYNCHRONIZATION);
                if (phoneBook != null) {
                    for(Entry entry : Entry.ofPhoneBook(phoneBook)) {
                        PhoneNumberContact phoneContact = contacts.get(entry.getNumber());
                        if (phoneContact == null) {
                            continue;
                        }
                        for(Jid jid : entry.getJids()) {
                            Contact contact = account.getRoster().getContact(jid);
                            final boolean needsCacheClean = contact.setPhoneContact(phoneContact);
                            if (needsCacheClean) {
                                service.getAvatarService().clear(contact);
                            }
                            withSystemAccounts.remove(contact);
                        }
                    }
                }
                for (Contact contact : withSystemAccounts) {
                    final boolean needsCacheClean = contact.unsetPhoneContact(PhoneNumberContact.class);
                    if (needsCacheClean) {
                        service.getAvatarService().clear(contact);
                    }
                }
            }
            service.syncRoster(account);
            service.updateRosterUi();
        });
        return true;
    }

    private static PhoneNumberContact findByUri(Collection<PhoneNumberContact> haystack, Uri needle) {
        for(PhoneNumberContact contact : haystack) {
            if (needle.equals(contact.getLookupUri())) {
                return contact;
            }
        }
        return null;
    }

    private static class Attempt {
        private final long timestamp;
        private int hash;

        private Attempt(long timestamp, int hash) {
            this.timestamp = timestamp;
            this.hash = hash;
        }

        public static Attempt create(int hash) {
            return new Attempt(SystemClock.elapsedRealtime(), hash);
        }

        public boolean retry(int hash) {
            return hash != this.hash || SystemClock.elapsedRealtime() - timestamp >= Config.CONTACT_SYNC_RETRY_INTERVAL;
        }
    }

    public static class Entry {
        private final List<Jid> jids;
        private final String number;

        private Entry(String number, List<Jid> jids) {
            this.number = number;
            this.jids = jids;
        }

        public static Entry of(Element element) {
            final String number = element.getAttribute("number");
            final List<Jid> jids = new ArrayList<>();
            for (Element jidElement : element.getChildren()) {
                String content = jidElement.getContent();
                if (content != null) {
                    jids.add(Jid.of(content));
                }
            }
            return new Entry(number, jids);
        }

        public static List<Entry> ofPhoneBook(Element phoneBook) {
            List<Entry> entries = new ArrayList<>();
            for (Element entry : phoneBook.getChildren()) {
                if ("entry".equals(entry.getName())) {
                    entries.add(of(entry));
                }
            }
            return entries;
        }

        public List<Jid> getJids() {
            return jids;
        }

        public String getNumber() {
            return number;
        }
    }

    public interface OnVerificationRequested {
        void onVerificationRequestFailed(int code);

        void onVerificationRequested();

        void onVerificationRequestedRetryAt(long timestamp);
    }

    public interface OnVerification {
        void onVerificationFailed(int code);

        void onVerificationSucceeded();

        void onVerificationRetryAt(long timestamp);
    }
}