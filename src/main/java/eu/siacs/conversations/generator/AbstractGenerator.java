package eu.siacs.conversations.generator;

import eu.siacs.conversations.BuildConfig;
import eu.siacs.conversations.services.XmppConnectionService;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

public abstract class AbstractGenerator {
    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);

    protected XmppConnectionService mXmppConnectionService;

    AbstractGenerator(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    public static String getTimestamp(long time) {
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
        return DATE_FORMAT.format(time);
    }

    String getIdentityVersion() {
        return BuildConfig.VERSION_NAME;
    }
}
