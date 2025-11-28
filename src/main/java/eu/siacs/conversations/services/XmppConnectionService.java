package eu.siacs.conversations.services;

import static eu.siacs.conversations.utils.Compatibility.s;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.security.KeyChain;
import android.util.Log;
import android.util.LruCache;
import android.util.Pair;
import androidx.annotation.IntegerRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.RemoteInput;
import androidx.core.content.ContextCompat;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.AppSettings;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.Conversations;
import eu.siacs.conversations.R;
import eu.siacs.conversations.android.JabberIdContact;
import eu.siacs.conversations.crypto.OmemoSetting;
import eu.siacs.conversations.crypto.PgpDecryptionService;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.crypto.axolotl.FingerprintStatus;
import eu.siacs.conversations.crypto.axolotl.XmppAxolotlMessage;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Blockable;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Conversational;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.PresenceTemplate;
import eu.siacs.conversations.entities.Presences;
import eu.siacs.conversations.entities.Reaction;
import eu.siacs.conversations.generator.AbstractGenerator;
import eu.siacs.conversations.generator.IqGenerator;
import eu.siacs.conversations.generator.MessageGenerator;
import eu.siacs.conversations.http.HttpConnectionManager;
import eu.siacs.conversations.http.ServiceOutageStatus;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.persistance.UnifiedPushDatabase;
import eu.siacs.conversations.receiver.SystemEventReceiver;
import eu.siacs.conversations.ui.ChooseAccountForProfilePictureActivity;
import eu.siacs.conversations.ui.RtpSessionActivity;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.ui.interfaces.OnAvatarPublication;
import eu.siacs.conversations.ui.interfaces.OnMediaLoaded;
import eu.siacs.conversations.ui.interfaces.OnSearchResultsAvailable;
import eu.siacs.conversations.utils.AccountUtils;
import eu.siacs.conversations.utils.Compatibility;
import eu.siacs.conversations.utils.ConversationsFileObserver;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.Emoticons;
import eu.siacs.conversations.utils.MimeUtils;
import eu.siacs.conversations.utils.PhoneHelper;
import eu.siacs.conversations.utils.QuickLoader;
import eu.siacs.conversations.utils.ReplacingSerialSingleThreadExecutor;
import eu.siacs.conversations.utils.Resolver;
import eu.siacs.conversations.utils.SerialSingleThreadExecutor;
import eu.siacs.conversations.utils.TorServiceUtils;
import eu.siacs.conversations.utils.WakeLockHelper;
import eu.siacs.conversations.utils.XmppUri;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.LocalizedContent;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.OnContactStatusChanged;
import eu.siacs.conversations.xmpp.OnKeyStatusUpdated;
import eu.siacs.conversations.xmpp.OnUpdateBlocklist;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.forms.Data;
import eu.siacs.conversations.xmpp.jingle.AbstractJingleConnection;
import eu.siacs.conversations.xmpp.jingle.JingleConnectionManager;
import eu.siacs.conversations.xmpp.jingle.Media;
import eu.siacs.conversations.xmpp.jingle.RtpEndUserState;
import eu.siacs.conversations.xmpp.mam.MamReference;
import eu.siacs.conversations.xmpp.manager.ActivityManager;
import eu.siacs.conversations.xmpp.manager.AvatarManager;
import eu.siacs.conversations.xmpp.manager.BlockingManager;
import eu.siacs.conversations.xmpp.manager.BookmarkManager;
import eu.siacs.conversations.xmpp.manager.ChatStateManager;
import eu.siacs.conversations.xmpp.manager.DisplayedManager;
import eu.siacs.conversations.xmpp.manager.MessageArchiveManager;
import eu.siacs.conversations.xmpp.manager.MultiUserChatManager;
import eu.siacs.conversations.xmpp.manager.NickManager;
import eu.siacs.conversations.xmpp.manager.PepManager;
import eu.siacs.conversations.xmpp.manager.PresenceManager;
import eu.siacs.conversations.xmpp.manager.RegistrationManager;
import eu.siacs.conversations.xmpp.manager.RosterManager;
import eu.siacs.conversations.xmpp.manager.VCardManager;
import eu.siacs.conversations.xmpp.pep.Avatar;
import im.conversations.android.model.Bookmark;
import im.conversations.android.model.ImmutableBookmark;
import im.conversations.android.xmpp.model.muc.Affiliation;
import im.conversations.android.xmpp.model.muc.Role;
import im.conversations.android.xmpp.model.stanza.Iq;
import im.conversations.android.xmpp.model.up.Push;
import java.io.File;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import me.leolin.shortcutbadger.ShortcutBadger;
import okhttp3.HttpUrl;
import org.jxmpp.stringprep.libidn.LibIdnXmppStringprep;
import org.openintents.openpgp.IOpenPgpService2;
import org.openintents.openpgp.util.OpenPgpApi;
import org.openintents.openpgp.util.OpenPgpServiceConnection;

public class XmppConnectionService extends Service {

    public static final String ACTION_REPLY_TO_CONVERSATION = "reply_to_conversations";
    public static final String ACTION_MARK_AS_READ = "mark_as_read";
    public static final String ACTION_SNOOZE = "snooze";
    public static final String ACTION_CLEAR_MESSAGE_NOTIFICATION = "clear_message_notification";
    public static final String ACTION_CLEAR_MISSED_CALL_NOTIFICATION =
            "clear_missed_call_notification";
    public static final String ACTION_DISMISS_ERROR_NOTIFICATIONS = "dismiss_error";
    public static final String ACTION_TRY_AGAIN = "try_again";

    public static final String ACTION_TEMPORARILY_DISABLE = "temporarily_disable";
    public static final String ACTION_PING = "ping";
    public static final String ACTION_IDLE_PING = "idle_ping";
    public static final String ACTION_INTERNAL_PING = "internal_ping";
    public static final String ACTION_FCM_TOKEN_REFRESH = "fcm_token_refresh";
    public static final String ACTION_FCM_MESSAGE_RECEIVED = "fcm_message_received";
    public static final String ACTION_DISMISS_CALL = "dismiss_call";
    public static final String ACTION_END_CALL = "end_call";
    public static final String ACTION_PROVISION_ACCOUNT = "provision_account";
    public static final String ACTION_CALL_INTEGRATION_SERVICE_STARTED =
            "call_integration_service_started";
    private static final String ACTION_POST_CONNECTIVITY_CHANGE =
            "eu.siacs.conversations.POST_CONNECTIVITY_CHANGE";
    public static final String ACTION_RENEW_UNIFIED_PUSH_ENDPOINTS =
            "eu.siacs.conversations.UNIFIED_PUSH_RENEW";
    public static final String ACTION_QUICK_LOG = "eu.siacs.conversations.QUICK_LOG";

    private static final String SETTING_LAST_ACTIVITY_TS = "last_activity_timestamp";

    public final CountDownLatch restoredFromDatabaseLatch = new CountDownLatch(1);
    private static final Executor FILE_OBSERVER_EXECUTOR = Executors.newSingleThreadExecutor();
    public static final Executor FILE_ATTACHMENT_EXECUTOR = Executors.newSingleThreadExecutor();

    public final ScheduledExecutorService internalPingExecutor =
            Executors.newSingleThreadScheduledExecutor();
    private static final SerialSingleThreadExecutor VIDEO_COMPRESSION_EXECUTOR =
            new SerialSingleThreadExecutor("VideoCompression");
    private final SerialSingleThreadExecutor mDatabaseWriterExecutor =
            new SerialSingleThreadExecutor("DatabaseWriter");
    private final SerialSingleThreadExecutor mDatabaseReaderExecutor =
            new SerialSingleThreadExecutor("DatabaseReader");
    private final SerialSingleThreadExecutor mNotificationExecutor =
            new SerialSingleThreadExecutor("NotificationExecutor");
    private final IBinder mBinder = new XmppConnectionBinder();
    private final List<Conversation> conversations = new CopyOnWriteArrayList<>();
    private final IqGenerator mIqGenerator = new IqGenerator(this);
    public final HashSet<Jid> mLowPingTimeoutMode = new HashSet<>();
    public DatabaseBackend databaseBackend;
    private final ReplacingSerialSingleThreadExecutor mContactMergerExecutor =
            new ReplacingSerialSingleThreadExecutor("ContactMerger");
    private long mLastActivity = 0;

    private final AppSettings appSettings = new AppSettings(this);
    private final FileBackend fileBackend = new FileBackend(this);
    private MemorizingTrustManager mMemorizingTrustManager;
    private final NotificationService mNotificationService = new NotificationService(this);
    private final UnifiedPushBroker unifiedPushBroker = new UnifiedPushBroker(this);
    private final ChannelDiscoveryService mChannelDiscoveryService =
            new ChannelDiscoveryService(this);
    private final ShortcutService mShortcutService = new ShortcutService(this);
    private final AtomicBoolean mInitialAddressbookSyncCompleted = new AtomicBoolean(false);
    private final AtomicBoolean mOngoingVideoTranscoding = new AtomicBoolean(false);
    private final AtomicBoolean mForceDuringOnCreate = new AtomicBoolean(false);
    private final AtomicReference<OngoingCall> ongoingCall = new AtomicReference<>();
    private final MessageGenerator mMessageGenerator = new MessageGenerator(this);
    public OnContactStatusChanged onContactStatusChanged =
            (contact, online) -> {
                final var conversation = find(contact);
                if (conversation == null) {
                    return;
                }
                if (online) {
                    if (contact.getPresences().size() == 1) {
                        sendUnsentMessages(conversation);
                    }
                }
            };
    private List<Account> accounts;
    private final JingleConnectionManager mJingleConnectionManager =
            new JingleConnectionManager(this);
    private final HttpConnectionManager mHttpConnectionManager = new HttpConnectionManager(this);
    private final AvatarService mAvatarService = new AvatarService(this);
    private final QuickConversationsService mQuickConversationsService =
            new QuickConversationsService(this);
    private final ConversationsFileObserver fileObserver =
            new ConversationsFileObserver(
                    Environment.getExternalStorageDirectory().getAbsolutePath()) {
                @Override
                public void onEvent(final int event, final File file) {
                    markFileDeleted(file);
                }
            };
    private boolean destroyed = false;

    private int unreadCount = -1;

    // Ui callback listeners
    private final Set<OnConversationUpdate> mOnConversationUpdates =
            Collections.newSetFromMap(new WeakHashMap<OnConversationUpdate, Boolean>());
    private final Set<OnShowErrorToast> mOnShowErrorToasts =
            Collections.newSetFromMap(new WeakHashMap<OnShowErrorToast, Boolean>());
    private final Set<OnAccountUpdate> mOnAccountUpdates =
            Collections.newSetFromMap(new WeakHashMap<OnAccountUpdate, Boolean>());
    private final Set<OnCaptchaRequested> mOnCaptchaRequested =
            Collections.newSetFromMap(new WeakHashMap<OnCaptchaRequested, Boolean>());
    private final Set<OnRosterUpdate> mOnRosterUpdates =
            Collections.newSetFromMap(new WeakHashMap<OnRosterUpdate, Boolean>());
    private final Set<OnUpdateBlocklist> mOnUpdateBlocklist =
            Collections.newSetFromMap(new WeakHashMap<OnUpdateBlocklist, Boolean>());
    private final Set<OnMucRosterUpdate> mOnMucRosterUpdate =
            Collections.newSetFromMap(new WeakHashMap<OnMucRosterUpdate, Boolean>());
    private final Set<OnKeyStatusUpdated> mOnKeyStatusUpdated =
            Collections.newSetFromMap(new WeakHashMap<OnKeyStatusUpdated, Boolean>());
    private final Set<OnJingleRtpConnectionUpdate> onJingleRtpConnectionUpdate =
            Collections.newSetFromMap(new WeakHashMap<OnJingleRtpConnectionUpdate, Boolean>());

    private final Object LISTENER_LOCK = new Object();

    public final Set<String> FILENAMES_TO_IGNORE_DELETION = new HashSet<>();

    private final AtomicLong mLastExpiryRun = new AtomicLong(0);

    private OpenPgpServiceConnection pgpServiceConnection;
    private PgpEngine mPgpEngine = null;
    private WakeLock wakeLock;
    private LruCache<String, Bitmap> mBitmapCache;
    private final BroadcastReceiver mInternalEventReceiver = new InternalEventReceiver();
    private final BroadcastReceiver mInternalRestrictedEventReceiver =
            new RestrictedEventReceiver(List.of(TorServiceUtils.ACTION_STATUS));
    private final BroadcastReceiver mInternalScreenEventReceiver = new InternalEventReceiver();

    private static String generateFetchKey(Account account, final Avatar avatar) {
        return account.getJid().asBareJid() + "_" + avatar.owner + "_" + avatar.sha1sum;
    }

    public boolean isInLowPingTimeoutMode(Account account) {
        synchronized (mLowPingTimeoutMode) {
            return mLowPingTimeoutMode.contains(account.getJid().asBareJid());
        }
    }

    public void startOngoingVideoTranscodingForegroundNotification() {
        mOngoingVideoTranscoding.set(true);
        toggleForegroundService();
    }

    public void stopOngoingVideoTranscodingForegroundNotification() {
        mOngoingVideoTranscoding.set(false);
        toggleForegroundService();
    }

    public boolean areMessagesInitialized() {
        return this.restoredFromDatabaseLatch.getCount() == 0;
    }

    public PgpEngine getPgpEngine() {
        if (!Config.supportOpenPgp()) {
            return null;
        } else if (pgpServiceConnection != null && pgpServiceConnection.isBound()) {
            if (this.mPgpEngine == null) {
                this.mPgpEngine =
                        new PgpEngine(
                                new OpenPgpApi(
                                        getApplicationContext(), pgpServiceConnection.getService()),
                                this);
            }
            return mPgpEngine;
        } else {
            return null;
        }
    }

    public OpenPgpApi getOpenPgpApi() {
        if (!Config.supportOpenPgp()) {
            return null;
        } else if (pgpServiceConnection != null && pgpServiceConnection.isBound()) {
            return new OpenPgpApi(this, pgpServiceConnection.getService());
        } else {
            return null;
        }
    }

    public AppSettings getAppSettings() {
        return this.appSettings;
    }

    public FileBackend getFileBackend() {
        return this.fileBackend;
    }

    public AvatarService getAvatarService() {
        return this.mAvatarService;
    }

    public ListenableFuture<Void> attachLocationToConversation(
            final Conversation conversation, final Uri uri) {
        final var encryption = conversation.getNextEncryption();
        final Message message = new Message(conversation, uri.toString(), encryption);
        Message.configurePrivateMessage(message);
        return encryptIfNeededAndSend(message);
    }

    public ListenableFuture<Void> attachFileToConversation(
            final Conversation conversation, final Uri uri, final String type) {
        final Message message;
        if (conversation.getNextEncryption() == Message.ENCRYPTION_PGP) {
            message = new Message(conversation, "", Message.ENCRYPTION_DECRYPTED);
        } else {
            message = new Message(conversation, "", conversation.getNextEncryption());
        }
        if (!Message.configurePrivateFileMessage(message)) {
            message.setCounterpart(conversation.getNextCounterpart());
            message.setType(Message.TYPE_FILE);
        }
        final var future = submitAttachToConversation(uri, type, message);
        return Futures.transformAsync(
                future, v -> encryptIfNeededAndSend(message), MoreExecutors.directExecutor());
    }

    private ListenableFuture<Void> submitAttachToConversation(
            final Uri uri, final String type, final Message message) {
        final AttachFileToConversationRunnable runnable =
                new AttachFileToConversationRunnable(this, uri, type, message);
        if (runnable.isVideoMessage()) {
            return Futures.submit(runnable, VIDEO_COMPRESSION_EXECUTOR);
        } else {
            return Futures.submit(runnable, FILE_ATTACHMENT_EXECUTOR);
        }
    }

    public ListenableFuture<Void> attachImageToConversation(
            final Conversation conversation, final Uri uri, final String type) {
        final String mimeType = MimeUtils.guessMimeTypeFromUriAndMime(this, uri, type);
        final String compressPictures = getCompressPicturesPreference();

        if ("never".equals(compressPictures)
                || ("auto".equals(compressPictures) && getFileBackend().useImageAsIs(uri))
                || (mimeType != null && mimeType.endsWith("/gif"))
                || getFileBackend().unusualBounds(uri)) {
            Log.d(
                    Config.LOGTAG,
                    conversation.getAccount().getJid().asBareJid()
                            + ": not compressing picture. sending as file");
            return attachFileToConversation(conversation, uri, mimeType);
        }
        final Message message;
        if (conversation.getNextEncryption() == Message.ENCRYPTION_PGP) {
            message = new Message(conversation, "", Message.ENCRYPTION_DECRYPTED);
        } else {
            message = new Message(conversation, "", conversation.getNextEncryption());
        }
        if (!Message.configurePrivateFileMessage(message)) {
            message.setCounterpart(conversation.getNextCounterpart());
            message.setType(Message.TYPE_IMAGE);
        }
        Log.d(Config.LOGTAG, "attachImage: type=" + message.getType());
        final var imageCopyFuture =
                Futures.submit(
                        () -> getFileBackend().copyImageToPrivateStorage(message, uri),
                        FILE_ATTACHMENT_EXECUTOR);
        final var future =
                Futures.catchingAsync(
                        imageCopyFuture,
                        FileBackend.ImageCompressionException.class,
                        ex -> {
                            message.setType(Message.TYPE_FILE);
                            return submitAttachToConversation(uri, type, message);
                        },
                        MoreExecutors.directExecutor());
        return Futures.transformAsync(
                future, v -> encryptIfNeededAndSend(message), MoreExecutors.directExecutor());
    }

