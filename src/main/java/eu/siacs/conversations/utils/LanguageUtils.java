package eu.siacs.conversations.utils;

import com.google.common.collect.ImmutableMap;

import java.util.Locale;
import java.util.Map;

public class LanguageUtils {

    private static final Map<String,String> LANGUAGE_MAP;

    static {
        ImmutableMap.Builder<String, String> builder = new ImmutableMap.Builder<>();
        builder.put("german","de");
        builder.put("deutsch","de");
        builder.put("english","en");
        builder.put("russian","ru");
        LANGUAGE_MAP = builder.build();
    }

    public static String convert(final String in) {
        if (in == null) {
            return null;
        }
        final String out = LANGUAGE_MAP.get(in.toLowerCase(Locale.US));
        return out == null ? in : out;
    }
}
