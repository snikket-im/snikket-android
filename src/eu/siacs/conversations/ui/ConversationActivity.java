package eu.siacs.conversations.ui;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.utils.ExceptionHelper;
import eu.siacs.conversations.utils.UIHelper;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.support.v4.widget.SlidingPaneLayout;
import android.support.v4.widget.SlidingPaneLayout.PanelSlideListener;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.TextView;
import android.widget.ImageView;

public class ConversationActivity extends XmppActivity {

	public static final String VIEW_CONVERSATION = "viewConversation";
	public static final String CONVERSATION = "conversationUuid";
	public static final String TEXT = "text";
	
	public static final int REQUEST_SEND_MESSAGE = 0x75441;
	public static final int REQUEST_DECRYPT_PGP = 0x76783;

	protected SlidingPaneLayout spl;

	private List<Conversation> conversationList = new ArrayList<Conversation>();
	private Conversation selectedConversation = null;
	private ListView listView;
	
	private boolean paneShouldBeOpen = true;
	private boolean useSubject = true;
	private ArrayAdapter<Conversation> listAdapter;
	
	private OnConversationListChangedListener onConvChanged = new OnConversationListChangedListener() {
		
		@Override
		public void onConversationListChanged() {
			runOnUiThread(new Runnable() {
				
				@Override
				public void run() {	
					updateConversationList();
					if(paneShouldBeOpen) {
						if (conversationList.size() >= 1) {
							swapConversationFragment();
						} else {
							startActivity(new Intent(getApplicationContext(), ContactsActivity.class));
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
	
	private DialogInterface.OnClickListener addToRoster = new DialogInterface.OnClickListener() {
		
		@Override
		public void onClick(DialogInterface dialog, int which) {
			String jid = getSelectedConversation().getContactJid();
			Account account = getSelectedConversation().getAccount();
			String name = jid.split("@")[0];
			Contact contact = new Contact(account, name, jid, null);
			xmppConnectionService.createContact(contact);
		}
	};
	protected ConversationActivity activity = this;
	
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
				Conversation conv;
				if (conversationList.size() > position) {
					conv = getItem(position);
				} else {
					return view;
				}
				if (!spl.isSlideable()) {
					if (conv==getSelectedConversation()) {
						view.setBackgroundColor(0xffdddddd);
					} else {
						view.setBackgroundColor(Color.TRANSPARENT);
					}
				} else {
					view.setBackgroundColor(Color.TRANSPARENT);
				}
				TextView convName = (TextView) view.findViewById(R.id.conversation_name);
				convName.setText(conv.getName(useSubject));
				TextView convLastMsg = (TextView) view.findViewById(R.id.conversation_lastmsg);
				convLastMsg.setText(conv.getLatestMessage().getBody());
				
				if(!conv.isRead()) {
					convName.setTypeface(null,Typeface.BOLD);
					convLastMsg.setTypeface(null,Typeface.BOLD);
				} else {
					convName.setTypeface(null,Typeface.NORMAL);
					convLastMsg.setTypeface(null,Typeface.NORMAL);
				}
				
				((TextView) view.findViewById(R.id.conversation_lastupdate))
				.setText(UIHelper.readableTimeDifference(conv.getLatestMessage().getTimeSent()));
				
				ImageView imageView = (ImageView) view.findViewById(R.id.conversation_image);
				imageView.setImageBitmap(UIHelper.getContactPicture(conv.getContact(), conv.getName(useSubject),200, activity.getApplicationContext()));
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
		spl = (SlidingPaneLayout) findViewById(R.id.slidingpanelayout);
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
				hideKeyboard();
			}

			@Override
			public void onPanelClosed(View arg0) {
				paneShouldBeOpen = false;
				if ((conversationList.size() > 0)&&(getSelectedConversation()!=null)) {
					getActionBar().setDisplayHomeAsUpEnabled(true);
					getActionBar().setTitle(getSelectedConversation().getName(useSubject));
					invalidateOptionsMenu();
					if (!getSelectedConversation().isRead()) {
						getSelectedConversation().markRead();
						UIHelper.updateNotification(getApplicationContext(), getConversationList(), null, false);
						listView.invalidateViews();
					}
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
		MenuItem menuSecure = (MenuItem) menu.findItem(R.id.action_security);
		MenuItem menuArchive = (MenuItem) menu.findItem(R.id.action_archive);
		MenuItem menuMucDetails = (MenuItem) menu.findItem(R.id.action_muc_details);
		MenuItem menuContactDetails = (MenuItem) menu.findItem(R.id.action_contact_details);
		MenuItem menuInviteContacts = (MenuItem) menu.findItem(R.id.action_invite);
		
		if ((spl.isOpen()&&(spl.isSlideable()))) {
			menuArchive.setVisible(false);
			menuMucDetails.setVisible(false);
			menuContactDetails.setVisible(false);
			menuSecure.setVisible(false);
			menuInviteContacts.setVisible(false);
		} else {
			((MenuItem) menu.findItem(R.id.action_add)).setVisible(!spl.isSlideable());
			if (this.getSelectedConversation()!=null) {
				if (this.getSelectedConversation().getMode() == Conversation.MODE_MULTI) {
					menuMucDetails.setVisible(true);
					menuContactDetails.setVisible(false);
					menuSecure.setVisible(false);
					menuArchive.setTitle("Leave conference");
					menuInviteContacts.setVisible(true);
				} else {
					menuContactDetails.setVisible(true);
					menuMucDetails.setVisible(false);
					menuInviteContacts.setVisible(false);
					if (this.getSelectedConversation().getLatestMessage().getEncryption() != Message.ENCRYPTION_NONE) {
						menuSecure.setIcon(R.drawable.ic_action_secure);
					}
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
		case R.id.action_add:
			startActivity(new Intent(this, ContactsActivity.class));
			break;
		case R.id.action_archive:
			Conversation conv = getSelectedConversation();
			conv.setStatus(Conversation.STATUS_ARCHIVED);
			paneShouldBeOpen = true;
			spl.openPane();
			xmppConnectionService.archiveConversation(conv);
			if (conversationList.size() > 0) {
				selectedConversation = conversationList.get(0);
			} else {
				selectedConversation = null;
			}
			break;
		case R.id.action_contact_details:
			Contact contact = this.getSelectedConversation().getContact();
			if (contact != null) {
				Intent intent = new Intent(this,ContactDetailsActivity.class);
				intent.setAction(ContactDetailsActivity.ACTION_VIEW_CONTACT);
				intent.putExtra("uuid", contact.getUuid());
				startActivity(intent);
			} else {
				String jid = getSelectedConversation().getContactJid();
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle(jid);
				builder.setMessage("The contact is not in your roster. Would you like to add it.");
				builder.setNegativeButton("Cancel", null);
				builder.setPositiveButton("Add",addToRoster);
				builder.create().show();
			}
			break;
		case R.id.action_muc_details:
			Intent intent = new Intent(this,MucDetailsActivity.class);
			intent.setAction(MucDetailsActivity.ACTION_VIEW_MUC);
			intent.putExtra("uuid", getSelectedConversation().getUuid());
			startActivity(intent);
			break;
		case R.id.action_invite:
			Intent inviteIntent = new Intent(getApplicationContext(),
					ContactsActivity.class);
			inviteIntent.setAction("invite");
			inviteIntent.putExtra("uuid",selectedConversation.getUuid());
			startActivity(inviteIntent);
			break;
		case R.id.action_security:
			final Conversation selConv = getSelectedConversation();
			View menuItemView = findViewById(R.id.action_security);
			PopupMenu popup = new PopupMenu(this, menuItemView);
			final ConversationFragment fragment = (ConversationFragment) getFragmentManager().findFragmentByTag("conversation");
		    if (fragment!=null) {
				popup.setOnMenuItemClickListener(new OnMenuItemClickListener() {
					
					@Override
					public boolean onMenuItemClick(MenuItem item) {
						switch (item.getItemId()) {
						case R.id.encryption_choice_none:
							selConv.nextMessageEncryption = Message.ENCRYPTION_NONE;
							item.setChecked(true);
							break;
						case R.id.encryption_choice_otr:
							selConv.nextMessageEncryption = Message.ENCRYPTION_OTR;
							item.setChecked(true);
							break;
						case R.id.encryption_choice_pgp:
							selConv.nextMessageEncryption = Message.ENCRYPTION_PGP;
							item.setChecked(true);
							break;
						default:
							selConv.nextMessageEncryption = Message.ENCRYPTION_NONE;
							break;
						}
						fragment.updateChatMsgHint();
						return true;
					}
				});
			    popup.inflate(R.menu.encryption_choices);
			    switch (selConv.nextMessageEncryption) {
				case Message.ENCRYPTION_NONE:
					popup.getMenu().findItem(R.id.encryption_choice_none).setChecked(true);
					break;
				case Message.ENCRYPTION_OTR:
					popup.getMenu().findItem(R.id.encryption_choice_otr).setChecked(true);
					break;
				case Message.ENCRYPTION_PGP:
					popup.getMenu().findItem(R.id.encryption_choice_pgp).setChecked(true);
					break;
				case Message.ENCRYPTION_DECRYPTED:
					popup.getMenu().findItem(R.id.encryption_choice_pgp).setChecked(true);
					break;
				default:
					popup.getMenu().findItem(R.id.encryption_choice_none).setChecked(true);
					break;
				}
			    popup.show();
		    }

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
	
	@Override
	public void onStart() {
		super.onStart();
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		this.useSubject = preferences.getBoolean("use_subject_in_muc", true);
		if (this.xmppConnectionServiceBound) {
			this.onBackendConnected();
		}
		if (conversationList.size()>=1) {
			onConvChanged.onConversationListChanged();
		}
	}
	
	@Override
	protected void onStop() {
		if (xmppConnectionServiceBound) {
        	xmppConnectionService.removeOnConversationListChangedListener();
		}
		super.onStop();
	}

	@Override
	void onBackendConnected() {
		this.registerListener();
		if (conversationList.size()==0) {
			updateConversationList();
		}

		if ((getIntent().getAction()!=null)&&(getIntent().getAction().equals(Intent.ACTION_VIEW) && (!handledViewIntent))) {
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
				String text = getIntent().getExtras().getString(TEXT, null);
				swapConversationFragment().setText(text);
			}
		} else {
			if (xmppConnectionService.getAccounts().size() == 0) {
				startActivity(new Intent(this, ManageAccountActivity.class));
				finish();
			} else if (conversationList.size() <= 0) {
				//add no history
				startActivity(new Intent(this, ContactsActivity.class));
				finish();
			} else {
				spl.openPane();
				//find currently loaded fragment
				ConversationFragment selectedFragment = (ConversationFragment) getFragmentManager().findFragmentByTag("conversation");
				if (selectedFragment!=null) {
					selectedFragment.onBackendConnected();
				} else {
					selectedConversation = conversationList.get(0);
					swapConversationFragment();
				}
				ExceptionHelper.checkForCrash(this,this.xmppConnectionService);
			}
		}
	}
	public void registerListener() {
		 if (xmppConnectionServiceBound) {
			 xmppConnectionService.setOnConversationListChangedListener(this.onConvChanged);
		 }
	}

	@Override
	 protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		 super.onActivityResult(requestCode, resultCode, data);
		 if (resultCode == RESULT_OK) {
			if (requestCode == REQUEST_DECRYPT_PGP) {
				ConversationFragment selectedFragment = (ConversationFragment) getFragmentManager().findFragmentByTag("conversation");
				if (selectedFragment!=null) {
					selectedFragment.hidePgpPassphraseBox();
				}
			}
		 }
	 }

	public void updateConversationList() {
		conversationList.clear();
		conversationList.addAll(xmppConnectionService
				.getConversations());
		listView.invalidateViews();
	}
}
