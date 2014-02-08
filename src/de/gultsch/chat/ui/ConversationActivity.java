package de.gultsch.chat.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import de.gultsch.chat.R;
import de.gultsch.chat.R.id;
import de.gultsch.chat.entities.Contact;
import de.gultsch.chat.entities.Conversation;
import de.gultsch.chat.utils.UIHelper;
import android.net.Uri;
import android.os.Bundle;
import android.app.FragmentTransaction;
import android.app.NotificationManager;
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
	public static final String CONVERSATION = "conversationUuid";

	protected SlidingPaneLayout spl;

	private List<Conversation> conversationList = new ArrayList<Conversation>();
	private Conversation selectedConversation = null;
	private ListView listView;
	
	private boolean paneShouldBeOpen = true;
	private ArrayAdapter<Conversation> listAdapter;
	
	private OnConversationListChangedListener onConvChanged = new OnConversationListChangedListener() {
		
		@Override
		public void onConversationListChanged() {
			final Conversation currentConv = getSelectedConversation();
			conversationList.clear();
			conversationList.addAll(xmppConnectionService
					.getConversations());
			runOnUiThread(new Runnable() {
				
				@Override
				public void run() {	
					updateConversationList();
					/*for(int i = 0; i < conversationList.size(); ++i) {
						if (currentConv == conversationList.get(i)) {
							selectedConversation = conversationList.get(i);
							break;
						}
					}*/
					if(paneShouldBeOpen) {
						selectedConversation = conversationList.get(0);
						if (conversationList.size() >= 1) {
							swapConversationFragment();
						} else {
							startActivity(new Intent(getApplicationContext(), NewConversationActivity.class));
							finish();
						}
					}
					ConversationFragment selectedFragment = (ConversationFragment) getFragmentManager().findFragmentByTag("conversation");
					if (selectedFragment!=null) {
						selectedFragment.updateMessages();
					}
				}
			});
		}
	};
	
	
	public List<Conversation> getConversationList() {
		return this.conversationList;
	}

	public Conversation getSelectedConversation() {
		return this.selectedConversation;
	}
	
	public ListView getConversationListView() {
		return this.listView;
	}
	
	public SlidingPaneLayout getSlidingPaneLayout() {
		return this.spl;
	}
	
	public boolean shouldPaneBeOpen() {
		return paneShouldBeOpen;
	}
	
	public void updateConversationList() {
		if (conversationList.size() >= 1) {
			Collections.sort(this.conversationList, new Comparator<Conversation>() {
				@Override
				public int compare(Conversation lhs, Conversation rhs) {
					return (int) (rhs.getLatestMessageDate() - lhs.getLatestMessageDate());
				}
			});
		}
		this.listView.invalidateViews();
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);

		setContentView(R.layout.fragment_conversations_overview);

		listView = (ListView) findViewById(R.id.list);

		this.listAdapter = new ArrayAdapter<Conversation>(this,
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
				((TextView) view.findViewById(R.id.conversation_lastmsg)).setText(getItem(position).getLatestMessage());
				((TextView) view.findViewById(R.id.conversation_lastupdate))
				.setText(UIHelper.readableTimeDifference(getItem(position).getLatestMessageDate()));
				
				Uri profilePhoto = getItem(position).getProfilePhotoUri();
				ImageView imageView = (ImageView) view.findViewById(R.id.conversation_image);
				if (profilePhoto!=null) {
					imageView.setImageURI(profilePhoto);
				} else {
					imageView.setImageBitmap(UIHelper.getUnknownContactPicture(getItem(position).getName(),200));
				}
				
				
				((ImageView) view.findViewById(R.id.conversation_image))
						.setImageURI(getItem(position).getProfilePhotoUri());
				return view;
			}

		};
		
		listView.setAdapter(this.listAdapter);

		listView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View clickedView,
					int position, long arg3) {
				paneShouldBeOpen = false;
				if (selectedConversation != conversationList.get(position)) {
					selectedConversation = conversationList.get(position);
					swapConversationFragment(); //.onBackendConnected(conversationList.get(position));
				} else {
					spl.closePane();
				}
			}
		});
		spl = (SlidingPaneLayout) findViewById(id.slidingpanelayout);
		spl.setParallaxDistance(150);
		spl.setShadowResource(R.drawable.es_slidingpane_shadow);
		spl.setSliderFadeColor(0);
		spl.setPanelSlideListener(new PanelSlideListener() {

			@Override
			public void onPanelOpened(View arg0) {
				paneShouldBeOpen = true;
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
			}

			@Override
			public void onPanelClosed(View arg0) {
				paneShouldBeOpen = false;
				if (conversationList.size() > 0) {
					getActionBar().setDisplayHomeAsUpEnabled(true);
					getActionBar().setTitle(getSelectedConversation().getName());
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
		getMenuInflater().inflate(R.menu.conversations, menu);

		if (spl.isOpen()) {
			((MenuItem) menu.findItem(R.id.action_archive)).setVisible(false);
			((MenuItem) menu.findItem(R.id.action_details)).setVisible(false);
			((MenuItem) menu.findItem(R.id.action_security)).setVisible(false);
		} else {
			((MenuItem) menu.findItem(R.id.action_add)).setVisible(false);
			if (this.getSelectedConversation()!=null) {
				if (this.getSelectedConversation().getMode() == Conversation.MODE_MULTI) {
					((MenuItem) menu.findItem(R.id.action_security)).setVisible(false);
				}
			}
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
			break;
		case R.id.action_archive:
			Conversation conv = getSelectedConversation();
			conv.setStatus(Conversation.STATUS_ARCHIVED);
			paneShouldBeOpen = true;
			spl.openPane();
			xmppConnectionService.archiveConversation(conv);
			selectedConversation = conversationList.get(0);
			break;
		case R.id.action_details:
			DialogContactDetails details = new DialogContactDetails();
			Contact contact = xmppConnectionService.findOrCreateContact(this.getSelectedConversation().getAccount(),this.getSelectedConversation().getContactJid());
			details.setContact(contact);
			details.show(getFragmentManager(), "details");
			break;
		default:
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	protected ConversationFragment swapConversationFragment() {
		ConversationFragment selectedFragment = new ConversationFragment();
		
		FragmentTransaction transaction = getFragmentManager()
				.beginTransaction();
		transaction.replace(R.id.selected_conversation, selectedFragment,"conversation");
		transaction.commit();
		return selectedFragment;
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
	
	public void onStart() {
		super.onStart();
		NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		nm.cancelAll();
		if (conversationList.size()>=1) {
			onConvChanged.onConversationListChanged();
		}
	}
	
	/*@Override
	protected void onPause() {
		super.onPause();
		if (xmppConnectionServiceBound) {
        	xmppConnectionService.removeOnConversationListChangedListener();
            unbindService(mConnection);
            xmppConnectionServiceBound = false;
        }
	}*/
	
	@Override
	protected void onStop() {
		Log.d("gultsch","called on stop in conversation activity");
		if (xmppConnectionServiceBound) {
        	xmppConnectionService.removeOnConversationListChangedListener();
		}
		super.onStop();
	}

	@Override
	void onBackendConnected() {
		
		xmppConnectionService.setOnConversationListChangedListener(this.onConvChanged);
		
		if (conversationList.size()==0) {
			conversationList.clear();
			conversationList.addAll(xmppConnectionService
					.getConversations());
			
			for(Conversation conversation : conversationList) {
				conversation.setMessages(xmppConnectionService.getMessages(conversation));
			}
	
			this.updateConversationList();
		}

		if ((getIntent().getAction().equals(Intent.ACTION_VIEW) && (!handledViewIntent))) {
			if (getIntent().getType().equals(
					ConversationActivity.VIEW_CONVERSATION)) {
				handledViewIntent = true;

				String convToView = (String) getIntent().getExtras().get(CONVERSATION);

				for(int i = 0; i < conversationList.size(); ++i) {
					if (conversationList.get(i).getUuid().equals(convToView)) {
						selectedConversation = conversationList.get(i);
					}
				}
				paneShouldBeOpen = false;
				swapConversationFragment();
			}
		} else {
			if (xmppConnectionService.getAccounts().size() == 0) {
				startActivity(new Intent(this, ManageAccountActivity.class));
				finish();
			} else if (conversationList.size() <= 0) {
				//add no history
				startActivity(new Intent(this, NewConversationActivity.class));
				finish();
			} else {
				spl.openPane();
				//find currently loaded fragment
				ConversationFragment selectedFragment = (ConversationFragment) getFragmentManager().findFragmentByTag("conversation");
				if (selectedFragment!=null) {
					Log.d("gultsch","ConversationActivity. found old fragment.");
					selectedFragment.onBackendConnected();
				} else {
					Log.d("gultsch","conversationactivity. no old fragment found. creating new one");
					selectedConversation = conversationList.get(0);
					Log.d("gultsch","selected conversation is #"+selectedConversation);
					swapConversationFragment();
				}
			}
		}
	}
}
