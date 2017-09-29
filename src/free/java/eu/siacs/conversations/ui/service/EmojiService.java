package eu.siacs.conversations.ui.service;

import android.content.Context;
import android.support.text.emoji.EmojiCompat;
import android.support.text.emoji.bundled.BundledEmojiCompatConfig;

public class EmojiService extends AbstractEmojiService {

	public EmojiService(Context context) {
		super(context);
	}

	@Override
	protected EmojiCompat.Config buildConfig() {
		return new BundledEmojiCompatConfig(context);
	}
}