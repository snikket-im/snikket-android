package eu.siacs.conversations.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.util.Log;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.persistance.UnifiedPushDatabase;
import eu.siacs.conversations.utils.Compatibility;

public class UnifiedPushDistributor extends BroadcastReceiver {

    public static final String ACTION_REGISTER = "org.unifiedpush.android.distributor.REGISTER";
    public static final String ACTION_UNREGISTER = "org.unifiedpush.android.distributor.UNREGISTER";
    public static final String ACTION_BYTE_MESSAGE =
            "org.unifiedpush.android.distributor.feature.BYTES_MESSAGE";
    public static final String ACTION_REGISTRATION_FAILED =
            "org.unifiedpush.android.connector.REGISTRATION_FAILED";
    public static final String ACTION_MESSAGE = "org.unifiedpush.android.connector.MESSAGE";
    public static final String ACTION_NEW_ENDPOINT =
            "org.unifiedpush.android.connector.NEW_ENDPOINT";

    public static final String PREFERENCE_ACCOUNT = "up_push_account";
    public static final String PREFERENCE_PUSH_SERVER = "up_push_server";

    public static final List<String> PREFERENCES =
            Arrays.asList(PREFERENCE_ACCOUNT, PREFERENCE_PUSH_SERVER);

    @Override
    public void onReceive(final Context context, final Intent intent) {
        if (intent == null) {
            return;
        }
        final String action = intent.getAction();
        final String application = intent.getStringExtra("application");
        final String instance = intent.getStringExtra("token");
        final List<String> features = intent.getStringArrayListExtra("features");
        switch (Strings.nullToEmpty(action)) {
            case ACTION_REGISTER:
                register(context, application, instance, features);
                break;
            case ACTION_UNREGISTER:
                unregister(context, instance);
                break;
            case Intent.ACTION_PACKAGE_FULLY_REMOVED:
                unregisterApplication(context, intent.getData());
                break;
            default:
                Log.d(Config.LOGTAG, "UnifiedPushDistributor received unknown action " + action);
                break;
        }
    }

    private void register(
            final Context context,
            final String application,
            final String instance,
            final Collection<String> features) {
        if (Strings.isNullOrEmpty(application) || Strings.isNullOrEmpty(instance)) {
            Log.w(Config.LOGTAG, "ignoring invalid UnifiedPush registration");
            return;
        }
        final List<String> receivers = getBroadcastReceivers(context, application);
        if (receivers.contains(application)) {
            final boolean byteMessage = features != null && features.contains(ACTION_BYTE_MESSAGE);
            Log.d(
                    Config.LOGTAG,
                    "received up registration from "
                            + application
                            + "/"
                            + instance
                            + " features: "
                            + features);
            if (UnifiedPushDatabase.getInstance(context).register(application, instance)) {
                Log.d(
                        Config.LOGTAG,
                        "successfully created UnifiedPush entry. waking up XmppConnectionService");
                final Intent serviceIntent = new Intent(context, XmppConnectionService.class);
                serviceIntent.setAction(XmppConnectionService.ACTION_RENEW_UNIFIED_PUSH_ENDPOINTS);
                serviceIntent.putExtra("instance", instance);
                Compatibility.startService(context, serviceIntent);
            } else {
                Log.d(Config.LOGTAG, "not successful. sending error message back to application");
                final Intent registrationFailed = new Intent(ACTION_REGISTRATION_FAILED);
                registrationFailed.setPackage(application);
                registrationFailed.putExtra("token", instance);
                context.sendBroadcast(registrationFailed);
            }
        } else {
            Log.d(
                    Config.LOGTAG,
                    "ignoring invalid UnifiedPush registration. Unknown application "
                            + application);
        }
    }

    private List<String> getBroadcastReceivers(final Context context, final String application) {
        final Intent messageIntent = new Intent(ACTION_MESSAGE);
        messageIntent.setPackage(application);
        final List<ResolveInfo> resolveInfo =
                context.getPackageManager().queryBroadcastReceivers(messageIntent, 0);
        return Lists.transform(
                resolveInfo, ri -> ri.activityInfo == null ? null : ri.activityInfo.packageName);
    }

    private void unregister(final Context context, final String instance) {
        if (Strings.isNullOrEmpty(instance)) {
            Log.w(Config.LOGTAG, "ignoring invalid UnifiedPush un-registration");
            return;
        }
        final UnifiedPushDatabase unifiedPushDatabase = UnifiedPushDatabase.getInstance(context);
        if (unifiedPushDatabase.deleteInstance(instance)) {
            Log.d(Config.LOGTAG, "successfully removed " + instance + " from UnifiedPush");
        }
    }

    private void unregisterApplication(final Context context, final Uri uri) {
        if (uri != null && "package".equalsIgnoreCase(uri.getScheme())) {
            final String application = uri.getSchemeSpecificPart();
            if (Strings.isNullOrEmpty(application)) {
                return;
            }
            Log.d(Config.LOGTAG, "app " + application + " has been removed from the system");
            final UnifiedPushDatabase database = UnifiedPushDatabase.getInstance(context);
            if (database.deleteApplication(application)) {
                Log.d(Config.LOGTAG, "successfully removed " + application + " from UnifiedPush");
            }
        }
    }

    public static String hash(String... components) {
        return BaseEncoding.base64()
                .encode(
                        Hashing.sha256()
                                .hashString(Joiner.on('\0').join(components), Charsets.UTF_8)
                                .asBytes());
    }
}
