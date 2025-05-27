package eu.siacs.conversations.xmpp.manager;

import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.heifwriter.AvifWriter;
import androidx.heifwriter.HeifWriter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.hash.Hashing;
import com.google.common.hash.HashingOutputStream;
import com.google.common.io.BaseEncoding;
import com.google.common.io.Files;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.Compatibility;
import eu.siacs.conversations.utils.PhoneHelper;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.pep.Avatar;
import im.conversations.android.xmpp.NodeConfiguration;
import im.conversations.android.xmpp.model.ByteContent;
import im.conversations.android.xmpp.model.avatar.Data;
import im.conversations.android.xmpp.model.avatar.Info;
import im.conversations.android.xmpp.model.avatar.Metadata;
import im.conversations.android.xmpp.model.pubsub.Items;
import im.conversations.android.xmpp.model.upload.purpose.Profile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class AvatarManager extends AbstractManager {

    private static final Executor AVATAR_COMPRESSION_EXECUTOR =
            MoreExecutors.newSequentialExecutor(Executors.newSingleThreadScheduledExecutor());

    private final XmppConnectionService service;

    public AvatarManager(final XmppConnectionService service, XmppConnection connection) {
        super(service.getApplicationContext(), connection);
        this.service = service;
    }

    public ListenableFuture<byte[]> fetch(final Jid address, final String itemId) {
        final var future = getManager(PubSubManager.class).fetchItem(address, itemId, Data.class);
        return Futures.transform(future, ByteContent::asBytes, MoreExecutors.directExecutor());
    }

    public ListenableFuture<Void> fetchAndStore(final Avatar avatar) {
        final var future = fetch(avatar.owner, avatar.sha1sum);
        return Futures.transform(
                future,
                data -> {
                    avatar.image = BaseEncoding.base64().encode(data);
                    if (service.getFileBackend().save(avatar)) {
                        setPepAvatar(avatar);
                        return null;
                    } else {
                        throw new IllegalStateException("Could not store avatar");
                    }
                },
                MoreExecutors.directExecutor());
    }

    private void setPepAvatar(final Avatar avatar) {
        final var account = getAccount();
        if (account.getJid().asBareJid().equals(avatar.owner)) {
            if (account.setAvatar(avatar.getFilename())) {
                getDatabase().updateAccount(account);
            }
            this.service.getAvatarService().clear(account);
            this.service.updateConversationUi();
            this.service.updateAccountUi();
        } else {
            final Contact contact = account.getRoster().getContact(avatar.owner);
            contact.setAvatar(avatar);
            account.getXmppConnection().getManager(RosterManager.class).writeToDatabaseAsync();
            this.service.getAvatarService().clear(contact);
            this.service.updateConversationUi();
            this.service.updateRosterUi();
        }
    }

    public void handleItems(final Jid from, final Items items) {
        final var account = getAccount();
        // TODO support retract
        final var entry = items.getFirstItemWithId(Metadata.class);
        final var avatar =
                entry == null ? null : Avatar.parseMetadata(entry.getKey(), entry.getValue());
        if (avatar == null) {
            Log.d(Config.LOGTAG, "could not parse avatar metadata from " + from);
            return;
        }
        avatar.owner = from.asBareJid();
        if (service.getFileBackend().isAvatarCached(avatar)) {
            if (account.getJid().asBareJid().equals(from)) {
                if (account.setAvatar(avatar.getFilename())) {
                    service.databaseBackend.updateAccount(account);
                    service.notifyAccountAvatarHasChanged(account);
                }
                service.getAvatarService().clear(account);
                service.updateConversationUi();
                service.updateAccountUi();
            } else {
                final Contact contact = account.getRoster().getContact(from);
                if (contact.setAvatar(avatar)) {
                    connection.getManager(RosterManager.class).writeToDatabaseAsync();
                    service.getAvatarService().clear(contact);
                    service.updateConversationUi();
                    service.updateRosterUi();
                }
            }
        } else if (service.isDataSaverDisabled()) {
            final var future = this.fetchAndStore(avatar);
            Futures.addCallback(
                    future,
                    new FutureCallback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            Log.d(
                                    Config.LOGTAG,
                                    account.getJid().asBareJid()
                                            + ": successfully fetched pep avatar for "
                                            + avatar.owner);
                        }

                        @Override
                        public void onFailure(@NonNull Throwable t) {
                            Log.d(Config.LOGTAG, "could not fetch avatar", t);
                        }
                    },
                    MoreExecutors.directExecutor());
        }
    }

    public void handleDelete(final Jid from) {
        final var account = getAccount();
        final boolean isAccount = account.getJid().asBareJid().equals(from);
        if (isAccount) {
            account.setAvatar(null);
            getDatabase().updateAccount(account);
            service.getAvatarService().clear(account);
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": deleted avatar metadata node");
        }
    }

    private Info resizeAndStoreAvatar(
            final Uri image, final int size, final ImageFormat format, final Integer charLimit)
            throws Exception {
        final var centerSquare = FileBackend.cropCenterSquare(context, image, size);
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
        if (randomFile.renameTo(avatarFile)) {
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
        return storeAsAvatar(randomFile, ImageFormat.AVIF, image.getHeight(), image.getWidth());
    }

    private Info storeAsAvatar(
            final File randomFile, final ImageFormat type, final int height, final int width)
            throws IOException {
        final var sha1 = Files.asByteSource(randomFile).hash(Hashing.sha1()).toString();
        final var avatarFile = FileBackend.getAvatarFile(context, sha1);
        if (randomFile.renameTo(avatarFile)) {
            return new Info(sha1, avatarFile.length(), type.toContentType(), height, width);
        }
        throw new IllegalStateException(
                String.format("Could not move file to %s", avatarFile.getAbsolutePath()));
    }

    public ListenableFuture<List<Info>> uploadAvatar(final Uri image, final int size) {
        final var avatarFutures = new ImmutableList.Builder<ListenableFuture<Info>>();
        final var avatarFuture = resizeAndStoreAvatarAsync(image, size, ImageFormat.JPEG);
        final var avatarWithUrlFuture =
                Futures.transformAsync(avatarFuture, this::upload, MoreExecutors.directExecutor());
        avatarFutures.add(avatarWithUrlFuture);

        if (Compatibility.twentyEight() && !PhoneHelper.isEmulator()) {
            final var avatarHeifFuture = resizeAndStoreAvatarAsync(image, size, ImageFormat.HEIF);
            final var avatarHeifWithUrlFuture =
                    Futures.transformAsync(
                            avatarHeifFuture, this::upload, MoreExecutors.directExecutor());
            avatarFutures.add(avatarHeifWithUrlFuture);
        }
        if (Compatibility.thirtyFour() && !PhoneHelper.isEmulator()) {
            final var avatarAvifFuture = resizeAndStoreAvatarAsync(image, size, ImageFormat.AVIF);
            final var avatarAvifWithUrlFuture =
                    Futures.transformAsync(
                            avatarAvifFuture, this::upload, MoreExecutors.directExecutor());
            avatarFutures.add(avatarAvifWithUrlFuture);
        }

        final var avatarThumbnailFuture =
                resizeAndStoreAvatarAsync(
                        image, Config.AVATAR_SIZE, ImageFormat.JPEG, Config.AVATAR_CHAR_LIMIT);
        avatarFutures.add(avatarThumbnailFuture);

        return Futures.allAsList(avatarFutures.build());
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

    public ListenableFuture<Void> publish(final Collection<Info> avatars, final boolean open) {
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

    public ListenableFuture<Void> uploadAndPublish(final Uri image, final boolean open) {
        final var infoFuture =
                connection
                        .getManager(AvatarManager.class)
                        .uploadAvatar(image, Config.AVATAR_FULL_SIZE);
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

    private String asContentType(final ImageFormat format) {
        return switch (format) {
            case WEBP -> "image/webp";
            case PNG -> "image/png";
            case JPEG -> "image/jpeg";
            case AVIF -> "image/avif";
            case HEIF -> "image/heif";
        };
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

        public static ImageFormat of(final Bitmap.CompressFormat compressFormat) {
            return switch (compressFormat) {
                case PNG -> PNG;
                case WEBP -> WEBP;
                case JPEG -> JPEG;
                default -> throw new AssertionError("Not implemented");
            };
        }
    }
}
