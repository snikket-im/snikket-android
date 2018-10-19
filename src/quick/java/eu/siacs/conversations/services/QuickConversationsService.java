package eu.siacs.conversations.services;


import android.util.Log;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.utils.PhoneNumberUtilWrapper;
import io.michaelrocks.libphonenumber.android.Phonenumber;
import rocks.xmpp.addr.Jid;

public class QuickConversationsService {

    private final XmppConnectionService service;

    private final Set<OnVerificationRequested> mOnVerificationRequested = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<OnVerified> mOnVerified = Collections.newSetFromMap(new WeakHashMap<>());

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

    public void requestVerification(Phonenumber.PhoneNumber phoneNumber) {
        String local = PhoneNumberUtilWrapper.normalize(service, phoneNumber);
        Log.d(Config.LOGTAG,"requesting verification for "+PhoneNumberUtilWrapper.normalize(service,phoneNumber));
        Account account = new Account(Jid.of(local,"quick.conversations.im",null),"foo");
        service.createAccount(account);
        synchronized (mOnVerificationRequested) {
            for(OnVerificationRequested onVerificationRequested : mOnVerificationRequested) {
                onVerificationRequested.onVerificationRequested();
            }
        }
    }

    public interface OnVerificationRequested {
        void onVerificationRequestFailed(int code);
        void onVerificationRequested();
    }

    public interface OnVerified {
        void onVerificationFailed();
        void onVerificationSucceeded();
    }
}