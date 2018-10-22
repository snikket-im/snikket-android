package eu.siacs.conversations.services;


import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.crypto.sasl.Plain;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.utils.AccountUtils;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.PhoneNumberUtilWrapper;
import io.michaelrocks.libphonenumber.android.Phonenumber;
import rocks.xmpp.addr.Jid;

public class QuickConversationsService {


    public static final int API_ERROR_OTHER = -1;
    public static final int API_ERROR_UNKNOWN_HOST = -2;
    public static final int API_ERROR_CONNECT = -3;

    private static final String BASE_URL = "https://venus.fritz.box:4567";

    private final XmppConnectionService service;

    private final Set<OnVerificationRequested> mOnVerificationRequested = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<OnVerification> mOnVerification = Collections.newSetFromMap(new WeakHashMap<>());

    private final AtomicBoolean mVerificationInProgress = new AtomicBoolean(false);
    private final AtomicBoolean mVerificationRequestInProgress = new AtomicBoolean(false);

    QuickConversationsService(XmppConnectionService xmppConnectionService) {
        this.service = xmppConnectionService;
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

        /**
         * GET /authentication/+phoneNumber
         *
         * - returns too many requests, (sms ist unterwegs), retry after seconden -- auf jeden fall in nächste activity (verify activity) weiter leiten weil es sein kann das sms noch ankommt
         * - returns OK; success (auch in activity weiter lassen. aber ohne error paramater; dh send again button is activ; und vielleicht kurzer toast bzw snackbar
         * - returns invalid request user error wenn die phone number falsch ist
         */
        if (mVerificationRequestInProgress.compareAndSet(false, true)) {
            new Thread(() -> {
                try {

                    Thread.sleep(5000);

                    final URL url = new URL(BASE_URL + "/authentication/" + e164);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(Config.SOCKET_TIMEOUT * 1000);
                    connection.setReadTimeout(Config.SOCKET_TIMEOUT * 1000);
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
        Jid jid = Jid.of(local, "quick.conversations.im", null);
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

    public void verify(Account account, String pin) {
        /**
         * POST /password
         * authentication gesetzt mit telephone nummber und verification code
         * body = password
         *
         * retry after, too many requests
         * code wrong
         * OK (weiterleiten auf publish avatar activity
         *
         */
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
                    final OutputStream os = connection.getOutputStream();
                    final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
                    writer.write(account.getPassword());
                    writer.flush();
                    writer.close();
                    os.close();
                    connection.connect();
                    final int code = connection.getResponseCode();
                    if (code == 200) {
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

    private static int getApiErrorCode(Exception e) {
        if (e instanceof UnknownHostException) {
            return API_ERROR_UNKNOWN_HOST;
        } else if (e instanceof ConnectException) {
            return API_ERROR_CONNECT;
        } else {
            Log.d(Config.LOGTAG,e.getClass().getName());
            return API_ERROR_OTHER;
        }
    }

    private static long retryAfter(HttpURLConnection connection) {
        try {
            return SystemClock.elapsedRealtime() + (Long.parseLong(connection.getHeaderField("Retry-After")) * 1000L);
        } catch (Exception e) {
            return 0;
        }
    }

    public boolean isVerifying() {
        return mVerificationInProgress.get();
    }

    public boolean isRequestingVerification() {
        return mVerificationRequestInProgress.get();
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