    public Conversation find(Bookmark bookmark) {
        return find(bookmark.getAccount(), bookmark.getAddress());
    }

    public Conversation find(final Account account, final Jid jid) {
        return find(getConversations(), account, jid);
    }

    public void search(
            final List<String> term,
            final String uuid,
            final OnSearchResultsAvailable onSearchResultsAvailable) {
        MessageSearchTask.search(this, term, uuid, onSearchResultsAvailable);
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        final String action = Strings.nullToEmpty(intent == null ? null : intent.getAction());
        final boolean needsForegroundService =
                intent != null
                        && intent.getBooleanExtra(
                                SystemEventReceiver.EXTRA_NEEDS_FOREGROUND_SERVICE, false);
        if (needsForegroundService) {
            Log.d(
                    Config.LOGTAG,
                    "toggle forced foreground service after receiving event (action="
                            + action
                            + ")");
            toggleForegroundService(true);
        }
        final String uuid = intent == null ? null : intent.getStringExtra("uuid");
        switch (action) {
            case QuickConversationsService.SMS_RETRIEVED_ACTION:
                mQuickConversationsService.handleSmsReceived(intent);
                break;
            case ConnectivityManager.CONNECTIVITY_ACTION:
                if (hasInternetConnection()) {
                    if (Config.POST_CONNECTIVITY_CHANGE_PING_INTERVAL > 0) {
                        schedulePostConnectivityChange();
                    }
                    if (Config.RESET_ATTEMPT_COUNT_ON_NETWORK_CHANGE) {
                        resetAllAttemptCounts(true, false);
                    }
                    Resolver.clearCache();
                }
                break;
            case Intent.ACTION_SHUTDOWN:
                logoutAndSave(true);
                return START_NOT_STICKY;
            case ACTION_CLEAR_MESSAGE_NOTIFICATION:
                mNotificationExecutor.execute(
                        () -> {
                            try {
                                final Conversation c = findConversationByUuid(uuid);
                                if (c != null) {
                                    mNotificationService.clearMessages(c);
                                } else {
                                    mNotificationService.clearMessages();
                                }
                                restoredFromDatabaseLatch.await();

                            } catch (InterruptedException e) {
                                Log.d(
                                        Config.LOGTAG,
                                        "unable to process clear message notification");
                            }
                        });
                break;
            case ACTION_CLEAR_MISSED_CALL_NOTIFICATION:
                mNotificationExecutor.execute(
                        () -> {
                            try {
                                final Conversation c = findConversationByUuid(uuid);
                                if (c != null) {
                                    mNotificationService.clearMissedCalls(c);
                                } else {
                                    mNotificationService.clearMissedCalls();
                                }
                                restoredFromDatabaseLatch.await();

                            } catch (InterruptedException e) {
                                Log.d(
                                        Config.LOGTAG,
                                        "unable to process clear missed call notification");
                            }
                        });
                break;
            case ACTION_DISMISS_CALL:
                {
                    if (intent == null) {
                        break;
                    }
                    final String sessionId =
                            intent.getStringExtra(RtpSessionActivity.EXTRA_SESSION_ID);
                    Log.d(
                            Config.LOGTAG,
                            "received intent to dismiss call with session id " + sessionId);
                    mJingleConnectionManager.rejectRtpSession(sessionId);
                    break;
                }
            case TorServiceUtils.ACTION_STATUS:
                final String status =
                        intent == null ? null : intent.getStringExtra(TorServiceUtils.EXTRA_STATUS);
                // TODO port and host are in 'extras' - but this may not be a reliable source?
                if ("ON".equals(status)) {
                    handleOrbotStartedEvent();
                    return START_STICKY;
                }
                break;
            case ACTION_END_CALL:
                {
                    if (intent == null) {
                        break;
                    }
                    final String sessionId =
                            intent.getStringExtra(RtpSessionActivity.EXTRA_SESSION_ID);
                    Log.d(
                            Config.LOGTAG,
                            "received intent to end call with session id " + sessionId);
                    mJingleConnectionManager.endRtpSession(sessionId);
                }
                break;
            case ACTION_PROVISION_ACCOUNT:
                {
                    if (intent == null) {
                        break;
                    }
                    final String address = intent.getStringExtra("address");
                    final String password = intent.getStringExtra("password");
                    if (QuickConversationsService.isQuicksy()
                            || Strings.isNullOrEmpty(address)
                            || Strings.isNullOrEmpty(password)) {
                        break;
                    }
                    provisionAccount(address, password);
                    break;
                }
            case ACTION_DISMISS_ERROR_NOTIFICATIONS:
                dismissErrorNotifications();
                break;
            case ACTION_TRY_AGAIN:
                resetAllAttemptCounts(false, true);
                break;
            case ACTION_REPLY_TO_CONVERSATION:
                final Bundle remoteInput =
                        intent == null ? null : RemoteInput.getResultsFromIntent(intent);
                if (remoteInput == null) {
                    break;
                }
                final CharSequence body = remoteInput.getCharSequence("text_reply");
                final boolean dismissNotification =
                        intent.getBooleanExtra("dismiss_notification", false);
                final String lastMessageUuid = intent.getStringExtra("last_message_uuid");
                if (body == null || body.length() <= 0) {
                    break;
                }
                mNotificationExecutor.execute(
                        () -> {
                            try {
                                restoredFromDatabaseLatch.await();
                                final Conversation c = findConversationByUuid(uuid);
                                if (c != null) {
                                    directReply(
                                            c,
                                            body.toString(),
                                            lastMessageUuid,
                                            dismissNotification);
                                }
                            } catch (InterruptedException e) {
                                Log.d(Config.LOGTAG, "unable to process direct reply");
                            }
                        });
                break;
            case ACTION_MARK_AS_READ:
                mNotificationExecutor.execute(
                        () -> {
                            final Conversation c = findConversationByUuid(uuid);
                            if (c == null) {
                                Log.d(
                                        Config.LOGTAG,
                                        "received mark read intent for unknown conversation ("
                                                + uuid
                                                + ")");
                                return;
                            }
                            try {
                                restoredFromDatabaseLatch.await();
                                sendReadMarker(c, null);
                            } catch (InterruptedException e) {
                                Log.d(
                                        Config.LOGTAG,
                                        "unable to process notification read marker for"
                                                + " conversation "
                                                + c.getName());
                            }
                        });
                break;
            case ACTION_SNOOZE:
                mNotificationExecutor.execute(
                        () -> {
                            final Conversation c = findConversationByUuid(uuid);
                            if (c == null) {
                                Log.d(
                                        Config.LOGTAG,
                                        "received snooze intent for unknown conversation ("
                                                + uuid
                                                + ")");
                                return;
                            }
                            c.setMutedTill(System.currentTimeMillis() + 30 * 60 * 1000);
                            mNotificationService.clearMessages(c);
                            updateConversation(c);
                        });
            case AudioManager.RINGER_MODE_CHANGED_ACTION:
            case NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED:
                if (appSettings.isDndOnSilentMode() && appSettings.isAutomaticAvailability()) {
                    refreshAllPresences();
                }
                break;
            case Intent.ACTION_SCREEN_ON:
                deactivateGracePeriod();
            case Intent.ACTION_USER_PRESENT:
            case Intent.ACTION_SCREEN_OFF:
                if (appSettings.isAwayWhenScreenLocked() && appSettings.isAutomaticAvailability()) {
                    refreshAllPresences();
                }
                break;
            case ACTION_FCM_TOKEN_REFRESH:
                refreshAllFcmTokens();
                break;
            case ACTION_RENEW_UNIFIED_PUSH_ENDPOINTS:
                if (intent == null) {
                    break;
                }
                final String instance = intent.getStringExtra("instance");
                final String application = intent.getStringExtra("application");
                final Messenger messenger = intent.getParcelableExtra("messenger");
                final UnifiedPushBroker.PushTargetMessenger pushTargetMessenger;
                if (messenger != null && application != null && instance != null) {
                    pushTargetMessenger =
                            new UnifiedPushBroker.PushTargetMessenger(
                                    new UnifiedPushDatabase.PushTarget(application, instance),
                                    messenger);
                    Log.d(Config.LOGTAG, "found push target messenger");
                } else {
                    pushTargetMessenger = null;
                }
                final Optional<UnifiedPushBroker.Transport> transport =
                        renewUnifiedPushEndpoints(pushTargetMessenger);
                if (instance != null && transport.isPresent()) {
                    unifiedPushBroker.rebroadcastEndpoint(messenger, instance, transport.get());
                }
                break;
            case ACTION_IDLE_PING:
                scheduleNextIdlePing();
                break;
            case ACTION_FCM_MESSAGE_RECEIVED:
                Log.d(Config.LOGTAG, "push message arrived in service. account");
                break;
            case ACTION_QUICK_LOG:
                final String message = intent == null ? null : intent.getStringExtra("message");
                if (message != null && Config.QUICK_LOG) {
                    quickLog(message);
                }
                break;
            case Intent.ACTION_SEND:
                final Uri uri = intent == null ? null : intent.getData();
                if (uri != null) {
                    Log.d(Config.LOGTAG, "received uri permission for " + uri);
                }
                return START_STICKY;
            case ACTION_TEMPORARILY_DISABLE:
                toggleSoftDisabled(true);
                if (checkListeners()) {
                    stopSelf();
                }
                return START_NOT_STICKY;
        }
        final var extras = intent == null ? null : intent.getExtras();
        try {
            internalPingExecutor.execute(() -> manageAccountConnectionStates(action, extras));
        } catch (final RejectedExecutionException e) {
            Log.e(Config.LOGTAG, "can not schedule connection states manager");
        }
        if (SystemClock.elapsedRealtime() - mLastExpiryRun.get() >= Config.EXPIRY_INTERVAL) {
            expireOldMessages();
        }
        return START_STICKY;
    }

    private void quickLog(final String message) {
        if (Strings.isNullOrEmpty(message)) {
            return;
        }
        final Account account = AccountUtils.getFirstEnabled(this);
        if (account == null) {
            return;
        }
        final Conversation conversation =
                findOrCreateConversation(account, Config.BUG_REPORTS, false, true);
        final Message report = new Message(conversation, message, Message.ENCRYPTION_NONE);
        report.setStatus(Message.STATUS_RECEIVED);
        conversation.add(report);
        databaseBackend.createMessage(report);
        updateConversationUi();
    }

    public void manageAccountConnectionStatesInternal() {
        manageAccountConnectionStates(ACTION_INTERNAL_PING, null);
    }

    private synchronized void manageAccountConnectionStates(
            final String action, final Bundle extras) {
        final String pushedAccountHash = extras == null ? null : extras.getString("account");
        final boolean interactive = java.util.Objects.equals(ACTION_TRY_AGAIN, action);
        WakeLockHelper.acquire(wakeLock);
        boolean pingNow =
                ConnectivityManager.CONNECTIVITY_ACTION.equals(action)
                        || (Config.POST_CONNECTIVITY_CHANGE_PING_INTERVAL > 0
                                && ACTION_POST_CONNECTIVITY_CHANGE.equals(action));
        final HashSet<Account> pingCandidates = new HashSet<>();
        final String androidId = pushedAccountHash == null ? null : PhoneHelper.getAndroidId(this);
        for (final Account account : accounts) {
            final boolean pushWasMeantForThisAccount =
                    androidId != null
                            && CryptoHelper.getAccountFingerprint(account, androidId)
                                    .equals(pushedAccountHash);
            pingNow |=
                    processAccountState(
                            account,
                            interactive,
                            "ui".equals(action),
                            pushWasMeantForThisAccount,
                            pingCandidates);
        }
        if (pingNow) {
            for (final Account account : pingCandidates) {
                final var connection = account.getXmppConnection();
                final boolean lowTimeout = isInLowPingTimeoutMode(account);
                final var delta =
                        (SystemClock.elapsedRealtime() - connection.getLastPacketReceived())
                                / 1000L;
                connection.sendPing();
                Log.d(
                        Config.LOGTAG,
                        String.format(
                                "%s: send ping (action=%s,lowTimeout=%s,interval=%s)",
                                account.getJid().asBareJid(), action, lowTimeout, delta));
                scheduleWakeUpCall(
                        lowTimeout ? Config.LOW_PING_TIMEOUT : Config.PING_TIMEOUT,
                        account.getUuid().hashCode());
            }
        }
        WakeLockHelper.release(wakeLock);
    }

    private void handleOrbotStartedEvent() {
        for (final Account account : accounts) {
            if (account.getStatus() == Account.State.TOR_NOT_AVAILABLE) {
                reconnectAccount(account, true, false);
            }
        }
    }

