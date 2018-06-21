package eu.siacs.conversations.ui.service;

import android.content.Context;
import android.os.Build;
import android.support.text.emoji.EmojiCompat;
import android.support.text.emoji.FontRequestEmojiCompatConfig;
import android.support.text.emoji.bundled.BundledEmojiCompatConfig;
import android.support.v4.provider.FontRequest;
import android.util.Log;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;

public class EmojiService {

    private final Context context;

    public EmojiService(Context context) {
        this.context = context;
    }

    public void init() {
        BundledEmojiCompatConfig config = new BundledEmojiCompatConfig(context);
        config.setReplaceAll(true);
        EmojiCompat.init(config);
    }

}