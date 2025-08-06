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
import android.text.TextUtils;
import android.util.DisplayMetrics;
import androidx.annotation.ColorInt;
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
import java.util.Locale;
import java.util.Set;

public class AvatarService {

    private static final int FG_COLOR = 0xFFFAFAFA;
    private static final int TRANSPARENT = 0x00000000;
    private static final int PLACEHOLDER_COLOR = 0xFF202020;

    public static final int SYSTEM_UI_AVATAR_SIZE = 48;

    private static final String PREFIX_CONTACT = "contact";
    private static final String PREFIX_CONVERSATION = "conversation";
    private static final String PREFIX_ACCOUNT = "account";
    private static final String PREFIX_GENERIC = "generic";

    private static final String CHANNEL_SYMBOL = "#";

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
            return getImpl(u, size, cachedOnly);
        } else if (avatar instanceof Room r) {
            return get(r, size, cachedOnly);
        }
        throw new AssertionError(
                "AvatarService does not know how to generate avatar from "
                        + avatar.getClass().getName());
    }

    private Bitmap get(final Room result, final int size, final boolean cacheOnly) {
        final Jid room = result.getRoom();
        Conversation conversation = room != null ? mXmppConnectionService.findFirstMuc(room) : null;
        if (conversation != null) {
            return get(conversation, size, cacheOnly);
        }
        return get(
                CHANNEL_SYMBOL,
                room != null ? room.asBareJid().toString() : result.getName(),
                size,
                cacheOnly);
    }

    private Bitmap get(final Contact contact, final int size, boolean cachedOnly) {
        if (contact.isSelf()) {
            return get(contact.getAccount(), size, cachedOnly);
        }
        final String KEY = key(contact, size);
        Bitmap avatar = this.mXmppConnectionService.getBitmapCache().get(KEY);
        if (avatar != null || cachedOnly) {
            return avatar;
        }
        if (contact.getAvatar() != null && QuickConversationsService.isQuicksy()) {
            avatar = mXmppConnectionService.getFileBackend().getAvatar(contact.getAvatar(), size);
        }
        if (avatar == null && contact.getProfilePhoto() != null) {
            avatar =
                    mXmppConnectionService
                            .getFileBackend()
                            .cropCenterSquare(Uri.parse(contact.getProfilePhoto()), size);
        }
        if (avatar == null && contact.getAvatar() != null) {
            avatar = mXmppConnectionService.getFileBackend().getAvatar(contact.getAvatar(), size);
        }
        if (avatar == null) {
            avatar =
                    get(
                            contact.getDisplayName(),
                            contact.getAddress().asBareJid().toString(),
                            size,
                            false);
        }
        this.mXmppConnectionService.getBitmapCache().put(KEY, avatar);
        return avatar;
    }

    public Bitmap getRoundedShortcut(final MucOptions mucOptions) {
        final DisplayMetrics metrics = mXmppConnectionService.getResources().getDisplayMetrics();
        final int size = Math.round(metrics.density * 48);
        final Bitmap bitmap = get(mucOptions, size, false);
        final Bitmap output =
                Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(output);
        final Paint paint = new Paint();
        drawAvatar(bitmap, canvas, paint);
        return output;
    }

    public Bitmap getRoundedShortcut(final Contact contact) {
        return getRoundedShortcut(contact, false);
    }

    public Bitmap getRoundedShortcutWithIcon(final Contact contact) {
        return getRoundedShortcut(contact, true);
    }

    private Bitmap getRoundedShortcut(final Contact contact, boolean withIcon) {
        DisplayMetrics metrics = mXmppConnectionService.getResources().getDisplayMetrics();
        int size = Math.round(metrics.density * 48);
        Bitmap bitmap = get(contact, size);
        Bitmap output =
                Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        final Paint paint = new Paint();

        drawAvatar(bitmap, canvas, paint);
        if (withIcon) {
            drawIcon(canvas, paint);
        }
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

    private Bitmap getImpl(final MucOptions.User user, final int size, boolean cachedOnly) {
        final String KEY = key(user, size);
        final Bitmap cached = this.mXmppConnectionService.getBitmapCache().get(KEY);
        if (cached != null || cachedOnly) {
            return cached;
        }
        if (user.getAvatar() != null) {
            final Bitmap avatar =
                    mXmppConnectionService.getFileBackend().getAvatar(user.getAvatar(), size);
            if (avatar != null) {
                this.mXmppConnectionService.getBitmapCache().put(KEY, avatar);
                return avatar;
            }
        }
        final String seed =
                user.getRealJid() != null ? user.getRealJid().asBareJid().toString() : null;
        final var avatar = get(user.getDisplayName(), seed, size, false);
        this.mXmppConnectionService.getBitmapCache().put(KEY, avatar);
        return avatar;
    }

    public void clear(final Contact contact) {
        synchronized (this.sizes) {
            for (final Integer size : sizes) {
                this.mXmppConnectionService.getBitmapCache().remove(key(contact, size));
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

    private String key(final Contact contact, final int size) {
        synchronized (this.sizes) {
            this.sizes.add(size);
        }
        return PREFIX_CONTACT
                + '\0'
                + contact.getAccount().getJid().asBareJid()
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

    public Bitmap get(ListItem item, int size, boolean cachedOnly) {
        if (item instanceof RawBlockable) {
            return get(item.getDisplayName(), item.getAddress().toString(), size, cachedOnly);
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
                String seed = jid != null ? jid.asBareJid().toString() : null;
                return get(bookmark.getDisplayName(), seed, size, cachedOnly);
            }
        } else {
            String seed =
                    item.getAddress() != null ? item.getAddress().asBareJid().toString() : null;
            return get(item.getDisplayName(), seed, size, cachedOnly);
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
        final String KEY = key(mucOptions, size);
        Bitmap bitmap = this.mXmppConnectionService.getBitmapCache().get(KEY);
        if (bitmap != null || cachedOnly) {
            return bitmap;
        }

        bitmap = mXmppConnectionService.getFileBackend().getAvatar(mucOptions.getAvatar(), size);

        if (bitmap == null) {
            Conversation c = mucOptions.getConversation();
            if (mucOptions.isPrivateAndNonAnonymous()) {
                final List<MucOptions.User> users = mucOptions.getUsersPreviewWithFallback();
                if (users.isEmpty()) {
                    bitmap =
                            getImpl(
                                    c.getName().toString(),
                                    c.getAddress().asBareJid().toString(),
                                    size);
                } else {
                    bitmap = getImpl(users, size);
                }
            } else {
                bitmap = getImpl(CHANNEL_SYMBOL, c.getAddress().asBareJid().toString(), size);
            }
        }

        this.mXmppConnectionService.getBitmapCache().put(KEY, bitmap);

        return bitmap;
    }

    private Bitmap get(List<MucOptions.User> users, int size, boolean cachedOnly) {
        final String KEY = key(users, size);
        Bitmap bitmap = this.mXmppConnectionService.getBitmapCache().get(KEY);
        if (bitmap != null || cachedOnly) {
            return bitmap;
        }
        bitmap = getImpl(users, size);
        this.mXmppConnectionService.getBitmapCache().put(KEY, bitmap);
        return bitmap;
    }

    private Bitmap getImpl(List<MucOptions.User> users, int size) {
        int count = users.size();
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        bitmap.eraseColor(TRANSPARENT);
        if (count == 0) {
            throw new AssertionError("Unable to draw tiles for 0 users");
        } else if (count == 1) {
            drawTile(canvas, users.get(0), 0, 0, size / 2 - 1, size);
            drawTile(canvas, users.get(0).getAccount(), size / 2 + 1, 0, size, size);
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
            drawTile(canvas, "\u2026", PLACEHOLDER_COLOR, size / 2 + 1, size / 2 + 1, size, size);
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

    public Bitmap get(Account account, int size) {
        return get(account, size, false);
    }

    public Bitmap get(Account account, int size, boolean cachedOnly) {
        final String KEY = key(account, size);
        Bitmap avatar = mXmppConnectionService.getBitmapCache().get(KEY);
        if (avatar != null || cachedOnly) {
            return avatar;
        }
        avatar = mXmppConnectionService.getFileBackend().getAvatar(account.getAvatar(), size);
        if (avatar == null) {
            final String displayName = account.getDisplayName();
            final String jid = account.getJid().asBareJid().toString();
            if (QuickConversationsService.isQuicksy() && !TextUtils.isEmpty(displayName)) {
                avatar = get(displayName, jid, size, false);
            } else {
                avatar = get(jid, null, size, false);
            }
        }
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
                    Jid tcp = message.getTrueCounterpart();
                    String seed = tcp != null ? tcp.asBareJid().toString() : null;
                    return get(UIHelper.getMessageDisplayName(message), seed, size, cachedOnly);
                }
            } else {
                return get(conversation.getContact(), size, cachedOnly);
            }
        } else {
            return get(conversation.getAccount(), size, cachedOnly);
        }
    }

    public void clear(Account account) {
        synchronized (this.sizes) {
            for (Integer size : sizes) {
                this.mXmppConnectionService.getBitmapCache().remove(key(account, size));
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

    private String key(Account account, int size) {
        synchronized (this.sizes) {
            this.sizes.add(size);
        }
        return PREFIX_ACCOUNT + "_" + account.getUuid() + "_" + size;
    }

    public Bitmap get(final String name, String seed, final int size, boolean cachedOnly) {
        final String KEY = key(seed == null ? name : name + "\0" + seed, size);
        Bitmap bitmap = mXmppConnectionService.getBitmapCache().get(KEY);
        if (bitmap != null || cachedOnly) {
            return bitmap;
        }
        bitmap = getImpl(name, seed, size);
        mXmppConnectionService.getBitmapCache().put(KEY, bitmap);
        return bitmap;
    }

    public static Bitmap get(final Jid jid, final int size) {
        return getImpl(jid.asBareJid().toString(), null, size);
    }

    private static Bitmap getImpl(final String name, final String seed, final int size) {
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        final String trimmedName = name == null ? "" : name.trim();
        drawTile(canvas, trimmedName, seed, 0, 0, size, size);
        return bitmap;
    }

    private String key(String name, int size) {
        synchronized (this.sizes) {
            this.sizes.add(size);
        }
        return PREFIX_GENERIC + "_" + name + "_" + size;
    }

    private static void drawTile(
            Canvas canvas, String letter, int tileColor, int left, int top, int right, int bottom) {
        letter = letter.toUpperCase(Locale.getDefault());
        Paint tilePaint = new Paint(), textPaint = new Paint();
        tilePaint.setColor(tileColor);
        textPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(FG_COLOR);
        textPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        textPaint.setTextSize((float) ((right - left) * 0.8));
        Rect rect = new Rect();

        canvas.drawRect(new Rect(left, top, right, bottom), tilePaint);
        textPaint.getTextBounds(letter, 0, 1, rect);
        float width = textPaint.measureText(letter);
        canvas.drawText(
                letter,
                (right + left) / 2f - width / 2f,
                (top + bottom) / 2f + rect.height() / 2f,
                textPaint);
    }

    private void drawTile(
            Canvas canvas, MucOptions.User user, int left, int top, int right, int bottom) {
        if (user.getAvatar() != null) {
            Uri uri = mXmppConnectionService.getFileBackend().getAvatarUri(user.getAvatar());
            if (drawTile(canvas, uri, left, top, right, bottom)) {
                return;
            }
        }
        String seed = user.getRealJid() == null ? null : user.getRealJid().asBareJid().toString();
        drawTile(canvas, user.getDisplayName(), seed, left, top, right, bottom);
    }

    private void drawTile(
            Canvas canvas, Account account, int left, int top, int right, int bottom) {
        String avatar = account.getAvatar();
        if (avatar != null) {
            Uri uri = mXmppConnectionService.getFileBackend().getAvatarUri(avatar);
            if (uri != null) {
                if (drawTile(canvas, uri, left, top, right, bottom)) {
                    return;
                }
            }
        }
        String name = account.getJid().asBareJid().toString();
        drawTile(canvas, name, name, left, top, right, bottom);
    }

    private static void drawTile(
            Canvas canvas, String name, String seed, int left, int top, int right, int bottom) {
        if (name != null) {
            final String letter = name.equals(CHANNEL_SYMBOL) ? name : getFirstLetter(name);
            final int color = UIHelper.getColorForName(seed == null ? name : seed);
            drawTile(canvas, letter, color, left, top, right, bottom);
        }
    }

    private static String getFirstLetter(String name) {
        for (Character c : name.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                return c.toString();
            }
        }
        return "X";
    }

    private boolean drawTile(Canvas canvas, Uri uri, int left, int top, int right, int bottom) {
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

        String getAvatarName();
    }
}
