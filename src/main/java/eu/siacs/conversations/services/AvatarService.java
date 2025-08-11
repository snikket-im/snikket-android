package eu.siacs.conversations.services;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.DisplayMetrics;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import com.google.common.base.Strings;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Conversational;
import eu.siacs.conversations.entities.ListItem;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.RawBlockable;
import eu.siacs.conversations.entities.Room;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.manager.MultiUserChatManager;
import im.conversations.android.model.Bookmark;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AvatarService {

    private static final int FG_COLOR = 0xFFFAFAFA;
    private static final int TRANSPARENT = 0x00000000;
    private static final int PLACEHOLDER_COLOR = 0xFF202020;
    private static final Character HORIZONTAL_ELLIPSIS = '…';
    private static final Character CHANNEL_SYMBOL = '#';

    private static final float FONT_SIZE_DEFAULT = 0.75f;
    private static final float FONT_SIZE_ADAPTIVE = 0.45f;

    private static final int AVATAR_SIZE_ADAPTIVE = 108;
    public static final int SYSTEM_UI_AVATAR_SIZE = 48;

    private static final String PREFIX_CONTACT = "contact";
    private static final String PREFIX_CONVERSATION = "conversation";
    private static final String PREFIX_ACCOUNT = "account";
    private static final String PREFIX_GENERIC = "generic";

    private final Set<Integer> sizes = new HashSet<>();
    // TODO refactor to multimap
    private final HashMap<String, Set<String>> conversationDependentKeys = new HashMap<>();

    protected XmppConnectionService mXmppConnectionService = null;

    AvatarService(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    public static int getSystemUiAvatarSize(final Context context) {
        return (int) (SYSTEM_UI_AVATAR_SIZE * context.getResources().getDisplayMetrics().density);
    }

    public Bitmap get(final Avatar avatar, final int size, final boolean cachedOnly) {
        if (avatar instanceof Account a) {
            return get(a, size, cachedOnly);
        } else if (avatar instanceof Conversation c) {
            return get(c, size, cachedOnly);
        } else if (avatar instanceof Message m) {
            return get(m, size, cachedOnly);
        } else if (avatar instanceof ListItem li) {
            return get(li, size, cachedOnly);
        } else if (avatar instanceof MucOptions.User u) {
            return get(u, size, cachedOnly);
        } else if (avatar instanceof Room r) {
            return get(r, size, cachedOnly);
        }
        throw new AssertionError(
                "AvatarService does not know how to generate avatar from "
                        + avatar.getClass().getName());
    }

    private Bitmap get(@NonNull final Room result, final int size, final boolean cacheOnly) {
        final Jid room = result.getRoom();
        Conversation conversation = room != null ? mXmppConnectionService.findFirstMuc(room) : null;
        if (conversation != null) {
            return get(conversation, size, cacheOnly);
        }
        return get(
                CHANNEL_SYMBOL,
                room == null
                        ? PLACEHOLDER_COLOR
                        : UIHelper.getColorForName(room.asBareJid().toString()),
                size,
                cacheOnly);
    }

    public Bitmap getAdaptive(final Contact contact) {
        final var metrics = this.mXmppConnectionService.getResources().getDisplayMetrics();
        int size = Math.round(metrics.density * AVATAR_SIZE_ADAPTIVE);
        return get(contact, size, FONT_SIZE_ADAPTIVE, false);
    }

    public Bitmap getAdaptive(final MucOptions mucOptions) {
        final var metrics = this.mXmppConnectionService.getResources().getDisplayMetrics();
        int size = Math.round(metrics.density * AVATAR_SIZE_ADAPTIVE);
        return get(mucOptions, size, FONT_SIZE_ADAPTIVE, false);
    }

    private Bitmap get(final Contact contact, final int size, boolean cachedOnly) {
        return get(contact, size, FONT_SIZE_DEFAULT, cachedOnly);
    }

    private Bitmap get(
            final Contact contact, final int size, final float fontSize, final boolean cachedOnly) {
        if (contact.isSelf()) {
            return get(contact.getAccount(), size, fontSize, cachedOnly);
        }
        final String KEY = key(contact, fontSize, size);
        final var cached = this.mXmppConnectionService.getBitmapCache().get(KEY);
        if (cached != null || cachedOnly) {
            return cached;
        }
        if (contact.getAvatar() != null && QuickConversationsService.isQuicksy()) {
            final var byHash =
                    mXmppConnectionService.getFileBackend().getAvatar(contact.getAvatar(), size);
            if (byHash != null) {
                this.mXmppConnectionService.getBitmapCache().put(KEY, byHash);
                return byHash;
            }
        }
        if (contact.getProfilePhoto() != null) {
            final var byPhoto =
                    mXmppConnectionService
                            .getFileBackend()
                            .cropCenterSquare(Uri.parse(contact.getProfilePhoto()), size);
            if (byPhoto != null) {
                this.mXmppConnectionService.getBitmapCache().put(KEY, byPhoto);
                return byPhoto;
            }
        }
        final var avatar = getByHashOrFallback(contact, contact.getAvatar(), fontSize, size);
        this.mXmppConnectionService.getBitmapCache().put(KEY, avatar);
        return avatar;
    }

    public Bitmap getRoundedShortcutWithIcon(final Contact contact) {
        DisplayMetrics metrics = mXmppConnectionService.getResources().getDisplayMetrics();
        int size = Math.round(metrics.density * 48);
        Bitmap bitmap = get(contact, size);
        Bitmap output =
                Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        final Paint paint = new Paint();

        drawAvatar(bitmap, canvas, paint);
        drawIcon(canvas, paint);
        return output;
    }

    private static void drawAvatar(final Bitmap bitmap, final Canvas canvas, final Paint paint) {
        final var rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        canvas.drawCircle(
                bitmap.getWidth() / 2.0f,
                bitmap.getHeight() / 2.0f,
                bitmap.getWidth() / 2.0f,
                paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);
    }

    private void drawIcon(final Canvas canvas, final Paint paint) {
        final Resources resources = mXmppConnectionService.getResources();
        final Bitmap icon = getRoundLauncherIcon(resources);
        if (icon == null) {
            return;
        }
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OVER));

        int iconSize = Math.round(canvas.getHeight() / 2.6f);

        int left = canvas.getWidth() - iconSize;
        int top = canvas.getHeight() - iconSize;
        final Rect rect = new Rect(left, top, left + iconSize, top + iconSize);
        canvas.drawBitmap(icon, null, rect, paint);
    }

    private static Bitmap getRoundLauncherIcon(Resources resources) {

        final Drawable drawable =
                ResourcesCompat.getDrawable(resources, R.mipmap.new_launcher_round, null);
        if (drawable == null) {
            return null;
        }

        if (drawable instanceof BitmapDrawable bitmapDrawable) {
            return bitmapDrawable.getBitmap();
        }

        Bitmap bitmap =
                Bitmap.createBitmap(
                        drawable.getIntrinsicWidth(),
                        drawable.getIntrinsicHeight(),
                        Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return bitmap;
    }

    private Bitmap get(final MucOptions.User user, final int size, boolean cachedOnly) {
        final String KEY = key(user, size);
        final var cached = this.mXmppConnectionService.getBitmapCache().get(KEY);
        if (cached != null || cachedOnly) {
            return cached;
        }
        final var avatar = getByHashOrFallback(user, user.getAvatar(), FONT_SIZE_DEFAULT, size);
        this.mXmppConnectionService.getBitmapCache().put(KEY, avatar);
        return avatar;
    }

    private Bitmap getByHashOrFallback(
            final Avatar avatar,
            @Nullable final String hash,
            final float fontSize,
            final int size) {
        if (hash != null) {
            final var byHash = mXmppConnectionService.getFileBackend().getAvatar(hash, size);
            if (byHash != null) {
                return byHash;
            }
        }
        return getImpl(getFirstLetter(avatar), avatar.getAvatarBackgroundColor(), fontSize, size);
    }

    public void clear(final Contact contact) {
        synchronized (this.sizes) {
            for (final Integer size : sizes) {
                this.mXmppConnectionService
                        .getBitmapCache()
                        .remove(key(contact, FONT_SIZE_DEFAULT, size));
                this.mXmppConnectionService
                        .getBitmapCache()
                        .remove(key(contact, FONT_SIZE_ADAPTIVE, size));
            }
        }
        final var connection = contact.getAccount().getXmppConnection();
        for (final var user : connection.getManager(MultiUserChatManager.class).getUsers(contact)) {
            final var mucOptions = user.getMucOptions();
            clear(user);
            if (Strings.isNullOrEmpty(mucOptions.getAvatar())
                    && mucOptions.isPrivateAndNonAnonymous()) {
                clear(mucOptions);
            }
        }
    }

    private String key(final Contact contact, final float fontSize, final int size) {
        synchronized (this.sizes) {
            this.sizes.add(size);
        }
        return PREFIX_CONTACT
                + '\0'
                + contact.getAccount().getJid().asBareJid()
                + '\0'
                + fontSize
                + '\0'
                + emptyOnNull(contact.getAddress())
                + '\0'
                + size;
    }

    private String key(MucOptions.User user, int size) {
        synchronized (this.sizes) {
            this.sizes.add(size);
        }
        return PREFIX_CONTACT
                + '\0'
                + user.getAccount().getJid().asBareJid()
                + '\0'
                + emptyOnNull(user.getFullJid())
                + '\0'
                + emptyOnNull(user.getRealJid())
                + '\0'
                + size;
    }

    public Bitmap get(ListItem item, int size) {
        return get(item, size, false);
    }

    public Bitmap get(final ListItem item, final int size, final boolean cachedOnly) {
        if (item instanceof RawBlockable) {
            return get(getFirstLetter(item), item.getAvatarBackgroundColor(), size, cachedOnly);
        } else if (item instanceof Contact contact) {
            return get(contact, size, cachedOnly);
        } else if (item instanceof Bookmark bookmark) {
            final MucOptions mucOptions =
                    bookmark.getAccount()
                            .getXmppConnection()
                            .getManager(MultiUserChatManager.class)
                            .getState(bookmark.getAddress().asBareJid());
            if (mucOptions != null) {
                return get(mucOptions, size, cachedOnly);
            } else {
                Jid jid = bookmark.getAddress();
                Account account = bookmark.getAccount();
                Contact contact = jid == null ? null : account.getRoster().getContact(jid);
                if (contact != null && contact.getAvatar() != null) {
                    return get(contact, size, cachedOnly);
                }
                return get(
                        getFirstLetter(bookmark),
                        bookmark.getAvatarBackgroundColor(),
                        size,
                        cachedOnly);
            }
        } else {
            return get(getFirstLetter(item), item.getAvatarBackgroundColor(), size, cachedOnly);
        }
    }

    public Bitmap get(Conversation conversation, int size) {
        return get(conversation, size, false);
    }

    public Bitmap get(final Conversation conversation, final int size, final boolean cachedOnly) {
        if (conversation.getMode() == Conversation.MODE_SINGLE) {
            return get(conversation.getContact(), size, cachedOnly);
        } else {
            return get(conversation.getMucOptions(), size, cachedOnly);
        }
    }

    private Bitmap get(final MucOptions mucOptions, final int size, final boolean cachedOnly) {
        return get(mucOptions, size, FONT_SIZE_DEFAULT, cachedOnly);
    }

    private Bitmap get(
            final MucOptions mucOptions,
            final int size,
            final float fontSize,
            final boolean cachedOnly) {
        final String KEY = key(mucOptions, size);
        final var cached = this.mXmppConnectionService.getBitmapCache().get(KEY);
        if (cached != null || cachedOnly) {
            return cached;
        }

        if (mucOptions.getAvatar() != null) {
            final var byHash =
                    mXmppConnectionService.getFileBackend().getAvatar(mucOptions.getAvatar(), size);
            if (byHash != null) {
                this.mXmppConnectionService.getBitmapCache().put(KEY, byHash);
                return byHash;
            }
        }

        final Bitmap bitmap;
        final Conversation c = mucOptions.getConversation();
        if (mucOptions.isPrivateAndNonAnonymous()) {
            final List<MucOptions.User> users = mucOptions.getUsersPreviewWithFallback();
            // for adaptive icons do not render the icons consisting of participants
            if (users.size() <= 1 || fontSize == FONT_SIZE_ADAPTIVE) {
                bitmap = getImpl(getFirstLetter(c), c.getAvatarBackgroundColor(), fontSize, size);
            } else {
                bitmap = getImpl(users, size);
            }
        } else {
            bitmap = getImpl(CHANNEL_SYMBOL, c.getAvatarBackgroundColor(), fontSize, size);
        }

        this.mXmppConnectionService.getBitmapCache().put(KEY, bitmap);

        return bitmap;
    }

    private Bitmap get(
            final List<MucOptions.User> users, final int size, final boolean cachedOnly) {
        final String KEY = key(users, size);
        final var cached = this.mXmppConnectionService.getBitmapCache().get(KEY);
        if (cached != null || cachedOnly) {
            return cached;
        }
        final var bitmap = getImpl(users, size);
        this.mXmppConnectionService.getBitmapCache().put(KEY, bitmap);
        return bitmap;
    }

    private Bitmap getImpl(final List<MucOptions.User> users, final int size) {
        final int count = users.size();
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        bitmap.eraseColor(TRANSPARENT);
        if (count <= 1) {
            throw new AssertionError("Unable to draw tiles for 0 or 1 users");
        } else if (count == 2) {
            drawTile(canvas, users.get(0), 0, 0, size / 2 - 1, size);
            drawTile(canvas, users.get(1), size / 2 + 1, 0, size, size);
        } else if (count == 3) {
            drawTile(canvas, users.get(0), 0, 0, size / 2 - 1, size);
            drawTile(canvas, users.get(1), size / 2 + 1, 0, size, size / 2 - 1);
            drawTile(canvas, users.get(2), size / 2 + 1, size / 2 + 1, size, size);
        } else if (count == 4) {
            drawTile(canvas, users.get(0), 0, 0, size / 2 - 1, size / 2 - 1);
            drawTile(canvas, users.get(1), 0, size / 2 + 1, size / 2 - 1, size);
            drawTile(canvas, users.get(2), size / 2 + 1, 0, size, size / 2 - 1);
            drawTile(canvas, users.get(3), size / 2 + 1, size / 2 + 1, size, size);
        } else {
            drawTile(canvas, users.get(0), 0, 0, size / 2 - 1, size / 2 - 1);
            drawTile(canvas, users.get(1), 0, size / 2 + 1, size / 2 - 1, size);
            drawTile(canvas, users.get(2), size / 2 + 1, 0, size, size / 2 - 1);
            drawTile(
                    canvas,
                    HORIZONTAL_ELLIPSIS,
                    PLACEHOLDER_COLOR,
                    size / 2 + 1,
                    size / 2 + 1,
                    size,
                    size);
        }
        return bitmap;
    }

    public void clear(final MucOptions options) {
        if (options == null) {
            return;
        }
        synchronized (this.sizes) {
            for (Integer size : sizes) {
                this.mXmppConnectionService.getBitmapCache().remove(key(options, size));
            }
        }
    }

    private String key(final MucOptions options, int size) {
        synchronized (this.sizes) {
            this.sizes.add(size);
        }
        return PREFIX_CONVERSATION + "_" + options.getConversation().getUuid() + "_" + size;
    }

    private String key(final List<MucOptions.User> users, final int size) {
        final Conversation conversation = users.get(0).getConversation();
        StringBuilder builder = new StringBuilder("TILE_");
        builder.append(conversation.getUuid());

        for (final MucOptions.User user : users) {
            builder.append("\0");
            builder.append(emptyOnNull(user.getRealJid()));
            builder.append("\0");
            builder.append(emptyOnNull(user.getFullJid()));
        }
        builder.append('\0');
        builder.append(size);
        final String key = builder.toString();
        synchronized (this.conversationDependentKeys) {
            Set<String> keys;
            if (this.conversationDependentKeys.containsKey(conversation.getUuid())) {
                keys = this.conversationDependentKeys.get(conversation.getUuid());
            } else {
                keys = new HashSet<>();
                this.conversationDependentKeys.put(conversation.getUuid(), keys);
            }
            keys.add(key);
        }
        return key;
    }

    public Bitmap get(final Account account, int size) {
        return get(account, size, false);
    }

    public Bitmap get(final Account account, int size, boolean cachedOnly) {
        return get(account, size, FONT_SIZE_DEFAULT, cachedOnly);
    }

    private Bitmap get(final Account account, int size, final float fontSize, boolean cachedOnly) {
        final String KEY = key(account, fontSize, size);
        final var cached = mXmppConnectionService.getBitmapCache().get(KEY);
        if (cached != null || cachedOnly) {
            return cached;
        }
        final var avatar = getByHashOrFallback(account, account.getAvatar(), fontSize, size);
        mXmppConnectionService.getBitmapCache().put(KEY, avatar);
        return avatar;
    }

    public Bitmap get(final Message message, final int size, final boolean cachedOnly) {
        final Conversational conversation = message.getConversation();
        if (message.getType() == Message.TYPE_STATUS
                && message.getCounterparts() != null
                && message.getCounterparts().size() > 1) {
            return get(message.getCounterparts(), size, cachedOnly);
        } else if (message.getStatus() == Message.STATUS_RECEIVED) {
            if (conversation.getMode() == Conversation.MODE_MULTI) {
                if (conversation instanceof Conversation c) {
                    return get(c.getMucOptions().getUserOrStub(message), size, cachedOnly);
                } else {
                    return get(
                            getFirstLetter(UIHelper.getMessageDisplayName(message)),
                            message.getAvatarBackgroundColor(),
                            size,
                            cachedOnly);
                }
            } else {
                return get(conversation.getContact(), size, cachedOnly);
            }
        } else {
            return get(conversation.getAccount(), size, cachedOnly);
        }
    }

    public void clear(final Account account) {
        synchronized (this.sizes) {
            for (Integer size : sizes) {
                this.mXmppConnectionService
                        .getBitmapCache()
                        .remove(key(account, FONT_SIZE_DEFAULT, size));
                this.mXmppConnectionService
                        .getBitmapCache()
                        .remove(key(account, FONT_SIZE_ADAPTIVE, size));
            }
        }
    }

    public void clear(final MucOptions.User user) {
        synchronized (this.sizes) {
            for (Integer size : sizes) {
                this.mXmppConnectionService.getBitmapCache().remove(key(user, size));
            }
        }
        synchronized (this.conversationDependentKeys) {
            final Set<String> keys =
                    this.conversationDependentKeys.get(user.getConversation().getUuid());
            if (keys == null) {
                return;
            }
            final var cache = this.mXmppConnectionService.getBitmapCache();
            for (final String key : keys) {
                cache.remove(key);
            }
            keys.clear();
        }
    }

    private String key(final Account account, final float fontSize, final int size) {
        synchronized (this.sizes) {
            this.sizes.add(size);
        }
        return PREFIX_ACCOUNT + '\0' + account.getUuid() + '\0' + fontSize + '\0' + size;
    }

    private Bitmap get(
            final Character character,
            final @ColorInt int background,
            final int size,
            final boolean cachedOnly) {
        return get(character, background, size, FONT_SIZE_DEFAULT, cachedOnly);
    }

    private Bitmap get(
            final Character character,
            final @ColorInt int background,
            final int size,
            final float fontSize,
            final boolean cachedOnly) {
        final String KEY = key(character.toString() + '\0' + background + '\0' + fontSize, size);
        final var cached = mXmppConnectionService.getBitmapCache().get(KEY);
        if (cached != null || cachedOnly) {
            return cached;
        }
        final var bitmap = getImpl(character, background, fontSize, size);
        mXmppConnectionService.getBitmapCache().put(KEY, bitmap);
        return bitmap;
    }

    public static Bitmap get(final Jid jid, final int size) {
        final var asString = jid.asBareJid().toString();
        return getImpl(
                getFirstLetter(asString),
                UIHelper.getColorForName(asString),
                FONT_SIZE_DEFAULT,
                size);
    }

    private static Bitmap getImpl(
            final Character character,
            @ColorInt final int background,
            final float fontSize,
            final int size) {
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawTile(canvas, character, background, 0, 0, size, size, fontSize);
        return bitmap;
    }

    private String key(String name, int size) {
        synchronized (this.sizes) {
            this.sizes.add(size);
        }
        return PREFIX_GENERIC + "_" + name + "_" + size;
    }

    private static void drawTile(
            final Canvas canvas,
            final Character character,
            final int tileColor,
            final int left,
            final int top,
            final int right,
            final int bottom) {
        drawTile(canvas, character, tileColor, left, top, right, bottom, FONT_SIZE_DEFAULT);
    }

    private static void drawTile(
            final Canvas canvas,
            final Character character,
            final int tileColor,
            final int left,
            final int top,
            final int right,
            final int bottom,
            final float fontSize) {
        Paint tilePaint = new Paint(), textPaint = new Paint();
        tilePaint.setColor(tileColor);
        textPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(FG_COLOR);
        textPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        textPaint.setTextSize((right - left) * fontSize);
        final Rect rect = new Rect();
        canvas.drawRect(new Rect(left, top, right, bottom), tilePaint);
        textPaint.getTextBounds(character.toString(), 0, 1, rect);
        float width = textPaint.measureText(character.toString());
        canvas.drawText(
                character.toString(),
                (right + left) / 2f - width / 2f,
                (top + bottom) / 2f + rect.height() / 2f,
                textPaint);
    }

    private void drawTile(
            final Canvas canvas,
            final MucOptions.User user,
            final int left,
            final int top,
            final int right,
            final int bottom) {

        if (user instanceof MucOptions.Self) {
            drawTile(canvas, user.getAccount(), left, top, right, bottom);
            return;
        }

        if (user.getAvatar() != null) {
            Uri uri = mXmppConnectionService.getFileBackend().getAvatarUri(user.getAvatar());
            if (drawTile(canvas, uri, left, top, right, bottom)) {
                return;
            }
        }
        drawTile(
                canvas,
                getFirstLetter(user),
                user.getAvatarBackgroundColor(),
                left,
                top,
                right,
                bottom);
    }

    private void drawTile(
            final Canvas canvas,
            final Account account,
            final int left,
            final int top,
            final int right,
            final int bottom) {
        String avatar = account.getAvatar();
        if (avatar != null) {
            Uri uri = mXmppConnectionService.getFileBackend().getAvatarUri(avatar);
            if (uri != null) {
                if (drawTile(canvas, uri, left, top, right, bottom)) {
                    return;
                }
            }
        }
        var character = getFirstLetter(account);
        drawTile(canvas, character, account.getAvatarBackgroundColor(), left, top, right, bottom);
    }

    private static Character getFirstLetter(final Avatar avatar) {
        if (avatar instanceof Account a) {
            final String displayName = a.getDisplayName();
            final String jid = a.getJid().asBareJid().toString();
            if (QuickConversationsService.isConversations() || Strings.isNullOrEmpty(displayName)) {
                return getFirstLetter(jid);
            } else {
                return getFirstLetter(displayName);
            }
        } else {
            return getFirstLetter(avatar.getDisplayName());
        }
    }

    private static Character getFirstLetter(final CharSequence name) {
        if (name.length() == 0) {
            return '␣';
        }
        for (int i = 0; i < name.length(); i++) {
            final char c = name.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                return Character.toUpperCase(c);
            }
        }
        return Character.toUpperCase(name.charAt(0));
    }

    private boolean drawTile(
            final Canvas canvas,
            final Uri uri,
            final int left,
            final int top,
            final int right,
            final int bottom) {
        if (uri != null) {
            Bitmap bitmap =
                    mXmppConnectionService
                            .getFileBackend()
                            .cropCenter(uri, bottom - top, right - left);
            if (bitmap != null) {
                drawTile(canvas, bitmap, left, top, right, bottom);
                return true;
            }
        }
        return false;
    }

    private void drawTile(
            Canvas canvas, Bitmap bm, int dstleft, int dsttop, int dstright, int dstbottom) {
        Rect dst = new Rect(dstleft, dsttop, dstright, dstbottom);
        canvas.drawBitmap(bm, null, dst, null);
    }

    private static String emptyOnNull(@Nullable Jid value) {
        return value == null ? "" : value.toString();
    }

    public interface Avatar {
        @ColorInt
        int getAvatarBackgroundColor();

        CharSequence getDisplayName();
    }
}