    private boolean processAccountState(
            final Account account,
            final boolean interactive,
            final boolean isUiAction,
            final boolean isAccountPushed,
            final HashSet<Account> pingCandidates) {
        final var connection = account.getXmppConnection();
        if (!account.getStatus().isAttemptReconnect()) {
            return false;
        }
        final var requestCode = account.getUuid().hashCode();
        if (!hasInternetConnection()) {
            connection.setStatusAndTriggerProcessor(Account.State.NO_INTERNET);
        } else {
            if (account.getStatus() == Account.State.NO_INTERNET) {
                connection.setStatusAndTriggerProcessor(Account.State.OFFLINE);
            }
            if (account.getStatus() == Account.State.ONLINE) {
                synchronized (mLowPingTimeoutMode) {
                    long lastReceived = account.getXmppConnection().getLastPacketReceived();
                    long lastSent = account.getXmppConnection().getLastPingSent();
                    long pingInterval =
                            isUiAction
                                    ? Config.PING_MIN_INTERVAL * 1000
                                    : Config.PING_MAX_INTERVAL * 1000;
                    long msToNextPing =
                            (Math.max(lastReceived, lastSent) + pingInterval)
                                    - SystemClock.elapsedRealtime();
                    int pingTimeout =
                            mLowPingTimeoutMode.contains(account.getJid().asBareJid())
                                    ? Config.LOW_PING_TIMEOUT * 1000
                                    : Config.PING_TIMEOUT * 1000;
                    long pingTimeoutIn = (lastSent + pingTimeout) - SystemClock.elapsedRealtime();
                    if (lastSent > lastReceived) {
                        if (pingTimeoutIn < 0) {
                            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": ping timeout");
                            this.reconnectAccount(account, true, interactive);
                        } else {
                            this.scheduleWakeUpCall(pingTimeoutIn, requestCode);
                        }
                    } else {
                        pingCandidates.add(account);
                        if (isAccountPushed) {
                            if (mLowPingTimeoutMode.add(account.getJid().asBareJid())) {
                                Log.d(
                                        Config.LOGTAG,
                                        account.getJid().asBareJid()
                                                + ": entering low ping timeout mode");
                            }
                            return true;
                        } else if (msToNextPing <= 0) {
                            return true;
                        } else {
                            this.scheduleWakeUpCall(msToNextPing, requestCode);
                            if (mLowPingTimeoutMode.remove(account.getJid().asBareJid())) {
                                Log.d(
                                        Config.LOGTAG,
                                        account.getJid().asBareJid()
                                                + ": leaving low ping timeout mode");
                            }
                        }
                    }
                }
            } else if (account.getStatus() == Account.State.OFFLINE) {
                reconnectAccount(account, true, interactive);
            } else if (account.getStatus() == Account.State.CONNECTING) {
                final var connectionDuration = connection.getConnectionDuration();
                final var discoDuration = connection.getDiscoDuration();
                final var connectionTimeout = Config.CONNECT_TIMEOUT * 1000L - connectionDuration;
                final var discoTimeout = Config.CONNECT_DISCO_TIMEOUT * 1000L - discoDuration;
                if (connectionTimeout < 0) {
                    connection.triggerConnectionTimeout();
                } else if (discoTimeout < 0) {
                    connection.sendDiscoTimeout();
                    scheduleWakeUpCall(discoTimeout, requestCode);
                } else {
                    scheduleWakeUpCall(Math.min(connectionTimeout, discoTimeout), requestCode);
                }
            } else {
                final boolean aggressive =
                        account.getStatus() == Account.State.SEE_OTHER_HOST
                                || hasJingleRtpConnection(account);
                if (connection.getTimeToNextAttempt(aggressive) <= 0) {
                    reconnectAccount(account, true, interactive);
                }
            }
        }
        return false;
    }

    private void toggleSoftDisabled(final boolean softDisabled) {
        for (final Account account : this.accounts) {
            if (account.isEnabled()) {
                if (account.setOption(Account.OPTION_SOFT_DISABLED, softDisabled)) {
                    updateAccount(account);
                }
            }
        }
    }

    public void fetchServiceOutageStatus(final Account account) {
        final var sosUrl = account.getKey(Account.KEY_SOS_URL);
        if (Strings.isNullOrEmpty(sosUrl)) {
            return;
        }
        final var url = HttpUrl.parse(sosUrl);
        if (url == null) {
            return;
        }
        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": fetching service outage " + url);
        Futures.addCallback(
                ServiceOutageStatus.fetch(getApplicationContext(), url),
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(final ServiceOutageStatus sos) {
                        Log.d(Config.LOGTAG, "fetched " + sos);
                        account.setServiceOutageStatus(sos);
                        updateAccountUi();
                    }

                    @Override
                    public void onFailure(@NonNull Throwable throwable) {
                        Log.d(Config.LOGTAG, "error fetching sos", throwable);
                    }
                },
                MoreExecutors.directExecutor());
    }

    public boolean processUnifiedPushMessage(
            final Account account, final Jid transport, final Push push) {
        return unifiedPushBroker.processPushMessage(account, transport, push);
    }

    public void reinitializeMuclumbusService() {
        mChannelDiscoveryService.initializeMuclumbusService();
    }

    public void discoverChannels(
            String query,
            ChannelDiscoveryService.Method method,
            ChannelDiscoveryService.OnChannelSearchResultsFound onChannelSearchResultsFound) {
        mChannelDiscoveryService.discover(
                Strings.nullToEmpty(query).trim(), method, onChannelSearchResultsFound);
    }

    public boolean isDataSaverDisabled() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return true;
        }
        final ConnectivityManager connectivityManager = getSystemService(ConnectivityManager.class);
        return !Compatibility.isActiveNetworkMetered(connectivityManager)
                || Compatibility.getRestrictBackgroundStatus(connectivityManager)
                        == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED;
    }

    private ListenableFuture<Void> directReply(
            final Conversation conversation,
            final String body,
            final String lastMessageUuid,
            final boolean dismissAfterReply) {
        final Message inReplyTo =
                lastMessageUuid == null ? null : conversation.findMessageWithUuid(lastMessageUuid);
        final Message message = new Message(conversation, body, conversation.getNextEncryption());
        if (inReplyTo != null && inReplyTo.isPrivateMessage()) {
            Message.configurePrivateMessage(message, inReplyTo.getCounterpart());
        }
        message.markUnread();
        final var future = encryptIfNeededAndSend(message);
        return Futures.transform(
                future,
                v -> {
                    if (dismissAfterReply) {
                        markRead(conversation, true);
                    } else {
                        mNotificationService.pushFromDirectReply(message);
                    }
                    return null;
                },
                MoreExecutors.directExecutor());
    }

    private String getCompressPicturesPreference() {
        return getPreferences()
                .getString(
                        "picture_compression",
                        getResources().getString(R.string.picture_compression));
    }

    private void resetAllAttemptCounts(boolean reallyAll, boolean retryImmediately) {
        Log.d(Config.LOGTAG, "resetting all attempt counts");
        for (Account account : accounts) {
            if (account.hasErrorStatus() || reallyAll) {
                final XmppConnection connection = account.getXmppConnection();
                if (connection != null) {
                    connection.resetAttemptCount(retryImmediately);
                }
            }
            if (account.setShowErrorNotification(true)) {
                mDatabaseWriterExecutor.execute(() -> databaseBackend.updateAccount(account));
            }
        }
        mNotificationService.updateErrorNotification();
    }

    private void dismissErrorNotifications() {
        for (final Account account : this.accounts) {
            if (account.hasErrorStatus()) {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid() + ": dismissing error notification");
                if (account.setShowErrorNotification(false)) {
                    mDatabaseWriterExecutor.execute(() -> databaseBackend.updateAccount(account));
                }
            }
        }
    }

    private void expireOldMessages() {
        expireOldMessages(false);
    }

    public void expireOldMessages(final boolean resetHasMessagesLeftOnServer) {
        mLastExpiryRun.set(SystemClock.elapsedRealtime());
        mDatabaseWriterExecutor.execute(
                () -> {
                    long timestamp = getAutomaticMessageDeletionDate();
                    if (timestamp > 0) {
                        databaseBackend.expireOldMessages(timestamp);
                        synchronized (XmppConnectionService.this.conversations) {
                            for (Conversation conversation :
                                    XmppConnectionService.this.conversations) {
                                conversation.expireOldMessages(timestamp);
                                if (resetHasMessagesLeftOnServer) {
                                    conversation.messagesLoaded.set(true);
                                    conversation.setHasMessagesLeftOnServer(true);
                                }
                            }
                        }
                        updateConversationUi();
                    }
                });
    }

    public boolean hasInternetConnection() {
        final ConnectivityManager cm =
                ContextCompat.getSystemService(this, ConnectivityManager.class);
        if (cm == null) {
            return true; // if internet connection can not be checked it is probably best to just
            // try
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                final Network activeNetwork = cm.getActiveNetwork();
                final NetworkCapabilities capabilities =
                        activeNetwork == null ? null : cm.getNetworkCapabilities(activeNetwork);
                return capabilities != null
                        && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            } else {
                final NetworkInfo networkInfo = cm.getActiveNetworkInfo();
                return networkInfo != null
                        && (networkInfo.isConnected()
                                || networkInfo.getType() == ConnectivityManager.TYPE_ETHERNET);
            }
        } catch (final RuntimeException e) {
            Log.d(Config.LOGTAG, "unable to check for internet connection", e);
            return true; // if internet connection can not be checked it is probably best to just
            // try
        }
    }

    @SuppressLint("TrulyRandom")
    @Override
    public void onCreate() {
        LibIdnXmppStringprep.setup();
        if (Compatibility.twentySix()) {
            mNotificationService.initializeChannels();
        }
        mChannelDiscoveryService.initializeMuclumbusService();
        mForceDuringOnCreate.set(Compatibility.twentySix());
        toggleForegroundService();
        this.destroyed = false;
        OmemoSetting.load(this);
        updateMemorizingTrustManager();
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = maxMemory / 8;
        this.mBitmapCache =
                new LruCache<>(cacheSize) {
                    @Override
                    protected int sizeOf(final String key, final Bitmap bitmap) {
                        return bitmap.getByteCount() / 1024;
                    }
                };
        if (mLastActivity == 0) {
            mLastActivity =
                    getPreferences().getLong(SETTING_LAST_ACTIVITY_TS, System.currentTimeMillis());
        }

        Log.d(Config.LOGTAG, "initializing database...");
        this.databaseBackend = DatabaseBackend.getInstance(getApplicationContext());
        Log.d(Config.LOGTAG, "restoring accounts...");
        this.accounts = databaseBackend.getAccounts();
        for (final var account : this.accounts) {
            account.setXmppConnection(new XmppConnection(account, this));
        }
        final boolean hasEnabledAccounts = hasEnabledAccounts();
        toggleSetProfilePictureActivity(hasEnabledAccounts);
        reconfigurePushDistributor();

        if (CallIntegration.hasSystemFeature(this)) {
            CallIntegrationConnectionService.togglePhoneAccountsAsync(this, this.accounts);
        }

        restoreFromDatabase();

        if (QuickConversationsService.isContactListIntegration(this)
                && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                        == PackageManager.PERMISSION_GRANTED) {
            startContactObserver();
        }
        FILE_OBSERVER_EXECUTOR.execute(fileBackend::deleteHistoricAvatarPath);
        if (Compatibility.hasStoragePermission(this)) {
            Log.d(Config.LOGTAG, "starting file observer");
            FILE_OBSERVER_EXECUTOR.execute(this.fileObserver::startWatching);
            FILE_OBSERVER_EXECUTOR.execute(this::checkForDeletedFiles);
        }
        if (Config.supportOpenPgp()) {
            this.pgpServiceConnection =
                    new OpenPgpServiceConnection(
                            this,
                            "org.sufficientlysecure.keychain",
                            new OpenPgpServiceConnection.OnBound() {
                                @Override
                                public void onBound(final IOpenPgpService2 service) {
                                    for (Account account : accounts) {
                                        final PgpDecryptionService pgp =
                                                account.getPgpDecryptionService();
                                        if (pgp != null) {
                                            pgp.continueDecryption(true);
                                        }
                                    }
                                }

                                @Override
                                public void onError(final Exception exception) {
                                    Log.e(
                                            Config.LOGTAG,
                                            "could not bind to OpenKeyChain",
                                            exception);
                                }
                            });
            this.pgpServiceConnection.bindToService();
        }

        final PowerManager powerManager = getSystemService(PowerManager.class);
        if (powerManager != null) {
            this.wakeLock =
                    powerManager.newWakeLock(
                            PowerManager.PARTIAL_WAKE_LOCK, "Conversations:Service");
        }

        toggleForegroundService();
        updateUnreadCountBadge();
        toggleScreenEventReceiver();
        final IntentFilter systemBroadcastFilter = new IntentFilter();
        scheduleNextIdlePing();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            systemBroadcastFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        }
        systemBroadcastFilter.addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED);
        ContextCompat.registerReceiver(
                this,
                this.mInternalEventReceiver,
                systemBroadcastFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED);
        final IntentFilter exportedBroadcastFilter = new IntentFilter();
        exportedBroadcastFilter.addAction(TorServiceUtils.ACTION_STATUS);
        ContextCompat.registerReceiver(
                this,
                this.mInternalRestrictedEventReceiver,
                exportedBroadcastFilter,
                ContextCompat.RECEIVER_EXPORTED);
        mForceDuringOnCreate.set(false);
        toggleForegroundService();
        internalPingExecutor.scheduleWithFixedDelay(
                this::manageAccountConnectionStatesInternal, 10, 10, TimeUnit.SECONDS);
        final SharedPreferences sharedPreferences =
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(this);
        sharedPreferences.registerOnSharedPreferenceChangeListener(
                new SharedPreferences.OnSharedPreferenceChangeListener() {
                    @Override
                    public void onSharedPreferenceChanged(
                            SharedPreferences sharedPreferences, @Nullable String key) {
                        Log.d(Config.LOGTAG, "preference '" + key + "' has changed");
                        if (AppSettings.KEEP_FOREGROUND_SERVICE.equals(key)) {
                            toggleForegroundService();
                        }
                    }
                });
    }

    private void checkForDeletedFiles() {
        if (destroyed) {
            Log.d(
                    Config.LOGTAG,
                    "Do not check for deleted files because service has been destroyed");
            return;
        }
        final long start = SystemClock.elapsedRealtime();
        final List<DatabaseBackend.FilePathInfo> relativeFilePaths =
                databaseBackend.getFilePathInfo();
        final List<DatabaseBackend.FilePathInfo> changed = new ArrayList<>();
        for (final DatabaseBackend.FilePathInfo filePath : relativeFilePaths) {
            if (destroyed) {
                Log.d(
                        Config.LOGTAG,
                        "Stop checking for deleted files because service has been destroyed");
                return;
            }
            final File file = fileBackend.getFileForPath(filePath.path);
            if (filePath.setDeleted(!file.exists())) {
                changed.add(filePath);
            }
        }
        final long duration = SystemClock.elapsedRealtime() - start;
        Log.d(
                Config.LOGTAG,
                "found "
                        + changed.size()
                        + " changed files on start up. total="
                        + relativeFilePaths.size()
                        + ". ("
                        + duration
                        + "ms)");
        if (changed.size() > 0) {
            databaseBackend.markFilesAsChanged(changed);
            markChangedFiles(changed);
        }
    }

    public void startContactObserver() {
        getContentResolver()
                .registerContentObserver(
                        ContactsContract.Contacts.CONTENT_URI,
                        true,
                        new ContentObserver(null) {
                            @Override
                            public void onChange(boolean selfChange) {
                                super.onChange(selfChange);
                                if (restoredFromDatabaseLatch.getCount() == 0) {
                                    loadPhoneContacts();
                                }
                            }
                        });
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_COMPLETE) {
            Log.d(Config.LOGTAG, "clear cache due to low memory");
            getBitmapCache().evictAll();
        }
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(this.mInternalEventReceiver);
            unregisterReceiver(this.mInternalRestrictedEventReceiver);
            unregisterReceiver(this.mInternalScreenEventReceiver);
        } catch (final RuntimeException e) {
            // ignored
        }
        destroyed = false;
        fileObserver.stopWatching();
        internalPingExecutor.shutdown();
        super.onDestroy();
    }

    public void restartFileObserver() {
        Log.d(Config.LOGTAG, "restarting file observer");
        FILE_OBSERVER_EXECUTOR.execute(this.fileObserver::restartWatching);
        FILE_OBSERVER_EXECUTOR.execute(this::checkForDeletedFiles);
    }

    public void toggleScreenEventReceiver() {
        if (appSettings.isAwayWhenScreenLocked() && appSettings.isAutomaticAvailability()) {
            final IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_USER_PRESENT);
            registerReceiver(this.mInternalScreenEventReceiver, filter);
        } else {
            try {
                unregisterReceiver(this.mInternalScreenEventReceiver);
            } catch (IllegalArgumentException e) {
                // ignored
            }
        }
    }

    public void toggleForegroundService() {
        toggleForegroundService(false);
    }

    public void setOngoingCall(
            AbstractJingleConnection.Id id, Set<Media> media, final boolean reconnecting) {
        ongoingCall.set(new OngoingCall(id, media, reconnecting));
        toggleForegroundService(false);
    }

    public void removeOngoingCall() {
        ongoingCall.set(null);
        toggleForegroundService(false);
    }

    private void toggleForegroundService(final boolean force) {
        final boolean status;
        final OngoingCall ongoing = ongoingCall.get();
        final boolean ongoingVideoTranscoding = mOngoingVideoTranscoding.get();
        final int id;
        if (force
                || mForceDuringOnCreate.get()
                || ongoingVideoTranscoding
                || ongoing != null
                || (appSettings.isKeepForegroundService() && hasEnabledAccounts())) {
            final Notification notification;
            if (ongoing != null) {
                notification = this.mNotificationService.getOngoingCallNotification(ongoing);
                id = NotificationService.ONGOING_CALL_NOTIFICATION_ID;
                startForegroundOrCatch(id, notification, true);
            } else if (ongoingVideoTranscoding) {
                notification = this.mNotificationService.getIndeterminateVideoTranscoding();
                id = NotificationService.ONGOING_VIDEO_TRANSCODING_NOTIFICATION_ID;
                startForegroundOrCatch(id, notification, false);
            } else {
                notification = this.mNotificationService.createForegroundNotification();
                id = NotificationService.FOREGROUND_NOTIFICATION_ID;
                startForegroundOrCatch(id, notification, false);
            }
            mNotificationService.notify(id, notification);
            status = true;
        } else {
            id = 0;
            stopForeground(true);
            status = false;
        }

        for (final int toBeRemoved :
                Collections2.filter(
                        Arrays.asList(
                                NotificationService.FOREGROUND_NOTIFICATION_ID,
                                NotificationService.ONGOING_CALL_NOTIFICATION_ID,
                                NotificationService.ONGOING_VIDEO_TRANSCODING_NOTIFICATION_ID),
                        i -> i != id)) {
            mNotificationService.cancel(toBeRemoved);
        }
        Log.d(
                Config.LOGTAG,
                "ForegroundService: " + (status ? "on" : "off") + ", notification: " + id);
    }

    private void startForegroundOrCatch(
            final int id, final Notification notification, final boolean requireMicrophone) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                final int foregroundServiceType;
                if (requireMicrophone
                        && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                                == PackageManager.PERMISSION_GRANTED) {
                    foregroundServiceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
                    Log.d(Config.LOGTAG, "defaulting to microphone foreground service type");
                } else if (getSystemService(PowerManager.class)
                        .isIgnoringBatteryOptimizations(getPackageName())) {
                    foregroundServiceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED;
                } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED) {
                    foregroundServiceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
                } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED) {
                    foregroundServiceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
                } else {
                    foregroundServiceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;
                    Log.w(Config.LOGTAG, "falling back to special use foreground service type");
                }
                startForeground(id, notification, foregroundServiceType);
            } else {
                startForeground(id, notification);
            }
        } catch (final IllegalStateException | SecurityException e) {
            Log.e(Config.LOGTAG, "Could not start foreground service", e);
        }
    }

    public boolean foregroundNotificationNeedsUpdatingWhenErrorStateChanges() {
        return !mOngoingVideoTranscoding.get()
                && ongoingCall.get() == null
                && appSettings.isKeepForegroundService()
                && hasEnabledAccounts();
    }

    @Override
    public void onTaskRemoved(final Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        if ((appSettings.isKeepForegroundService() && hasEnabledAccounts())
                || mOngoingVideoTranscoding.get()
                || ongoingCall.get() != null) {
            Log.d(Config.LOGTAG, "ignoring onTaskRemoved because foreground service is activated");
        } else {
            this.logoutAndSave(false);
        }
    }

    private void logoutAndSave(boolean stop) {
        int activeAccounts = 0;
        for (final Account account : accounts) {
            if (account.isConnectionEnabled()) {
                account.getXmppConnection().getManager(RosterManager.class).writeToDatabase();
                activeAccounts++;
            }
            XmppConnection.RECONNNECTION_EXECUTOR.execute(() -> disconnect(account, false));
        }
        if (stop || activeAccounts == 0) {
            Log.d(Config.LOGTAG, "good bye");
            stopSelf();
        }
    }

    private void schedulePostConnectivityChange() {
        final AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        final long triggerAtMillis =
                SystemClock.elapsedRealtime()
                        + (Config.POST_CONNECTIVITY_CHANGE_PING_INTERVAL * 1000);
        final Intent intent = new Intent(this, SystemEventReceiver.class);
        intent.setAction(ACTION_POST_CONNECTIVITY_CHANGE);
        try {
            final PendingIntent pendingIntent =
                    PendingIntent.getBroadcast(
                            this,
                            1,
                            intent,
                            s()
                                    ? PendingIntent.FLAG_IMMUTABLE
                                            | PendingIntent.FLAG_UPDATE_CURRENT
                                    : PendingIntent.FLAG_UPDATE_CURRENT);
            alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, pendingIntent);
        } catch (RuntimeException e) {
            Log.e(Config.LOGTAG, "unable to schedule alarm for post connectivity change", e);
        }
    }

    public void scheduleWakeUpCall(final int seconds, final int requestCode) {
        scheduleWakeUpCall((seconds < 0 ? 1 : seconds + 1) * 1000L, requestCode);
    }

    public void scheduleWakeUpCall(final long milliSeconds, final int requestCode) {
        final var timeToWake = SystemClock.elapsedRealtime() + milliSeconds;
        final var alarmManager = getSystemService(AlarmManager.class);
        final Intent intent = new Intent(this, SystemEventReceiver.class);
        intent.setAction(ACTION_PING);
        try {
            final PendingIntent pendingIntent =
                    PendingIntent.getBroadcast(
                            this, requestCode, intent, PendingIntent.FLAG_IMMUTABLE);
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, timeToWake, pendingIntent);
        } catch (final RuntimeException e) {
            Log.e(Config.LOGTAG, "unable to schedule alarm for ping", e);
        }
    }

    private void scheduleNextIdlePing() {
        final long timeToWake = SystemClock.elapsedRealtime() + (Config.IDLE_PING_INTERVAL * 1000);
        final AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        final Intent intent = new Intent(this, SystemEventReceiver.class);
        intent.setAction(ACTION_IDLE_PING);
        try {
            final PendingIntent pendingIntent =
                    PendingIntent.getBroadcast(
                            this,
                            0,
                            intent,
                            s()
                                    ? PendingIntent.FLAG_IMMUTABLE
                                            | PendingIntent.FLAG_UPDATE_CURRENT
                                    : PendingIntent.FLAG_UPDATE_CURRENT);
            alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP, timeToWake, pendingIntent);
        } catch (RuntimeException e) {
            Log.d(Config.LOGTAG, "unable to schedule alarm for idle ping", e);
        }
    }

    private void sendFileMessage(
            final Message message, final boolean delay, final boolean forceP2P) {
        final var account = message.getConversation().getAccount();
        Log.d(
                Config.LOGTAG,
                account.getJid().asBareJid() + ": send file message. forceP2P=" + forceP2P);
        if ((account.httpUploadAvailable(fileBackend.getFile(message, false).getSize())
                        || message.getConversation().getMode() == Conversation.MODE_MULTI)
                && !forceP2P) {
            mHttpConnectionManager.createNewUploadConnection(message, delay);
        } else {
            mJingleConnectionManager.startJingleFileTransfer(message);
        }
    }

    public ListenableFuture<Void> encryptIfNeededAndSend(final Message message) {
        return Futures.transform(
                PgpEngine.encryptIfNeeded(getPgpEngine(), message),
                v -> {
                    sendMessage(message);
                    return null;
                },
                MoreExecutors.directExecutor());
    }

    public void sendMessage(final Message message) {
        sendMessage(message, false, false, false);
    }

    private void sendMessage(
            final Message message,
            final boolean resend,
            final boolean delay,
            final boolean forceP2P) {
        final Account account = message.getConversation().getAccount();
        if (account.setShowErrorNotification(true)) {
            databaseBackend.updateAccount(account);
            mNotificationService.updateErrorNotification();
        }
        final Conversation conversation = (Conversation) message.getConversation();
        account.getXmppConnection().getManager(ActivityManager.class).reset();

        if (QuickConversationsService.isQuicksy()
                && conversation.getMode() == Conversation.MODE_SINGLE) {
            final Contact contact = conversation.getContact();
            if (!contact.showInRoster() && contact.getOption(Contact.Options.SYNCED_VIA_OTHER)) {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": adding "
                                + contact.getAddress()
                                + " on sending message");
                createContact(contact);
            }
        }

        im.conversations.android.xmpp.model.stanza.Message packet = null;
        final boolean addToConversation = !message.edited();
        boolean saveInDb = addToConversation;
        message.setStatus(Message.STATUS_WAITING);

        if (message.getEncryption() != Message.ENCRYPTION_NONE
                && conversation.getMode() == Conversation.MODE_MULTI
                && conversation.isPrivateAndNonAnonymous()) {
            if (conversation.setAttribute(
                    Conversation.ATTRIBUTE_FORMERLY_PRIVATE_NON_ANONYMOUS, true)) {
                databaseBackend.updateConversation(conversation);
            }
        }

        final boolean inProgressJoin =
                account.getXmppConnection()
                        .getManager(MultiUserChatManager.class)
                        .isJoinInProgress(conversation);

        if (account.isOnlineAndConnected() && !inProgressJoin) {
            switch (message.getEncryption()) {
                case Message.ENCRYPTION_NONE:
                    if (message.needsUploading()) {
                        if (account.httpUploadAvailable(
                                        fileBackend.getFile(message, false).getSize())
                                || conversation.getMode() == Conversation.MODE_MULTI
                                || message.fixCounterpart()) {
                            this.sendFileMessage(message, delay, forceP2P);
                        } else {
                            break;
                        }
                    } else {
                        packet = mMessageGenerator.generateChat(message);
                    }
                    break;
                case Message.ENCRYPTION_PGP:
                case Message.ENCRYPTION_DECRYPTED:
                    if (message.needsUploading()) {
                        if (account.httpUploadAvailable(
                                        fileBackend.getFile(message, false).getSize())
                                || conversation.getMode() == Conversation.MODE_MULTI
                                || message.fixCounterpart()) {
                            this.sendFileMessage(message, delay, forceP2P);
                        } else {
                            break;
                        }
                    } else {
                        packet = mMessageGenerator.generatePgpChat(message);
                    }
                    break;
                case Message.ENCRYPTION_AXOLOTL:
                    message.setFingerprint(account.getAxolotlService().getOwnFingerprint());
                    if (message.needsUploading()) {
                        if (account.httpUploadAvailable(
                                        fileBackend.getFile(message, false).getSize())
                                || conversation.getMode() == Conversation.MODE_MULTI
                                || message.fixCounterpart()) {
                            this.sendFileMessage(message, delay, forceP2P);
                        } else {
                            break;
                        }
                    } else {
                        XmppAxolotlMessage axolotlMessage =
                                account.getAxolotlService().fetchAxolotlMessageFromCache(message);
                        if (axolotlMessage == null) {
                            account.getAxolotlService().preparePayloadMessage(message, delay);
                        } else {
                            packet = mMessageGenerator.generateAxolotlChat(message, axolotlMessage);
                        }
                    }
                    break;
            }
            if (packet != null) {
                if (account.getXmppConnection().getFeatures().sm()
                        || (conversation.getMode() == Conversation.MODE_MULTI
                                && message.getCounterpart().isBareJid())) {
                    message.setStatus(Message.STATUS_UNSEND);
                } else {
                    message.setStatus(Message.STATUS_SEND);
                }
            }
        } else {
            switch (message.getEncryption()) {
                case Message.ENCRYPTION_DECRYPTED:
                    if (!message.needsUploading()) {
                        String pgpBody = message.getEncryptedBody();
                        String decryptedBody = message.getBody();
                        message.setBody(pgpBody); // TODO might throw NPE
                        message.setEncryption(Message.ENCRYPTION_PGP);
                        if (message.edited()) {
                            message.setBody(decryptedBody);
                            message.setEncryption(Message.ENCRYPTION_DECRYPTED);
                            if (!databaseBackend.updateMessage(message, message.getEditedId())) {
                                Log.e(Config.LOGTAG, "error updated message in DB after edit");
                            }
                            updateConversationUi();
                            return;
                        } else {
                            databaseBackend.createMessage(message);
                            saveInDb = false;
                            message.setBody(decryptedBody);
                            message.setEncryption(Message.ENCRYPTION_DECRYPTED);
                        }
                    }
                    break;
                case Message.ENCRYPTION_AXOLOTL:
                    message.setFingerprint(account.getAxolotlService().getOwnFingerprint());
                    break;
            }
        }

        boolean mucMessage =
                conversation.getMode() == Conversation.MODE_MULTI && !message.isPrivateMessage();
        if (mucMessage) {
            message.setCounterpart(conversation.getMucOptions().getSelf().getFullJid());
        }

        if (resend) {
            if (packet != null && addToConversation) {
                if (account.getXmppConnection().getFeatures().sm() || mucMessage) {
                    markMessage(message, Message.STATUS_UNSEND);
                } else {
                    markMessage(message, Message.STATUS_SEND);
                }
            }
        } else {
            if (addToConversation) {
                conversation.add(message);
            }
            if (saveInDb) {
                databaseBackend.createMessage(message);
            } else if (message.edited()) {
                if (!databaseBackend.updateMessage(message, message.getEditedId())) {
                    Log.e(Config.LOGTAG, "error updated message in DB after edit");
                }
            }
            updateConversationUi();
        }
        if (packet != null) {
            if (delay) {
                mMessageGenerator.addDelay(packet, message.getTimeSent());
            }
            final var chatStateManager =
                    account.getXmppConnection().getManager(ChatStateManager.class);
            if (chatStateManager.setOutgoingChatState(conversation, Config.DEFAULT_CHAT_STATE)) {
                if (this.appSettings.isSendChatStates()) {
                    packet.addExtension(
                            chatStateManager.getOutgoingChatStateExtension(conversation));
                }
            }
            sendMessagePacket(account, packet);
        }
    }

    public void sendUnsentMessages(final Conversation conversation) {
        conversation.findWaitingMessages(message -> resendMessage(message, true));
    }

    public void resendMessage(final Message message, final boolean delay) {
        sendMessage(message, true, delay, false);
    }

    public void markReadUpToStanzaId(final Conversation conversation, final String stanzaId) {
        final Message message = conversation.findMessageWithServerMsgId(stanzaId);
        if (message == null) { // do we want to check if isRead?
            return;
        }
        markReadUpTo(conversation, message);
    }

    public void markReadUpTo(final Conversation conversation, final Message message) {
        final boolean isDismissNotification = isDismissNotification(message);
        final var uuid = message.getUuid();
        Log.d(
                Config.LOGTAG,
                conversation.getAccount().getJid().asBareJid()
                        + ": mark "
                        + conversation.getAddress().asBareJid()
                        + " as read up to "
                        + uuid);
        markRead(conversation, uuid, isDismissNotification);
    }

    private static boolean isDismissNotification(final Message message) {
        Message next = message.next();
        while (next != null) {
            if (message.getStatus() == Message.STATUS_RECEIVED) {
                return false;
            }
            next = next.next();
        }
        return true;
    }

    public void createBookmark(final Account account, final Bookmark bookmark) {
        account.getXmppConnection().getManager(BookmarkManager.class).create(bookmark);
    }

    public void deleteBookmark(final Account account, final Bookmark bookmark) {
        account.getXmppConnection().getManager(BookmarkManager.class).delete(bookmark);
    }

    private void restoreFromDatabase() {
        synchronized (this.conversations) {
            final Map<String, Account> accountLookupTable =
                    ImmutableMap.copyOf(Maps.uniqueIndex(this.accounts, Account::getUuid));
            Log.d(Config.LOGTAG, "restoring conversations...");
            final long startTimeConversationsRestore = SystemClock.elapsedRealtime();
            this.conversations.addAll(
                    databaseBackend.getConversations(Conversation.STATUS_AVAILABLE));
            for (Iterator<Conversation> iterator = conversations.listIterator();
                    iterator.hasNext(); ) {
                Conversation conversation = iterator.next();
                Account account = accountLookupTable.get(conversation.getAccountUuid());
                if (account != null) {
                    conversation.setAccount(account);
                } else {
                    Log.e(
                            Config.LOGTAG,
                            "unable to restore Conversations with " + conversation.getAddress());
                    iterator.remove();
                }
            }
            long diffConversationsRestore =
                    SystemClock.elapsedRealtime() - startTimeConversationsRestore;
            Log.d(
                    Config.LOGTAG,
                    "finished restoring conversations in " + diffConversationsRestore + "ms");
            Runnable runnable =
                    () -> {
                        if (DatabaseBackend.requiresMessageIndexRebuild()) {
                            DatabaseBackend.getInstance(this).rebuildMessagesIndex();
                        }
                        final long deletionDate = getAutomaticMessageDeletionDate();
                        mLastExpiryRun.set(SystemClock.elapsedRealtime());
                        if (deletionDate > 0) {
                            Log.d(
                                    Config.LOGTAG,
                                    "deleting messages that are older than "
                                            + AbstractGenerator.getTimestamp(deletionDate));
                            databaseBackend.expireOldMessages(deletionDate);
                        }
                        Log.d(Config.LOGTAG, "restoring roster...");
                        for (final Account account : accounts) {
                            account.getXmppConnection().getManager(RosterManager.class).restore();
                        }
                        getBitmapCache().evictAll();
                        loadPhoneContacts();
                        Log.d(Config.LOGTAG, "restoring messages...");
                        final long startMessageRestore = SystemClock.elapsedRealtime();
                        final Conversation quickLoad = QuickLoader.get(this.conversations);
                        if (quickLoad != null) {
                            restoreMessages(quickLoad);
                            updateConversationUi();
                            final long diffMessageRestore =
                                    SystemClock.elapsedRealtime() - startMessageRestore;
                            Log.d(
                                    Config.LOGTAG,
                                    "quickly restored "
                                            + quickLoad.getName()
                                            + " after "
                                            + diffMessageRestore
                                            + "ms");
                        }
                        for (Conversation conversation : this.conversations) {
                            if (quickLoad != conversation) {
                                restoreMessages(conversation);
                            }
                        }
                        mNotificationService.finishBacklog();
                        restoredFromDatabaseLatch.countDown();
                        final long diffMessageRestore =
                                SystemClock.elapsedRealtime() - startMessageRestore;
                        Log.d(
                                Config.LOGTAG,
                                "finished restoring messages in " + diffMessageRestore + "ms");
                        updateConversationUi();
                    };
            mDatabaseReaderExecutor.execute(
                    runnable); // will contain one write command (expiry) but that's fine
        }
    }

    private void restoreMessages(Conversation conversation) {
        conversation.addAll(0, databaseBackend.getMessages(conversation, Config.PAGE_SIZE));
        conversation.findUnsentTextMessages(
                message -> markMessage(message, Message.STATUS_WAITING));
        conversation.findUnreadMessagesAndCalls(mNotificationService::pushFromBacklog);
    }

    public void loadPhoneContacts() {
        mContactMergerExecutor.execute(
                () -> {
                    final Map<Jid, JabberIdContact> contacts = JabberIdContact.load(this);
                    Log.d(Config.LOGTAG, "start merging phone contacts with roster");
                    // TODO if we do this merge this only on enabled accounts we need to trigger
                    // this upon enable
                    for (final Account account : accounts) {
                        final var remaining =
                                new ArrayList<>(
                                        account.getRoster()
                                                .getWithSystemAccounts(JabberIdContact.class));
                        for (final JabberIdContact jidContact : contacts.values()) {
                            final Contact contact =
                                    account.getRoster().getContact(jidContact.getJid());
                            boolean needsCacheClean = contact.setPhoneContact(jidContact);
                            if (needsCacheClean) {
                                getAvatarService().clear(contact);
                            }
                            remaining.remove(contact);
                        }
                        for (final Contact contact : remaining) {
                            boolean needsCacheClean =
                                    contact.unsetPhoneContact(JabberIdContact.class);
                            if (needsCacheClean) {
                                getAvatarService().clear(contact);
                            }
                        }
                    }
                    Log.d(Config.LOGTAG, "finished merging phone contacts");
                    mShortcutService.refresh(
                            mInitialAddressbookSyncCompleted.compareAndSet(false, true));
                    updateRosterUi();
                    mQuickConversationsService.considerSync();
                });
    }

    public List<Conversation> getConversations() {
        return this.conversations;
    }

    private void markFileDeleted(final File file) {
        synchronized (FILENAMES_TO_IGNORE_DELETION) {
            if (FILENAMES_TO_IGNORE_DELETION.remove(file.getAbsolutePath())) {
                Log.d(Config.LOGTAG, "ignored deletion of " + file.getAbsolutePath());
                return;
            }
        }
        final boolean isInternalFile = fileBackend.isInternalFile(file);
        final List<String> uuids = databaseBackend.markFileAsDeleted(file, isInternalFile);
        Log.d(
                Config.LOGTAG,
                "deleted file "
                        + file.getAbsolutePath()
                        + " internal="
                        + isInternalFile
                        + ", database hits="
                        + uuids.size());
        markUuidsAsDeletedFiles(uuids);
    }

    private void markUuidsAsDeletedFiles(List<String> uuids) {
        boolean deleted = false;
        for (Conversation conversation : getConversations()) {
            deleted |= conversation.markAsDeleted(uuids);
        }
        for (final String uuid : uuids) {
            evictPreview(uuid);
        }
        if (deleted) {
            updateConversationUi();
        }
    }

    private void markChangedFiles(List<DatabaseBackend.FilePathInfo> infos) {
        boolean changed = false;
        for (Conversation conversation : getConversations()) {
            changed |= conversation.markAsChanged(infos);
        }
        if (changed) {
            updateConversationUi();
        }
    }

    public void populateWithOrderedConversations(final List<Conversation> list) {
        populateWithOrderedConversations(list, true, true);
    }

    public void populateWithOrderedConversations(
            final List<Conversation> list, final boolean includeNoFileUpload, final boolean sort) {
        final List<String> orderedUuids;
        if (sort) {
            orderedUuids = null;
        } else {
            orderedUuids = new ArrayList<>();
            for (Conversation conversation : list) {
                orderedUuids.add(conversation.getUuid());
            }
        }
        list.clear();
        if (includeNoFileUpload) {
            list.addAll(getConversations());
        } else {
            for (Conversation conversation : getConversations()) {
                if (conversation.getMode() == Conversation.MODE_SINGLE
                        || (conversation.getAccount().httpUploadAvailable()
                                && conversation.getMucOptions().participating())) {
                    list.add(conversation);
                }
            }
        }
        try {
            if (orderedUuids != null) {
                Collections.sort(
                        list,
                        (a, b) -> {
                            final int indexA = orderedUuids.indexOf(a.getUuid());
                            final int indexB = orderedUuids.indexOf(b.getUuid());
                            if (indexA == -1 || indexB == -1 || indexA == indexB) {
                                return a.compareTo(b);
                            }
                            return indexA - indexB;
                        });
            } else {
                Collections.sort(list);
            }
        } catch (IllegalArgumentException e) {
            // ignore
        }
    }

    public void loadMoreMessages(
            final Conversation conversation,
            final long timestamp,
            final OnMoreMessagesLoaded callback) {
        final var connection = conversation.getAccount().getXmppConnection();
        if (connection
                .getManager(MessageArchiveManager.class)
                .queryInProgress(conversation, callback)) {
            return;
        } else if (timestamp == 0) {
            return;
        }
        Log.d(
                Config.LOGTAG,
                "load more messages for "
                        + conversation.getName()
                        + " prior to "
                        + MessageGenerator.getTimestamp(timestamp));
        final Runnable runnable =
                () -> {
                    final Account account = conversation.getAccount();
                    List<Message> messages =
                            databaseBackend.getMessages(conversation, 50, timestamp);
                    if (messages.size() > 0) {
                        conversation.addAll(0, messages);
                        callback.onMoreMessagesLoaded(messages.size(), conversation);
                    } else if (conversation.hasMessagesLeftOnServer()
                            && account.isOnlineAndConnected()
                            && conversation.getLastClearHistory().getTimestamp() == 0) {
                        final boolean mamAvailable;
                        if (conversation.getMode() == Conversation.MODE_SINGLE) {
                            mamAvailable =
                                    account.getXmppConnection()
                                                    .getManager(MessageArchiveManager.class)
                                                    .hasFeature()
                                            && !conversation.getContact().isBlocked();
                        } else {
                            mamAvailable = conversation.getMucOptions().mamSupport();
                        }
                        if (mamAvailable) {
                            MessageArchiveManager.Query query =
                                    connection
                                            .getManager(MessageArchiveManager.class)
                                            .query(
                                                    conversation,
                                                    new MamReference(0),
                                                    timestamp,
                                                    false);
                            if (query != null) {
                                query.setCallback(callback);
                                callback.informUser(R.string.fetching_history_from_server);
                            } else {
                                callback.informUser(R.string.not_fetching_history_retention_period);
                            }
                        }
                    }
                };
        mDatabaseReaderExecutor.execute(runnable);
    }

    public List<Account> getAccounts() {
        return this.accounts;
    }

    public Conversation find(final Contact contact) {
        for (final Conversation conversation : this.conversations) {
            if (conversation.getContact() == contact) {
                return conversation;
            }
        }
        return null;
    }

    public Conversation find(
            final Iterable<Conversation> haystack, final Account account, final Jid jid) {
        if (jid == null) {
            return null;
        }
        for (final Conversation conversation : haystack) {
            if ((account == null || conversation.getAccount() == account)
                    && (conversation.getAddress().asBareJid().equals(jid.asBareJid()))) {
                return conversation;
            }
        }
        return null;
    }

    public boolean isConversationStillOpen(final Conversation conversation) {
        synchronized (this.conversations) {
            for (Conversation current : this.conversations) {
                if (current == conversation) {
                    return true;
                }
            }
        }
        return false;
    }

    public Conversation findOrCreateConversation(
            Account account, Jid jid, boolean muc, final boolean async) {
        return this.findOrCreateConversation(account, jid, muc, false, async);
    }

    public Conversation findOrCreateConversation(
            final Account account,
            final Jid jid,
            final boolean muc,
            final boolean joinAfterCreate,
            final boolean async) {
        return this.findOrCreateConversation(account, jid, muc, joinAfterCreate, null, async);
    }

    public Conversation findOrCreateConversation(
            final Account account,
            final Jid jid,
            final boolean muc,
            final boolean joinAfterCreate,
            final MessageArchiveManager.Query query,
            final boolean async) {
        synchronized (this.conversations) {
            final var cached = find(account, jid);
            if (cached != null) {
                return cached;
            }
            final var existing = databaseBackend.findConversation(account, jid);
            final Conversation conversation;
            final boolean loadMessagesFromDb;
            if (existing != null) {
                conversation = existing;
                loadMessagesFromDb = restoreFromArchive(conversation, jid, muc);
            } else {
                String conversationName;
                final Contact contact = account.getRoster().getContact(jid);
                if (contact != null) {
                    conversationName = contact.getDisplayName();
                } else {
                    conversationName = jid.getLocal();
                }
                if (muc) {
                    conversation =
                            new Conversation(
                                    conversationName, account, jid, Conversation.MODE_MULTI);
                } else {
                    conversation =
                            new Conversation(
                                    conversationName,
                                    account,
                                    jid.asBareJid(),
                                    Conversation.MODE_SINGLE);
                }
                this.databaseBackend.createConversation(conversation);
                loadMessagesFromDb = false;
            }
            if (async) {
                mDatabaseReaderExecutor.execute(
                        () ->
                                postProcessConversation(
                                        conversation, loadMessagesFromDb, joinAfterCreate, query));
            } else {
                postProcessConversation(conversation, loadMessagesFromDb, joinAfterCreate, query);
            }
            this.conversations.add(conversation);
            updateConversationUi();
            return conversation;
        }
    }

    public Conversation findConversationByUuidReliable(final String uuid) {
        final var cached = findConversationByUuid(uuid);
        if (cached != null) {
            return cached;
        }
        final var existing = databaseBackend.findConversation(uuid);
        if (existing == null) {
            return null;
        }
        Log.d(Config.LOGTAG, "restoring conversation with " + existing.getAddress() + " from DB");
        final Map<String, Account> accounts =
                ImmutableMap.copyOf(Maps.uniqueIndex(this.accounts, Account::getUuid));
        final var account = accounts.get(existing.getAccountUuid());
        if (account == null) {
            Log.d(Config.LOGTAG, "could not find account " + existing.getAccountUuid());
            return null;
        }
        existing.setAccount(account);
        final var loadMessagesFromDb = restoreFromArchive(existing);
        mDatabaseReaderExecutor.execute(
                () ->
                        postProcessConversation(
                                existing,
                                loadMessagesFromDb,
                                existing.getMode() == Conversational.MODE_MULTI,
                                null));
        this.conversations.add(existing);
        if (existing.getMode() == Conversational.MODE_MULTI) {
            account.getXmppConnection()
                    .getManager(BookmarkManager.class)
                    .ensureBookmarkIsAutoJoin(existing);
        }
        updateConversationUi();
        return existing;
    }

    private boolean restoreFromArchive(
            final Conversation conversation, final Jid jid, final boolean muc) {
        if (muc) {
            conversation.setMode(Conversation.MODE_MULTI);
            conversation.setContactJid(jid);
        } else {
            conversation.setMode(Conversation.MODE_SINGLE);
            conversation.setContactJid(jid.asBareJid());
        }
        return restoreFromArchive(conversation);
    }

    private boolean restoreFromArchive(final Conversation conversation) {
        conversation.setStatus(Conversation.STATUS_AVAILABLE);
        databaseBackend.updateConversation(conversation);
        return conversation.messagesLoaded.compareAndSet(true, false);
    }

    private void postProcessConversation(
            final Conversation c,
            final boolean loadMessagesFromDb,
            final boolean joinAfterCreate,
            final MessageArchiveManager.Query query) {
        final var singleMode = c.getMode() == Conversational.MODE_SINGLE;
        final var account = c.getAccount();
        if (loadMessagesFromDb) {
            c.addAll(0, databaseBackend.getMessages(c, Config.PAGE_SIZE));
            updateConversationUi();
            c.messagesLoaded.set(true);
        }
        final var connection = account.getXmppConnection();
        final var archiveManager = connection.getManager(MessageArchiveManager.class);
        if (account.getXmppConnection() != null
                && !c.getContact().isBlocked()
                && archiveManager.hasFeature()
                && singleMode) {
            if (query == null) {
                archiveManager.query(c);
            } else {
                if (query.getConversation() == null) {
                    archiveManager.query(c, query.getStart(), query.isCatchup());
                }
            }
        }
        if (joinAfterCreate) {
            joinMuc(c);
        }
    }

    public void archiveConversation(Conversation conversation) {
        archiveConversation(conversation, true);
    }

    public void archiveConversation(
            Conversation conversation, final boolean maySynchronizeWithBookmarks) {
        final var account = conversation.getAccount();
        final var connection = account.getXmppConnection();
        getNotificationService().clear(conversation);
        conversation.setStatus(Conversation.STATUS_ARCHIVED);
        conversation.setNextMessage(null);
        synchronized (this.conversations) {
            connection.getManager(MessageArchiveManager.class).kill(conversation);
            if (conversation.getMode() == Conversation.MODE_MULTI) {
                // TODO always clean up bookmarks no matter if we are currently connected
                // TODO always delete reference to conversation in bookmark
                if (conversation.getAccount().getStatus() == Account.State.ONLINE) {
                    final Bookmark existing = conversation.getBookmark();
                    if (maySynchronizeWithBookmarks && existing != null) {
                        if (conversation.getMucOptions().getError() == MucOptions.Error.DESTROYED) {
                            deleteBookmark(account, existing);
                        } else if (existing.isAutoJoin()) {
                            final var bookmark =
                                    ImmutableBookmark.builder()
                                            .from(existing)
                                            .isAutoJoin(false)
                                            .build();
                            createBookmark(bookmark.getAccount(), bookmark);
                        }
                    }
                }
                connection.getManager(MultiUserChatManager.class).leave(conversation);
            } else {
                if (conversation
                        .getContact()
                        .getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)) {
                    stopPresenceUpdatesTo(conversation.getContact());
                }
            }
            updateConversation(conversation);
            this.conversations.remove(conversation);
            updateConversationUi();
        }
    }

    public void stopPresenceUpdatesTo(final Contact contact) {
        Log.d(Config.LOGTAG, "Canceling presence request from " + contact.getAddress().toString());
        contact.resetOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST);
        contact.getAccount()
                .getXmppConnection()
                .getManager(PresenceManager.class)
                .unsubscribed(contact.getAddress().asBareJid());
    }

    public void createAccount(final Account account) {
        account.setXmppConnection(new XmppConnection(account, this));
        databaseBackend.createAccount(account);
        if (CallIntegration.hasSystemFeature(this)) {
            CallIntegrationConnectionService.togglePhoneAccountAsync(this, account);
        }
        this.accounts.add(account);
        this.reconnectAccountInBackground(account);
        updateAccountUi();
        Conversations.getInstance(this).resetAccounts();
        toggleForegroundService();
    }

    private void toggleSetProfilePictureActivity(final boolean enabled) {
        try {
            final ComponentName name =
                    new ComponentName(this, ChooseAccountForProfilePictureActivity.class);
            final int targetState =
                    enabled
                            ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
            getPackageManager()
                    .setComponentEnabledSetting(name, targetState, PackageManager.DONT_KILL_APP);
        } catch (IllegalStateException e) {
            Log.d(Config.LOGTAG, "unable to toggle profile picture activity");
        }
    }

    public boolean reconfigurePushDistributor() {
        return this.unifiedPushBroker.reconfigurePushDistributor();
    }

    private Optional<UnifiedPushBroker.Transport> renewUnifiedPushEndpoints(
            final UnifiedPushBroker.PushTargetMessenger pushTargetMessenger) {
        return this.unifiedPushBroker.renewUnifiedPushEndpoints(pushTargetMessenger);
    }

    public Optional<UnifiedPushBroker.Transport> renewUnifiedPushEndpoints() {
        return this.unifiedPushBroker.renewUnifiedPushEndpoints(null);
    }

    public UnifiedPushBroker getUnifiedPushBroker() {
        return this.unifiedPushBroker;
    }

    private void provisionAccount(final String address, final String password) {
        final Jid jid = Jid.of(address);
        final Account account = new Account(jid, password);
        account.setOption(Account.OPTION_DISABLED, true);
        Log.d(Config.LOGTAG, jid.asBareJid().toString() + ": provisioning account");
        createAccount(account);
    }

    public void createAccountFromKey(final String alias, final OnAccountCreated callback) {
        new Thread(
                        () -> {
                            try {
                                final X509Certificate[] chain =
                                        KeyChain.getCertificateChain(this, alias);
                                final X509Certificate cert =
                                        chain != null && chain.length > 0 ? chain[0] : null;
                                if (cert == null) {
                                    callback.informUser(R.string.unable_to_parse_certificate);
                                    return;
                                }
                                Pair<Jid, String> info = CryptoHelper.extractJidAndName(cert);
                                if (info == null) {
                                    callback.informUser(R.string.certificate_does_not_contain_jid);
                                    return;
                                }
                                if (findAccountByJid(info.first) == null) {
                                    final Account account = new Account(info.first, "");
                                    account.setPrivateKeyAlias(alias);
                                    account.setOption(Account.OPTION_DISABLED, true);
                                    account.setOption(Account.OPTION_FIXED_USERNAME, true);
                                    account.setDisplayName(info.second);
                                    createAccount(account);
                                    callback.onAccountCreated(account);
                                    if (Config.X509_VERIFICATION) {
                                        try {
                                            getMemorizingTrustManager()
                                                    .getNonInteractive(account.getServer())
                                                    .checkClientTrusted(chain, "RSA");
                                        } catch (CertificateException e) {
                                            callback.informUser(
                                                    R.string.certificate_chain_is_not_trusted);
                                        }
                                    }
                                } else {
                                    callback.informUser(R.string.account_already_exists);
                                }
                            } catch (Exception e) {
                                callback.informUser(R.string.unable_to_parse_certificate);
                            }
                        })
                .start();
    }

    public void updateKeyInAccount(final Account account, final String alias) {
        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": update key in account " + alias);
        try {
            X509Certificate[] chain =
                    KeyChain.getCertificateChain(XmppConnectionService.this, alias);
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + " loaded certificate chain");
            Pair<Jid, String> info = CryptoHelper.extractJidAndName(chain[0]);
            if (info == null) {
                showErrorToastInUi(R.string.certificate_does_not_contain_jid);
                return;
            }
            if (account.getJid().asBareJid().equals(info.first)) {
                account.setPrivateKeyAlias(alias);
                account.setDisplayName(info.second);
                databaseBackend.updateAccount(account);
                if (Config.X509_VERIFICATION) {
                    try {
                        getMemorizingTrustManager()
                                .getNonInteractive()
                                .checkClientTrusted(chain, "RSA");
                    } catch (CertificateException e) {
                        showErrorToastInUi(R.string.certificate_chain_is_not_trusted);
                    }
                    account.getAxolotlService().regenerateKeys(true);
                }
            } else {
                showErrorToastInUi(R.string.jid_does_not_match_certificate);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean updateAccount(final Account account) {
        if (databaseBackend.updateAccount(account)) {
            account.setShowErrorNotification(true);
            // TODO what was the purpose of that? will likely be triggered by reconnect anyway?
            // this.statusListener.onStatusChanged(account);
            databaseBackend.updateAccount(account);
            reconnectAccountInBackground(account);
            updateAccountUi();
            getNotificationService().updateErrorNotification();
            toggleForegroundService();
            Conversations.getInstance(this).resetAccounts();
            mChannelDiscoveryService.cleanCache();
            if (CallIntegration.hasSystemFeature(this)) {
                CallIntegrationConnectionService.togglePhoneAccountAsync(this, account);
            }
            return true;
        } else {
            return false;
        }
    }

    public ListenableFuture<Void> updateAccountPasswordOnServer(
            final Account account, final String newPassword) {
        final var connection = account.getXmppConnection();
        return connection.getManager(RegistrationManager.class).setPassword(newPassword);
    }

    public void deleteAccount(final Account account) {
        final boolean connected = account.getStatus() == Account.State.ONLINE;
        synchronized (this.conversations) {
            if (connected) {
                account.getAxolotlService().deleteOmemoIdentity();
            }
            for (final Conversation conversation : conversations) {
                if (conversation.getAccount() == account) {
                    if (conversation.getMode() == Conversation.MODE_MULTI) {
                        if (connected) {
                            account.getXmppConnection()
                                    .getManager(MultiUserChatManager.class)
                                    .unavailable(conversation);
                        }
                    }
                    conversations.remove(conversation);
                    mNotificationService.clear(conversation);
                }
            }
            XmppConnection.RECONNNECTION_EXECUTOR.execute(() -> disconnect(account, !connected));
            mDatabaseWriterExecutor.execute(
                    () -> {
                        if (databaseBackend.deleteAccount(account)) {
                            Log.d(Config.LOGTAG, "deleted account from database");
                        }
                    });
            this.accounts.remove(account);
            if (CallIntegration.hasSystemFeature(this)) {
                CallIntegrationConnectionService.unregisterPhoneAccount(this, account);
            }
            updateAccountUi();
            mNotificationService.updateErrorNotification();
            Conversations.getInstance(this).resetAccounts();
            toggleForegroundService();
        }
    }

    public void setOnConversationListChangedListener(OnConversationUpdate listener) {
        final boolean remainingListeners;
        synchronized (LISTENER_LOCK) {
            remainingListeners = checkListeners();
            if (!this.mOnConversationUpdates.add(listener)) {
                Log.w(
                        Config.LOGTAG,
                        listener.getClass().getName()
                                + " is already registered as ConversationListChangedListener");
            }
            this.mNotificationService.setIsInForeground(!this.mOnConversationUpdates.isEmpty());
        }
        if (remainingListeners) {
            switchToForeground();
        }
    }

    public void removeOnConversationListChangedListener(OnConversationUpdate listener) {
        final boolean remainingListeners;
        synchronized (LISTENER_LOCK) {
            this.mOnConversationUpdates.remove(listener);
            this.mNotificationService.setIsInForeground(!this.mOnConversationUpdates.isEmpty());
            remainingListeners = checkListeners();
        }
        if (remainingListeners) {
            switchToBackground();
        }
    }

    public void setOnShowErrorToastListener(OnShowErrorToast listener) {
        final boolean remainingListeners;
        synchronized (LISTENER_LOCK) {
            remainingListeners = checkListeners();
            if (!this.mOnShowErrorToasts.add(listener)) {
                Log.w(
                        Config.LOGTAG,
                        listener.getClass().getName()
                                + " is already registered as OnShowErrorToastListener");
            }
        }
        if (remainingListeners) {
            switchToForeground();
        }
    }

    public void removeOnShowErrorToastListener(OnShowErrorToast onShowErrorToast) {
        final boolean remainingListeners;
        synchronized (LISTENER_LOCK) {
            this.mOnShowErrorToasts.remove(onShowErrorToast);
            remainingListeners = checkListeners();
        }
        if (remainingListeners) {
            switchToBackground();
        }
    }

    public void setOnAccountListChangedListener(OnAccountUpdate listener) {
        final boolean remainingListeners;
        synchronized (LISTENER_LOCK) {
            remainingListeners = checkListeners();
            if (!this.mOnAccountUpdates.add(listener)) {
                Log.w(
                        Config.LOGTAG,
                        listener.getClass().getName()
                                + " is already registered as OnAccountListChangedtListener");
            }
        }
        if (remainingListeners) {
            switchToForeground();
        }
    }

    public void removeOnAccountListChangedListener(OnAccountUpdate listener) {
        final boolean remainingListeners;
        synchronized (LISTENER_LOCK) {
            this.mOnAccountUpdates.remove(listener);
            remainingListeners = checkListeners();
        }
        if (remainingListeners) {
            switchToBackground();
        }
    }

    public void setOnCaptchaRequestedListener(OnCaptchaRequested listener) {
        final boolean remainingListeners;
        synchronized (LISTENER_LOCK) {
            remainingListeners = checkListeners();
            if (!this.mOnCaptchaRequested.add(listener)) {
                Log.w(
                        Config.LOGTAG,
                        listener.getClass().getName()
                                + " is already registered as OnCaptchaRequestListener");
            }
        }
        if (remainingListeners) {
            switchToForeground();
        }
    }

    public void removeOnCaptchaRequestedListener(OnCaptchaRequested listener) {
        final boolean remainingListeners;
        synchronized (LISTENER_LOCK) {
            this.mOnCaptchaRequested.remove(listener);
            remainingListeners = checkListeners();
        }
        if (remainingListeners) {
            switchToBackground();
        }
    }

    public void setOnRosterUpdateListener(final OnRosterUpdate listener) {
        final boolean remainingListeners;
        synchronized (LISTENER_LOCK) {
            remainingListeners = checkListeners();
            if (!this.mOnRosterUpdates.add(listener)) {
                Log.w(
                        Config.LOGTAG,
                        listener.getClass().getName()
                                + " is already registered as OnRosterUpdateListener");
            }
        }
        if (remainingListeners) {
            switchToForeground();
        }
    }

    public void removeOnRosterUpdateListener(final OnRosterUpdate listener) {
        final boolean remainingListeners;
        synchronized (LISTENER_LOCK) {
            this.mOnRosterUpdates.remove(listener);
            remainingListeners = checkListeners();
        }
        if (remainingListeners) {
            switchToBackground();
        }
    }

    public void setOnUpdateBlocklistListener(final OnUpdateBlocklist listener) {
        final boolean remainingListeners;
        synchronized (LISTENER_LOCK) {
            remainingListeners = checkListeners();
            if (!this.mOnUpdateBlocklist.add(listener)) {
                Log.w(
                        Config.LOGTAG,
                        listener.getClass().getName()
                                + " is already registered as OnUpdateBlocklistListener");
            }
        }
        if (remainingListeners) {
            switchToForeground();
        }
    }

    public void removeOnUpdateBlocklistListener(final OnUpdateBlocklist listener) {
        final boolean remainingListeners;
        synchronized (LISTENER_LOCK) {
            this.mOnUpdateBlocklist.remove(listener);
            remainingListeners = checkListeners();
        }
        if (remainingListeners) {
            switchToBackground();
        }
    }

    public void setOnKeyStatusUpdatedListener(final OnKeyStatusUpdated listener) {
        final boolean remainingListeners;
        synchronized (LISTENER_LOCK) {
            remainingListeners = checkListeners();
            if (!this.mOnKeyStatusUpdated.add(listener)) {
                Log.w(
                        Config.LOGTAG,
                        listener.getClass().getName()
                                + " is already registered as OnKeyStatusUpdateListener");
            }
        }
        if (remainingListeners) {
            switchToForeground();
        }
    }

    public void removeOnNewKeysAvailableListener(final OnKeyStatusUpdated listener) {
        final boolean remainingListeners;
        synchronized (LISTENER_LOCK) {
            this.mOnKeyStatusUpdated.remove(listener);
            remainingListeners = checkListeners();
        }
        if (remainingListeners) {
            switchToBackground();
        }
    }

    public void setOnRtpConnectionUpdateListener(final OnJingleRtpConnectionUpdate listener) {
        final boolean remainingListeners;
        synchronized (LISTENER_LOCK) {
            remainingListeners = checkListeners();
            if (!this.onJingleRtpConnectionUpdate.add(listener)) {
                Log.w(
                        Config.LOGTAG,
                        listener.getClass().getName()
                                + " is already registered as OnJingleRtpConnectionUpdate");
            }
        }
        if (remainingListeners) {
            switchToForeground();
        }
    }

    public void removeRtpConnectionUpdateListener(final OnJingleRtpConnectionUpdate listener) {
        final boolean remainingListeners;
        synchronized (LISTENER_LOCK) {
            this.onJingleRtpConnectionUpdate.remove(listener);
            remainingListeners = checkListeners();
        }
        if (remainingListeners) {
            switchToBackground();
        }
    }

    public void setOnMucRosterUpdateListener(OnMucRosterUpdate listener) {
        final boolean remainingListeners;
        synchronized (LISTENER_LOCK) {
            remainingListeners = checkListeners();
            if (!this.mOnMucRosterUpdate.add(listener)) {
                Log.w(
                        Config.LOGTAG,
                        listener.getClass().getName()
                                + " is already registered as OnMucRosterListener");
            }
        }
        if (remainingListeners) {
            switchToForeground();
        }
    }

    public void removeOnMucRosterUpdateListener(final OnMucRosterUpdate listener) {
        final boolean remainingListeners;
        synchronized (LISTENER_LOCK) {
            this.mOnMucRosterUpdate.remove(listener);
            remainingListeners = checkListeners();
        }
        if (remainingListeners) {
            switchToBackground();
        }
    }

    public boolean checkListeners() {
        return (this.mOnAccountUpdates.isEmpty()
                && this.mOnConversationUpdates.isEmpty()
                && this.mOnRosterUpdates.isEmpty()
                && this.mOnCaptchaRequested.isEmpty()
                && this.mOnMucRosterUpdate.isEmpty()
                && this.mOnUpdateBlocklist.isEmpty()
                && this.mOnShowErrorToasts.isEmpty()
                && this.onJingleRtpConnectionUpdate.isEmpty()
                && this.mOnKeyStatusUpdated.isEmpty());
    }

    private void switchToForeground() {
        toggleSoftDisabled(false);
        final boolean broadcastLastActivity = appSettings.isBroadcastLastActivity();
        for (final var account : getAccounts()) {
            final XmppConnection connection = account.getXmppConnection();
            connection.getManager(MultiUserChatManager.class).resetChatStates();
            connection.getManager(ChatStateManager.class).resetChatStates();
            if (account.getStatus() != Account.State.ONLINE) {
                continue;
            }
            connection.getManager(ActivityManager.class).reset();
            if (connection.getFeatures().csi()) {
                connection.sendActive();
            }
            if (broadcastLastActivity) {
                // send new presence but don't include idle because we are not
                connection.getManager(PresenceManager.class).available(false);
            }
        }
        Log.d(Config.LOGTAG, "app switched into foreground");
    }

    private void switchToBackground() {
        final boolean broadcastLastActivity = appSettings.isBroadcastLastActivity();
        if (broadcastLastActivity) {
            mLastActivity = System.currentTimeMillis();
            final SharedPreferences.Editor editor = getPreferences().edit();
            editor.putLong(SETTING_LAST_ACTIVITY_TS, mLastActivity);
            editor.apply();
        }
        for (final var account : getAccounts()) {
            if (account.getStatus() != Account.State.ONLINE) {
                continue;
            }
            final var connection = account.getXmppConnection();
            if (broadcastLastActivity) {
                connection.getManager(PresenceManager.class).available(true);
            }
            if (connection.getFeatures().csi()) {
                connection.sendInactive();
            }
        }
        this.mNotificationService.setIsInForeground(false);
        Log.d(Config.LOGTAG, "app switched into background");
    }

    public void connectMultiModeConversations(Account account) {
        List<Conversation> conversations = getConversations();
        for (Conversation conversation : conversations) {
            if (conversation.getMode() == Conversation.MODE_MULTI
                    && conversation.getAccount() == account) {
                joinMuc(conversation);
            }
        }
    }

    public void joinMuc(final Conversation conversation) {
        final var account = conversation.getAccount();
        account.getXmppConnection().getManager(MultiUserChatManager.class).join(conversation);
    }

    public void providePasswordForMuc(final Conversation conversation, final String password) {
        final var account = conversation.getAccount();
        account.getXmppConnection()
                .getManager(MultiUserChatManager.class)
                .setPassword(conversation, password);
    }

    public void deleteAvatar(final Account account) {
        final var connection = account.getXmppConnection();

        final var vCardPhotoDeletionFuture =
                connection.getManager(VCardManager.class).deletePhoto();
        final var pepDeletionFuture = connection.getManager(AvatarManager.class).delete();

        final var deletionFuture = Futures.allAsList(vCardPhotoDeletionFuture, pepDeletionFuture);

        Futures.addCallback(
                deletionFuture,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(List<Void> result) {
                        Log.d(
                                Config.LOGTAG,
                                account.getJid().asBareJid() + ": deleted avatar from server");
                        account.setAvatar(null);
                        databaseBackend.updateAccount(account);
                        getAvatarService().clear(account);
                        updateAccountUi();
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        Log.d(
                                Config.LOGTAG,
                                account.getJid().asBareJid() + ": could not delete avatar",
                                t);
                    }
                },
                MoreExecutors.directExecutor());
    }

    public void deletePepNode(final Account account, final String node) {
        final var future = account.getXmppConnection().getManager(PepManager.class).delete(node);
        Futures.addCallback(
                future,
                new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        Log.d(
                                Config.LOGTAG,
                                account.getJid().asBareJid()
                                        + ": successfully deleted pep node "
                                        + node);
                    }

                    @Override
                    public void onFailure(@NonNull Throwable t) {
                        Log.d(
                                Config.LOGTAG,
                                account.getJid().asBareJid() + ": failed to delete node " + node,
                                t);
                    }
                },
                MoreExecutors.directExecutor());
    }

    private boolean hasEnabledAccounts() {
        if (this.accounts == null) {
            return false;
        }
        for (final Account account : this.accounts) {
            if (account.isConnectionEnabled()) {
                return true;
            }
        }
        return false;
    }

    public void getAttachments(
            final Conversation conversation, int limit, final OnMediaLoaded onMediaLoaded) {
        getAttachments(
                conversation.getAccount(),
                conversation.getAddress().asBareJid(),
                limit,
                onMediaLoaded);
    }

    public void getAttachments(
            final Account account,
            final Jid jid,
            final int limit,
            final OnMediaLoaded onMediaLoaded) {
        getAttachments(account.getUuid(), jid.asBareJid(), limit, onMediaLoaded);
    }

    public void getAttachments(
            final String account,
            final Jid jid,
            final int limit,
            final OnMediaLoaded onMediaLoaded) {
        new Thread(
                        () ->
                                onMediaLoaded.onMediaLoaded(
                                        fileBackend.convertToAttachments(
                                                databaseBackend.getRelativeFilePaths(
                                                        account, jid, limit))))
                .start();
    }

    public void persistSelfNick(final MucOptions.User self, final boolean modified) {
        final Conversation conversation = self.getConversation();
        final Account account = conversation.getAccount();
        final Jid full = self.getFullJid();
        if (!full.equals(conversation.getAddress())) {
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": persisting full jid " + full);
            conversation.setContactJid(full);
            databaseBackend.updateConversation(conversation);
        }

        final Bookmark existing = conversation.getBookmark();
        if (existing == null || !modified) {
            return;
        }
        final var nick = full.getResource();
        final String defaultNick = MucOptions.defaultNick(account);
        if (nick.equals(defaultNick) || nick.equals(existing.getNick())) {
            return;
        }

        // TODO should we just call Bookmark.nickOfAddress and use that; meaning we would remove a
        // bookmark if it is there

        Log.d(
                Config.LOGTAG,
                account.getJid().asBareJid()
                        + ": persist nick '"
                        + full.getResource()
                        + "' into bookmark for "
                        + conversation.getAddress().asBareJid());
        final var bookmark = ImmutableBookmark.builder().from(existing).nick(nick).build();
        createBookmark(bookmark.getAccount(), bookmark);
    }

    public void checkMucRequiresRename() {
        synchronized (this.conversations) {
            for (final Conversation conversation : this.conversations) {
                if (conversation.getMode() == Conversational.MODE_MULTI) {
                    final var account = conversation.getAccount();
                    account.getXmppConnection()
                            .getManager(MultiUserChatManager.class)
                            .checkMucRequiresRename(conversation);
                }
            }
        }
    }

    public ListenableFuture<Conversation> createAdhocConference(
            final Account account, final String name, final Collection<Jid> addresses) {
        final var manager = account.getXmppConnection().getManager(MultiUserChatManager.class);
        return manager.createPrivateGroupChat(name, addresses);
    }

    public void pushNodeConfiguration(
            Account account,
            final String node,
            final Bundle options,
            final OnConfigurationPushed callback) {
        pushNodeConfiguration(account, account.getJid().asBareJid(), node, options, callback);
    }

    public void pushNodeConfiguration(
            Account account,
            final Jid jid,
            final String node,
            final Bundle options,
            final OnConfigurationPushed callback) {
        Log.d(Config.LOGTAG, "pushing node configuration");
        sendIqPacket(
                account,
                mIqGenerator.requestPubsubConfiguration(jid, node),
                responseToRequest -> {
                    if (responseToRequest.getType() == Iq.Type.RESULT) {
                        Element pubsub =
                                responseToRequest.findChild(
                                        "pubsub", "http://jabber.org/protocol/pubsub#owner");
                        Element configuration =
                                pubsub == null ? null : pubsub.findChild("configure");
                        Element x =
                                configuration == null
                                        ? null
                                        : configuration.findChild("x", Namespace.DATA);
                        if (x != null) {
                            final Data data = Data.parse(x);
                            data.submit(options);
                            sendIqPacket(
                                    account,
                                    mIqGenerator.publishPubsubConfiguration(jid, node, data),
                                    responseToPublish -> {
                                        if (responseToPublish.getType() == Iq.Type.RESULT
                                                && callback != null) {
                                            Log.d(
                                                    Config.LOGTAG,
                                                    account.getJid().asBareJid()
                                                            + ": successfully changed node"
                                                            + " configuration for node "
                                                            + node);
                                            callback.onPushSucceeded();
                                        } else if (responseToPublish.getType() == Iq.Type.ERROR
                                                && callback != null) {
                                            callback.onPushFailed();
                                        }
                                    });
                        } else if (callback != null) {
                            callback.onPushFailed();
                        }
                    } else if (responseToRequest.getType() == Iq.Type.ERROR && callback != null) {
                        callback.onPushFailed();
                    }
                });
    }

    public void pushSubjectToConference(final Conversation conference, final String subject) {
        final var account = conference.getAccount();
        account.getXmppConnection()
                .getManager(MultiUserChatManager.class)
                .setSubject(conference, subject);
    }

    public void changeAffiliationInConference(
            final Conversation conference,
            Jid user,
            final Affiliation affiliation,
            final OnAffiliationChanged callback) {
        final var account = conference.getAccount();
        final var future =
                account.getXmppConnection()
                        .getManager(MultiUserChatManager.class)
                        .setAffiliation(conference, affiliation, user);
        Futures.addCallback(
                future,
                new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        if (callback != null) {
                            callback.onAffiliationChangedSuccessful(user);
                        } else {
                            Log.d(
                                    Config.LOGTAG,
                                    "changed affiliation of " + user + " to " + affiliation);
                        }
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        if (callback != null) {
                            callback.onAffiliationChangeFailed(
                                    user, R.string.could_not_change_affiliation);
                        } else {
                            Log.d(Config.LOGTAG, "could not change affiliation", t);
                        }
                    }
                },
                MoreExecutors.directExecutor());
    }

    public void changeRoleInConference(
            final Conversation conference, final String nick, Role role) {
        final var account = conference.getAccount();
        account.getXmppConnection()
                .getManager(MultiUserChatManager.class)
                .setRole(conference.getAddress().asBareJid(), role, nick);
    }

    public ListenableFuture<Void> destroyRoom(final Conversation conversation) {
        final var account = conversation.getAccount();
        return account.getXmppConnection()
                .getManager(MultiUserChatManager.class)
                .destroy(conversation.getAddress().asBareJid());
    }

    private void disconnect(final Account account, boolean force) {
        final XmppConnection connection = account.getXmppConnection();
        if (connection == null) {
            return;
        }
        if (!force) {
            final List<Conversation> conversations = getConversations();
            for (Conversation conversation : conversations) {
                if (conversation.getAccount() == account) {
                    if (conversation.getMode() == Conversation.MODE_MULTI) {
                        account.getXmppConnection()
                                .getManager(MultiUserChatManager.class)
                                .unavailable(conversation);
                    }
                }
            }
            connection.getManager(PresenceManager.class).unavailable();
        }
        connection.disconnect(force);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public void updateMessage(Message message) {
        updateMessage(message, true);
    }

    public void updateMessage(Message message, boolean includeBody) {
        databaseBackend.updateMessage(message, includeBody);
        updateConversationUi();
    }

    public void createMessageAsync(final Message message) {
        mDatabaseWriterExecutor.execute(() -> databaseBackend.createMessage(message));
    }

    public void updateMessage(Message message, String uuid) {
        if (!databaseBackend.updateMessage(message, uuid)) {
            Log.e(Config.LOGTAG, "error updated message in DB after edit");
        }
        updateConversationUi();
    }

    public void createContact(final Contact contact) {
        createContact(contact, null);
    }

    public void createContact(final Contact contact, final String preAuth) {
        contact.setOption(Contact.Options.PREEMPTIVE_GRANT);
        contact.setOption(Contact.Options.ASKING);
        final var connection = contact.getAccount().getXmppConnection();
        connection.getManager(RosterManager.class).addRosterItem(contact, preAuth);
    }

    public void deleteContactOnServer(final Contact contact) {
        final var connection = contact.getAccount().getXmppConnection();
        connection.getManager(RosterManager.class).deleteRosterItem(contact);
    }

    public void publishMucAvatar(
            final Conversation conversation, final Uri image, final OnAvatarPublication callback) {
        final var connection = conversation.getAccount().getXmppConnection();
        final var future =
                connection
                        .getManager(AvatarManager.class)
                        .publishVCard(conversation.getAddress().asBareJid(), image);
        Futures.addCallback(
                future,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Void result) {
                        callback.onAvatarPublicationSucceeded();
                    }

                    @Override
                    public void onFailure(@NonNull Throwable t) {
                        Log.d(Config.LOGTAG, "could not publish MUC avatar", t);
                        callback.onAvatarPublicationFailed(
                                R.string.error_publish_avatar_server_reject);
                    }
                },
                MoreExecutors.directExecutor());
    }

    public void publishAvatar(
            final Account account,
            final Uri image,
            final boolean open,
            final OnAvatarPublication callback) {

        final var connection = account.getXmppConnection();
        final var publicationFuture =
                connection.getManager(AvatarManager.class).uploadAndPublish(image, open);

        Futures.addCallback(
                publicationFuture,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(final Void result) {
                        Log.d(Config.LOGTAG, "published avatar");
                        callback.onAvatarPublicationSucceeded();
                    }

                    @Override
                    public void onFailure(@NonNull final Throwable t) {
                        Log.d(Config.LOGTAG, "avatar upload failed", t);
                        // TODO actually figure out what went wrong
                        callback.onAvatarPublicationFailed(
                                R.string.error_publish_avatar_server_reject);
                    }
                },
                MoreExecutors.directExecutor());
    }

    public ListenableFuture<Void> checkForAvatar(final Account account) {
        final var connection = account.getXmppConnection();
        return connection
                .getManager(AvatarManager.class)
                .fetchAndStore(account.getJid().asBareJid());
    }

    public void notifyAccountAvatarHasChanged(final Account account) {
        final XmppConnection connection = account.getXmppConnection();
        // this was bookmark conversion for a bit which doesn't make sense
        if (connection.getManager(AvatarManager.class).hasPepToVCardConversion()) {
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid()
                            + ": avatar changed. resending presence to online group chats");
            for (Conversation conversation : conversations) {
                if (conversation.getAccount() == account
                        && conversation.getMode() == Conversational.MODE_MULTI) {
                    connection.getManager(MultiUserChatManager.class).resendPresence(conversation);
                }
            }
        }
    }

    public void updateConversation(final Conversation conversation) {
        mDatabaseWriterExecutor.execute(() -> databaseBackend.updateConversation(conversation));
    }

    public void reconnectAccount(
            final Account account, final boolean force, final boolean interactive) {
        synchronized (account) {
            final XmppConnection connection = account.getXmppConnection();
            final boolean hasInternet = hasInternetConnection();
            if (account.isConnectionEnabled() && hasInternet) {
                if (!force) {
                    disconnect(account, false);
                }
                Thread thread = new Thread(connection);
                connection.setInteractive(interactive);
                connection.prepareNewConnection();
                connection.interrupt();
                thread.start();
                scheduleWakeUpCall(Config.CONNECT_DISCO_TIMEOUT, account.getUuid().hashCode());
            } else {
                disconnect(account, force || account.getTrueStatus().isError() || !hasInternet);
                connection.getManager(PresenceManager.class).clear();
                connection.resetEverything();
                final AxolotlService axolotlService = account.getAxolotlService();
                if (axolotlService != null) {
                    axolotlService.resetBrokenness();
                }
                if (!hasInternet) {
                    // TODO should this go via XmppConnection.setStatusAndTriggerProcessor()?
                    account.setStatus(Account.State.NO_INTERNET);
                }
            }
        }
    }

    public void reconnectAccountInBackground(final Account account) {
        XmppConnection.RECONNNECTION_EXECUTOR.execute(() -> reconnectAccount(account, false, true));
    }

    public void invite(final Conversation conversation, final Jid contact) {
        final var account = conversation.getAccount();
        account.getXmppConnection()
                .getManager(MultiUserChatManager.class)
                .invite(conversation, contact);
    }

    public void directInvite(Conversation conversation, Jid jid) {
        final var account = conversation.getAccount();
        account.getXmppConnection()
                .getManager(MultiUserChatManager.class)
                .directInvite(conversation, jid);
    }

    public void resetSendingToWaiting(Account account) {
        for (Conversation conversation : getConversations()) {
            if (conversation.getAccount() == account) {
                conversation.findUnsentTextMessages(
                        message -> markMessage(message, Message.STATUS_WAITING));
            }
        }
    }

    public Message markMessage(
            final Account account, final Jid recipient, final String uuid, final int status) {
        return markMessage(account, recipient, uuid, status, null);
    }

    public Message markMessage(
            final Account account,
            final Jid recipient,
            final String uuid,
            final int status,
            String errorMessage) {
        if (uuid == null) {
            return null;
        }
        for (Conversation conversation : getConversations()) {
            if (conversation.getAddress().asBareJid().equals(recipient)
                    && conversation.getAccount() == account) {
                final Message message = conversation.findSentMessageWithUuidOrRemoteId(uuid);
                if (message != null) {
                    markMessage(message, status, errorMessage);
                }
                return message;
            }
        }
        return null;
    }

    public boolean markMessage(
            final Conversation conversation,
            final String uuid,
            final int status,
            final String serverMessageId) {
        return markMessage(conversation, uuid, status, serverMessageId, null);
    }

    public boolean markMessage(
            final Conversation conversation,
            final String uuid,
            final int status,
            final String serverMessageId,
            final LocalizedContent body) {
        if (uuid == null) {
            return false;
        } else {
            final Message message = conversation.findSentMessageWithUuid(uuid);
            if (message != null) {
                if (message.getServerMsgId() == null) {
                    message.setServerMsgId(serverMessageId);
                }
                if (message.getEncryption() == Message.ENCRYPTION_NONE
                        && message.isTypeText()
                        && isBodyModified(message, body)) {
                    message.setBody(body.content);
                    if (body.count > 1) {
                        message.setBodyLanguage(body.language);
                    }
                    markMessage(message, status, null, true);
                } else {
                    markMessage(message, status);
                }
                return true;
            } else {
                return false;
            }
        }
    }

    private static boolean isBodyModified(final Message message, final LocalizedContent body) {
        if (body == null || body.content == null) {
            return false;
        }
        return !body.content.equals(message.getBody());
    }

    public void markMessage(Message message, int status) {
        markMessage(message, status, null);
    }

    public void markMessage(final Message message, final int status, final String errorMessage) {
        markMessage(message, status, errorMessage, false);
    }

    public void markMessage(
            final Message message,
            final int status,
            final String errorMessage,
            final boolean includeBody) {
        final int oldStatus = message.getStatus();
        if (status == Message.STATUS_SEND_FAILED
                && (oldStatus == Message.STATUS_SEND_RECEIVED
                        || oldStatus == Message.STATUS_SEND_DISPLAYED)) {
            return;
        }
        if (status == Message.STATUS_SEND_RECEIVED && oldStatus == Message.STATUS_SEND_DISPLAYED) {
            return;
        }
        message.setErrorMessage(errorMessage);
        message.setStatus(status);
        databaseBackend.updateMessage(message, includeBody);
        updateConversationUi();
        if (oldStatus != status && status == Message.STATUS_SEND_FAILED) {
            mNotificationService.pushFailedDelivery(message);
        }
    }

    private SharedPreferences getPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    }

    public long getAutomaticMessageDeletionDate() {
        final long timeout =
                getLongPreference(
                        AppSettings.AUTOMATIC_MESSAGE_DELETION,
                        R.integer.automatic_message_deletion);
        return timeout == 0 ? timeout : (System.currentTimeMillis() - (timeout * 1000));
    }

    public long getLongPreference(String name, @IntegerRes int res) {
        long defaultValue = getResources().getInteger(res);
        try {
            return Long.parseLong(getPreferences().getString(name, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean allowMessageCorrection() {
        return appSettings.isAllowMessageCorrection();
    }

    public boolean useTorToConnect() {
        return appSettings.isUseTor();
    }

    public int unreadCount() {
        int count = 0;
        for (Conversation conversation : getConversations()) {
            count += conversation.unreadCount();
        }
        return count;
    }

    private <T> List<T> threadSafeList(Set<T> set) {
        synchronized (LISTENER_LOCK) {
            return set.isEmpty() ? Collections.emptyList() : new ArrayList<>(set);
        }
    }

    public void showErrorToastInUi(int resId) {
        for (OnShowErrorToast listener : threadSafeList(this.mOnShowErrorToasts)) {
            listener.onShowErrorToast(resId);
        }
    }

    public void updateConversationUi() {
        for (OnConversationUpdate listener : threadSafeList(this.mOnConversationUpdates)) {
            listener.onConversationUpdate();
        }
    }

    public void notifyJingleRtpConnectionUpdate(
            final Account account,
            final Jid with,
            final String sessionId,
            final RtpEndUserState state) {
        for (OnJingleRtpConnectionUpdate listener :
                threadSafeList(this.onJingleRtpConnectionUpdate)) {
            listener.onJingleRtpConnectionUpdate(account, with, sessionId, state);
        }
    }

    public void notifyJingleRtpConnectionUpdate(
            CallIntegration.AudioDevice selectedAudioDevice,
            Set<CallIntegration.AudioDevice> availableAudioDevices) {
        for (OnJingleRtpConnectionUpdate listener :
                threadSafeList(this.onJingleRtpConnectionUpdate)) {
            listener.onAudioDeviceChanged(selectedAudioDevice, availableAudioDevices);
        }
    }

    public void updateAccountUi() {
        for (final OnAccountUpdate listener : threadSafeList(this.mOnAccountUpdates)) {
            listener.onAccountUpdate();
        }
    }

    public void updateRosterUi() {
        for (OnRosterUpdate listener : threadSafeList(this.mOnRosterUpdates)) {
            listener.onRosterUpdate();
        }
    }

    public boolean displayCaptchaRequest(
            final Account account,
            final im.conversations.android.xmpp.model.data.Data data,
            final Bitmap captcha) {
        if (mOnCaptchaRequested.isEmpty()) {
            return false;
        }
        final var metrics = getApplicationContext().getResources().getDisplayMetrics();
        Bitmap scaled =
                Bitmap.createScaledBitmap(
                        captcha,
                        (int) (captcha.getWidth() * metrics.scaledDensity),
                        (int) (captcha.getHeight() * metrics.scaledDensity),
                        false);
        for (final OnCaptchaRequested listener : threadSafeList(this.mOnCaptchaRequested)) {
            listener.onCaptchaRequested(account, data, scaled);
        }
        return true;
    }

    public void updateBlocklistUi(final OnUpdateBlocklist.Status status) {
        for (OnUpdateBlocklist listener : threadSafeList(this.mOnUpdateBlocklist)) {
            listener.OnUpdateBlocklist(status);
        }
    }

    public void updateMucRosterUi() {
        for (OnMucRosterUpdate listener : threadSafeList(this.mOnMucRosterUpdate)) {
            listener.onMucRosterUpdate();
        }
    }

    public void keyStatusUpdated(AxolotlService.FetchStatus report) {
        for (OnKeyStatusUpdated listener : threadSafeList(this.mOnKeyStatusUpdated)) {
            listener.onKeyStatusUpdated(report);
        }
    }

    public Account findAccountByJid(final Jid jid) {
        for (final Account account : this.accounts) {
            if (account.getJid().asBareJid().equals(jid.asBareJid())) {
                return account;
            }
        }
        return null;
    }

    public Account findAccountByUuid(final String uuid) {
        for (Account account : this.accounts) {
            if (account.getUuid().equals(uuid)) {
                return account;
            }
        }
        return null;
    }

    public Conversation findConversationByUuid(String uuid) {
        for (Conversation conversation : getConversations()) {
            if (conversation.getUuid().equals(uuid)) {
                return conversation;
            }
        }
        return null;
    }

    public Conversation findUniqueConversationByJid(XmppUri xmppUri) {
        List<Conversation> findings = new ArrayList<>();
        for (Conversation c : getConversations()) {
            if (c.getAccount().isEnabled()
                    && c.getAddress().asBareJid().equals(xmppUri.getJid())
                    && ((c.getMode() == Conversational.MODE_MULTI)
                            == xmppUri.isAction(XmppUri.ACTION_JOIN))) {
                findings.add(c);
            }
        }
        return findings.size() == 1 ? findings.get(0) : null;
    }

    public boolean markRead(final Conversation conversation, boolean dismiss) {
        return markRead(conversation, null, dismiss).size() > 0;
    }

    public void markRead(final Conversation conversation) {
        markRead(conversation, null, true);
    }

    public List<Message> markRead(
            final Conversation conversation, String upToUuid, boolean dismiss) {
        if (dismiss) {
            mNotificationService.clear(conversation);
        }
        final List<Message> readMessages = conversation.markRead(upToUuid);
        if (readMessages.size() > 0) {
            Runnable runnable =
                    () -> {
                        for (Message message : readMessages) {
                            databaseBackend.updateMessage(message, false);
                        }
                    };
            mDatabaseWriterExecutor.execute(runnable);
            updateConversationUi();
            updateUnreadCountBadge();
            return readMessages;
        } else {
            return readMessages;
        }
    }

    public synchronized void updateUnreadCountBadge() {
        int count = unreadCount();
        if (unreadCount != count) {
            Log.d(Config.LOGTAG, "update unread count to " + count);
            if (count > 0) {
                ShortcutBadger.applyCount(getApplicationContext(), count);
            } else {
                ShortcutBadger.removeCount(getApplicationContext());
            }
            unreadCount = count;
        }
    }

    public void sendReadMarker(final Conversation conversation, final String upToUuid) {
        final List<Message> readMessages = this.markRead(conversation, upToUuid, true);
        if (readMessages.isEmpty()) {
            return;
        }
        final var account = conversation.getAccount();
        final var connection = account.getXmppConnection();
        updateConversationUi();
        connection.getManager(DisplayedManager.class).displayed(readMessages);
    }

    public boolean sendReactions(final Message message, final Collection<String> reactions) {
        if (message.getConversation() instanceof Conversation conversation) {
            final var isPrivateMessage = message.isPrivateMessage();
            final Jid reactTo;
            final boolean typeGroupChat;
            final String reactToId;
            final Collection<Reaction> combinedReactions;
            if (conversation.getMode() == Conversational.MODE_MULTI && !isPrivateMessage) {
                final var mucOptions = conversation.getMucOptions();
                if (!mucOptions.participating()) {
                    Log.e(Config.LOGTAG, "not participating in MUC");
                    return false;
                }
                final var self = mucOptions.getSelf();
                final String occupantId = self.getOccupantId();
                if (Strings.isNullOrEmpty(occupantId)) {
                    Log.e(Config.LOGTAG, "occupant id not found for reaction in MUC");
                    return false;
                }
                final var existingRaw =
                        ImmutableSet.copyOf(
                                Collections2.transform(message.getReactions(), r -> r.reaction));
                final var reactionsAsExistingVariants =
                        ImmutableSet.copyOf(
                                Collections2.transform(
                                        reactions, r -> Emoticons.existingVariant(r, existingRaw)));
                if (!reactions.equals(reactionsAsExistingVariants)) {
                    Log.d(Config.LOGTAG, "modified reactions to existing variants");
                }
                reactToId = message.getServerMsgId();
                reactTo = conversation.getAddress().asBareJid();
                typeGroupChat = true;
                combinedReactions =
                        Reaction.withOccupantId(
                                message.getReactions(),
                                reactionsAsExistingVariants,
                                false,
                                self.getFullJid(),
                                conversation.getAccount().getJid(),
                                occupantId);
            } else {
                if (message.isCarbon() || message.getStatus() == Message.STATUS_RECEIVED) {
                    reactToId = message.getRemoteMsgId();
                } else {
                    reactToId = message.getUuid();
                }
                typeGroupChat = false;
                if (isPrivateMessage) {
                    reactTo = message.getCounterpart();
                } else {
                    reactTo = conversation.getAddress().asBareJid();
                }
                combinedReactions =
                        Reaction.withFrom(
                                message.getReactions(),
                                reactions,
                                false,
                                conversation.getAccount().getJid());
            }
            if (reactTo == null || Strings.isNullOrEmpty(reactToId)) {
                Log.e(Config.LOGTAG, "could not find id to react to");
                return false;
            }
            final var reactionMessage =
                    mMessageGenerator.reaction(reactTo, typeGroupChat, reactToId, reactions);
            sendMessagePacket(conversation.getAccount(), reactionMessage);
            message.setReactions(combinedReactions);
            updateMessage(message, false);
            return true;
        } else {
            return false;
        }
    }

    public MemorizingTrustManager getMemorizingTrustManager() {
        return this.mMemorizingTrustManager;
    }

    public void setMemorizingTrustManager(MemorizingTrustManager trustManager) {
        this.mMemorizingTrustManager = trustManager;
    }

    public void updateMemorizingTrustManager() {
        final MemorizingTrustManager trustManager;
        // Quicksy hides security / Server connection preference category
        if (QuickConversationsService.isQuicksy() || appSettings.isTrustSystemCAStore()) {
            trustManager = new MemorizingTrustManager(getApplicationContext());
        } else {
            trustManager = new MemorizingTrustManager(getApplicationContext(), null);
        }
        setMemorizingTrustManager(trustManager);
    }

    public LruCache<String, Bitmap> getBitmapCache() {
        return this.mBitmapCache;
    }

    public Collection<String> getKnownHosts() {
        final Set<String> hosts = new HashSet<>();
        for (final Account account : getAccounts()) {
            hosts.add(account.getServer());
            for (final Contact contact : account.getRoster().getContacts()) {
                if (contact.showInRoster()) {
                    final String server = contact.getServer();
                    if (server != null) {
                        hosts.add(server);
                    }
                }
            }
        }
        if (Config.QUICKSY_DOMAIN != null) {
            hosts.remove(
                    Config.QUICKSY_DOMAIN
                            .toString()); // we only want to show this when we type a e164
            // number
        }
        if (Config.MAGIC_CREATE_DOMAIN != null) {
            hosts.add(Config.MAGIC_CREATE_DOMAIN);
        }
        return hosts;
    }

    public Collection<String> getKnownConferenceHosts() {
        final var builder = new ImmutableSet.Builder<Jid>();
        for (final Account account : accounts) {
            final var connection = account.getXmppConnection();
            builder.addAll(connection.getManager(MultiUserChatManager.class).getServices());
            for (final var bookmark : connection.getManager(BookmarkManager.class).getBookmarks()) {
                final Jid jid = bookmark.getAddress();
                final Jid domain = jid == null ? null : jid.getDomain();
                if (domain == null) {
                    continue;
                }
                builder.add(domain);
            }
        }
        return Collections2.transform(builder.build(), Jid::toString);
    }

    public void sendMessagePacket(
            final Account account,
            final im.conversations.android.xmpp.model.stanza.Message packet) {
        final XmppConnection connection = account.getXmppConnection();
        if (connection != null) {
            connection.sendMessagePacket(packet);
        }
    }

    public void sendIqPacket(final Account account, final Iq packet, final Consumer<Iq> callback) {
        final XmppConnection connection = account.getXmppConnection();
        if (connection != null) {
            connection.sendIqPacket(packet, callback);
        } else if (callback != null) {
            callback.accept(Iq.TIMEOUT);
        }
    }

    private void deactivateGracePeriod() {
        for (final var account : getAccounts()) {
            account.getXmppConnection().getManager(ActivityManager.class).reset();
        }
    }

    public void refreshAllPresences() {
        final boolean includeIdleTimestamp =
                checkListeners() && appSettings.isBroadcastLastActivity();
        for (final var account : getAccounts()) {
            if (account.isConnectionEnabled()) {
                account.getXmppConnection()
                        .getManager(PresenceManager.class)
                        .available(includeIdleTimestamp);
            }
        }
    }

    private void refreshAllFcmTokens() {
        final var pushManagementService = new PushManagementService(this.getApplicationContext());
        for (final var account : getAccounts()) {
            if (account.isConnectionEnabled() && pushManagementService.available(account)) {
                pushManagementService.registerPushTokenOnServer(account);
            }
        }
    }

    public MessageGenerator getMessageGenerator() {
        return this.mMessageGenerator;
    }

    public IqGenerator getIqGenerator() {
        return this.mIqGenerator;
    }

    public JingleConnectionManager getJingleConnectionManager() {
        return this.mJingleConnectionManager;
    }

    public boolean hasJingleRtpConnection(final Account account) {
        return this.mJingleConnectionManager.hasJingleRtpConnection(account);
    }

    public QuickConversationsService getQuickConversationsService() {
        return this.mQuickConversationsService;
    }

    public List<Contact> findContacts(Jid jid, String accountJid) {
        ArrayList<Contact> contacts = new ArrayList<>();
        for (Account account : getAccounts()) {
            if ((account.isEnabled() || accountJid != null)
                    && (accountJid == null
                            || accountJid.equals(account.getJid().asBareJid().toString()))) {
                Contact contact = account.getRoster().getContactFromContactList(jid);
                if (contact != null) {
                    contacts.add(contact);
                }
            }
        }
        return contacts;
    }

    public Conversation findFirstMuc(Jid jid) {
        for (Conversation conversation : getConversations()) {
            if (conversation.getAccount().isEnabled()
                    && conversation.getAddress().asBareJid().equals(jid.asBareJid())
                    && conversation.getMode() == Conversation.MODE_MULTI) {
                return conversation;
            }
        }
        return null;
    }

    public NotificationService getNotificationService() {
        return this.mNotificationService;
    }

    public HttpConnectionManager getHttpConnectionManager() {
        return this.mHttpConnectionManager;
    }

    public void resendFailedMessages(final Message message, final boolean forceP2P) {
        message.setTime(System.currentTimeMillis());
        markMessage(message, Message.STATUS_WAITING);
        this.sendMessage(message, true, false, forceP2P);
        if (message.getConversation() instanceof Conversation c) {
            c.sort();
        }
        updateConversationUi();
    }

    public void clearConversationHistory(final Conversation conversation) {
        final long clearDate;
        final String reference;
        if (conversation.countMessages() > 0) {
            Message latestMessage = conversation.getLatestMessage();
            clearDate = latestMessage.getTimeSent() + 1000;
            reference = latestMessage.getServerMsgId();
        } else {
            clearDate = System.currentTimeMillis();
            reference = null;
        }
        conversation.clearMessages();
        conversation.setHasMessagesLeftOnServer(false); // avoid messages getting loaded through mam
        conversation.setLastClearHistory(clearDate, reference);
        Runnable runnable =
                () -> {
                    databaseBackend.deleteMessagesInConversation(conversation);
                    databaseBackend.updateConversation(conversation);
                };
        mDatabaseWriterExecutor.execute(runnable);
    }

    public boolean sendBlockRequest(
            final Blockable blockable, final boolean reportSpam, final String serverMsgId) {
        final var account = blockable.getAccount();
        final var connection = account.getXmppConnection();
        return connection
                .getManager(BlockingManager.class)
                .block(blockable, reportSpam, serverMsgId);
    }

    public boolean removeBlockedConversations(final Account account, final Jid blockedJid) {
        boolean removed = false;
        synchronized (this.conversations) {
            boolean domainJid = blockedJid.getLocal() == null;
            for (Conversation conversation : this.conversations) {
                boolean jidMatches =
                        (domainJid
                                        && blockedJid
                                                .getDomain()
                                                .equals(conversation.getAddress().getDomain()))
                                || blockedJid.equals(conversation.getAddress().asBareJid());
                if (conversation.getAccount() == account
                        && conversation.getMode() == Conversation.MODE_SINGLE
                        && jidMatches) {
                    this.conversations.remove(conversation);
                    markRead(conversation);
                    conversation.setStatus(Conversation.STATUS_ARCHIVED);
                    Log.d(
                            Config.LOGTAG,
                            account.getJid().asBareJid()
                                    + ": archiving conversation "
                                    + conversation.getAddress().asBareJid()
                                    + " because jid was blocked");
                    updateConversation(conversation);
                    removed = true;
                }
            }
        }
        return removed;
    }

    public void sendUnblockRequest(final Blockable blockable) {
        final var account = blockable.getAccount();
        final var connection = account.getXmppConnection();
        connection.getManager(BlockingManager.class).unblock(blockable);
    }

    public void publishDisplayName(final Account account) {
        final var connection = account.getXmppConnection();
        final String displayName = account.getDisplayName();
        mAvatarService.clear(account);
        final var future = connection.getManager(NickManager.class).publish(displayName);
        Futures.addCallback(
                future,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Void result) {
                        Log.d(
                                Config.LOGTAG,
                                account.getJid().asBareJid() + ": published User Nick");
                    }

                    @Override
                    public void onFailure(@NonNull Throwable t) {
                        Log.d(Config.LOGTAG, "could not publish User Nick", t);
                    }
                },
                MoreExecutors.directExecutor());
    }

    public void changeStatus(
            final Account account, final PresenceTemplate template, final String signature) {
        if (!template.getStatusMessage().isEmpty()) {
            databaseBackend.insertPresenceTemplate(template);
        }
        account.setPgpSignature(signature);
        account.setPresenceStatus(template.getStatus());
        account.setPresenceStatusMessage(template.getStatusMessage());
        databaseBackend.updateAccount(account);
        account.getXmppConnection().getManager(PresenceManager.class).available();
    }

    public List<PresenceTemplate> getPresenceTemplates(Account account) {
        List<PresenceTemplate> templates = databaseBackend.getPresenceTemplates();
        for (PresenceTemplate template :
                Presences.asTemplates(account.getSelfContact().getPresences())) {
            if (!templates.contains(template)) {
                templates.add(0, template);
            }
        }
        return templates;
    }

    public boolean verifyFingerprints(Contact contact, List<XmppUri.Fingerprint> fingerprints) {
        boolean performedVerification = false;
        final AxolotlService axolotlService = contact.getAccount().getAxolotlService();
        for (XmppUri.Fingerprint fp : fingerprints) {
            if (fp.type == XmppUri.FingerprintType.OMEMO) {
                String fingerprint = "05" + fp.fingerprint.replaceAll("\\s", "");
                FingerprintStatus fingerprintStatus =
                        axolotlService.getFingerprintTrust(fingerprint);
                if (fingerprintStatus != null) {
                    if (!fingerprintStatus.isVerified()) {
                        performedVerification = true;
                        axolotlService.setFingerprintTrust(
                                fingerprint, fingerprintStatus.toVerified());
                    }
                } else {
                    axolotlService.preVerifyFingerprint(contact, fingerprint);
                }
            }
        }
        return performedVerification;
    }

    public boolean verifyFingerprints(Account account, List<XmppUri.Fingerprint> fingerprints) {
        final AxolotlService axolotlService = account.getAxolotlService();
        boolean verifiedSomething = false;
        for (XmppUri.Fingerprint fp : fingerprints) {
            if (fp.type == XmppUri.FingerprintType.OMEMO) {
                String fingerprint = "05" + fp.fingerprint.replaceAll("\\s", "");
                Log.d(Config.LOGTAG, "trying to verify own fp=" + fingerprint);
                FingerprintStatus fingerprintStatus =
                        axolotlService.getFingerprintTrust(fingerprint);
                if (fingerprintStatus != null) {
                    if (!fingerprintStatus.isVerified()) {
                        axolotlService.setFingerprintTrust(
                                fingerprint, fingerprintStatus.toVerified());
                        verifiedSomething = true;
                    }
                } else {
                    axolotlService.preVerifyFingerprint(account, fingerprint);
                    verifiedSomething = true;
                }
            }
        }
        return verifiedSomething;
    }

    public ShortcutService getShortcutService() {
        return mShortcutService;
    }

    public void evictPreview(String uuid) {
        if (mBitmapCache.remove(uuid) != null) {
            Log.d(Config.LOGTAG, "deleted cached preview");
        }
    }

    public long getLastActivity() {
        return this.mLastActivity;
    }

    public interface OnAccountCreated {
        void onAccountCreated(Account account);

        void informUser(int r);
    }

    public interface OnMoreMessagesLoaded {
        void onMoreMessagesLoaded(int count, Conversation conversation);

        void informUser(int r);
    }

    public interface OnAffiliationChanged {
        void onAffiliationChangedSuccessful(Jid jid);

        void onAffiliationChangeFailed(Jid jid, int resId);
    }

    public interface OnConversationUpdate {
        void onConversationUpdate();
    }

    public interface OnJingleRtpConnectionUpdate {
        void onJingleRtpConnectionUpdate(
                final Account account,
                final Jid with,
                final String sessionId,
                final RtpEndUserState state);

        void onAudioDeviceChanged(
                CallIntegration.AudioDevice selectedAudioDevice,
                Set<CallIntegration.AudioDevice> availableAudioDevices);
    }

    public interface OnAccountUpdate {
        void onAccountUpdate();
    }

    public interface OnCaptchaRequested {
        void onCaptchaRequested(
                Account account,
                im.conversations.android.xmpp.model.data.Data data,
                Bitmap captcha);
    }

    public interface OnRosterUpdate {
        void onRosterUpdate();
    }

    public interface OnMucRosterUpdate {
        void onMucRosterUpdate();
    }

    public interface OnConfigurationPushed {
        void onPushSucceeded();

        void onPushFailed();
    }

    public interface OnShowErrorToast {
        void onShowErrorToast(int resId);
    }

    public class XmppConnectionBinder extends Binder {
        public XmppConnectionService getService() {
            return XmppConnectionService.this;
        }
    }

    private class InternalEventReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(final Context context, final Intent intent) {
            onStartCommand(intent, 0, 0);
        }
    }

    private class RestrictedEventReceiver extends BroadcastReceiver {

        private final Collection<String> allowedActions;

        private RestrictedEventReceiver(final Collection<String> allowedActions) {
            this.allowedActions = allowedActions;
        }

        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent == null ? null : intent.getAction();
            if (allowedActions.contains(action)) {
                onStartCommand(intent, 0, 0);
            } else {
                Log.e(Config.LOGTAG, "restricting broadcast of event " + action);
            }
        }
    }

    public static class OngoingCall {
        public final AbstractJingleConnection.Id id;
        public final Set<Media> media;
        public final boolean reconnecting;

        public OngoingCall(
                AbstractJingleConnection.Id id, Set<Media> media, final boolean reconnecting) {
            this.id = id;
            this.media = media;
            this.reconnecting = reconnecting;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            OngoingCall that = (OngoingCall) o;
            return reconnecting == that.reconnecting
                    && Objects.equal(id, that.id)
                    && Objects.equal(media, that.media);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(id, media, reconnecting);
        }
    }

    public static void toggleForegroundService(final XmppConnectionService service) {
        if (service == null) {
            return;
        }
        service.toggleForegroundService();
    }

    public static void toggleForegroundService(final XmppActivity activity) {
        if (activity == null) {
            return;
        }
        toggleForegroundService(activity.xmppConnectionService);
    }
}
