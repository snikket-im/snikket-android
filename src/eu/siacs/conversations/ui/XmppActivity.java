package eu.siacs.conversations.ui;

import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.services.XmppConnectionService.XmppConnectionBinder;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

public abstract class XmppActivity extends Activity {
	public XmppConnectionService xmppConnectionService;
	public boolean xmppConnectionServiceBound = false;
	protected boolean handledViewIntent = false;
	protected ServiceConnection mConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			XmppConnectionBinder binder = (XmppConnectionBinder) service;
			xmppConnectionService = binder.getService();
			xmppConnectionServiceBound = true;
			onBackendConnected();
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			xmppConnectionServiceBound = false;
		}
	};
	
	@Override
	protected void onStart() {
		startService(new Intent(this, XmppConnectionService.class));
		super.onStart();
		if (!xmppConnectionServiceBound) {
			Intent intent = new Intent(this, XmppConnectionService.class);
			bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
		}
	}
	
	@Override
	protected void onStop() {
		super.onStop();
		if (xmppConnectionServiceBound) {
			unbindService(mConnection);
			xmppConnectionServiceBound = false;
		}
	}
	
	protected void hideKeyboard() {
		InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

		View focus = getCurrentFocus();

		if (focus != null) {

			inputManager.hideSoftInputFromWindow(
					focus.getWindowToken(),
					InputMethodManager.HIDE_NOT_ALWAYS);
		}
	}
	
	abstract void onBackendConnected();
}
