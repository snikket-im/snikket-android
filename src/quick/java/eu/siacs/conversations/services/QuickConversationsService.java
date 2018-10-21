package eu.siacs.conversations.services;


import android.util.Log;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.PhoneNumberUtilWrapper;
import io.michaelrocks.libphonenumber.android.Phonenumber;
import rocks.xmpp.addr.Jid;

public class QuickConversationsService {

    private final XmppConnectionService service;

    private final Set<OnVerificationRequested> mOnVerificationRequested = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<OnVerification> mOnVerification = Collections.newSetFromMap(new WeakHashMap<>());

    private final AtomicBoolean mVerificationInProgress = new AtomicBoolean(false);

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
        String local = PhoneNumberUtilWrapper.normalize(service, phoneNumber);
        Log.d(Config.LOGTAG,"requesting verification for "+PhoneNumberUtilWrapper.normalize(service,phoneNumber));
        Account account = new Account(Jid.of(local,"quick.conversations.im",null), CryptoHelper.createPassword(new SecureRandom()));
        account.setOption(Account.OPTION_DISABLED, true);
        account.setOption(Account.OPTION_UNVERIFIED, true);
        service.createAccount(account);
        synchronized (mOnVerificationRequested) {
            for(OnVerificationRequested onVerificationRequested : mOnVerificationRequested) {
                onVerificationRequested.onVerificationRequested();
            }
        }
    }

    public void verify(Account account, String pin) {
        if (mVerificationInProgress.compareAndSet(false, true)) {
            new Thread(() -> {
                try {
                    Thread.sleep(5000);
                    synchronized (mOnVerification) {
                        for (OnVerification onVerification : mOnVerification) {
                            onVerification.onVerificationFailed();
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    mVerificationInProgress.set(false);
                }
            }).start();
        }
    }

    public boolean isVerifying() {
        return mVerificationInProgress.get();
    }

    public interface OnVerificationRequested {
        void onVerificationRequestFailed(int code);
        void onVerificationRequested();
    }

    public interface OnVerification {
        void onVerificationFailed();
        void onVerificationSucceeded();
    }
}