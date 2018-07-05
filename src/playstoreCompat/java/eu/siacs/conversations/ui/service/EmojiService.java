package eu.siacs.conversations.ui.service;

import android.content.Context;
import android.os.Build;
import android.support.text.emoji.EmojiCompat;
import android.support.text.emoji.FontRequestEmojiCompatConfig;
import android.support.v4.provider.FontRequest;
import android.util.Log;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;

public class EmojiService {


    private final EmojiCompat.InitCallback initCallback = new EmojiCompat.InitCallback() {
        @Override
        public void onInitialized() {
            super.onInitialized();
            Log.d(Config.LOGTAG, "EmojiService succeeded in loading fonts");

        }

        @Override
        public void onFailed(Throwable throwable) {
            super.onFailed(throwable);
            Log.d(Config.LOGTAG, "EmojiService failed to load fonts", throwable);
        }
    };

    private final Context context;

    public EmojiService(Context context) {
        this.context = context;
    }

    public void init() {
        final FontRequest fontRequest = new FontRequest(
                "com.google.android.gms.fonts",
                "com.google.android.gms",
                "Noto Color Emoji Compat",
                R.array.font_certs);
        FontRequestEmojiCompatConfig fontRequestEmojiCompatConfig = new FontRequestEmojiCompatConfig(context, fontRequest);
        fontRequestEmojiCompatConfig.registerInitCallback(initCallback);
        //On recent Androids we assume to have the latest emojis
        //there are some annoying bugs with emoji compat that make it a safer choice not to use it when possible
        // a) when using the ondemand emoji font (play store) flags don’t work
        // b) the text preview has annoying glitches when the cut of text contains emojis (the emoji will be half visible)
        // c) can trigger a hardware rendering bug https://issuetracker.google.com/issues/67102093
        fontRequestEmojiCompatConfig.setReplaceAll(Build.VERSION.SDK_INT < Build.VERSION_CODES.O);
        EmojiCompat.init(fontRequestEmojiCompatConfig);
    }

}