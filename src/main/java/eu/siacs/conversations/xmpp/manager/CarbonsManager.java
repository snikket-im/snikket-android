package eu.siacs.conversations.xmpp.manager;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.carbons.Enable;
import im.conversations.android.xmpp.model.stanza.Iq;

public class CarbonsManager extends AbstractManager {

    private boolean enabled = false;

    public CarbonsManager(final Context context, final XmppConnection connection) {
        super(context, connection);
    }

    public void setEnabledOnBind(final boolean enabledOnBind) {
        this.enabled = enabledOnBind;
    }

    public void enable() {
        final var request = new Iq(Iq.Type.SET);
        request.addExtension(new Enable());
        final var future = this.connection.sendIqPacket(request);
        Futures.addCallback(
                future,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(final Iq result) {
                        CarbonsManager.this.enabled = true;
                        Log.d(
                                Config.LOGTAG,
                                getAccount().getJid().asBareJid()
                                        + ": successfully enabled carbons");
                    }

                    @Override
                    public void onFailure(@NonNull final Throwable throwable) {
                        Log.d(
                                Config.LOGTAG,
                                getAccount().getJid().asBareJid() + ": could not enable carbons",
                                throwable);
                    }
                },
                MoreExecutors.directExecutor());
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public boolean hasFeature() {
        return getManager(DiscoManager.class).hasServerFeature(Namespace.CARBONS);
    }
}
