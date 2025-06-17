package eu.siacs.conversations.xmpp.manager;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.heifwriter.AvifWriter;
import androidx.heifwriter.HeifWriter;
import com.google.common.base.Preconditions;
import com.google.common.collect.Collections2;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import com.google.common.hash.Hashing;
import com.google.common.hash.HashingOutputStream;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import eu.siacs.conversations.AppSettings;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Conversational;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.Compatibility;
import eu.siacs.conversations.utils.PhoneHelper;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.xmpp.NodeConfiguration;
import im.conversations.android.xmpp.model.ByteContent;
import im.conversations.android.xmpp.model.avatar.Data;
import im.conversations.android.xmpp.model.avatar.Info;
import im.conversations.android.xmpp.model.avatar.Metadata;
import im.conversations.android.xmpp.model.pubsub.Items;
import im.conversations.android.xmpp.model.upload.purpose.Profile;
import im.conversations.android.xmpp.model.vcard.update.VCardUpdate;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class AvatarManager extends AbstractManager {

    private static final Object RENAME_LOCK = new Object();

    private static final List<String> SUPPORTED_CONTENT_TYPES;

    private static final Ordering<Info> AVATAR_ORDERING =
            new Ordering<>() {
                @Override
                public int compare(Info left, Info right) {
                    return ComparisonChain.start()
                            .compare(
                                    right.getWidth() * right.getHeight(),
                                    left.getWidth() * left.getHeight())
                            .compare(
                                    ImageFormat.formatPriority(right.getType()),
                                    ImageFormat.formatPriority(left.getType()))
                            .result();
                }
            };

    static {
        final ImmutableList.Builder<ImageFormat> builder = new ImmutableList.Builder<>();
        builder.add(ImageFormat.JPEG, ImageFormat.PNG, ImageFormat.WEBP);
        if (Compatibility.twentyEight()) {
            builder.add(ImageFormat.HEIF);
        }
        if (Compatibility.thirtyFour()) {
            builder.add(ImageFormat.AVIF);
        }
        final var supportedFormats = builder.build();
        SUPPORTED_CONTENT_TYPES =
                ImmutableList.copyOf(
                        Collections2.transform(supportedFormats, ImageFormat::toContentType));
    }

    private static final Executor AVATAR_COMPRESSION_EXECUTOR =
            MoreExecutors.newSequentialExecutor(Executors.newSingleThreadScheduledExecutor());

    private final XmppConnectionService service;

    public AvatarManager(final XmppConnectionService service, XmppConnection connection) {
        super(service.getApplicationContext(), connection);
        this.service = service;
    }

    private ListenableFuture<byte[]> fetch(final Jid address, final String itemId) {
        final var future = getManager(PubSubManager.class).fetchItem(address, itemId, Data.class);
        return Futures.transform(future, ByteContent::asBytes, MoreExecutors.directExecutor());
    }

    private ListenableFuture<Info> fetchAndStoreWithFallback(
            final Jid address, final Info picked, final Info fallback) {
        Preconditions.checkArgument(fallback.getUrl() == null, "fallback avatar must be in-band");
        final var url = picked.getUrl();
        if (url != null) {
            final var httpDownloadFuture = fetchAndStoreHttp(url, picked);
            return Futures.catchingAsync(
                    httpDownloadFuture,
                    Exception.class,
                    ex -> {
                        Log.d(
                                Config.LOGTAG,
                                getAccount().getJid().asBareJid()
                                        + ": could not download avatar for "
                                        + address
                                        + " from "
                                        + url,
                                ex);
                        return fetchAndStoreInBand(address, fallback);
                    },
                    MoreExecutors.directExecutor());
        } else {
            return fetchAndStoreInBand(address, picked);
        }
    }

    private ListenableFuture<Info> fetchAndStoreInBand(final Jid address, final Info avatar) {
        final var future = fetch(address, avatar.getId());
        return Futures.transformAsync(
                future,
                data -> {
                    final var actualHash = Hashing.sha1().hashBytes(data).toString();
                    if (!actualHash.equals(avatar.getId())) {
                        throw new IllegalStateException(
                                String.format("In-band avatar hash of %s did not match", address));
                    }

                    final var file = FileBackend.getAvatarFile(context, avatar.getId());
                    if (file.exists()) {
                        return Futures.immediateFuture(avatar);
                    }
                    return Futures.transform(
                            write(file, data), v -> avatar, MoreExecutors.directExecutor());
                },
                MoreExecutors.directExecutor());
    }

    private ListenableFuture<Void> write(final File destination, byte[] bytes) {
        return Futures.submit(
                () -> {
                    final var randomFile =
                            new File(context.getCacheDir(), UUID.randomUUID().toString());
                    Files.write(bytes, randomFile);
                    if (moveAvatarIntoCache(randomFile, destination)) {
                        return null;
                    }
                    throw new IllegalStateException(
                            String.format(
                                    "Could not move file to %s", destination.getAbsolutePath()));
                },
                AVATAR_COMPRESSION_EXECUTOR);
    }

    private ListenableFuture<Info> fetchAndStoreHttp(final HttpUrl url, final Info avatar) {
        final SettableFuture<Info> settableFuture = SettableFuture.create();
        final OkHttpClient client =
                service.getHttpConnectionManager().buildHttpClient(url, getAccount(), 30, false);
        final var request = new Request.Builder().url(url).get().build();
        client.newCall(request)
                .enqueue(
                        new Callback() {
                            @Override
                            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                                settableFuture.setException(e);
                            }

                            @Override
                            public void onResponse(@NonNull Call call, @NonNull Response response) {
                                if (response.isSuccessful()) {
                                    try {
                                        write(avatar, response);
                                    } catch (final Exception e) {
                                        settableFuture.setException(e);
                                        return;
                                    }
                                    settableFuture.set(avatar);
                                } else {
                                    settableFuture.setException(
                                            new IOException("HTTP call was not successful"));
                                }
                            }
                        });
        return settableFuture;
    }

    private void write(final Info avatar, Response response) throws IOException {
        final var body = response.body();
        if (body == null) {
            throw new IOException("Body was null");
        }
        final long bytes = avatar.getBytes();
        final long actualBytes;
        final var inputStream = ByteStreams.limit(body.byteStream(), avatar.getBytes());
        final var randomFile = new File(context.getCacheDir(), UUID.randomUUID().toString());
        final String actualHash;
        try (final var fileOutputStream = new FileOutputStream(randomFile);
                var hashingOutputStream =
                        new HashingOutputStream(Hashing.sha1(), fileOutputStream)) {
            actualBytes = ByteStreams.copy(inputStream, hashingOutputStream);
            actualHash = hashingOutputStream.hash().toString();
        }
        if (actualBytes != bytes) {
            throw new IllegalStateException("File size did not meet expected size");
        }
        if (!actualHash.equals(avatar.getId())) {
            throw new IllegalStateException("File hash did not match");
        }
        final var avatarFile = FileBackend.getAvatarFile(context, avatar.getId());
        if (moveAvatarIntoCache(randomFile, avatarFile)) {
            return;
        }
        throw new IOException("Could not move avatar to avatar location");
    }

    private void setAvatarInfo(final Jid address, @NonNull final Info info) {
        setAvatar(address, info.getId());
    }

    private void setAvatar(final Jid from, @Nullable final String id) {
        Log.d(Config.LOGTAG, "setting avatar for " + from + " to " + id);
        if (from.isBareJid()) {
            setAvatarContact(from, id);
        } else {
            setAvatarMucUser(from, id);
        }
    }

    private void setAvatarContact(final Jid from, @Nullable final String id) {
        final var account = getAccount();
        if (account.getJid().asBareJid().equals(from)) {
            if (account.setAvatar(id)) {
                getDatabase().updateAccount(account);
                service.notifyAccountAvatarHasChanged(account);
            }
            service.getAvatarService().clear(account);
            service.updateConversationUi();
            service.updateAccountUi();
        } else {
            final Contact contact = account.getRoster().getContact(from);
            if (contact.setAvatar(id)) {
                connection.getManager(RosterManager.class).writeToDatabaseAsync();
                service.getAvatarService().clear(contact);

                final var conversation = service.find(account, from);
                if (conversation != null && conversation.getMode() == Conversational.MODE_MULTI) {
                    service.getAvatarService().clear(conversation.getMucOptions());
                }

                service.updateConversationUi();
                service.updateRosterUi();
            }
        }
    }

    private void setAvatarMucUser(final Jid from, final String id) {
        final var account = getAccount();
        final Conversation conversation = service.find(account, from.asBareJid());
        if (conversation == null || conversation.getMode() != Conversation.MODE_MULTI) {
            return;
        }
        final var user = conversation.getMucOptions().findUserByFullJid(from);
        if (user == null) {
            return;
        }
        if (user.setAvatar(id)) {
            service.getAvatarService().clear(user);
            service.updateConversationUi();
            service.updateMucRosterUi();
        }
    }

    public void handleItems(final Jid from, final Items items) {
        final var account = getAccount();
        // TODO support retract
        final var entry = items.getFirstItemWithId(Metadata.class);
        if (entry == null) {
            return;
        }
        final var avatar = getPreferredFallback(entry);
        if (avatar == null) {
            return;
        }

        Log.d(Config.LOGTAG, "picked avatar from " + from + ": " + avatar.preferred);

        final var cache = FileBackend.getAvatarFile(context, avatar.preferred.getId());

        if (cache.exists()) {
            setAvatarInfo(from, avatar.preferred);
        } else if (service.isDataSaverDisabled()) {
            final var contact = getManager(RosterManager.class).getContactFromContactList(from);
            final ListenableFuture<Info> future;
            if (contact != null && contact.showInContactList()) {
                future = this.fetchAndStoreWithFallback(from, avatar.preferred, avatar.fallback);
            } else {
                future = fetchAndStoreInBand(from, avatar.fallback);
            }
            Futures.addCallback(
                    future,
                    new FutureCallback<>() {
                        @Override
                        public void onSuccess(Info result) {
                            setAvatarInfo(from, result);
                            Log.d(
                                    Config.LOGTAG,
                                    account.getJid().asBareJid()
                                            + ": successfully fetched pep avatar for "
                                            + from);
                        }

                        @Override
                        public void onFailure(@NonNull Throwable t) {
                            Log.d(Config.LOGTAG, "could not fetch avatar", t);
                        }
                    },
                    MoreExecutors.directExecutor());
        }
    }

    public void handleVCardUpdate(final Jid address, final VCardUpdate vCardUpdate) {
        final var hash = vCardUpdate.getHash();
        if (hash == null) {
            return;
        }
        handleVCardUpdate(address, hash);
    }

    public void handleVCardUpdate(final Jid address, final String hash) {
        Preconditions.checkArgument(VCardUpdate.isValidSHA1(hash));
        final var avatarFile = FileBackend.getAvatarFile(context, hash);
        if (avatarFile.exists()) {
            setAvatar(address, hash);
        } else if (service.isDataSaverDisabled()) {
            final var future = this.fetchAndStoreVCard(address, hash);
            Futures.addCallback(
                    future,
                    new FutureCallback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            Log.d(Config.LOGTAG, "successfully fetch vCard avatar for " + address);
                        }

                        @Override
                        public void onFailure(@NonNull Throwable t) {
                            Log.d(Config.LOGTAG, "could not fetch avatar for " + address, t);
                        }
                    },
                    MoreExecutors.directExecutor());
        }
    }

    private PreferredFallback getPreferredFallback(final Map.Entry<String, Metadata> entry) {
        final var mainItemId = entry.getKey();
        final var infos = entry.getValue().getInfos();

        final var inBandAvatar = Iterables.find(infos, i -> mainItemId.equals(i.getId()), null);

        if (inBandAvatar == null || inBandAvatar.getUrl() != null) {
            return null;
        }

        final var optionalAutoAcceptSize = new AppSettings(context).getAutoAcceptFileSize();
        if (optionalAutoAcceptSize.isEmpty()) {
            return new PreferredFallback(inBandAvatar);
        } else {

            final var supported =
                    Collections2.filter(
                            infos,
                            i ->
                                    Objects.nonNull(i.getId())
                                            && i.getBytes() > 0
                                            && i.getHeight() > 0
                                            && i.getWidth() > 0
                                            && SUPPORTED_CONTENT_TYPES.contains(i.getType()));

            final var autoAcceptSize = optionalAutoAcceptSize.get();

            final var supportedBelowLimit =
                    Collections2.filter(supported, i -> i.getBytes() <= autoAcceptSize);

            if (supportedBelowLimit.isEmpty()) {
                return new PreferredFallback(inBandAvatar);
            } else {
                final var preferred =
                        Iterables.getFirst(AVATAR_ORDERING.sortedCopy(supportedBelowLimit), null);
                return new PreferredFallback(preferred, inBandAvatar);
            }
        }
    }

    public void handleDelete(final Jid from) {
        Preconditions.checkArgument(
                from.isBareJid(), "node deletion can only be triggered from bare JIDs");
        setAvatar(from, null);
    }

    private Info resizeAndStoreAvatar(
            final Uri image, final int size, final ImageFormat format, final Integer charLimit)
            throws Exception {
        final var centerSquare = FileBackend.cropCenterSquare(context, image, size);
        final var info = resizeAndStoreAvatar(centerSquare, format, charLimit);
        centerSquare.recycle();
        return info;
    }

    private Info resizeAndStoreAvatar(
            final Bitmap centerSquare, final ImageFormat format, final Integer charLimit)
            throws Exception {
        if (charLimit == null || format == ImageFormat.PNG) {
            return resizeAndStoreAvatar(centerSquare, format, 90);
        } else {
            Info avatar = null;
            for (int quality = 90; quality >= 50; quality = quality - 2) {
                if (avatar != null) {
                    FileBackend.getAvatarFile(context, avatar.getId()).delete();
                }
                Log.d(Config.LOGTAG, "trying to save thumbnail with quality " + quality);
                avatar = resizeAndStoreAvatar(centerSquare, format, quality);
                if (avatar.getBytes() <= charLimit) {
                    return avatar;
                }
            }
            return avatar;
        }
    }

    private Info resizeAndStoreAvatar(final Bitmap image, ImageFormat format, final int quality)
            throws Exception {
        return switch (format) {
            case PNG -> resizeAndStoreAvatar(image, Bitmap.CompressFormat.PNG, quality);
            case JPEG -> resizeAndStoreAvatar(image, Bitmap.CompressFormat.JPEG, quality);
            case WEBP -> resizeAndStoreAvatar(image, Bitmap.CompressFormat.WEBP, quality);
            case HEIF -> resizeAndStoreAvatarAsHeif(image, quality);
            case AVIF -> resizeAndStoreAvatarAsAvif(image, quality);
        };
    }

    private Info resizeAndStoreAvatar(
            final Bitmap image, final Bitmap.CompressFormat format, final int quality)
            throws IOException {
        final var randomFile = new File(context.getCacheDir(), UUID.randomUUID().toString());
        final var fileOutputStream = new FileOutputStream(randomFile);
        final var hashingOutputStream = new HashingOutputStream(Hashing.sha1(), fileOutputStream);
        image.compress(format, quality, hashingOutputStream);
        hashingOutputStream.close();
        final var sha1 = hashingOutputStream.hash().toString();
        final var avatarFile = FileBackend.getAvatarFile(context, sha1);
        if (moveAvatarIntoCache(randomFile, avatarFile)) {
            return new Info(
                    sha1,
                    avatarFile.length(),
                    ImageFormat.of(format).toContentType(),
                    image.getHeight(),
                    image.getWidth());
        }
        throw new IllegalStateException(
                String.format("Could not move file to %s", avatarFile.getAbsolutePath()));
    }

    private Info resizeAndStoreAvatarAsHeif(final Bitmap image, final int quality)
            throws Exception {
        final var randomFile = new File(context.getCacheDir(), UUID.randomUUID().toString());
        try (final var fileOutputStream = new FileOutputStream(randomFile);
                final var heifWriter =
                        new HeifWriter.Builder(
                                        fileOutputStream.getFD(),
                                        image.getWidth(),
                                        image.getHeight(),
                                        HeifWriter.INPUT_MODE_BITMAP)
                                .setMaxImages(1)
                                .setQuality(quality)
                                .build()) {

            heifWriter.start();
            heifWriter.addBitmap(image);
            heifWriter.stop(3_000);
        }
        return storeAsAvatar(randomFile, ImageFormat.HEIF, image.getHeight(), image.getWidth());
    }

    private Info resizeAndStoreAvatarAsAvif(final Bitmap image, final int quality)
            throws Exception {
        final var randomFile = new File(context.getCacheDir(), UUID.randomUUID().toString());
        try (final var fileOutputStream = new FileOutputStream(randomFile);
                final var avifWriter =
                        new AvifWriter.Builder(
                                        fileOutputStream.getFD(),
                                        image.getWidth(),
                                        image.getHeight(),
                                        AvifWriter.INPUT_MODE_BITMAP)
                                .setMaxImages(1)
                                .setQuality(quality)
                                .build()) {
            avifWriter.start();
            avifWriter.addBitmap(image);
            avifWriter.stop(3_000);
        }
        var readCheck = BitmapFactory.decodeFile(randomFile.getAbsolutePath());
        if (readCheck == null) {
            throw new AvifCompressionException("AVIF image was null after trying to decode");
        }
        if (readCheck.getWidth() != image.getWidth()
                || readCheck.getHeight() != image.getHeight()) {
            readCheck.recycle();
            throw new AvifCompressionException("AVIF had wrong image bounds");
        }
        readCheck.recycle();
        return storeAsAvatar(randomFile, ImageFormat.AVIF, image.getHeight(), image.getWidth());
    }

    private Info storeAsAvatar(
            final File randomFile, final ImageFormat type, final int height, final int width)
            throws IOException {
        final var sha1 = Files.asByteSource(randomFile).hash(Hashing.sha1()).toString();
        final var avatarFile = FileBackend.getAvatarFile(context, sha1);
        if (moveAvatarIntoCache(randomFile, avatarFile)) {
            return new Info(sha1, avatarFile.length(), type.toContentType(), height, width);
        }
        throw new IllegalStateException(
                String.format("Could not move file to %s", avatarFile.getAbsolutePath()));
    }

    private ListenableFuture<Collection<Info>> uploadAvatar(final Uri image) {
        return Futures.transformAsync(
                hasAlphaChannel(image),
                hasAlphaChannel -> uploadAvatar(image, hasAlphaChannel),
                MoreExecutors.directExecutor());
    }

    private ListenableFuture<Collection<Info>> uploadAvatar(
            final Uri image, final boolean hasAlphaChannel) {
        final var avatarFutures = new ImmutableList.Builder<ListenableFuture<Info>>();

        final ListenableFuture<Info> avatarThumbnailFuture;
        if (hasAlphaChannel) {
            avatarThumbnailFuture =
                    resizeAndStoreAvatarAsync(
                            image, Config.AVATAR_THUMBNAIL_SIZE / 2, ImageFormat.PNG);
        } else {
            avatarThumbnailFuture =
                    resizeAndStoreAvatarAsync(
                            image,
                            Config.AVATAR_THUMBNAIL_SIZE,
                            ImageFormat.JPEG,
                            Config.AVATAR_THUMBNAIL_CHAR_LIMIT);
        }

        final var uploadManager = getManager(HttpUploadManager.class);

        final var uploadService = uploadManager.getService();
        if (uploadService == null || !uploadService.supportsPurpose(Profile.class)) {
            Log.d(
                    Config.LOGTAG,
                    getAccount().getJid() + ": 'profile' upload purpose not supported");
            return Futures.transform(
                    avatarThumbnailFuture, ImmutableList::of, MoreExecutors.directExecutor());
        }

        final ListenableFuture<Info> avatarFuture;
        if (hasAlphaChannel) {
            avatarFuture =
                    resizeAndStoreAvatarAsync(image, Config.AVATAR_FULL_SIZE / 2, ImageFormat.PNG);
        } else {
            final int autoAcceptFileSize =
                    context.getResources().getInteger(R.integer.auto_accept_filesize);
            avatarFuture =
                    resizeAndStoreAvatarAsync(
                            image, Config.AVATAR_FULL_SIZE, ImageFormat.JPEG, autoAcceptFileSize);

            if (Compatibility.twentyEight() && !PhoneHelper.isEmulator()) {
                final var avatarHeifFuture =
                        resizeAndStoreAvatarAsync(
                                image,
                                Config.AVATAR_FULL_SIZE,
                                ImageFormat.HEIF,
                                autoAcceptFileSize);
                final var avatarHeifWithUrlFuture =
                        Futures.transformAsync(
                                avatarHeifFuture, this::upload, MoreExecutors.directExecutor());
                avatarFutures.add(avatarHeifWithUrlFuture);
            }
            if (Compatibility.thirtyFour() && !PhoneHelper.isEmulator()) {
                final var avatarAvifFuture =
                        resizeAndStoreAvatarAsync(
                                image,
                                Config.AVATAR_FULL_SIZE,
                                ImageFormat.AVIF,
                                autoAcceptFileSize);
                final var avatarAvifWithUrlFuture =
                        Futures.transformAsync(
                                avatarAvifFuture, this::upload, MoreExecutors.directExecutor());
                final var caughtAvifWithUrlFuture =
                        Futures.catching(
                                avatarAvifWithUrlFuture,
                                Exception.class,
                                ex -> {
                                    Log.d(Config.LOGTAG, "ignoring AVIF compression failure", ex);
                                    return null;
                                },
                                MoreExecutors.directExecutor());
                avatarFutures.add(caughtAvifWithUrlFuture);
            }
        }
        avatarFutures.add(avatarThumbnailFuture);
        final var avatarWithUrlFuture =
                Futures.transformAsync(avatarFuture, this::upload, MoreExecutors.directExecutor());
        avatarFutures.add(avatarWithUrlFuture);

        final var all = Futures.allAsList(avatarFutures.build());
        return Futures.transform(
                all,
                input -> Collections2.filter(input, Objects::nonNull),
                MoreExecutors.directExecutor());
    }

    private ListenableFuture<Boolean> hasAlphaChannel(final Uri image) {
        return Futures.submit(
                () -> {
                    final var cropped =
                            FileBackend.cropCenterSquare(context, image, Config.AVATAR_FULL_SIZE);
                    final var hasAlphaChannel = FileBackend.hasAlpha(cropped);
                    cropped.recycle();
                    return hasAlphaChannel;
                },
                AVATAR_COMPRESSION_EXECUTOR);
    }

    private ListenableFuture<Info> upload(final Info avatar) {
        final var file = FileBackend.getAvatarFile(context, avatar.getId());
        final var urlFuture =
                getManager(HttpUploadManager.class).upload(file, avatar.getType(), new Profile());
        return Futures.transform(
                urlFuture,
                url -> {
                    avatar.setUrl(url);
                    return avatar;
                },
                MoreExecutors.directExecutor());
    }

    private ListenableFuture<Info> resizeAndStoreAvatarAsync(
            final Uri image, final int size, final ImageFormat format) {
        return resizeAndStoreAvatarAsync(image, size, format, null);
    }

    private ListenableFuture<Info> resizeAndStoreAvatarAsync(
            final Uri image, final int size, final ImageFormat format, final Integer charLimit) {
        return Futures.submit(
                () -> resizeAndStoreAvatar(image, size, format, charLimit),
                AVATAR_COMPRESSION_EXECUTOR);
    }

    private ListenableFuture<Void> publish(final Collection<Info> avatars, final boolean open) {
        final Info mainAvatarInfo;
        final byte[] mainAvatar;
        try {
            mainAvatarInfo = Iterables.find(avatars, a -> Objects.isNull(a.getUrl()));
            mainAvatar =
                    Files.asByteSource(FileBackend.getAvatarFile(context, mainAvatarInfo.getId()))
                            .read();
        } catch (final IOException | NoSuchElementException e) {
            return Futures.immediateFailedFuture(e);
        }
        final NodeConfiguration configuration =
                open ? NodeConfiguration.OPEN : NodeConfiguration.PRESENCE;
        final var avatarData = new Data();
        avatarData.setContent(mainAvatar);
        final var future =
                getManager(PepManager.class)
                        .publish(avatarData, mainAvatarInfo.getId(), configuration);
        return Futures.transformAsync(
                future,
                v -> {
                    final var id = mainAvatarInfo.getId();
                    final var metadata = new Metadata();
                    metadata.addExtensions(avatars);
                    return getManager(PepManager.class).publish(metadata, id, configuration);
                },
                MoreExecutors.directExecutor());
    }

    public ListenableFuture<Void> publishVCard(final Jid address, final Uri image) {

        ListenableFuture<Info> avatarThumbnailFuture =
                Futures.transformAsync(
                        hasAlphaChannel(image),
                        hasAlphaChannel -> {
                            if (hasAlphaChannel) {
                                return resizeAndStoreAvatarAsync(
                                        image, Config.AVATAR_THUMBNAIL_SIZE / 2, ImageFormat.PNG);
                            } else {
                                return resizeAndStoreAvatarAsync(
                                        image,
                                        Config.AVATAR_THUMBNAIL_SIZE,
                                        ImageFormat.JPEG,
                                        Config.AVATAR_THUMBNAIL_CHAR_LIMIT);
                            }
                        },
                        MoreExecutors.directExecutor());
        return Futures.transformAsync(
                avatarThumbnailFuture,
                info -> {
                    final var avatar =
                            Files.asByteSource(FileBackend.getAvatarFile(context, info.getId()))
                                    .read();
                    return getManager(VCardManager.class)
                            .publishPhoto(address, info.getType(), avatar);
                },
                AVATAR_COMPRESSION_EXECUTOR);
    }

    public ListenableFuture<Void> uploadAndPublish(final Uri image, final boolean open) {
        final var infoFuture = uploadAvatar(image);
        return Futures.transformAsync(
                infoFuture, avatars -> publish(avatars, open), MoreExecutors.directExecutor());
    }

    public boolean hasPepToVCardConversion() {
        return getManager(DiscoManager.class).hasAccountFeature(Namespace.AVATAR_CONVERSION);
    }

    public ListenableFuture<Void> delete() {
        final var pepManager = getManager(PepManager.class);
        final var deleteMetaDataFuture = pepManager.delete(Namespace.AVATAR_METADATA);
        final var deleteDataFuture = pepManager.delete(Namespace.AVATAR_DATA);
        return Futures.transform(
                Futures.allAsList(deleteDataFuture, deleteMetaDataFuture),
                vs -> null,
                MoreExecutors.directExecutor());
    }

    public ListenableFuture<Void> fetchAndStore(final Jid address) {
        final var metaDataFuture =
                getManager(PubSubManager.class).fetchItems(address, Metadata.class);
        return Futures.transformAsync(
                metaDataFuture,
                metaData -> {
                    final var entry = Iterables.getFirst(metaData.entrySet(), null);
                    if (entry == null) {
                        throw new IllegalStateException("Metadata item not found");
                    }
                    final var avatar = getPreferredFallback(entry);

                    if (avatar == null) {
                        throw new IllegalStateException("No avatar found");
                    }

                    final var cache = FileBackend.getAvatarFile(context, avatar.preferred.getId());

                    if (cache.exists()) {
                        Log.d(
                                Config.LOGTAG,
                                "fetchAndStore. file existed " + cache.getAbsolutePath());
                        setAvatarInfo(address, avatar.preferred);
                        return Futures.immediateVoidFuture();
                    } else {
                        final var future =
                                this.fetchAndStoreWithFallback(
                                        address, avatar.preferred, avatar.fallback);
                        return Futures.transform(
                                future,
                                info -> {
                                    setAvatarInfo(address, info);
                                    return null;
                                },
                                MoreExecutors.directExecutor());
                    }
                },
                MoreExecutors.directExecutor());
    }

    private static boolean moveAvatarIntoCache(final File randomFile, final File destination) {
        synchronized (RENAME_LOCK) {
            if (destination.exists()) {
                return true;
            }
            final var directory = destination.getParentFile();
            if (directory != null && directory.mkdirs()) {
                Log.d(
                        Config.LOGTAG,
                        "create avatar cache directory: " + directory.getAbsolutePath());
            }
            return randomFile.renameTo(destination);
        }
    }

    public ListenableFuture<Void> fetchAndStoreVCard(final Jid address, final String expectedHash) {
        final var future = connection.getManager(VCardManager.class).retrievePhoto(address);
        return Futures.transformAsync(
                future,
                photo -> {
                    final var actualHash = Hashing.sha1().hashBytes(photo).toString();
                    if (!actualHash.equals(expectedHash)) {
                        return Futures.immediateFailedFuture(
                                new IllegalStateException(
                                        String.format(
                                                "Hash in vCard update for %s did not match",
                                                address)));
                    }
                    final var avatarFile = FileBackend.getAvatarFile(context, actualHash);
                    if (avatarFile.exists()) {
                        setAvatar(address, actualHash);
                        return Futures.immediateVoidFuture();
                    }
                    final var writeFuture = write(avatarFile, photo);
                    return Futures.transform(
                            writeFuture,
                            v -> {
                                setAvatar(address, actualHash);
                                return null;
                            },
                            MoreExecutors.directExecutor());
                },
                AVATAR_COMPRESSION_EXECUTOR);
    }

    private static final class AvifCompressionException extends IllegalStateException {
        AvifCompressionException(final String message) {
            super(message);
        }
    }

    public enum ImageFormat {
        PNG,
        JPEG,
        WEBP,
        HEIF,
        AVIF;

        public String toContentType() {
            return switch (this) {
                case WEBP -> "image/webp";
                case PNG -> "image/png";
                case JPEG -> "image/jpeg";
                case AVIF -> "image/avif";
                case HEIF -> "image/heif";
            };
        }

        public static int formatPriority(final String type) {
            final var format = ofContentType(type);
            return format == null ? Integer.MIN_VALUE : format.ordinal();
        }

        private static ImageFormat ofContentType(final String type) {
            return switch (type) {
                case "image/png" -> PNG;
                case "image/jpeg" -> JPEG;
                case "image/webp" -> WEBP;
                case "image/heif" -> HEIF;
                case "image/avif" -> AVIF;
                default -> null;
            };
        }

        public static ImageFormat of(final Bitmap.CompressFormat compressFormat) {
            return switch (compressFormat) {
                case PNG -> PNG;
                case WEBP -> WEBP;
                case JPEG -> JPEG;
                default -> throw new AssertionError("Not implemented");
            };
        }
    }

    private static final class PreferredFallback {
        private final Info preferred;
        private final Info fallback;

        private PreferredFallback(final Info fallback) {
            this(fallback, fallback);
        }

        private PreferredFallback(Info preferred, Info fallback) {
            this.preferred = preferred;
            this.fallback = fallback;
        }
    }
}
