package eu.siacs.conversations.ui;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.support.v4.widget.SlidingPaneLayout;
import android.support.v4.widget.SlidingPaneLayout.PanelSlideListener;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Blockable;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService.OnAccountUpdate;
import eu.siacs.conversations.services.XmppConnectionService.OnConversationUpdate;
import eu.siacs.conversations.services.XmppConnectionService.OnRosterUpdate;
import eu.siacs.conversations.ui.adapter.ConversationAdapter;
import eu.siacs.conversations.utils.ExceptionHelper;
import eu.siacs.conversations.xmpp.OnUpdateBlocklist;

public class ConversationActivity extends XmppActivity
	implements OnAccountUpdate, OnConversationUpdate, OnRosterUpdate, OnUpdateBlocklist {

	public static final String VIEW_CONVERSATION = "viewConversation";
	public static final String CONVERSATION = "conversationUuid";
	public static final String TEXT = "text";
	public static final String NICK = "nick";
	public static final String PRESENCE = "eu.siacs.conversations.presence";

	public static final int REQUEST_SEND_MESSAGE = 0x0201;
	public static final int REQUEST_DECRYPT_PGP = 0x0202;
	public static final int REQUEST_ENCRYPT_MESSAGE = 0x0207;
	private static final int REQUEST_ATTACH_IMAGE_DIALOG = 0x0203;
	private static final int REQUEST_IMAGE_CAPTURE = 0x0204;
	private static final int REQUEST_RECORD_AUDIO = 0x0205;
	private static final int REQUEST_SEND_PGP_IMAGE = 0x0206;
	private static final int REQUEST_ATTACH_FILE_DIALOG = 0x0208;
	private static final int ATTACHMENT_CHOICE_CHOOSE_IMAGE = 0x0301;
	private static final int ATTACHMENT_CHOICE_TAKE_PHOTO = 0x0302;
	private static final int ATTACHMENT_CHOICE_CHOOSE_FILE = 0x0303;
	private static final String STATE_OPEN_CONVERSATION = "state_open_conversation";
	private static final String STATE_PANEL_OPEN = "state_panel_open";
	private static final String STATE_PENDING_URI = "state_pending_uri";

	private String mOpenConverstaion = null;
	private boolean mPanelOpen = true;
	private Uri mPendingImageUri = null;
	private Uri mPendingFileUri = null;

	private View mContentView;

	private List<Conversation> conversationList = new ArrayList<>();
	private Conversation mSelectedConversation = null;
	private ListView listView;
	private ConversationFragment mConversationFragment;

	private ArrayAdapter<Conversation> listAdapter;

	private Toast prepareFileToast;


	public List<Conversation> getConversationList() {
		return this.conversationList;
	}

	public Conversation getSelectedConversation() {
		return this.mSelectedConversation;
	}

	public void setSelectedConversation(Conversation conversation) {
		this.mSelectedConversation = conversation;
	}

	public ListView getConversationListView() {
		return this.listView;
	}

	public void showConversationsOverview() {
		if (mContentView instanceof SlidingPaneLayout) {
			SlidingPaneLayout mSlidingPaneLayout = (SlidingPaneLayout) mContentView;
			mSlidingPaneLayout.openPane();
		}
	}

	@Override
	protected String getShareableUri() {
		Conversation conversation = getSelectedConversation();
		if (conversation != null) {
			return conversation.getAccount().getShareableUri();
		} else {
			return "";
		}
	}

	public void hideConversationsOverview() {
		if (mContentView instanceof SlidingPaneLayout) {
			SlidingPaneLayout mSlidingPaneLayout = (SlidingPaneLayout) mContentView;
			mSlidingPaneLayout.closePane();
		}
	}

	public boolean isConversationsOverviewHideable() {
		if (mContentView instanceof SlidingPaneLayout) {
			SlidingPaneLayout mSlidingPaneLayout = (SlidingPaneLayout) mContentView;
			return mSlidingPaneLayout.isSlideable();
		} else {
			return false;
		}
	}

	public boolean isConversationsOverviewVisable() {
		if (mContentView instanceof SlidingPaneLayout) {
			SlidingPaneLayout mSlidingPaneLayout = (SlidingPaneLayout) mContentView;
			return mSlidingPaneLayout.isOpen();
		} else {
			return true;
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (savedInstanceState != null) {mOpenConverstaion = savedInstanceState.getString(
				STATE_OPEN_CONVERSATION, null);
		mPanelOpen = savedInstanceState.getBoolean(STATE_PANEL_OPEN, true);
		String pending = savedInstanceState.getString(STATE_PENDING_URI, null);
		if (pending != null) {
			mPendingImageUri = Uri.parse(pending);
		}
		}

		setContentView(R.layout.fragment_conversations_overview);

		this.mConversationFragment = new ConversationFragment();
		FragmentTransaction transaction = getFragmentManager().beginTransaction();
		transaction.replace(R.id.selected_conversation, this.mConversationFragment, "conversation");
		transaction.commit();

		listView = (ListView) findViewById(R.id.list);
		this.listAdapter = new ConversationAdapter(this, conversationList);
		listView.setAdapter(this.listAdapter);

		if (getActionBar() != null) {
			getActionBar().setDisplayHomeAsUpEnabled(false);
			getActionBar().setHomeButtonEnabled(false);
		}

		listView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View clickedView,
					int position, long arg3) {
				if (getSelectedConversation() != conversationList.get(position)) {
					setSelectedConversation(conversationList.get(position));
					ConversationActivity.this.mConversationFragment.reInit(getSelectedConversation());
				}
				hideConversationsOverview();
			}
		});
		mContentView = findViewById(R.id.content_view_spl);
		if (mContentView == null) {
			mContentView = findViewById(R.id.content_view_ll);
		}
		if (mContentView instanceof SlidingPaneLayout) {
			SlidingPaneLayout mSlidingPaneLayout = (SlidingPaneLayout) mContentView;
			mSlidingPaneLayout.setParallaxDistance(150);
			mSlidingPaneLayout
				.setShadowResource(R.drawable.es_slidingpane_shadow);
			mSlidingPaneLayout.setSliderFadeColor(0);
			mSlidingPaneLayout.setPanelSlideListener(new PanelSlideListener() {

				@Override
				public void onPanelOpened(View arg0) {
					updateActionBarTitle();
					invalidateOptionsMenu();
					hideKeyboard();
					if (xmppConnectionServiceBound) {
						xmppConnectionService.getNotificationService()
							.setOpenConversation(null);
					}
					closeContextMenu();
				}

				@Override
				public void onPanelClosed(View arg0) {
					openConversation();
				}

				@Override
				public void onPanelSlide(View arg0, float arg1) {
					// TODO Auto-generated method stub

				}
			});
		}
	}

	@Override
	public void switchToConversation(Conversation conversation) {
		setSelectedConversation(conversation);
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				ConversationActivity.this.mConversationFragment.reInit(getSelectedConversation());
				openConversation();
			}
		});
	}

	private void updateActionBarTitle() {
		updateActionBarTitle(isConversationsOverviewHideable() && !isConversationsOverviewVisable());
	}

	private void updateActionBarTitle(boolean titleShouldBeName) {
		final ActionBar ab = getActionBar();
		final Conversation conversation = getSelectedConversation();
		if (ab != null) {
			if (titleShouldBeName && conversation != null) {
				ab.setDisplayHomeAsUpEnabled(true);
				ab.setHomeButtonEnabled(true);
				if (conversation.getMode() == Conversation.MODE_SINGLE || useSubjectToIdentifyConference()) {
					ab.setTitle(conversation.getName());
				} else {
					ab.setTitle(conversation.getJid().toBareJid().toString());
				}
			} else {
				ab.setDisplayHomeAsUpEnabled(false);
				ab.setHomeButtonEnabled(false);
				ab.setTitle(R.string.app_name);
			}
		}
	}

	private void openConversation() {
		this.updateActionBarTitle();
		this.invalidateOptionsMenu();
		if (xmppConnectionServiceBound) {
			xmppConnectionService.getNotificationService().setOpenConversation(getSelectedConversation());
			if (!getSelectedConversation().isRead()) {
				xmppConnectionService.markRead(getSelectedConversation(), true);
				listView.invalidateViews();
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.conversations, menu);
		final MenuItem menuSecure = menu.findItem(R.id.action_security);
		final MenuItem menuArchive = menu.findItem(R.id.action_archive);
		final MenuItem menuMucDetails = menu.findItem(R.id.action_muc_details);
		final MenuItem menuContactDetails = menu.findItem(R.id.action_contact_details);
		final MenuItem menuAttach = menu.findItem(R.id.action_attach_file);
		final MenuItem menuClearHistory = menu.findItem(R.id.action_clear_history);
		final MenuItem menuAdd = menu.findItem(R.id.action_add);
		final MenuItem menuInviteContact = menu.findItem(R.id.action_invite);
		final MenuItem menuMute = menu.findItem(R.id.action_mute);
		final MenuItem menuUnmute = menu.findItem(R.id.action_unmute);
		final MenuItem menuBlock = menu.findItem(R.id.action_block);
		final MenuItem menuUnblock = menu.findItem(R.id.action_unblock);

		if (isConversationsOverviewVisable()
				&& isConversationsOverviewHideable()) {
			menuArchive.setVisible(false);
			menuMucDetails.setVisible(false);
			menuContactDetails.setVisible(false);
			menuSecure.setVisible(false);
			menuInviteContact.setVisible(false);
			menuAttach.setVisible(false);
			menuClearHistory.setVisible(false);
			menuMute.setVisible(false);
			menuUnmute.setVisible(false);
			menuBlock.setVisible(false);
			menuUnblock.setVisible(false);
		} else {
			menuAdd.setVisible(!isConversationsOverviewHideable());
			if (this.getSelectedConversation() != null) {
				if (this.getSelectedConversation().getLatestMessage()
						.getEncryption() != Message.ENCRYPTION_NONE) {
					menuSecure.setIcon(R.drawable.ic_action_secure);
						}
				if (this.getSelectedConversation().getMode() == Conversation.MODE_MULTI) {
					menuContactDetails.setVisible(false);
					menuAttach.setVisible(false);
					menuBlock.setVisible(false);
					menuUnblock.setVisible(false);
				} else {
					menuMucDetails.setVisible(false);
					menuInviteContact.setTitle(R.string.conference_with);
					if (this.getSelectedConversation().isBlocked()) {
						menuBlock.setVisible(false);
					} else {
						menuUnblock.setVisible(false);
					}
					final Account account = this.getSelectedConversation().getAccount();
					if (account.isOnlineAndConnected() || !account.getXmppConnection().getFeatures().blocking()) {
						menuBlock.setVisible(false);
						menuUnblock.setVisible(false);
					}
				}
				if (this.getSelectedConversation().isMuted()) {
					menuMute.setVisible(false);
				} else {
					menuUnmute.setVisible(false);
				}
			}
		}
		return true;
	}

	private void selectPresenceToAttachFile(final int attachmentChoice) {
		selectPresence(getSelectedConversation(), new OnPresenceSelected() {

			@Override
			public void onPresenceSelected() {
				if (attachmentChoice == ATTACHMENT_CHOICE_TAKE_PHOTO) {
					mPendingImageUri = xmppConnectionService.getFileBackend()
						.getTakePhotoUri();
					Intent takePictureIntent = new Intent(
							MediaStore.ACTION_IMAGE_CAPTURE);
					takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT,
							mPendingImageUri);
					if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
						startActivityForResult(takePictureIntent,
								REQUEST_IMAGE_CAPTURE);
					}
				} else if (attachmentChoice == ATTACHMENT_CHOICE_CHOOSE_IMAGE) {
					Intent attachFileIntent = new Intent();
					attachFileIntent.setType("image/*");
					attachFileIntent.setAction(Intent.ACTION_GET_CONTENT);
					Intent chooser = Intent.createChooser(attachFileIntent,
							getString(R.string.attach_file));
					startActivityForResult(chooser, REQUEST_ATTACH_IMAGE_DIALOG);
				} else if (attachmentChoice == ATTACHMENT_CHOICE_CHOOSE_FILE) {
					Intent attachFileIntent = new Intent();
					//attachFileIntent.setType("file/*");
					attachFileIntent.setType("*/*");
					attachFileIntent.addCategory(Intent.CATEGORY_OPENABLE);
					attachFileIntent.setAction(Intent.ACTION_GET_CONTENT);
					Intent chooser = Intent.createChooser(attachFileIntent,
							getString(R.string.attach_file));
					startActivityForResult(chooser, REQUEST_ATTACH_FILE_DIALOG);
				}
			}
		});
	}

	private void attachFile(final int attachmentChoice) {
		final Conversation conversation = getSelectedConversation();
		if (conversation.getNextEncryption(forceEncryption()) == Message.ENCRYPTION_PGP) {
			if (hasPgp()) {
				if (conversation.getContact().getPgpKeyId() != 0) {
					xmppConnectionService.getPgpEngine().hasKey(
							conversation.getContact(),
							new UiCallback<Contact>() {

								@Override
								public void userInputRequried(PendingIntent pi,
										Contact contact) {
									ConversationActivity.this.runIntent(pi,
											attachmentChoice);
								}

								@Override
								public void success(Contact contact) {
									selectPresenceToAttachFile(attachmentChoice);
								}

								@Override
								public void error(int error, Contact contact) {
									displayErrorDialog(error);
								}
							});
				} else {
					final ConversationFragment fragment = (ConversationFragment) getFragmentManager()
						.findFragmentByTag("conversation");
					if (fragment != null) {
						fragment.showNoPGPKeyDialog(false,
								new OnClickListener() {

									@Override
									public void onClick(DialogInterface dialog,
											int which) {
										conversation
											.setNextEncryption(Message.ENCRYPTION_NONE);
										xmppConnectionService.databaseBackend
											.updateConversation(conversation);
										selectPresenceToAttachFile(attachmentChoice);
									}
								});
					}
				}
			} else {
				showInstallPgpDialog();
			}
		} else if (getSelectedConversation().getNextEncryption(
					forceEncryption()) == Message.ENCRYPTION_NONE) {
			selectPresenceToAttachFile(attachmentChoice);
		} else {
			selectPresenceToAttachFile(attachmentChoice);
		}
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			showConversationsOverview();
			return true;
		} else if (item.getItemId() == R.id.action_add) {
			startActivity(new Intent(this, StartConversationActivity.class));
			return true;
		} else if (getSelectedConversation() != null) {
			switch (item.getItemId()) {
				case R.id.action_attach_file:
					attachFileDialog();
					break;
				case R.id.action_archive:
					this.endConversation(getSelectedConversation());
					break;
				case R.id.action_contact_details:
					Contact contact = this.getSelectedConversation().getContact();
					if (contact.showInRoster()) {
						switchToContactDetails(contact);
					} else {
						showAddToRosterDialog(getSelectedConversation());
					}
					break;
				case R.id.action_muc_details:
					Intent intent = new Intent(this,
							ConferenceDetailsActivity.class);
					intent.setAction(ConferenceDetailsActivity.ACTION_VIEW_MUC);
					intent.putExtra("uuid", getSelectedConversation().getUuid());
					startActivity(intent);
					break;
				case R.id.action_invite:
					inviteToConversation(getSelectedConversation());
					break;
				case R.id.action_security:
					selectEncryptionDialog(getSelectedConversation());
					break;
				case R.id.action_clear_history:
					clearHistoryDialog(getSelectedConversation());
					break;
				case R.id.action_mute:
					muteConversationDialog(getSelectedConversation());
					break;
				case R.id.action_unmute:
					unmuteConversation(getSelectedConversation());
					break;
				case R.id.action_block:
					BlockContactDialog.show(this, xmppConnectionService, getSelectedConversation());
					break;
				case R.id.action_unblock:
					BlockContactDialog.show(this, xmppConnectionService, getSelectedConversation());
					break;
				default:
					break;
			}
			return super.onOptionsItemSelected(item);
		} else {
			return super.onOptionsItemSelected(item);
		}
	}

	public void endConversation(Conversation conversation) {
		conversation.setStatus(Conversation.STATUS_ARCHIVED);
		showConversationsOverview();
		xmppConnectionService.archiveConversation(conversation);
		if (conversationList.size() > 0) {
			setSelectedConversation(conversationList.get(0));
			this.mConversationFragment.reInit(getSelectedConversation());
		} else {
			setSelectedConversation(null);
		}
	}

	@SuppressLint("InflateParams")
	protected void clearHistoryDialog(final Conversation conversation) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(getString(R.string.clear_conversation_history));
		View dialogView = getLayoutInflater().inflate(
				R.layout.dialog_clear_history, null);
		final CheckBox endConversationCheckBox = (CheckBox) dialogView
			.findViewById(R.id.end_conversation_checkbox);
		builder.setView(dialogView);
		builder.setNegativeButton(getString(R.string.cancel), null);
		builder.setPositiveButton(getString(R.string.delete_messages),
				new OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						ConversationActivity.this.xmppConnectionService.clearConversationHistory(conversation);
						if (endConversationCheckBox.isChecked()) {
							endConversation(conversation);
						} else {
							updateConversationList();
							ConversationActivity.this.mConversationFragment.updateMessages();
						}
					}
				});
		builder.create().show();
	}

	protected void attachFileDialog() {
		View menuAttachFile = findViewById(R.id.action_attach_file);
		if (menuAttachFile == null) {
			return;
		}
		PopupMenu attachFilePopup = new PopupMenu(this, menuAttachFile);
		attachFilePopup.inflate(R.menu.attachment_choices);
		attachFilePopup
			.setOnMenuItemClickListener(new OnMenuItemClickListener() {

				@Override
				public boolean onMenuItemClick(MenuItem item) {
					switch (item.getItemId()) {
						case R.id.attach_choose_picture:
							attachFile(ATTACHMENT_CHOICE_CHOOSE_IMAGE);
							break;
						case R.id.attach_take_picture:
							attachFile(ATTACHMENT_CHOICE_TAKE_PHOTO);
							break;
						case R.id.attach_record_voice:
							attachFile(ATTACHMENT_CHOICE_CHOOSE_FILE);
							break;
					}
					return false;
				}
			});
		attachFilePopup.show();
	}

	protected void selectEncryptionDialog(final Conversation conversation) {
		View menuItemView = findViewById(R.id.action_security);
		if (menuItemView == null) {
			return;
		}
		PopupMenu popup = new PopupMenu(this, menuItemView);
		final ConversationFragment fragment = (ConversationFragment) getFragmentManager()
			.findFragmentByTag("conversation");
		if (fragment != null) {
			popup.setOnMenuItemClickListener(new OnMenuItemClickListener() {

				@Override
				public boolean onMenuItemClick(MenuItem item) {
					switch (item.getItemId()) {
						case R.id.encryption_choice_none:
							conversation.setNextEncryption(Message.ENCRYPTION_NONE);
							item.setChecked(true);
							break;
						case R.id.encryption_choice_otr:
							conversation.setNextEncryption(Message.ENCRYPTION_OTR);
							item.setChecked(true);
							break;
						case R.id.encryption_choice_pgp:
							if (hasPgp()) {
								if (conversation.getAccount().getKeys()
										.has("pgp_signature")) {
									conversation
										.setNextEncryption(Message.ENCRYPTION_PGP);
									item.setChecked(true);
								} else {
									announcePgp(conversation.getAccount(),
											conversation);
								}
							} else {
								showInstallPgpDialog();
							}
							break;
						default:
							conversation.setNextEncryption(Message.ENCRYPTION_NONE);
							break;
					}
					xmppConnectionService.databaseBackend
						.updateConversation(conversation);
					fragment.updateChatMsgHint();
					return true;
				}
			});
			popup.inflate(R.menu.encryption_choices);
			MenuItem otr = popup.getMenu().findItem(R.id.encryption_choice_otr);
			MenuItem none = popup.getMenu().findItem(
					R.id.encryption_choice_none);
			if (conversation.getMode() == Conversation.MODE_MULTI) {
				otr.setEnabled(false);
			} else {
				if (forceEncryption()) {
					none.setVisible(false);
				}
			}
			switch (conversation.getNextEncryption(forceEncryption())) {
				case Message.ENCRYPTION_NONE:
					none.setChecked(true);
					break;
				case Message.ENCRYPTION_OTR:
					otr.setChecked(true);
					break;
				case Message.ENCRYPTION_PGP:
					popup.getMenu().findItem(R.id.encryption_choice_pgp)
						.setChecked(true);
					break;
				default:
					popup.getMenu().findItem(R.id.encryption_choice_none)
						.setChecked(true);
					break;
			}
			popup.show();
		}
	}

	protected void muteConversationDialog(final Conversation conversation) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.disable_notifications);
		final int[] durations = getResources().getIntArray(
				R.array.mute_options_durations);
		builder.setItems(R.array.mute_options_descriptions,
				new OnClickListener() {

					@Override
					public void onClick(final DialogInterface dialog, final int which) {
						final long till;
						if (durations[which] == -1) {
							till = Long.MAX_VALUE;
						} else {
							till = SystemClock.elapsedRealtime()
								+ (durations[which] * 1000);
						}
						conversation.setMutedTill(till);
						ConversationActivity.this.xmppConnectionService.databaseBackend
							.updateConversation(conversation);
						updateConversationList();
						ConversationActivity.this.mConversationFragment.updateMessages();
						invalidateOptionsMenu();
					}
				});
		builder.create().show();
	}

	public void unmuteConversation(final Conversation conversation) {
		conversation.setMutedTill(0);
		this.xmppConnectionService.databaseBackend.updateConversation(conversation);
		updateConversationList();
		ConversationActivity.this.mConversationFragment.updateMessages();
		invalidateOptionsMenu();
	}

	@Override
	public void onBackPressed() {
		if (!isConversationsOverviewVisable()) {
			showConversationsOverview();
		} else {
			moveTaskToBack(true);
		}
	}

	@Override
	protected void onNewIntent(final Intent intent) {
		if (xmppConnectionServiceBound) {
			if (intent != null && VIEW_CONVERSATION.equals(intent.getType())) {
				handleViewConversationIntent(intent);
			}
		} else {
			setIntent(intent);
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		if (this.xmppConnectionServiceBound) {
			this.onBackendConnected();
		}
		if (conversationList.size() >= 1) {
			this.onConversationUpdate();
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		int theme = findTheme();
		if (this.mTheme != theme) {
			recreate();
		}
	}

	@Override
	public void onSaveInstanceState(final Bundle savedInstanceState) {
		Conversation conversation = getSelectedConversation();
		if (conversation != null) {
			savedInstanceState.putString(STATE_OPEN_CONVERSATION,
					conversation.getUuid());
		}
		savedInstanceState.putBoolean(STATE_PANEL_OPEN,
				isConversationsOverviewVisable());
		if (this.mPendingImageUri != null) {
			savedInstanceState.putString(STATE_PENDING_URI, this.mPendingImageUri.toString());
		}
		super.onSaveInstanceState(savedInstanceState);
	}

	@Override
	void onBackendConnected() {
		updateConversationList();
		if (xmppConnectionService.getAccounts().size() == 0) {
			startActivity(new Intent(this, EditAccountActivity.class));
		} else if (conversationList.size() <= 0) {
			startActivity(new Intent(this, StartConversationActivity.class));
			finish();
		} else if (getIntent() != null
				&& VIEW_CONVERSATION.equals(getIntent().getType())) {
			handleViewConversationIntent(getIntent());
		} else if (mOpenConverstaion != null) {
			selectConversationByUuid(mOpenConverstaion);
			if (mPanelOpen) {
				showConversationsOverview();
			} else {
				if (isConversationsOverviewHideable()) {
					openConversation();
				}
			}
			this.mConversationFragment.reInit(getSelectedConversation());
			mOpenConverstaion = null;
		} else if (getSelectedConversation() != null) {
			this.mConversationFragment.updateMessages();
		} else {
			showConversationsOverview();
			mPendingImageUri = null;
			mPendingFileUri = null;
			setSelectedConversation(conversationList.get(0));
			this.mConversationFragment.reInit(getSelectedConversation());
		}

		if (mPendingImageUri != null) {
			attachImageToConversation(getSelectedConversation(),mPendingImageUri);
			mPendingImageUri = null;
		} else if (mPendingFileUri != null) {
			attachFileToConversation(getSelectedConversation(),mPendingFileUri);
			mPendingFileUri = null;
		}
		ExceptionHelper.checkForCrash(this, this.xmppConnectionService);
		setIntent(new Intent());
	}

	private void handleViewConversationIntent(Intent intent) {
		String uuid = (String) intent.getExtras().get(CONVERSATION);
		String text = intent.getExtras().getString(TEXT, "");
		String nick = intent.getExtras().getString(NICK,null);
		selectConversationByUuid(uuid);
		this.mConversationFragment.reInit(getSelectedConversation());
		if (nick!=null) {
			this.mConversationFragment.highlightInConference(nick);
		} else {
			this.mConversationFragment.appendText(text);
		}
		hideConversationsOverview();
		openConversation();
		if (mContentView instanceof SlidingPaneLayout) {
			updateActionBarTitle(true); //fixes bug where slp isn't properly closed yet
		}
	}

	private void selectConversationByUuid(String uuid) {
		for (Conversation aConversationList : conversationList) {
			if (aConversationList.getUuid().equals(uuid)) {
				setSelectedConversation(aConversationList);
			}
		}
	}

	@Override
	protected void unregisterListeners() {
		super.unregisterListeners();
		xmppConnectionService.getNotificationService().setOpenConversation(null);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode,
			final Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode == RESULT_OK) {
			if (requestCode == REQUEST_DECRYPT_PGP) {
				mConversationFragment.hideSnackbar();
				mConversationFragment.updateMessages();
			} else if (requestCode == REQUEST_ATTACH_IMAGE_DIALOG) {
				mPendingImageUri = data.getData();
				if (xmppConnectionServiceBound) {
					attachImageToConversation(getSelectedConversation(),
							mPendingImageUri);
					mPendingImageUri = null;
				}
			} else if (requestCode == REQUEST_ATTACH_FILE_DIALOG) {
				mPendingFileUri = data.getData();
				if (xmppConnectionServiceBound) {
					attachFileToConversation(getSelectedConversation(),
							mPendingFileUri);
					mPendingFileUri = null;
				}
			} else if (requestCode == REQUEST_SEND_PGP_IMAGE) {

			} else if (requestCode == ATTACHMENT_CHOICE_CHOOSE_IMAGE) {
				attachFile(ATTACHMENT_CHOICE_CHOOSE_IMAGE);
			} else if (requestCode == ATTACHMENT_CHOICE_TAKE_PHOTO) {
				attachFile(ATTACHMENT_CHOICE_TAKE_PHOTO);
			} else if (requestCode == REQUEST_ANNOUNCE_PGP) {
				announcePgp(getSelectedConversation().getAccount(),
						getSelectedConversation());
			} else if (requestCode == REQUEST_ENCRYPT_MESSAGE) {
				// encryptTextMessage();
			} else if (requestCode == REQUEST_IMAGE_CAPTURE && mPendingImageUri != null) {
				if (xmppConnectionServiceBound) {
					attachImageToConversation(getSelectedConversation(),
							mPendingImageUri);
					mPendingImageUri = null;
				}
				Intent intent = new Intent(
						Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
				intent.setData(mPendingImageUri);
				sendBroadcast(intent);
			}
		} else {
			if (requestCode == REQUEST_IMAGE_CAPTURE) {
				mPendingImageUri = null;
			}
		}
	}

	private void attachFileToConversation(Conversation conversation, Uri uri) {
		prepareFileToast = Toast.makeText(getApplicationContext(),
				getText(R.string.preparing_file), Toast.LENGTH_LONG);
		prepareFileToast.show();
		xmppConnectionService.attachFileToConversation(conversation,uri, new UiCallback<Message>() {
			@Override
			public void success(Message message) {
				hidePrepareFileToast();
				xmppConnectionService.sendMessage(message);
			}

			@Override
			public void error(int errorCode, Message message) {
				displayErrorDialog(errorCode);
			}

			@Override
			public void userInputRequried(PendingIntent pi, Message message) {

			}
		});
	}

	private void attachImageToConversation(Conversation conversation, Uri uri) {
		prepareFileToast = Toast.makeText(getApplicationContext(),
				getText(R.string.preparing_image), Toast.LENGTH_LONG);
		prepareFileToast.show();
		xmppConnectionService.attachImageToConversation(conversation, uri,
				new UiCallback<Message>() {

					@Override
					public void userInputRequried(PendingIntent pi,
							Message object) {
						hidePrepareFileToast();
						ConversationActivity.this.runIntent(pi,
								ConversationActivity.REQUEST_SEND_PGP_IMAGE);
					}

					@Override
					public void success(Message message) {
						xmppConnectionService.sendMessage(message);
					}

					@Override
					public void error(int error, Message message) {
						hidePrepareFileToast();
						displayErrorDialog(error);
					}
				});
	}

	private void hidePrepareFileToast() {
		if (prepareFileToast != null) {
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					prepareFileToast.cancel();
				}
			});
		}
	}

	public void updateConversationList() {
		xmppConnectionService
			.populateWithOrderedConversations(conversationList);
		listAdapter.notifyDataSetChanged();
	}

	public void runIntent(PendingIntent pi, int requestCode) {
		try {
			this.startIntentSenderForResult(pi.getIntentSender(), requestCode,
					null, 0, 0, 0);
		} catch (final SendIntentException ignored) {
		}
	}

	public void encryptTextMessage(Message message) {
		xmppConnectionService.getPgpEngine().encrypt(message,
				new UiCallback<Message>() {

					@Override
					public void userInputRequried(PendingIntent pi,
							Message message) {
						ConversationActivity.this.runIntent(pi,
								ConversationActivity.REQUEST_SEND_MESSAGE);
					}

					@Override
					public void success(Message message) {
						message.setEncryption(Message.ENCRYPTION_DECRYPTED);
						xmppConnectionService.sendMessage(message);
					}

					@Override
					public void error(int error, Message message) {

					}
				});
	}

	public boolean forceEncryption() {
		return getPreferences().getBoolean("force_encryption", false);
	}

	public boolean useSendButtonToIndicateStatus() {
		return getPreferences().getBoolean("send_button_status", false);
	}

	public boolean indicateReceived() {
		return getPreferences().getBoolean("indicate_received", false);
	}

	@Override
	public void onAccountUpdate() {
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				updateConversationList();
				ConversationActivity.this.mConversationFragment.updateMessages();
				updateActionBarTitle();
			}
		});
	}

	@Override
	public void onConversationUpdate() {
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				updateConversationList();
				if (conversationList.size() == 0) {
					startActivity(new Intent(getApplicationContext(),
								StartConversationActivity.class));
					finish();
				}
				ConversationActivity.this.mConversationFragment.updateMessages();
				updateActionBarTitle();
			}
		});
	}

	@Override
	public void onRosterUpdate() {
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				updateConversationList();
				ConversationActivity.this.mConversationFragment.updateMessages();
				updateActionBarTitle();
			}
		});
	}

	@Override
	public void OnUpdateBlocklist(Status status) {
		invalidateOptionsMenu();
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				ConversationActivity.this.mConversationFragment.updateMessages();
			}
		});
	}

	public void unblockConversation(final Blockable conversation) {
		xmppConnectionService.sendUnblockRequest(conversation);
	}

	public void blockConversation(final Blockable conversation) {
		xmppConnectionService.sendBlockRequest(conversation);
	}
}
