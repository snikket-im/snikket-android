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
        //On recent Androids we assume to have the latest emojis
        //there are some annoying bugs with emoji compat that make it a safer choice not to use it when possible
        // a) the text preview has annoying glitches when the cut of text contains emojis (the emoji will be half visible)
        // b) can trigger a hardware rendering bug https://issuetracker.google.com/issues/67102093
        config.setReplaceAll(Build.VERSION.SDK_INT < Build.VERSION_CODES.O);
        EmojiCompat.init(config);
    }

}