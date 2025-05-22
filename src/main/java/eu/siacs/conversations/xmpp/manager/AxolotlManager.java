package eu.siacs.conversations.xmpp.manager;

import android.content.Context;
import android.util.Log;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.axolotl.DeviceList;
import im.conversations.android.xmpp.model.pubsub.Items;
import java.util.HashSet;
import java.util.Set;

public class AxolotlManager extends AbstractManager {

    public AxolotlManager(final Context context, final XmppConnection connection) {
        super(context, connection);
    }

    public void handleItems(final Jid from, final Items items) {
        final var account = getAccount();
        final var deviceList = items.getFirstItem(DeviceList.class);
        if (deviceList == null) {
            return;
        }
        final Set<Integer> deviceIds = deviceList.getDeviceIds();
        Log.d(
                Config.LOGTAG,
                AxolotlService.getLogprefix(account)
                        + "Received PEP device list "
                        + deviceIds
                        + " update from "
                        + from
                        + ", processing... ");
        final AxolotlService axolotlService = account.getAxolotlService();
        axolotlService.registerDevices(from, new HashSet<>(deviceIds));
    }
}
