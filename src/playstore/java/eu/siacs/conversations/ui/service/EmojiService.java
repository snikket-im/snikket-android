package eu.siacs.conversations.ui.service;

import android.content.Context;
import android.support.text.emoji.EmojiCompat;
import android.support.text.emoji.FontRequestEmojiCompatConfig;
import android.support.v4.provider.FontRequest;
import android.util.Log;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;

public class EmojiService extends AbstractEmojiService {


	private final EmojiCompat.InitCallback initCallback = new EmojiCompat.InitCallback() {
		@Override
		public void onInitialized() {
			super.onInitialized();
			Log.d(Config.LOGTAG,"EmojiService succeeded in loading fonts");

		}

		@Override
		public void onFailed(Throwable throwable) {
			super.onFailed(throwable);
			Log.d(Config.LOGTAG,"EmojiService failed to load fonts",throwable);
		}
	};

	public EmojiService(Context context) {
		super(context);
	}

	@Override
	protected EmojiCompat.Config buildConfig() {
		final FontRequest fontRequest = new FontRequest(
				"com.google.android.gms.fonts",
				"com.google.android.gms",
				"Noto Color Emoji Compat",
				R.array.font_certs);
		FontRequestEmojiCompatConfig fontRequestEmojiCompatConfig = new FontRequestEmojiCompatConfig(context, fontRequest);
		fontRequestEmojiCompatConfig.registerInitCallback(initCallback);
		return fontRequestEmojiCompatConfig;
	}
}