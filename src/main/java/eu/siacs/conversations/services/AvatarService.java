package eu.siacs.conversations.services;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.ListItem;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.utils.UIHelper;

public class AvatarService {

	private static final int FG_COLOR = 0xFFFAFAFA;
	private static final int TRANSPARENT = 0x00000000;
	private static final int PLACEHOLDER_COLOR = 0xFF202020;

	private static final String PREFIX_CONTACT = "contact";
	private static final String PREFIX_CONVERSATION = "conversation";
	private static final String PREFIX_ACCOUNT = "account";
	private static final String PREFIX_GENERIC = "generic";

	final private ArrayList<Integer> sizes = new ArrayList<>();

	protected XmppConnectionService mXmppConnectionService = null;

	public AvatarService(XmppConnectionService service) {
		this.mXmppConnectionService = service;
	}

	public Bitmap get(final Contact contact, final int size) {
		final String KEY = key(contact, size);
		Bitmap avatar = this.mXmppConnectionService.getBitmapCache().get(KEY);
		if (avatar != null) {
			return avatar;
		}
		if (contact.getProfilePhoto() != null) {
			avatar = mXmppConnectionService.getFileBackend().cropCenterSquare(Uri.parse(contact.getProfilePhoto()), size);
		}
		if (avatar == null && contact.getAvatar() != null) {
			avatar = mXmppConnectionService.getFileBackend().getAvatar(contact.getAvatar(), size);
		}
		if (avatar == null) {
            avatar = get(contact.getDisplayName(), size);
		}
		this.mXmppConnectionService.getBitmapCache().put(KEY, avatar);
		return avatar;
	}

	public void clear(Contact contact) {
		synchronized (this.sizes) {
			for (Integer size : sizes) {
				this.mXmppConnectionService.getBitmapCache().remove(
						key(contact, size));
			}
		}
	}

	private String key(Contact contact, int size) {
		synchronized (this.sizes) {
			if (!this.sizes.contains(size)) {
				this.sizes.add(size);
			}
		}
		return PREFIX_CONTACT + "_" + contact.getAccount().getJid().toBareJid() + "_"
				+ contact.getJid() + "_" + String.valueOf(size);
	}

	public Bitmap get(ListItem item, int size) {
		if (item instanceof Contact) {
			return get((Contact) item, size);
		} else if (item instanceof Bookmark) {
			Bookmark bookmark = (Bookmark) item;
			if (bookmark.getConversation() != null) {
				return get(bookmark.getConversation(), size);
			} else {
				return get(bookmark.getDisplayName(), size);
			}
		} else {
			return get(item.getDisplayName(), size);
		}
	}

	public Bitmap get(Conversation conversation, int size) {
		if (conversation.getMode() == Conversation.MODE_SINGLE) {
			return get(conversation.getContact(), size);
		} else {
			return get(conversation.getMucOptions(), size);
		}
	}

	public void clear(Conversation conversation) {
		if (conversation.getMode() == Conversation.MODE_SINGLE) {
			clear(conversation.getContact());
		} else {
			clear(conversation.getMucOptions());
		}
	}

	public Bitmap get(MucOptions mucOptions, int size) {
		final String KEY = key(mucOptions, size);
		Bitmap bitmap = this.mXmppConnectionService.getBitmapCache().get(KEY);
		if (bitmap != null) {
			return bitmap;
		}
		final List<MucOptions.User> users = new ArrayList<>(mucOptions.getUsers());
		int count = users.size();
		bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(bitmap);
		bitmap.eraseColor(TRANSPARENT);

		if (count == 0) {
			String name = mucOptions.getConversation().getName();
			final String letter = name.isEmpty() ? "X" : name.substring(0,1);
			final int color = UIHelper.getColorForName(name);
			drawTile(canvas, letter, color, 0, 0, size, size);
		} else if (count == 1) {
			drawTile(canvas, users.get(0), 0, 0, size, size);
		} else if (count == 2) {
			drawTile(canvas, users.get(0), 0, 0, size / 2 - 1, size);
			drawTile(canvas, users.get(1), size / 2 + 1, 0, size, size);
		} else if (count == 3) {
			drawTile(canvas, users.get(0), 0, 0, size / 2 - 1, size);
			drawTile(canvas, users.get(1), size / 2 + 1, 0, size, size / 2 - 1);
			drawTile(canvas, users.get(2), size / 2 + 1, size / 2 + 1, size,
					size);
		} else if (count == 4) {
			drawTile(canvas, users.get(0), 0, 0, size / 2 - 1, size / 2 - 1);
			drawTile(canvas, users.get(1), 0, size / 2 + 1, size / 2 - 1, size);
			drawTile(canvas, users.get(2), size / 2 + 1, 0, size, size / 2 - 1);
			drawTile(canvas, users.get(3), size / 2 + 1, size / 2 + 1, size,
					size);
		} else {
			drawTile(canvas, users.get(0), 0, 0, size / 2 - 1, size / 2 - 1);
			drawTile(canvas, users.get(1), 0, size / 2 + 1, size / 2 - 1, size);
			drawTile(canvas, users.get(2), size / 2 + 1, 0, size, size / 2 - 1);
			drawTile(canvas, "\u2026", PLACEHOLDER_COLOR, size / 2 + 1, size / 2 + 1,
					size, size);
		}
		this.mXmppConnectionService.getBitmapCache().put(KEY, bitmap);
		return bitmap;
	}

