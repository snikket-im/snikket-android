package eu.siacs.conversations.services;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.emoji2.bundled.BundledEmojiCompatConfig;
import androidx.emoji2.text.EmojiCompat;

import eu.siacs.conversations.Config;

public class EmojiInitializationService {

    public static void execute(final Context context) {
        EmojiCompat.init(new BundledEmojiCompatConfig(context).setReplaceAll(true))
                .registerInitCallback(
                        new EmojiCompat.InitCallback() {
                            @Override
                            public void onInitialized() {
                                Log.d(Config.LOGTAG, "initialized EmojiCompat");
                                super.onInitialized();
                            }

                            @Override
                            public void onFailed(@Nullable Throwable throwable) {
                                Log.e(Config.LOGTAG, "failed to initialize EmojiCompat", throwable);
                                super.onFailed(throwable);
                            }
                        });
    }
}
