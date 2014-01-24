package de.gultsch.chat.ui;

import java.util.HashMap;

import de.gultsch.chat.Contact;
import de.gultsch.chat.Conversation;
import de.gultsch.chat.ConversationCursor;
import de.gultsch.chat.ConversationList;
import de.gultsch.chat.R;
import de.gultsch.chat.R.id;
import android.os.Bundle;
import android.app.Activity;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.support.v4.widget.SlidingPaneLayout;
import android.support.v4.widget.SlidingPaneLayout.PanelSlideListener;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

public class ConversationActivity extends Activity {

	public static final String START_CONVERSATION = "startconversation";
	public static final String CONVERSATION_CONTACT = "conversationcontact";

	protected SlidingPaneLayout spl;

	protected HashMap<Conversation, ConversationFragment> conversationFragments = new HashMap<Conversation, ConversationFragment>();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.fragment_conversations_overview);

		final ConversationList conversationList = new ConversationList();

		String[] fromColumns = { ConversationCursor.NAME,
				ConversationCursor.LAST_MSG };
		int[] toViews = { R.id.conversation_name, R.id.conversation_lastmsg };

		final SimpleCursorAdapter adapter = new SimpleCursorAdapter(this,
				R.layout.conversation_list_row, conversationList.getCursor(),
				fromColumns, toViews, 0);
		final ListView listView = (ListView) findViewById(R.id.list);
		listView.setAdapter(adapter);

		listView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View clickedView,
					int position, long arg3) {
				conversationList.setSelectedConversationPosition(position);
				swapConversationFragment(conversationList);
				getActionBar().setTitle(
						conversationList.getSelectedConversation().getName());
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
					getActionBar().setTitle(
							conversationList.getSelectedConversation()
									.getName());
					invalidateOptionsMenu();
				}
			}

			@Override
			public void onPanelSlide(View arg0, float arg1) {
				// TODO Auto-generated method stub

			}
		});

		if (getIntent().getAction().equals(Intent.ACTION_VIEW)) {
			if (getIntent().getType().equals(
					ConversationActivity.START_CONVERSATION)) {
				Contact contact = (Contact) getIntent().getExtras().get(
						ConversationActivity.CONVERSATION_CONTACT);
				Log.d("gultsch",
						"start conversation with " + contact.getDisplayName());
				int pos = conversationList
						.addAndReturnPosition(new Conversation(contact
								.getDisplayName()));
				conversationList.setSelectedConversationPosition(pos);
				swapConversationFragment(conversationList);
				spl.closePane();

				// why do i even need this
				getActionBar().setDisplayHomeAsUpEnabled(true);
				getActionBar().setTitle(
						conversationList.getSelectedConversation().getName());
			}
		} else {
			// normal startup
			if (conversationList.size() >= 1) {
				conversationList.setSelectedConversationPosition(0);
				swapConversationFragment(conversationList);
			} else {
				startActivity(new Intent(this, NewConversationActivity.class));
			}
		}
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
		default:
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	protected void swapConversationFragment(
			final ConversationList conversationList) {
		ConversationFragment selectedFragment;
		if (conversationFragments.containsKey(conversationList
				.getSelectedConversation())) {
			selectedFragment = conversationFragments.get(conversationList
					.getSelectedConversation());
		} else {
			selectedFragment = new ConversationFragment();
			conversationFragments.put(
					conversationList.getSelectedConversation(),
					selectedFragment);
		}
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

}
