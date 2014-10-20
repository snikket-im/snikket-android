package eu.siacs.conversations.services;

import java.util.List;
import java.util.Locale;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.ListItem;
import eu.siacs.conversations.entities.MucOptions;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;

public class AvatarService {

	private static final int FG_COLOR = 0xFFFAFAFA;
	private static final int TRANSPARENT = 0x00000000;

	protected XmppConnectionService mXmppConnectionService = null;

	public AvatarService(XmppConnectionService service) {
		this.mXmppConnectionService = service;
	}

	public Bitmap getAvatar(Contact contact, int size) {
		Bitmap avatar = mXmppConnectionService.getFileBackend().getAvatar(
				contact.getAvatar(), size);
		if (avatar == null) {
			if (contact.getProfilePhoto() != null) {
				avatar = mXmppConnectionService.getFileBackend()
						.cropCenterSquare(Uri.parse(contact.getProfilePhoto()),
								size);
				if (avatar == null) {
					avatar = getAvatar(contact.getDisplayName(), size);
				}
			} else {
				avatar = getAvatar(contact.getDisplayName(), size);
			}
		}
		return avatar;
	}

	public Bitmap getAvatar(ListItem item, int size) {
		if (item instanceof Contact) {
			return getAvatar((Contact) item, size);
		} else if (item instanceof Bookmark) {
			Bookmark bookmark = (Bookmark) item;
			if (bookmark.getConversation() != null) {
				return getAvatar(bookmark.getConversation(), size);
			} else {
				return getAvatar(bookmark.getDisplayName(), size);
			}
		} else {
			return getAvatar(item.getDisplayName(), size);
		}
	}

	public Bitmap getAvatar(Conversation conversation, int size) {
		if (conversation.getMode() == Conversation.MODE_SINGLE) {
			return getAvatar(conversation.getContact(), size);
		} else {
			return getAvatar(conversation.getMucOptions(), size);
		}
	}

	public Bitmap getAvatar(MucOptions mucOptions, int size) {
		List<MucOptions.User> users = mucOptions.getUsers();
		int count = users.size();
		Bitmap bitmap = Bitmap
				.createBitmap(size, size, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(bitmap);
		bitmap.eraseColor(TRANSPARENT);

		if (count == 0) {
			String name = mucOptions.getConversation().getName();
			String letter = name.substring(0, 1);
			int color = this.getColorForName(name);
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
			drawTile(canvas, "\u2026", 0xFF202020, size / 2 + 1, size / 2 + 1,
					size, size);
		}
		return bitmap;
	}

	public Bitmap getAvatar(Account account, int size) {
		Bitmap avatar = mXmppConnectionService.getFileBackend().getAvatar(
				account.getAvatar(), size);
		if (avatar == null) {
			avatar = getAvatar(account.getJid(), size);
		}
		return avatar;
	}

	public Bitmap getAvatar(String name, int size) {
		Bitmap bitmap = Bitmap
				.createBitmap(size, size, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(bitmap);
		String letter = name.substring(0, 1);
		int color = this.getColorForName(name);
		drawTile(canvas, letter, color, 0, 0, size, size);
		return bitmap;
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
			if (contact.getAvatar() != null) {
				uri = mXmppConnectionService.getFileBackend().getAvatarUri(
						contact.getAvatar());
			} else if (contact.getProfilePhoto() != null) {
				uri = Uri.parse(contact.getProfilePhoto());
			}
			if (uri != null) {
				Bitmap bitmap = mXmppConnectionService.getFileBackend()
						.cropCenter(uri, bottom - top, right - left);
				if (bitmap != null) {
					drawTile(canvas, bitmap, left, top, right, bottom);
				} else {
					String letter = user.getName().substring(0, 1);
					int color = this.getColorForName(user.getName());
					drawTile(canvas, letter, color, left, top, right, bottom);
				}
			} else {
				String letter = user.getName().substring(0, 1);
				int color = this.getColorForName(user.getName());
				drawTile(canvas, letter, color, left, top, right, bottom);
			}
		} else {
			String letter = user.getName().substring(0, 1);
			int color = this.getColorForName(user.getName());
			drawTile(canvas, letter, color, left, top, right, bottom);
		}
	}

	private void drawTile(Canvas canvas, Bitmap bm, int dstleft, int dsttop,
			int dstright, int dstbottom) {
		Rect dst = new Rect(dstleft, dsttop, dstright, dstbottom);
		canvas.drawBitmap(bm, null, dst, null);
	}

	private int getColorForName(String name) {
		int holoColors[] = { 0xFFe91e63, 0xFF9c27b0, 0xFF673ab7, 0xFF3f51b5,
				0xFF5677fc, 0xFF03a9f4, 0xFF00bcd4, 0xFF009688, 0xFFff5722,
				0xFF795548, 0xFF607d8b };
		return holoColors[(int) ((name.hashCode() & 0xffffffffl) % holoColors.length)];
	}

}
