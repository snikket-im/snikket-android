package eu.siacs.conversations.services;

import android.content.Context;
import eu.siacs.conversations.entities.Account;

public class PushManagementService {

    protected final Context context;

    public PushManagementService(final Context context) {
        this.context = context;
    }

    public void registerPushTokenOnServer(Account account) {
        // stub implementation. only affects PlayStore flavor
    }

    public boolean available(Account account) {
        return false;
    }

    public static boolean isStub() {
        return true;
    }
}
