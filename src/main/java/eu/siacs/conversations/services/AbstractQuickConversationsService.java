package eu.siacs.conversations.services;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import com.google.common.collect.Iterables;

import eu.siacs.conversations.BuildConfig;

import java.util.Arrays;

public abstract class AbstractQuickConversationsService {

    public static final String SMS_RETRIEVED_ACTION =
            "com.google.android.gms.auth.api.phone.SMS_RETRIEVED";

    private static Boolean declaredReadContacts = null;

    protected final XmppConnectionService service;

    public AbstractQuickConversationsService(XmppConnectionService service) {
        this.service = service;
    }

    public abstract void considerSync();

    public static boolean isQuicksy() {
        return "quicksy".equals(BuildConfig.FLAVOR_mode);
    }

    public static boolean isConversations() {
        return "conversations".equals(BuildConfig.FLAVOR_mode);
    }

    public static boolean isPlayStoreFlavor() {
        return "playstore".equals(BuildConfig.FLAVOR_distribution);
    }

    public static boolean isContactListIntegration(final Context context) {
        if ("quicksy".equals(BuildConfig.FLAVOR_mode)) {
            return true;
        }
        final var readContacts = AbstractQuickConversationsService.declaredReadContacts;
        if (readContacts != null) {
            return Boolean.TRUE.equals(readContacts);
        }
        AbstractQuickConversationsService.declaredReadContacts = hasDeclaredReadContacts(context);
        return AbstractQuickConversationsService.declaredReadContacts;
    }

    private static boolean hasDeclaredReadContacts(final Context context) {
        final String[] permissions;
        try {
            permissions =
                    context.getPackageManager()
                            .getPackageInfo(
                                    context.getPackageName(), PackageManager.GET_PERMISSIONS)
                            .requestedPermissions;
        } catch (final PackageManager.NameNotFoundException e) {
            return false;
        }
        return Iterables.any(
                Arrays.asList(permissions), p -> p.equals(Manifest.permission.READ_CONTACTS));
    }

    public static boolean isQuicksyPlayStore() {
        return isQuicksy() && isPlayStoreFlavor();
    }

    public abstract void signalAccountStateChange();

    public abstract boolean isSynchronizing();

    public abstract void considerSyncBackground(boolean force);

    public abstract void handleSmsReceived(Intent intent);
}
