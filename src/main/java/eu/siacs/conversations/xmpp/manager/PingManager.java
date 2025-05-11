package eu.siacs.conversations.xmpp.manager;

import android.content.Context;
import androidx.annotation.NonNull;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.ping.Ping;
import im.conversations.android.xmpp.model.stanza.Iq;
import java.util.concurrent.TimeoutException;

public class PingManager extends AbstractManager {

    public PingManager(final Context context, final XmppConnection connection) {
        super(context, connection);
    }

    public void ping() {
        if (connection.getStreamFeatures().sm()) {
            this.connection.sendRequestStanza();
        } else {
            this.connection.sendIqPacket(new Iq(Iq.Type.GET, new Ping()));
        }
    }

    public void ping(final Runnable runnable) {
        final var pingFuture = this.connection.sendIqPacket(new Iq(Iq.Type.GET, new Ping()));
        Futures.addCallback(
                pingFuture,
                new FutureCallback<Iq>() {
                    @Override
                    public void onSuccess(Iq result) {
                        runnable.run();
                    }

                    @Override
                    public void onFailure(final @NonNull Throwable t) {
                        if (t instanceof TimeoutException) {
                            return;
                        }
                        runnable.run();
                    }
                },
                MoreExecutors.directExecutor());
    }
}
