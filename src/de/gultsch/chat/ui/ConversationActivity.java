package de.gultsch.chat.ui;

import java.util.HashMap;
import java.util.List;

import de.gultsch.chat.ConversationCursor;
import de.gultsch.chat.ConversationList;
import de.gultsch.chat.R;
import de.gultsch.chat.R.id;
import de.gultsch.chat.entities.Account;
import de.gultsch.chat.entities.Contact;
import de.gultsch.chat.entities.Conversation;
import de.gultsch.chat.persistance.DatabaseBackend;
import android.net.Uri;
import android.os.Bundle;
import android.app.Activity;
import android.app.FragmentManager;
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

public class ConversationActivity extends Activity {

	public static final String START_CONVERSATION = "startconversation";
	public static final String CONVERSATION_CONTACT = "conversationcontact";

	protected SlidingPaneLayout spl;

	protected HashMap<Conversation, ConversationFragment> conversationFragments = new HashMap<Conversation, ConversationFragment>();
	private DatabaseBackend dbb;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		
		dbb = DatabaseBackend.getInstance(this);
		
		super.onCreate(savedInstanceState);

		final List<Conversation> conversationList = dbb.getConversations(Conversation.STATUS_AVAILABLE);

		if (getIntent().getAction().equals(Intent.ACTION_MAIN)) {
			if (conversationList.size() < 0) {
				Log.d("gultsch",
						"no conversations detected. redirect to new conversation activity");
				startActivity(new Intent(this, NewConversationActivity.class));
				finish();
			}
		}

		setContentView(R.layout.fragment_conversations_overview);

		final ListView listView = (ListView) findViewById(R.id.list);
		
		listView.setAdapter(new ArrayAdapter<Conversation>(this, R.layout.conversation_list_row, conversationList) {
			@Override
			public View getView (int position, View view, ViewGroup parent) {
				if (view == null) {
					LayoutInflater  inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
					view = (View) inflater.inflate(R.layout.conversation_list_row,null);
					((TextView) view.findViewById(R.id.conversation_name)).setText(getItem(position).getName());
					((ImageView) view.findViewById(R.id.conversation_image)).setImageURI(getItem(position).getProfilePhotoUri());
				}
				return view;
			}
			
		});
		
		listView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View clickedView,
					int position, long arg3) {
				swapConversationFragment(conversationList.get(position));
				getActionBar().setTitle(conversationList.get(position).getName());
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
					ConversationFragment convFrag = (ConversationFragment) getFragmentManager().findFragmentById(R.id.selected_conversation);
					getActionBar().setTitle(convFrag.getConversation().getName());
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

				// start new conversation
				Conversation conversation = new Conversation(
						contact.getDisplayName(), contact.getProfilePhoto(),
						new Account(), contact.getJid());

				//@TODO don't write to database here; always go through service
				dbb.addConversation(conversation);
				conversationList.add(0, conversation);
				swapConversationFragment(conversationList.get(0));
				spl.closePane();

				// why do i even need this
				getActionBar().setDisplayHomeAsUpEnabled(true);
				getActionBar().setTitle(conversationList.get(0).getName());
			}
		} else {
			// normal startup
			if (conversationList.size() >= 1) {
				swapConversationFragment(conversationList.get(0));
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
		case R.id.action_archive:
			
		default:
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	protected void swapConversationFragment(Conversation conv) {
		ConversationFragment selectedFragment;
		if (conversationFragments.containsKey(conv)) {
			selectedFragment = conversationFragments.get(conv);
		} else {
			selectedFragment = new ConversationFragment();
			selectedFragment.setConversation(conv);
			conversationFragments.put(conv,selectedFragment);
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
