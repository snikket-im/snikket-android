package eu.siacs.conversations.ui;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.services.XmppConnectionService.XmppConnectionBinder;
import eu.siacs.conversations.utils.ExceptionHelper;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

public abstract class XmppActivity extends Activity {
	
	protected final static String LOGTAG = "xmppService";
	
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
			connectToBackend();
		}
	}
	
	public void connectToBackend() {
		Intent intent = new Intent(this, XmppConnectionService.class);
		intent.setAction("ui");
		startService(intent);
		bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
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
			builder.setTitle(getString(R.string.openkeychain_required));
			builder.setIconAttribute(android.R.attr.alertDialogIcon);
			builder.setMessage(getText(R.string.openkeychain_required_long));
			builder.setNegativeButton(getString(R.string.cancel), null);
			builder.setNeutralButton(getString(R.string.restart), new OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					// TODO Auto-generated method stub
					
				}
			});
			builder.setPositiveButton(getString(R.string.install), new OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					Uri uri = Uri.parse("market://details?id=org.sufficientlysecure.keychain");
					Intent intent = new Intent(Intent.ACTION_VIEW, uri);
					startActivity(intent);
				}
			});
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
	
	public void switchToConversation(Conversation conversation, String text) {
		Intent viewConversationIntent = new Intent(this,
				ConversationActivity.class);
		viewConversationIntent.setAction(Intent.ACTION_VIEW);
		viewConversationIntent.putExtra(ConversationActivity.CONVERSATION,
				conversation.getUuid());
		if (text!=null) {
			viewConversationIntent.putExtra(ConversationActivity.TEXT, text);
		}
		viewConversationIntent.setType(ConversationActivity.VIEW_CONVERSATION);
		viewConversationIntent.setFlags(viewConversationIntent.getFlags()
				| Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(viewConversationIntent);
	}
}
