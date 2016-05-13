package eu.siacs.conversations.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import android.util.Log;

import org.openintents.openpgp.util.OpenPgpApi;

import java.util.List;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.ListItem;
import eu.siacs.conversations.entities.Presence;
import eu.siacs.conversations.entities.PresenceTemplate;
import eu.siacs.conversations.utils.UIHelper;

public class SetPresenceActivity extends XmppActivity implements View.OnClickListener {

	//data
	protected Account mAccount;
	private List<PresenceTemplate> mTemplates;

	//UI Elements
	protected ScrollView mScrollView;
	protected EditText mStatusMessage;
	protected Spinner mShowSpinner;
	protected CheckBox mAllAccounts;
	protected LinearLayout mTemplatesView;
	private Pair<Integer, Intent> mPostponedActivityResult;

	private Runnable onPresenceChanged = new Runnable() {
		@Override
		public void run() {
			finish();
		}
	};

	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_set_presence);
		mScrollView = (ScrollView) findViewById(R.id.scroll_view);
		mShowSpinner = (Spinner) findViewById(R.id.presence_show);
		ArrayAdapter adapter = ArrayAdapter.createFromResource(this,
				R.array.presence_show_options,
				R.layout.simple_list_item);
		mShowSpinner.setAdapter(adapter);
		mShowSpinner.setSelection(1);
		mStatusMessage = (EditText) findViewById(R.id.presence_status_message);
		mAllAccounts = (CheckBox) findViewById(R.id.all_accounts);
		mTemplatesView = (LinearLayout) findViewById(R.id.templates);
		final Button changePresence = (Button) findViewById(R.id.change_presence);
		changePresence.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				executeChangePresence();
			}
		});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.change_presence, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		if (item.getItemId() == R.id.action_account_details) {
			if (mAccount != null) {
				switchToAccount(mAccount);
			}
			return true;
		} else {
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode == RESULT_OK) {
			if (xmppConnectionServiceBound && mAccount != null) {
				if (requestCode == REQUEST_ANNOUNCE_PGP) {
					announcePgp(mAccount, null, onPresenceChanged);
				}
				this.mPostponedActivityResult = null;
			} else {
				this.mPostponedActivityResult = new Pair<>(requestCode, data);
			}
		}
	}

	private void executeChangePresence() {
		Presence.Status status = getStatusFromSpinner();
		boolean allAccounts = mAllAccounts.isChecked();
		String statusMessage = mStatusMessage.getText().toString().trim();
		if (allAccounts && noAccountUsesPgp()) {
			xmppConnectionService.changeStatus(status, statusMessage);
			finish();
		} else if (mAccount != null) {
			if (mAccount.getPgpId() == 0 || !hasPgp()) {
				xmppConnectionService.changeStatus(mAccount, status, statusMessage, true);
				finish();
			} else {
				xmppConnectionService.changeStatus(mAccount, status, statusMessage, false);
				announcePgp(mAccount, null, onPresenceChanged);
			}
		}
	}

	private Presence.Status getStatusFromSpinner() {
		switch (mShowSpinner.getSelectedItemPosition()) {
			case 0:
				return Presence.Status.CHAT;
			case 2:
				return Presence.Status.AWAY;
			case 3:
				return Presence.Status.XA;
			case 4:
				return Presence.Status.DND;
			default:
				return Presence.Status.ONLINE;
		}
	}

	private void setStatusInSpinner(Presence.Status status) {
		switch(status) {
			case AWAY:
				mShowSpinner.setSelection(2);
				break;
			case XA:
				mShowSpinner.setSelection(3);
				break;
			case CHAT:
				mShowSpinner.setSelection(0);
				break;
			case DND:
				mShowSpinner.setSelection(4);
				break;
			default:
				mShowSpinner.setSelection(1);
				break;
		}
	}

	@Override
	protected void refreshUiReal() {

	}

	@Override
	void onBackendConnected() {
		mAccount = extractAccount(getIntent());
		if (mAccount != null) {
			setStatusInSpinner(mAccount.getPresenceStatus());
			String message = mAccount.getPresenceStatusMessage();
			if (mStatusMessage.getText().length() == 0 && message != null) {
				mStatusMessage.append(message);
			}
			mTemplates = xmppConnectionService.getPresenceTemplates(mAccount);
			if (this.mPostponedActivityResult != null) {
				this.onActivityResult(mPostponedActivityResult.first, RESULT_OK, mPostponedActivityResult.second);
			}
			boolean e = noAccountUsesPgp();
			mAllAccounts.setEnabled(e);
			mAllAccounts.setTextColor(e ? getPrimaryTextColor() : getSecondaryTextColor());
		}
		redrawTemplates();
	}

	private void redrawTemplates() {
		if (mTemplates == null || mTemplates.size() == 0) {
			mTemplatesView.setVisibility(View.GONE);
		} else {
			mTemplatesView.removeAllViews();
			mTemplatesView.setVisibility(View.VISIBLE);
			LayoutInflater inflater = getLayoutInflater();
			for (PresenceTemplate template : mTemplates) {
				View templateLayout = inflater.inflate(R.layout.presence_template, mTemplatesView, false);
				templateLayout.setTag(template);
				setListItemBackgroundOnView(templateLayout);
				templateLayout.setOnClickListener(this);
				TextView message = (TextView) templateLayout.findViewById(R.id.presence_status_message);
				TextView status = (TextView) templateLayout.findViewById(R.id.status);
				ImageButton button = (ImageButton) templateLayout.findViewById(R.id.delete_button);
				button.setTag(template);
				button.setOnClickListener(this);
				ListItem.Tag tag = UIHelper.getTagForStatus(this, template.getStatus());
				status.setText(tag.getName());
				status.setBackgroundColor(tag.getColor());
				message.setText(template.getStatusMessage());
				mTemplatesView.addView(templateLayout);
			}
		}
	}

	@Override
	public void onClick(View v) {
		PresenceTemplate template = (PresenceTemplate) v.getTag();
		if (template == null) {
			return;
		}
		if (v.getId() == R.id.presence_template) {
			setStatusInSpinner(template.getStatus());
			mStatusMessage.getEditableText().clear();
			mStatusMessage.getEditableText().append(template.getStatusMessage());
			new Handler().post(new Runnable() {
				@Override
				public void run() {
					mScrollView.smoothScrollTo(0,0);
				}
			});
		} else if (v.getId() == R.id.delete_button) {
			xmppConnectionService.databaseBackend.deletePresenceTemplate(template);
			mTemplates.remove(template);
			redrawTemplates();
		}
	}
}
