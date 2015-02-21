package eu.siacs.conversations;

import android.graphics.Bitmap;

import eu.siacs.conversations.xmpp.chatstate.ChatState;

public final class Config {

	public static final String LOGTAG = "conversations";

	public static final int PING_MAX_INTERVAL = 300;
	public static final int PING_MIN_INTERVAL = 30;
	public static final int PING_TIMEOUT = 10;
	public static final int CONNECT_TIMEOUT = 90;
	public static final int CARBON_GRACE_PERIOD = 60;
	public static final int MINI_GRACE_PERIOD = 750;

	public static final int AVATAR_SIZE = 192;
	public static final Bitmap.CompressFormat AVATAR_FORMAT = Bitmap.CompressFormat.WEBP;

	public static final int MESSAGE_MERGE_WINDOW = 20;

	public static final int PAGE_SIZE = 50;
	public static final int MAX_NUM_PAGES = 3;

	public static final int PROGRESS_UI_UPDATE_INTERVAL = 750;
	public static final int REFRESH_UI_INTERVAL = 500;

	public static final boolean NO_PROXY_LOOKUP = false; //useful to debug ibb

	public static final long MILLISECONDS_IN_DAY = 24 * 60 * 60 * 1000;
	public static final long MAM_MAX_CATCHUP =  MILLISECONDS_IN_DAY / 2;
	public static final int MAM_MAX_MESSAGES = 500;

	public static final ChatState DEFAULT_CHATSTATE = ChatState.ACTIVE;
	public static final int TYPING_TIMEOUT = 8;

	public static final String ENABLED_CIPHERS[] = {
		"TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
		"TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA384",
		"TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA256",
		"TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
		"TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
		"TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",

		"TLS_DHE_RSA_WITH_AES_128_GCM_SHA256",
		"TLS_DHE_RSA_WITH_AES_128_GCM_SHA384",
		"TLS_DHE_RSA_WITH_AES_256_GCM_SHA256",
		"TLS_DHE_RSA_WITH_AES_256_GCM_SHA384",

		"TLS_DHE_RSA_WITH_CAMELLIA_256_SHA",

		// Fallback.
		"TLS_RSA_WITH_AES_128_GCM_SHA256",
		"TLS_RSA_WITH_AES_128_GCM_SHA384",
		"TLS_RSA_WITH_AES_256_GCM_SHA256",
		"TLS_RSA_WITH_AES_256_GCM_SHA384",
		"TLS_RSA_WITH_AES_128_CBC_SHA256",
		"TLS_RSA_WITH_AES_128_CBC_SHA384",
		"TLS_RSA_WITH_AES_256_CBC_SHA256",
		"TLS_RSA_WITH_AES_256_CBC_SHA384",
		"TLS_RSA_WITH_AES_128_CBC_SHA",
		"TLS_RSA_WITH_AES_256_CBC_SHA",
	};

	private Config() {

	}
}
