package eu.siacs.conversations.xml;

import com.google.common.collect.Iterables;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class LocalizedContent {

    public static final String STREAM_LANGUAGE = "en";

    public final String content;
    public final String language;
    public final int count;

    private LocalizedContent(final String content, final String language, final int count) {
        this.content = content;
        this.language = language;
        this.count = count;
    }

    public static LocalizedContent get(final Map<String, String> contents) {
        if (contents.isEmpty()) {
            return null;
        }
        final String userLanguage = Locale.getDefault().getLanguage();
        final String localized = contents.get(userLanguage);
        if (localized != null) {
            return new LocalizedContent(localized, userLanguage, contents.size());
        }
        final String streamLanguageContent = contents.get(STREAM_LANGUAGE);
        if (streamLanguageContent != null) {
            return new LocalizedContent(streamLanguageContent, STREAM_LANGUAGE, contents.size());
        }
        final Map.Entry<String, String> first = Iterables.get(contents.entrySet(), 0);
        return new LocalizedContent(first.getValue(), first.getKey(), contents.size());
    }
}
