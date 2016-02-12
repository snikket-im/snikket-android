package eu.siacs.conversations.services;

import android.provider.Settings;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.iid.InstanceID;

import java.io.IOException;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.xmpp.OnIqPacketReceived;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;

public class PushManagementService {

	private static final String APP_SERVER = "push.siacs.eu";

	protected final XmppConnectionService mXmppConnectionService;

	public PushManagementService(XmppConnectionService service) {
		this.mXmppConnectionService = service;
	}

	public void registerPushTokenOnServer(final Account account) {
		Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": has push support");
		retrieveGcmInstanceToken(new OnGcmInstanceTokenRetrieved() {
			@Override
			public void onGcmInstanceTokenRetrieved(String token) {
				try {
					final String deviceId = Settings.Secure.getString(mXmppConnectionService.getContentResolver(), Settings.Secure.ANDROID_ID);
					IqPacket packet = mXmppConnectionService.getIqGenerator().pushTokenToAppServer(Jid.fromString(APP_SERVER), token, deviceId);
					mXmppConnectionService.sendIqPacket(account, packet, new OnIqPacketReceived() {
						@Override
						public void onIqPacketReceived(Account account, IqPacket packet) {
							Log.d(Config.LOGTAG, "push to app server result: " + packet.toString());
						}
					});
				} catch (InvalidJidException ignored) {

				}
			}
		});
	}

	private void retrieveGcmInstanceToken(final OnGcmInstanceTokenRetrieved instanceTokenRetrieved) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				InstanceID instanceID = InstanceID.getInstance(mXmppConnectionService);
				try {
					String token = instanceID.getToken(mXmppConnectionService.getString(R.string.gcm_defaultSenderId), GoogleCloudMessaging.INSTANCE_ID_SCOPE, null);
					instanceTokenRetrieved.onGcmInstanceTokenRetrieved(token);
				} catch (IOException e) {
				}
			}
		}).start();

	}


	public boolean available(Account account) {
		return account.getXmppConnection().getFeatures().push() && playServicesAvailable();
	}

	private boolean playServicesAvailable() {
		return GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(mXmppConnectionService) == ConnectionResult.SUCCESS;
	}

	interface OnGcmInstanceTokenRetrieved {
		void onGcmInstanceTokenRetrieved(String token);
	}
}
