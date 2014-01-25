package de.gultsch.chat.ui;

import java.util.ArrayList;
import java.util.List;

import de.gultsch.chat.R;
import de.gultsch.chat.R.id;
import de.gultsch.chat.entities.Account;
import de.gultsch.chat.entities.Contact;
import de.gultsch.chat.entities.Conversation;
import de.gultsch.chat.persistance.DatabaseBackend;
import android.os.Bundle;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.support.v4.widget.SlidingPaneLayout;
import android.support.v4.widget.SlidingPaneLayout.PanelSlideListener;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ImageView;

public class ConversationActivity extends XmppActivity {

	public static final String VIEW_CONVERSATION = "viewConversation";
	private static final String LOGTAG = "secureconversation";
	protected static final String CONVERSATION = "conversationUuid";

	protected SlidingPaneLayout spl;

	final List<Conversation> conversationList = new ArrayList<Conversation>();

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);

		setContentView(R.layout.fragment_conversations_overview);

		final ListView listView = (ListView) findViewById(R.id.list);

		listView.setAdapter(new ArrayAdapter<Conversation>(this,
				R.layout.conversation_list_row, conversationList) {
			@Override
			public View getView(int position, View view, ViewGroup parent) {
				if (view == null) {
					LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
					view = (View) inflater.inflate(
							R.layout.conversation_list_row, null);
				}
				((TextView) view.findViewById(R.id.conversation_name))
						.setText(getItem(position).getName());
				((ImageView) view.findViewById(R.id.conversation_image))
						.setImageURI(getItem(position).getProfilePhotoUri());
				return view;
			}

		});

		listView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View clickedView,
					int position, long arg3) {
				Log.d(LOGTAG, "List view was klicked on position " + position);
				swapConversationFragment(conversationList.get(position));
				getActionBar().setTitle(
						conversationList.get(position).getName());
				spl.closePane();
			}
		});
		spl = (SlidingPaneLayout) findViewById(id.slidingpanelayout);
		spl.setParallaxDistance(150);
		spl.openPane();
		spl.setShadowResource(R.drawable.es_slidingpane_shadow);
		spl.setSliderFadeColor(0);
		spl.setPanelSlideListener(new PanelSlideListener() {

			@Override
			public void onPanelOpened(View arg0) {
				getActionBar().setDisplayHomeAsUpEnabled(false);
				getActionBar().setTitle(R.string.app_name);
				invalidateOptionsMenu();

				InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

				View focus = getCurrentFocus();

				if (focus != null) {

					inputManager.hideSoftInputFromWindow(
							focus.getWindowToken(),
							InputMethodManager.HIDE_NOT_ALWAYS);
				}
				listView.requestFocus();
			}

			@Override
			public void onPanelClosed(View arg0) {
				if (conversationList.size() > 0) {
					getActionBar().setDisplayHomeAsUpEnabled(true);
					ConversationFragment convFrag = (ConversationFragment) getFragmentManager()
							.findFragmentById(R.id.selected_conversation);
					if (convFrag == null) {
						Log.d(LOGTAG, "conversation fragment was not found.");
						return; // just do nothing. at least dont crash
					}
					getActionBar().setTitle(
							convFrag.getConversation().getName());
					invalidateOptionsMenu();
				}
			}

			@Override
			public void onPanelSlide(View arg0, float arg1) {
				// TODO Auto-generated method stub

			}
		});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.conversations, menu);

		if (spl.isOpen()) {
			((MenuItem) menu.findItem(R.id.action_archive)).setVisible(false);
			((MenuItem) menu.findItem(R.id.action_details)).setVisible(false);
			((MenuItem) menu.findItem(R.id.action_security)).setVisible(false);
		} else {
			((MenuItem) menu.findItem(R.id.action_add)).setVisible(false);
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			spl.openPane();
			break;
		case R.id.action_settings:
			startActivity(new Intent(this, SettingsActivity.class));
			break;
		case R.id.action_accounts:
			startActivity(new Intent(this, ManageAccountActivity.class));
			break;
		case R.id.action_add:
			startActivity(new Intent(this, NewConversationActivity.class));
		case R.id.action_archive:

			break;
		default:
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	protected void swapConversationFragment(Conversation conv) {
		Log.d(LOGTAG, "swap conversation fragment to " + conv.getName());
		ConversationFragment selectedFragment = new ConversationFragment();
		selectedFragment.setConversation(conv);
		FragmentTransaction transaction = getFragmentManager()
				.beginTransaction();
		transaction.replace(R.id.selected_conversation, selectedFragment);
		transaction.commit();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			if (!spl.isOpen()) {
				spl.openPane();
				return false;
			}
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public void onStart() {
		super.onStart();
		if (xmppConnectionServiceBound) {
			conversationList.clear();
			conversationList.addAll(xmppConnectionService
					.getConversations(Conversation.STATUS_AVAILABLE));
		}
	}

	@Override
	void servConnected() {
		conversationList.clear();
		conversationList.addAll(xmppConnectionService
				.getConversations(Conversation.STATUS_AVAILABLE));

		//spl.openPane();

		if ((getIntent().getAction().equals(Intent.ACTION_VIEW) && (!handledViewIntent))) {
			if (getIntent().getType().equals(
					ConversationActivity.VIEW_CONVERSATION)) {
				handledViewIntent = true;

				swapConversationFragment(conversationList.get(0));
				spl.closePane();

				// why do i even need this
				getActionBar().setDisplayHomeAsUpEnabled(true);
				getActionBar().setTitle(conversationList.get(0).getName());

			}
		} else {
			if (conversationList.size() <= 0) {
				//add no history
				startActivity(new Intent(this, NewConversationActivity.class));
				finish();
			} else {
				swapConversationFragment(conversationList.get(0));
			}
		}
	}
}
