package eu.siacs.conversations.xmpp.manager;

import android.content.Context;
import android.util.Log;
import eu.siacs.conversations.AppSettings;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import java.time.Duration;
import java.time.Instant;

public class ActivityManager extends AbstractManager {

    private final AppSettings appSettings;

    private Activity activity;

    public ActivityManager(final Context context, final XmppConnection connection) {
        super(context, connection);
        this.reset();
        this.appSettings = new AppSettings(context);
    }

    public void record(final Jid address, final ActivityType activityType) {
        final var activity = new Activity(address, Instant.now(), activityType);
        Log.d(Config.LOGTAG, "recording " + activity);
        this.activity = activity;
    }

    public void reset() {
        this.activity =
                new Activity(getAccount().getJid().asBareJid(), Instant.MIN, ActivityType.NONE);
    }

    public boolean isInGracePeriod() {
        final var activity = this.activity;
        if (activity == null) {
            return false;
        }
        final var gracePeriod = appSettings.getGracePeriodLength();
        if (gracePeriod.isZero()) {
            return false;
        }
        final var until = activity.instant.plus(gracePeriod);
        final var now = Instant.now();
        if (until.isBefore(now)) {
            return false;
        }
        final var account = getAccount();
        Log.d(
                Config.LOGTAG,
                account.getJid().asBareJid()
                        + ": in grace period for "
                        + Duration.between(now, until)
                        + " due to "
                        + activity);
        return true;
    }

    public record Activity(Jid address, Instant instant, ActivityType activityType) {}

    public enum ActivityType {
        DISPLAYED,
        CHAT_STATE,
        MESSAGE,
        NONE
    }
}
