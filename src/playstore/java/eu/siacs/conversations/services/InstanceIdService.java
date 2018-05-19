package eu.siacs.conversations.services;

import android.content.Intent;

import com.google.firebase.iid.FirebaseInstanceIdService;

public class InstanceIdService extends FirebaseInstanceIdService {

	@Override
	public void onTokenRefresh() {
		Intent intent = new Intent(this, XmppConnectionService.class);
		intent.setAction(XmppConnectionService.ACTION_FCM_TOKEN_REFRESH);
		startService(intent);
	}
}
