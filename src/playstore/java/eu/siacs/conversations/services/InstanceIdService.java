package eu.siacs.conversations.services;

import android.content.Intent;

import com.google.android.gms.iid.InstanceIDListenerService;

public class InstanceIdService extends InstanceIDListenerService {

	@Override
	public void onTokenRefresh() {
		Intent intent = new Intent(this, XmppConnectionService.class);
		intent.setAction(XmppConnectionService.ACTION_GCM_TOKEN_REFRESH);
		startService(intent);
	}
}
