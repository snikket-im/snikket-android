package eu.siacs.conversations.ui;

import eu.siacs.conversations.R;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.services.XmppConnectionService.XmppConnectionBinder;
import eu.siacs.conversations.utils.ExceptionHelper;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.MenuItem;
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
		super.onStart();
		if (!xmppConnectionServiceBound) {
			Intent intent = new Intent(this, XmppConnectionService.class);
			intent.setAction("ui");
			startService(intent);
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
	
	public boolean hasPgp() {
		if (xmppConnectionService.getPgpEngine()!=null) {
			return true;
		} else {
			Builder builder = new AlertDialog.Builder(this);
			builder.setTitle("OpenKeychain not found");
			builder.setIconAttribute(android.R.attr.alertDialogIcon);
			builder.setMessage("Please make sure you have installed OpenKeychain");
			builder.create().show();
			return false;
		}
	}
	
	abstract void onBackendConnected();
	
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.action_settings:
			startActivity(new Intent(this, SettingsActivity.class));
			break;
		case R.id.action_accounts:
			startActivity(new Intent(this, ManageAccountActivity.class));
			break;
		}
		return super.onOptionsItemSelected(item);
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		ExceptionHelper.init(getApplicationContext());
	}
}
