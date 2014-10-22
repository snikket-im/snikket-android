package eu.siacs.conversations;

import android.graphics.Bitmap;

public final class Config {

	public static final String LOGTAG = "conversations";

	public static final int PING_MAX_INTERVAL = 300;
	public static final int PING_MIN_INTERVAL = 30;
	public static final int PING_TIMEOUT = 10;
	public static final int CONNECT_TIMEOUT = 90;
	public static final int CARBON_GRACE_PERIOD = 60;

	public static final int AVATAR_SIZE = 192;
	public static final Bitmap.CompressFormat AVATAR_FORMAT = Bitmap.CompressFormat.WEBP;

	public static final int MESSAGE_MERGE_WINDOW = 20;

	public static final boolean PARSE_EMOTICONS = false;

	private Config() {

	}
}
