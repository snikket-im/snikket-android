package eu.siacs.conversations.xmpp.manager;

import android.content.Context;
import eu.siacs.conversations.AppSettings;
import eu.siacs.conversations.generator.AbstractGenerator;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.error.Condition;
import im.conversations.android.xmpp.model.error.Error;
import im.conversations.android.xmpp.model.stanza.Iq;
import im.conversations.android.xmpp.model.time.Time;
import java.util.Locale;
import java.util.TimeZone;

public class EntityTimeManager extends AbstractManager {

    public EntityTimeManager(Context context, XmppConnection connection) {
        super(context, connection);
    }

    public void request(final Iq request) {
        final var appSettings = new AppSettings(this.context);
        if (appSettings.isUseTor() || getAccount().isOnion()) {
            this.connection.sendErrorFor(request, Error.Type.AUTH, new Condition.Forbidden());
            return;
        }
        final var time = new Time();
        final long now = System.currentTimeMillis();
        time.setUniversalTime(AbstractGenerator.getTimestamp(now));
        final TimeZone ourTimezone = TimeZone.getDefault();
        final long offsetSeconds = ourTimezone.getOffset(now) / 1000;
        final long offsetMinutes = Math.abs((offsetSeconds % 3600) / 60);
        final long offsetHours = offsetSeconds / 3600;
        final String hours;
        if (offsetHours < 0) {
            hours = String.format(Locale.US, "%03d", offsetHours);
        } else {
            hours = String.format(Locale.US, "%02d", offsetHours);
        }
        String minutes = String.format(Locale.US, "%02d", offsetMinutes);
        time.setTimeZoneOffset(hours + ":" + minutes);
        this.connection.sendResultFor(request, time);
    }
}
