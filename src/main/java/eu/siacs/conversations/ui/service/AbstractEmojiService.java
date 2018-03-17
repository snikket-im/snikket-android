package eu.siacs.conversations.ui.service;

import android.content.Context;
import android.os.Build;
import android.support.text.emoji.EmojiCompat;

public abstract class AbstractEmojiService {

	protected final Context context;

	public AbstractEmojiService(Context context) {
		this.context = context;
	}

	protected abstract EmojiCompat.Config buildConfig();

	public void init() {
		final EmojiCompat.Config config = buildConfig();
		//On recent Androids we assume to have the latest emojis
		//there are some annoying bugs with emoji compat that make it a safer choice not to use it when possible
		// a) when using the ondemand emoji font (play store) flags don’t work
		// b) the text preview has annoying glitches when the cut of text contains emojis (the emoji will be half visible)
		config.setReplaceAll(Build.VERSION.SDK_INT < Build.VERSION_CODES.O);
		EmojiCompat.init(config);
	}
}