	public void clear(MucOptions options) {
		synchronized (this.sizes) {
			for (Integer size : sizes) {
				this.mXmppConnectionService.getBitmapCache().remove(
						key(options, size));
			}
		}
	}

	private String key(MucOptions options, int size) {
		synchronized (this.sizes) {
			if (!this.sizes.contains(size)) {
				this.sizes.add(size);
			}
		}
		return PREFIX_CONVERSATION + "_" + options.getConversation().getUuid()
				+ "_" + String.valueOf(size);
	}

	public Bitmap get(Account account, int size) {
		final String KEY = key(account, size);
		Bitmap avatar = mXmppConnectionService.getBitmapCache().get(KEY);
		if (avatar != null) {
			return avatar;
		}
		avatar = mXmppConnectionService.getFileBackend().getAvatar(
				account.getAvatar(), size);
		if (avatar == null) {
			avatar = get(account.getJid().toBareJid().toString(), size);
		}
		mXmppConnectionService.getBitmapCache().put(KEY, avatar);
		return avatar;
	}

	public void clear(Account account) {
		synchronized (this.sizes) {
			for (Integer size : sizes) {
				this.mXmppConnectionService.getBitmapCache().remove(
						key(account, size));
			}
		}
	}

	private String key(Account account, int size) {
		synchronized (this.sizes) {
			if (!this.sizes.contains(size)) {
				this.sizes.add(size);
			}
		}
		return PREFIX_ACCOUNT + "_" + account.getUuid() + "_"
				+ String.valueOf(size);
	}

	public Bitmap get(final String name, final int size) {
		final String KEY = key(name, size);
		Bitmap bitmap = mXmppConnectionService.getBitmapCache().get(KEY);
		if (bitmap != null) {
			return bitmap;
		}
		bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(bitmap);
		final String letter = name.isEmpty() ? "X" : name.substring(0,1);
		final int color = UIHelper.getColorForName(name);
		drawTile(canvas, letter, color, 0, 0, size, size);
		mXmppConnectionService.getBitmapCache().put(KEY, bitmap);
		return bitmap;
	}

	private String key(String name, int size) {
		synchronized (this.sizes) {
			if (!this.sizes.contains(size)) {
				this.sizes.add(size);
			}
		}
		return PREFIX_GENERIC + "_" + name + "_" + String.valueOf(size);
	}

	private void drawTile(Canvas canvas, String letter, int tileColor,
						  int left, int top, int right, int bottom) {
		letter = letter.toUpperCase(Locale.getDefault());
		Paint tilePaint = new Paint(), textPaint = new Paint();
		tilePaint.setColor(tileColor);
		textPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
		textPaint.setColor(FG_COLOR);
		textPaint.setTypeface(Typeface.create("sans-serif-light",
				Typeface.NORMAL));
		textPaint.setTextSize((float) ((right - left) * 0.8));
		Rect rect = new Rect();

		canvas.drawRect(new Rect(left, top, right, bottom), tilePaint);
		textPaint.getTextBounds(letter, 0, 1, rect);
		float width = textPaint.measureText(letter);
		canvas.drawText(letter, (right + left) / 2 - width / 2, (top + bottom)
				/ 2 + rect.height() / 2, textPaint);
	}

	private void drawTile(Canvas canvas, MucOptions.User user, int left,
						  int top, int right, int bottom) {
		Contact contact = user.getContact();
		if (contact != null) {
			Uri uri = null;
			if (contact.getProfilePhoto() != null) {
				uri = Uri.parse(contact.getProfilePhoto());
			} else if (contact.getAvatar() != null) {
				uri = mXmppConnectionService.getFileBackend().getAvatarUri(
						contact.getAvatar());
			}
			if (uri != null) {
				Bitmap bitmap = mXmppConnectionService.getFileBackend()
						.cropCenter(uri, bottom - top, right - left);
				if (bitmap != null) {
					drawTile(canvas, bitmap, left, top, right, bottom);
					return;
				}
			}
		}
		String name = contact != null ? contact.getDisplayName() : user.getName();
		final String letter = name.isEmpty() ? "X" : name.substring(0,1);
		final int color = UIHelper.getColorForName(name);
		drawTile(canvas, letter, color, left, top, right, bottom);
	}

	private void drawTile(Canvas canvas, Bitmap bm, int dstleft, int dsttop,
						  int dstright, int dstbottom) {
		Rect dst = new Rect(dstleft, dsttop, dstright, dstbottom);
		canvas.drawBitmap(bm, null, dst, null);
	}

}
