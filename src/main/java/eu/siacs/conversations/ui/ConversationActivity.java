package eu.siacs.conversations.ui;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.v4.widget.SlidingPaneLayout;
import android.support.v4.widget.SlidingPaneLayout.PanelSlideListener;
import android.util.Log;
import android.util.Pair;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.Toast;

import net.java.otr4j.session.SessionStatus;

import org.openintents.openpgp.util.OpenPgpApi;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import de.timroes.android.listview.EnhancedListView;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.crypto.axolotl.FingerprintStatus;
import eu.siacs.conversations.crypto.axolotl.XmppAxolotlSession;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Blockable;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Transferable;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.services.XmppConnectionService.OnAccountUpdate;
import eu.siacs.conversations.services.XmppConnectionService.OnConversationUpdate;
import eu.siacs.conversations.services.XmppConnectionService.OnRosterUpdate;
import eu.siacs.conversations.ui.adapter.ConversationAdapter;
import eu.siacs.conversations.utils.ExceptionHelper;
import eu.siacs.conversations.xmpp.OnUpdateBlocklist;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;

public class ConversationActivity extends XmppActivity
	implements OnAccountUpdate, OnConversationUpdate, OnRosterUpdate, OnUpdateBlocklist, XmppConnectionService.OnShowErrorToast {

	public static final String ACTION_VIEW_CONVERSATION = "eu.siacs.conversations.action.VIEW";
	public static final String CONVERSATION = "conversationUuid";
	public static final String EXTRA_DOWNLOAD_UUID = "eu.siacs.conversations.download_uuid";
	public static final String TEXT = "text";
	public static final String NICK = "nick";
	public static final String PRIVATE_MESSAGE = "pm";

	public static final int REQUEST_SEND_MESSAGE = 0x0201;
	public static final int REQUEST_DECRYPT_PGP = 0x0202;
	public static final int REQUEST_ENCRYPT_MESSAGE = 0x0207;
	public static final int REQUEST_TRUST_KEYS_TEXT = 0x0208;
	public static final int REQUEST_TRUST_KEYS_MENU = 0x0209;
	public static final int REQUEST_START_DOWNLOAD = 0x0210;
	public static final int ATTACHMENT_CHOICE_CHOOSE_IMAGE = 0x0301;
	public static final int ATTACHMENT_CHOICE_TAKE_PHOTO = 0x0302;
	public static final int ATTACHMENT_CHOICE_CHOOSE_FILE = 0x0303;
	public static final int ATTACHMENT_CHOICE_RECORD_VOICE = 0x0304;
	public static final int ATTACHMENT_CHOICE_LOCATION = 0x0305;
	public static final int ATTACHMENT_CHOICE_INVALID = 0x0306;
	private static final String STATE_OPEN_CONVERSATION = "state_open_conversation";
	private static final String STATE_PANEL_OPEN = "state_panel_open";
	private static final String STATE_PENDING_URI = "state_pending_uri";
	private static final String STATE_FIRST_VISIBLE = "first_visible";
	private static final String STATE_OFFSET_FROM_TOP = "offset_from_top";

	private String mOpenConversation = null;
	private boolean mPanelOpen = true;
	private AtomicBoolean mShouldPanelBeOpen = new AtomicBoolean(false);
	private Pair<Integer,Integer> mScrollPosition = null;
	final private List<Uri> mPendingImageUris = new ArrayList<>();
	final private List<Uri> mPendingFileUris = new ArrayList<>();
	private Uri mPendingGeoUri = null;
	private boolean forbidProcessingPendings = false;
	private Message mPendingDownloadableMessage = null;

	private boolean conversationWasSelectedByKeyboard = false;

	private View mContentView;

	private List<Conversation> conversationList = new ArrayList<>();
	private Conversation swipedConversation = null;
	private Conversation mSelectedConversation = null;
	private EnhancedListView listView;
	private ConversationFragment mConversationFragment;

	private ArrayAdapter<Conversation> listAdapter;

	private boolean mActivityPaused = false;
	private AtomicBoolean mRedirected = new AtomicBoolean(false);
	private Pair<Integer, Intent> mPostponedActivityResult;
	private boolean mUnprocessedNewIntent = false;

	public Conversation getSelectedConversation() {
		return this.mSelectedConversation;
	}

	public void setSelectedConversation(Conversation conversation) {
		this.mSelectedConversation = conversation;
	}

	public void showConversationsOverview() {
		if (mContentView instanceof SlidingPaneLayout) {
			SlidingPaneLayout mSlidingPaneLayout = (SlidingPaneLayout) mContentView;
			mShouldPanelBeOpen.set(true);
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
			mShouldPanelBeOpen.set(false);
			mSlidingPaneLayout.closePane();
		}
	}

	public boolean isConversationsOverviewHideable() {
		return mContentView instanceof SlidingPaneLayout;
	}

	public boolean isConversationsOverviewVisable() {
		if (mContentView instanceof SlidingPaneLayout) {
			return mShouldPanelBeOpen.get();
		} else {
			return true;
		}
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (savedInstanceState != null) {
			mOpenConversation = savedInstanceState.getString(STATE_OPEN_CONVERSATION, null);
			mPanelOpen = savedInstanceState.getBoolean(STATE_PANEL_OPEN, true);
			int pos = savedInstanceState.getInt(STATE_FIRST_VISIBLE, -1);
			int offset = savedInstanceState.getInt(STATE_OFFSET_FROM_TOP, 1);
			if (pos >= 0 && offset <= 0) {
				Log.d(Config.LOGTAG,"retrieved scroll position from instanceState "+pos+":"+offset);
				mScrollPosition = new Pair<>(pos,offset);
			} else {
				mScrollPosition = null;
			}
			String pending = savedInstanceState.getString(STATE_PENDING_URI, null);
			if (pending != null) {
				Log.d(Config.LOGTAG,"ConversationsActivity.onCreate() - restoring pending image uri");
				mPendingImageUris.clear();
				mPendingImageUris.add(Uri.parse(pending));
			}
		}

		setContentView(R.layout.fragment_conversations_overview);

		this.mConversationFragment = new ConversationFragment();
		FragmentTransaction transaction = getFragmentManager().beginTransaction();
		transaction.replace(R.id.selected_conversation, this.mConversationFragment, "conversation");
		transaction.commit();

		listView = (EnhancedListView) findViewById(R.id.list);
		this.listAdapter = new ConversationAdapter(this, conversationList);
		listView.setAdapter(this.listAdapter);

		final ActionBar actionBar = getActionBar();
		if (actionBar != null) {
			actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_TITLE);
		}

		listView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View clickedView,
									int position, long arg3) {
				if (getSelectedConversation() != conversationList.get(position)) {
					setSelectedConversation(conversationList.get(position));
					ConversationActivity.this.mConversationFragment.reInit(getSelectedConversation());
					conversationWasSelectedByKeyboard = false;
				}
				hideConversationsOverview();
				openConversation();
			}
		});

		listView.setDismissCallback(new EnhancedListView.OnDismissCallback() {

			@Override
			public EnhancedListView.Undoable onDismiss(final EnhancedListView enhancedListView, final int position) {

				final int index = listView.getFirstVisiblePosition();
				View v = listView.getChildAt(0);
				final int top = (v == null) ? 0 : (v.getTop() - listView.getPaddingTop());

				try {
					swipedConversation = listAdapter.getItem(position);
				} catch (IndexOutOfBoundsException e) {
					return null;
				}
				listAdapter.remove(swipedConversation);
				xmppConnectionService.markRead(swipedConversation);

				final boolean formerlySelected = (getSelectedConversation() == swipedConversation);
				if (position == 0 && listAdapter.getCount() == 0) {
					endConversation(swipedConversation, false, true);
					return null;
				} else if (formerlySelected) {
					setSelectedConversation(listAdapter.getItem(0));
					ConversationActivity.this.mConversationFragment
							.reInit(getSelectedConversation());
				}

				return new EnhancedListView.Undoable() {

					@Override
					public void undo() {
						listAdapter.insert(swipedConversation, position);
						if (formerlySelected) {
							setSelectedConversation(swipedConversation);
							ConversationActivity.this.mConversationFragment
									.reInit(getSelectedConversation());
						}
						swipedConversation = null;
						listView.setSelectionFromTop(index + (listView.getChildCount() < position ? 1 : 0), top);
					}

					@Override
					public void discard() {
						if (!swipedConversation.isRead()
								&& swipedConversation.getMode() == Conversation.MODE_SINGLE) {
							swipedConversation = null;
							return;
						}
						endConversation(swipedConversation, false, false);
						swipedConversation = null;
					}

					@Override
					public String getTitle() {
						if (swipedConversation.getMode() == Conversation.MODE_MULTI) {
							return getResources().getString(R.string.title_undo_swipe_out_muc);
						} else {
							return getResources().getString(R.string.title_undo_swipe_out_conversation);
						}
					}
				};
			}
		});
		listView.enableSwipeToDismiss();
		listView.setSwipingLayout(R.id.swipeable_item);
		listView.setUndoStyle(EnhancedListView.UndoStyle.SINGLE_POPUP);
		listView.setUndoHideDelay(5000);
		listView.setRequireTouchBeforeDismiss(false);

		mContentView = findViewById(R.id.content_view_spl);
		if (mContentView == null) {
			mContentView = findViewById(R.id.content_view_ll);
		}
		if (mContentView instanceof SlidingPaneLayout) {
			SlidingPaneLayout mSlidingPaneLayout = (SlidingPaneLayout) mContentView;
			mSlidingPaneLayout.setShadowResource(R.drawable.es_slidingpane_shadow);
			mSlidingPaneLayout.setSliderFadeColor(0);
			mSlidingPaneLayout.setPanelSlideListener(new PanelSlideListener() {

				@Override
				public void onPanelOpened(View arg0) {
					mShouldPanelBeOpen.set(true);
					updateActionBarTitle();
					invalidateOptionsMenu();
					hideKeyboard();
					if (xmppConnectionServiceBound) {
						xmppConnectionService.getNotificationService().setOpenConversation(null);
					}
					closeContextMenu();
				}

				@Override
				public void onPanelClosed(View arg0) {
					mShouldPanelBeOpen.set(false);
					listView.discardUndo();
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
				if ((ab.getDisplayOptions() & ActionBar.DISPLAY_HOME_AS_UP) != ActionBar.DISPLAY_HOME_AS_UP) {
					ab.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_TITLE);
				}
				if (conversation.getMode() == Conversation.MODE_SINGLE || useSubjectToIdentifyConference()) {
					ab.setTitle(conversation.getName());
				} else {
					ab.setTitle(conversation.getJid().toBareJid().toString());
				}
			} else {
				if ((ab.getDisplayOptions() & ActionBar.DISPLAY_HOME_AS_UP) == ActionBar.DISPLAY_HOME_AS_UP) {
					ab.setDisplayOptions(ActionBar.DISPLAY_SHOW_TITLE);
				}
				ab.setTitle(R.string.app_name);
			}
		}
	}

	private void openConversation() {
		this.updateActionBarTitle();
		this.invalidateOptionsMenu();
		if (xmppConnectionServiceBound) {
			final Conversation conversation = getSelectedConversation();
			xmppConnectionService.getNotificationService().setOpenConversation(conversation);
			sendReadMarkerIfNecessary(conversation);
		}
		listAdapter.notifyDataSetChanged();
	}

	public void sendReadMarkerIfNecessary(final Conversation conversation) {
		if (!mActivityPaused && !mUnprocessedNewIntent && conversation != null) {
			xmppConnectionService.sendReadMarker(conversation);
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

		if (isConversationsOverviewVisable() && isConversationsOverviewHideable()) {
			menuArchive.setVisible(false);
			menuMucDetails.setVisible(false);
			menuContactDetails.setVisible(false);
			menuSecure.setVisible(false);
			menuInviteContact.setVisible(false);
			menuAttach.setVisible(false);
			menuClearHistory.setVisible(false);
			menuMute.setVisible(false);
			menuUnmute.setVisible(false);
		} else {
			menuAdd.setVisible(!isConversationsOverviewHideable());
			if (this.getSelectedConversation() != null) {
				if (this.getSelectedConversation().getNextEncryption() != Message.ENCRYPTION_NONE) {
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
						menuSecure.setIcon(R.drawable.ic_lock_white_24dp);
					} else {
						menuSecure.setIcon(R.drawable.ic_action_secure);
					}
				}
				if (this.getSelectedConversation().getMode() == Conversation.MODE_MULTI) {
					menuContactDetails.setVisible(false);
					menuAttach.setVisible(getSelectedConversation().getAccount().httpUploadAvailable() && getSelectedConversation().getMucOptions().participating());
					menuInviteContact.setVisible(getSelectedConversation().getMucOptions().canInvite());
					menuSecure.setVisible((Config.supportOpenPgp() || Config.supportOmemo()) && Config.multipleEncryptionChoices()); //only if pgp is supported we have a choice
				} else {
					menuContactDetails.setVisible(!this.getSelectedConversation().withSelf());
					menuMucDetails.setVisible(false);
					menuSecure.setVisible(Config.multipleEncryptionChoices());
					menuInviteContact.setVisible(xmppConnectionService != null && xmppConnectionService.findConferenceServer(getSelectedConversation().getAccount()) != null);
				}
				if (this.getSelectedConversation().isMuted()) {
					menuMute.setVisible(false);
				} else {
					menuUnmute.setVisible(false);
				}
			}
		}
		if (Config.supportOmemo()) {
			new Handler().post(new Runnable() {
				@Override
				public void run() {
					View view = findViewById(R.id.action_security);
					if (view != null) {
						view.setOnLongClickListener(new View.OnLongClickListener() {
							@Override
							public boolean onLongClick(View v) {
								return quickOmemoDebugger(getSelectedConversation());
							}
						});
					}
				}
			});
		}
		return super.onCreateOptionsMenu(menu);
	}

	private boolean quickOmemoDebugger(Conversation c) {
		if (c != null) {
			boolean single = c.getMode() == Conversation.MODE_SINGLE;
			AxolotlService axolotlService = c.getAccount().getAxolotlService();
			Pair<AxolotlService.AxolotlCapability,Jid> capabilityJidPair = axolotlService.isConversationAxolotlCapableDetailed(c);
			switch (capabilityJidPair.first) {
				case MISSING_PRESENCE:
					Toast.makeText(ConversationActivity.this,single ? getString(R.string.missing_presence_subscription) : getString(R.string.missing_presence_subscription_with_x,capabilityJidPair.second.toBareJid().toString()),Toast.LENGTH_SHORT).show();
					return true;
				case MISSING_KEYS:
					Toast.makeText(ConversationActivity.this,single ? getString(R.string.missing_omemo_keys) : getString(R.string.missing_keys_from_x,capabilityJidPair.second.toBareJid().toString()),Toast.LENGTH_SHORT).show();
					return true;
				case WRONG_CONFIGURATION:
					Toast.makeText(ConversationActivity.this,R.string.wrong_conference_configuration, Toast.LENGTH_SHORT).show();
					return true;
				case NO_MEMBERS:
					Toast.makeText(ConversationActivity.this,R.string.this_conference_has_no_members, Toast.LENGTH_SHORT).show();
					return true;
			}
		}
		return false;
	}

	protected void selectPresenceToAttachFile(final int attachmentChoice, final int encryption) {
		final Conversation conversation = getSelectedConversation();
		final Account account = conversation.getAccount();
		final OnPresenceSelected callback = new OnPresenceSelected() {

			@Override
			public void onPresenceSelected() {
				Intent intent = new Intent();
				boolean chooser = false;
				String fallbackPackageId = null;
				switch (attachmentChoice) {
					case ATTACHMENT_CHOICE_CHOOSE_IMAGE:
						intent.setAction(Intent.ACTION_GET_CONTENT);
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
							intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
						}
						intent.setType("image/*");
						chooser = true;
						break;
					case ATTACHMENT_CHOICE_TAKE_PHOTO:
						Uri uri = xmppConnectionService.getFileBackend().getTakePhotoUri();
						intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
						intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
						intent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);
						intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
						mPendingImageUris.clear();
						mPendingImageUris.add(uri);
						break;
					case ATTACHMENT_CHOICE_CHOOSE_FILE:
						chooser = true;
						intent.setType("*/*");
						intent.addCategory(Intent.CATEGORY_OPENABLE);
						intent.setAction(Intent.ACTION_GET_CONTENT);
						break;
					case ATTACHMENT_CHOICE_RECORD_VOICE:
						intent.setAction(MediaStore.Audio.Media.RECORD_SOUND_ACTION);
						fallbackPackageId = "eu.siacs.conversations.voicerecorder";
						break;
					case ATTACHMENT_CHOICE_LOCATION:
						intent.setAction("eu.siacs.conversations.location.request");
						fallbackPackageId = "eu.siacs.conversations.sharelocation";
						break;
				}
				if (intent.resolveActivity(getPackageManager()) != null) {
					if (chooser) {
						startActivityForResult(
								Intent.createChooser(intent, getString(R.string.perform_action_with)),
								attachmentChoice);
					} else {
						startActivityForResult(intent, attachmentChoice);
					}
				} else if (fallbackPackageId != null) {
					startActivity(getInstallApkIntent(fallbackPackageId));
				}
			}
		};
		if ((account.httpUploadAvailable() || attachmentChoice == ATTACHMENT_CHOICE_LOCATION) && encryption != Message.ENCRYPTION_OTR) {
			conversation.setNextCounterpart(null);
			callback.onPresenceSelected();
		} else {
			selectPresence(conversation, callback);
		}
	}

	private Intent getInstallApkIntent(final String packageId) {
		Intent intent = new Intent(Intent.ACTION_VIEW);
		intent.setData(Uri.parse("market://details?id=" + packageId));
		if (intent.resolveActivity(getPackageManager()) != null) {
			return intent;
		} else {
			intent.setData(Uri.parse("http://play.google.com/store/apps/details?id=" + packageId));
			return intent;
		}
	}

	public void attachFile(final int attachmentChoice) {
		if (attachmentChoice != ATTACHMENT_CHOICE_LOCATION) {
			if (!Config.ONLY_INTERNAL_STORAGE && !hasStoragePermission(attachmentChoice)) {
				return;
			}
		}
		switch (attachmentChoice) {
			case ATTACHMENT_CHOICE_LOCATION:
				getPreferences().edit().putString("recently_used_quick_action", "location").apply();
				break;
			case ATTACHMENT_CHOICE_RECORD_VOICE:
				getPreferences().edit().putString("recently_used_quick_action", "voice").apply();
				break;
			case ATTACHMENT_CHOICE_TAKE_PHOTO:
				getPreferences().edit().putString("recently_used_quick_action", "photo").apply();
				break;
			case ATTACHMENT_CHOICE_CHOOSE_IMAGE:
				getPreferences().edit().putString("recently_used_quick_action", "picture").apply();
				break;
		}
		final Conversation conversation = getSelectedConversation();
		final int encryption = conversation.getNextEncryption();
		final int mode = conversation.getMode();
		if (encryption == Message.ENCRYPTION_PGP) {
			if (hasPgp()) {
				if (mode == Conversation.MODE_SINGLE && conversation.getContact().getPgpKeyId() != 0) {
					xmppConnectionService.getPgpEngine().hasKey(
							conversation.getContact(),
							new UiCallback<Contact>() {

								@Override
								public void userInputRequried(PendingIntent pi, Contact contact) {
									ConversationActivity.this.runIntent(pi, attachmentChoice);
								}

								@Override
								public void success(Contact contact) {
									selectPresenceToAttachFile(attachmentChoice, encryption);
								}

								@Override
								public void error(int error, Contact contact) {
									replaceToast(getString(error));
								}
							});
				} else if (mode == Conversation.MODE_MULTI && conversation.getMucOptions().pgpKeysInUse()) {
					if (!conversation.getMucOptions().everybodyHasKeys()) {
						Toast warning = Toast
								.makeText(this,
										R.string.missing_public_keys,
										Toast.LENGTH_LONG);
						warning.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
						warning.show();
					}
					selectPresenceToAttachFile(attachmentChoice, encryption);
				} else {
					final ConversationFragment fragment = (ConversationFragment) getFragmentManager()
							.findFragmentByTag("conversation");
					if (fragment != null) {
						fragment.showNoPGPKeyDialog(false,
								new OnClickListener() {

									@Override
									public void onClick(DialogInterface dialog,
														int which) {
										conversation.setNextEncryption(Message.ENCRYPTION_NONE);
										xmppConnectionService.updateConversation(conversation);
										selectPresenceToAttachFile(attachmentChoice, Message.ENCRYPTION_NONE);
									}
								});
					}
				}
			} else {
				showInstallPgpDialog();
			}
		} else {
			if (encryption != Message.ENCRYPTION_AXOLOTL || !trustKeysIfNeeded(REQUEST_TRUST_KEYS_MENU, attachmentChoice)) {
				selectPresenceToAttachFile(attachmentChoice, encryption);
			}
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
		if (grantResults.length > 0)
			if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				if (requestCode == REQUEST_START_DOWNLOAD) {
					if (this.mPendingDownloadableMessage != null) {
						startDownloadable(this.mPendingDownloadableMessage);
					}
				} else {
					attachFile(requestCode);
				}
			} else {
				Toast.makeText(this, R.string.no_storage_permission, Toast.LENGTH_SHORT).show();
			}
	}

	public void startDownloadable(Message message) {
		if (!Config.ONLY_INTERNAL_STORAGE && !hasStoragePermission(ConversationActivity.REQUEST_START_DOWNLOAD)) {
			this.mPendingDownloadableMessage = message;
			return;
		}
		Transferable transferable = message.getTransferable();
		if (transferable != null) {
			if (!transferable.start()) {
				Toast.makeText(this, R.string.not_connected_try_again, Toast.LENGTH_SHORT).show();
			}
		} else if (message.treatAsDownloadable() != Message.Decision.NEVER) {
			xmppConnectionService.getHttpConnectionManager().createNewDownloadConnection(message, true);
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
					switchToContactDetails(getSelectedConversation().getContact());
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
		endConversation(conversation, true, true);
	}

	public void endConversation(Conversation conversation, boolean showOverview, boolean reinit) {
		if (showOverview) {
			showConversationsOverview();
		}
		xmppConnectionService.archiveConversation(conversation);
		if (reinit) {
			if (conversationList.size() > 0) {
				setSelectedConversation(conversationList.get(0));
				this.mConversationFragment.reInit(getSelectedConversation());
			} else {
				setSelectedConversation(null);
				if (mRedirected.compareAndSet(false, true)) {
					Intent intent = new Intent(this, StartConversationActivity.class);
					intent.putExtra("init", true);
					startActivity(intent);
					finish();
				}
			}
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
		if (new Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION).resolveActivity(getPackageManager()) == null) {
			attachFilePopup.getMenu().findItem(R.id.attach_record_voice).setVisible(false);
		}
		if (new Intent("eu.siacs.conversations.location.request").resolveActivity(getPackageManager()) == null) {
			attachFilePopup.getMenu().findItem(R.id.attach_location).setVisible(false);
		}
		attachFilePopup.setOnMenuItemClickListener(new OnMenuItemClickListener() {

			@Override
			public boolean onMenuItemClick(MenuItem item) {
				switch (item.getItemId()) {
					case R.id.attach_choose_picture:
						attachFile(ATTACHMENT_CHOICE_CHOOSE_IMAGE);
						break;
					case R.id.attach_take_picture:
						attachFile(ATTACHMENT_CHOICE_TAKE_PHOTO);
						break;
					case R.id.attach_choose_file:
						attachFile(ATTACHMENT_CHOICE_CHOOSE_FILE);
						break;
					case R.id.attach_record_voice:
						attachFile(ATTACHMENT_CHOICE_RECORD_VOICE);
						break;
					case R.id.attach_location:
						attachFile(ATTACHMENT_CHOICE_LOCATION);
						break;
				}
				return false;
			}
		});
		attachFilePopup.show();
	}

	public void verifyOtrSessionDialog(final Conversation conversation, View view) {
		if (!conversation.hasValidOtrSession() || conversation.getOtrSession().getSessionStatus() != SessionStatus.ENCRYPTED) {
			Toast.makeText(this, R.string.otr_session_not_started, Toast.LENGTH_LONG).show();
			return;
		}
		if (view == null) {
			return;
		}
		PopupMenu popup = new PopupMenu(this, view);
		popup.inflate(R.menu.verification_choices);
		popup.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			@Override
			public boolean onMenuItemClick(MenuItem menuItem) {
				Intent intent = new Intent(ConversationActivity.this, VerifyOTRActivity.class);
				intent.setAction(VerifyOTRActivity.ACTION_VERIFY_CONTACT);
				intent.putExtra("contact", conversation.getContact().getJid().toBareJid().toString());
				intent.putExtra(EXTRA_ACCOUNT, conversation.getAccount().getJid().toBareJid().toString());
				switch (menuItem.getItemId()) {
					case R.id.scan_fingerprint:
						intent.putExtra("mode", VerifyOTRActivity.MODE_SCAN_FINGERPRINT);
						break;
					case R.id.ask_question:
						intent.putExtra("mode", VerifyOTRActivity.MODE_ASK_QUESTION);
						break;
					case R.id.manual_verification:
						intent.putExtra("mode", VerifyOTRActivity.MODE_MANUAL_VERIFICATION);
						break;
				}
				startActivity(intent);
				return true;
			}
		});
		popup.show();
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
								if (conversation.getAccount().getPgpSignature() != null) {
									conversation.setNextEncryption(Message.ENCRYPTION_PGP);
									item.setChecked(true);
								} else {
									announcePgp(conversation.getAccount(), conversation, onOpenPGPKeyPublished);
								}
							} else {
								showInstallPgpDialog();
							}
							break;
						case R.id.encryption_choice_axolotl:
							Log.d(Config.LOGTAG, AxolotlService.getLogprefix(conversation.getAccount())
									+ "Enabled axolotl for Contact " + conversation.getContact().getJid());
							conversation.setNextEncryption(Message.ENCRYPTION_AXOLOTL);
							item.setChecked(true);
							break;
						default:
							conversation.setNextEncryption(Message.ENCRYPTION_NONE);
							break;
					}
					xmppConnectionService.updateConversation(conversation);
					fragment.updateChatMsgHint();
					invalidateOptionsMenu();
					refreshUi();
					return true;
				}
			});
			popup.inflate(R.menu.encryption_choices);
			MenuItem otr = popup.getMenu().findItem(R.id.encryption_choice_otr);
			MenuItem none = popup.getMenu().findItem(R.id.encryption_choice_none);
			MenuItem pgp = popup.getMenu().findItem(R.id.encryption_choice_pgp);
			MenuItem axolotl = popup.getMenu().findItem(R.id.encryption_choice_axolotl);
			pgp.setVisible(Config.supportOpenPgp());
			none.setVisible(Config.supportUnencrypted() || conversation.getMode() == Conversation.MODE_MULTI);
			otr.setVisible(Config.supportOtr());
			axolotl.setVisible(Config.supportOmemo());
			if (conversation.getMode() == Conversation.MODE_MULTI) {
				otr.setVisible(false);
			}
			if (!conversation.getAccount().getAxolotlService().isConversationAxolotlCapable(conversation)) {
				axolotl.setEnabled(false);
			}
			switch (conversation.getNextEncryption()) {
				case Message.ENCRYPTION_NONE:
					none.setChecked(true);
					break;
				case Message.ENCRYPTION_OTR:
					otr.setChecked(true);
					break;
				case Message.ENCRYPTION_PGP:
					pgp.setChecked(true);
					break;
				case Message.ENCRYPTION_AXOLOTL:
					axolotl.setChecked(true);
					break;
				default:
					none.setChecked(true);
					break;
			}
			popup.show();
		}
	}

	protected void muteConversationDialog(final Conversation conversation) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.disable_notifications);
		final int[] durations = getResources().getIntArray(R.array.mute_options_durations);
		builder.setItems(R.array.mute_options_descriptions,
				new OnClickListener() {

					@Override
					public void onClick(final DialogInterface dialog, final int which) {
						final long till;
						if (durations[which] == -1) {
							till = Long.MAX_VALUE;
						} else {
							till = System.currentTimeMillis() + (durations[which] * 1000);
						}
						conversation.setMutedTill(till);
						ConversationActivity.this.xmppConnectionService.updateConversation(conversation);
						updateConversationList();
						ConversationActivity.this.mConversationFragment.updateMessages();
						invalidateOptionsMenu();
					}
				});
		builder.create().show();
	}

	public void unmuteConversation(final Conversation conversation) {
		conversation.setMutedTill(0);
		this.xmppConnectionService.updateConversation(conversation);
		updateConversationList();
		ConversationActivity.this.mConversationFragment.updateMessages();
		invalidateOptionsMenu();
	}

	@Override
	public void onBackPressed() {
		if (!isConversationsOverviewVisable()) {
			showConversationsOverview();
		} else {
			super.onBackPressed();
		}
	}

	@Override
	public boolean onKeyUp(int key, KeyEvent event) {
		int rotation = getWindowManager().getDefaultDisplay().getRotation();
		final int upKey;
		final int downKey;
		switch (rotation) {
			case Surface.ROTATION_90:
				upKey = KeyEvent.KEYCODE_DPAD_LEFT;
				downKey = KeyEvent.KEYCODE_DPAD_RIGHT;
				break;
			case Surface.ROTATION_180:
				upKey = KeyEvent.KEYCODE_DPAD_DOWN;
				downKey = KeyEvent.KEYCODE_DPAD_UP;
				break;
			case Surface.ROTATION_270:
				upKey = KeyEvent.KEYCODE_DPAD_RIGHT;
				downKey = KeyEvent.KEYCODE_DPAD_LEFT;
				break;
			case Surface.ROTATION_0:
			default:
				upKey = KeyEvent.KEYCODE_DPAD_UP;
				downKey = KeyEvent.KEYCODE_DPAD_DOWN;
		}
		final boolean modifier = event.isCtrlPressed() || (event.getMetaState() & KeyEvent.META_ALT_LEFT_ON) != 0;
		if (modifier && key == KeyEvent.KEYCODE_TAB && isConversationsOverviewHideable()) {
			toggleConversationsOverview();
			return true;
		} else if (modifier && key == KeyEvent.KEYCODE_SPACE) {
			startActivity(new Intent(this, StartConversationActivity.class));
			return true;
		} else if (modifier && key == downKey) {
			if (isConversationsOverviewHideable() && !isConversationsOverviewVisable()) {
				showConversationsOverview();
				;
			}
			return selectDownConversation();
		} else if (modifier && key == upKey) {
			if (isConversationsOverviewHideable() && !isConversationsOverviewVisable()) {
				showConversationsOverview();
			}
			return selectUpConversation();
		} else if (modifier && key == KeyEvent.KEYCODE_1) {
			return openConversationByIndex(0);
		} else if (modifier && key == KeyEvent.KEYCODE_2) {
			return openConversationByIndex(1);
		} else if (modifier && key == KeyEvent.KEYCODE_3) {
			return openConversationByIndex(2);
		} else if (modifier && key == KeyEvent.KEYCODE_4) {
			return openConversationByIndex(3);
		} else if (modifier && key == KeyEvent.KEYCODE_5) {
			return openConversationByIndex(4);
		} else if (modifier && key == KeyEvent.KEYCODE_6) {
			return openConversationByIndex(5);
		} else if (modifier && key == KeyEvent.KEYCODE_7) {
			return openConversationByIndex(6);
		} else if (modifier && key == KeyEvent.KEYCODE_8) {
			return openConversationByIndex(7);
		} else if (modifier && key == KeyEvent.KEYCODE_9) {
			return openConversationByIndex(8);
		} else if (modifier && key == KeyEvent.KEYCODE_0) {
			return openConversationByIndex(9);
		} else {
			return super.onKeyUp(key, event);
		}
	}

	private void toggleConversationsOverview() {
		if (isConversationsOverviewVisable()) {
			hideConversationsOverview();
			if (mConversationFragment != null) {
				mConversationFragment.setFocusOnInputField();
			}
		} else {
			showConversationsOverview();
		}
	}

	private boolean selectUpConversation() {
		if (this.mSelectedConversation != null) {
			int index = this.conversationList.indexOf(this.mSelectedConversation);
			if (index > 0) {
				return openConversationByIndex(index - 1);
			}
		}
		return false;
	}

	private boolean selectDownConversation() {
		if (this.mSelectedConversation != null) {
			int index = this.conversationList.indexOf(this.mSelectedConversation);
			if (index != -1 && index < this.conversationList.size() - 1) {
				return openConversationByIndex(index + 1);
			}
		}
		return false;
	}

	private boolean openConversationByIndex(int index) {
		try {
			this.conversationWasSelectedByKeyboard = true;
			setSelectedConversation(this.conversationList.get(index));
			this.mConversationFragment.reInit(getSelectedConversation());
			if (index > listView.getLastVisiblePosition() - 1 || index < listView.getFirstVisiblePosition() + 1) {
				this.listView.setSelection(index);
			}
			openConversation();
			return true;
		} catch (IndexOutOfBoundsException e) {
			return false;
		}
	}

	@Override
	protected void onNewIntent(final Intent intent) {
		if (intent != null && ACTION_VIEW_CONVERSATION.equals(intent.getAction())) {
			mOpenConversation = null;
			mUnprocessedNewIntent = true;
			if (xmppConnectionServiceBound) {
				handleViewConversationIntent(intent);
				intent.setAction(Intent.ACTION_MAIN);
			} else {
				setIntent(intent);
			}
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		this.mRedirected.set(false);
		if (this.xmppConnectionServiceBound) {
			this.onBackendConnected();
		}
		if (conversationList.size() >= 1) {
			this.onConversationUpdate();
		}
	}

	@Override
	public void onPause() {
		listView.discardUndo();
		super.onPause();
		this.mActivityPaused = true;
	}

	@Override
	public void onResume() {
		super.onResume();
		final int theme = findTheme();
		final boolean usingEnterKey = usingEnterKey();
		if (this.mTheme != theme || usingEnterKey != mUsingEnterKey) {
			recreate();
		}
		this.mActivityPaused = false;


		if (!isConversationsOverviewVisable() || !isConversationsOverviewHideable()) {
			sendReadMarkerIfNecessary(getSelectedConversation());
		}

	}

	@Override
	public void onSaveInstanceState(final Bundle savedInstanceState) {
		Conversation conversation = getSelectedConversation();
		if (conversation != null) {
			savedInstanceState.putString(STATE_OPEN_CONVERSATION, conversation.getUuid());
			Pair<Integer,Integer> scrollPosition = mConversationFragment.getScrollPosition();
			if (scrollPosition != null) {
				savedInstanceState.putInt(STATE_FIRST_VISIBLE, scrollPosition.first);
				savedInstanceState.putInt(STATE_OFFSET_FROM_TOP, scrollPosition.second);
			}
		} else {
			savedInstanceState.remove(STATE_OPEN_CONVERSATION);
		}
		savedInstanceState.putBoolean(STATE_PANEL_OPEN, isConversationsOverviewVisable());
		if (this.mPendingImageUris.size() >= 1) {
			Log.d(Config.LOGTAG,"ConversationsActivity.onSaveInstanceState() - saving pending image uri");
			savedInstanceState.putString(STATE_PENDING_URI, this.mPendingImageUris.get(0).toString());
		} else {
			savedInstanceState.remove(STATE_PENDING_URI);
		}
		super.onSaveInstanceState(savedInstanceState);
	}

	private void clearPending() {
		mPendingImageUris.clear();
		mPendingFileUris.clear();
		mPendingGeoUri = null;
		mPostponedActivityResult = null;
	}

	@Override
	void onBackendConnected() {
		this.xmppConnectionService.getNotificationService().setIsInForeground(true);
		updateConversationList();

		if (mPendingConferenceInvite != null) {
			if (mPendingConferenceInvite.execute(this)) {
				mToast = Toast.makeText(this, R.string.creating_conference, Toast.LENGTH_LONG);
				mToast.show();
			}
			mPendingConferenceInvite = null;
		}

		final Intent intent = getIntent();

		if (xmppConnectionService.getAccounts().size() == 0) {
			if (mRedirected.compareAndSet(false, true)) {
				if (Config.X509_VERIFICATION) {
					startActivity(new Intent(this, ManageAccountActivity.class));
				} else if (Config.MAGIC_CREATE_DOMAIN != null) {
					startActivity(new Intent(this, WelcomeActivity.class));
				} else {
					Intent editAccount = new Intent(this, EditAccountActivity.class);
					editAccount.putExtra("init",true);
					startActivity(editAccount);
				}
				finish();
			}
		} else if (conversationList.size() <= 0) {
			if (mRedirected.compareAndSet(false, true)) {
				Account pendingAccount = xmppConnectionService.getPendingAccount();
				if (pendingAccount == null) {
					Intent startConversationActivity = new Intent(this, StartConversationActivity.class);
					intent.putExtra("init", true);
					startActivity(startConversationActivity);
				} else {
					switchToAccount(pendingAccount, true);
				}
				finish();
			}
		} else if (selectConversationByUuid(mOpenConversation)) {
			if (mPanelOpen) {
				showConversationsOverview();
			} else {
				if (isConversationsOverviewHideable()) {
					openConversation();
					updateActionBarTitle(true);
				}
			}
			if (this.mConversationFragment.reInit(getSelectedConversation())) {
				Log.d(Config.LOGTAG,"setting scroll position on fragment");
				this.mConversationFragment.setScrollPosition(mScrollPosition);
			}
			mOpenConversation = null;
		} else if (intent != null && ACTION_VIEW_CONVERSATION.equals(intent.getAction())) {
			clearPending();
			handleViewConversationIntent(intent);
			intent.setAction(Intent.ACTION_MAIN);
		} else if (getSelectedConversation() == null) {
			showConversationsOverview();
			clearPending();
			setSelectedConversation(conversationList.get(0));
			this.mConversationFragment.reInit(getSelectedConversation());
		} else {
			this.mConversationFragment.messageListAdapter.updatePreferences();
			this.mConversationFragment.messagesView.invalidateViews();
			this.mConversationFragment.setupIme();
		}

		if (this.mPostponedActivityResult != null) {
			this.onActivityResult(mPostponedActivityResult.first, RESULT_OK, mPostponedActivityResult.second);
		}

		final boolean stopping;
		if (Build.VERSION.SDK_INT >= 17) {
			stopping = isFinishing() || isDestroyed();
		} else {
			stopping = isFinishing();
		}

		if (!forbidProcessingPendings) {
			for (Iterator<Uri> i = mPendingImageUris.iterator(); i.hasNext(); i.remove()) {
				Uri foo = i.next();
				Log.d(Config.LOGTAG,"ConversationsActivity.onBackendConnected() - attaching image to conversations. stopping="+Boolean.toString(stopping));
				attachImageToConversation(getSelectedConversation(), foo);
			}

			for (Iterator<Uri> i = mPendingFileUris.iterator(); i.hasNext(); i.remove()) {
				Log.d(Config.LOGTAG,"ConversationsActivity.onBackendConnected() - attaching file to conversations. stopping="+Boolean.toString(stopping));
				attachFileToConversation(getSelectedConversation(), i.next());
			}

			if (mPendingGeoUri != null) {
				attachLocationToConversation(getSelectedConversation(), mPendingGeoUri);
				mPendingGeoUri = null;
			}
		}
		forbidProcessingPendings = false;

		if (!ExceptionHelper.checkForCrash(this, this.xmppConnectionService)) {
			openBatteryOptimizationDialogIfNeeded();
		}
		if (isConversationsOverviewVisable() && isConversationsOverviewHideable()) {
			xmppConnectionService.getNotificationService().setOpenConversation(null);
		} else {
			xmppConnectionService.getNotificationService().setOpenConversation(getSelectedConversation());
		}
	}

	private void handleViewConversationIntent(final Intent intent) {
		final String uuid = intent.getStringExtra(CONVERSATION);
		final String downloadUuid = intent.getStringExtra(EXTRA_DOWNLOAD_UUID);
		final String text = intent.getStringExtra(TEXT);
		final String nick = intent.getStringExtra(NICK);
		final boolean pm = intent.getBooleanExtra(PRIVATE_MESSAGE, false);
		if (selectConversationByUuid(uuid)) {
			this.mConversationFragment.reInit(getSelectedConversation());
			if (nick != null) {
				if (pm) {
					Jid jid = getSelectedConversation().getJid();
					try {
						Jid next = Jid.fromParts(jid.getLocalpart(), jid.getDomainpart(), nick);
						this.mConversationFragment.privateMessageWith(next);
					} catch (final InvalidJidException ignored) {
						//do nothing
					}
				} else {
					this.mConversationFragment.highlightInConference(nick);
				}
			} else {
				this.mConversationFragment.appendText(text);
			}
			hideConversationsOverview();
			mUnprocessedNewIntent = false;
			openConversation();
			if (mContentView instanceof SlidingPaneLayout) {
				updateActionBarTitle(true); //fixes bug where slp isn't properly closed yet
			}
			if (downloadUuid != null) {
				final Message message = mSelectedConversation.findMessageWithFileAndUuid(downloadUuid);
				if (message != null) {
					startDownloadable(message);
				}
			}
		} else {
			mUnprocessedNewIntent = false;
		}
	}

	private boolean selectConversationByUuid(String uuid) {
		if (uuid == null) {
			return false;
		}
		for (Conversation aConversationList : conversationList) {
			if (aConversationList.getUuid().equals(uuid)) {
				setSelectedConversation(aConversationList);
				return true;
			}
		}
		return false;
	}

	@Override
	protected void unregisterListeners() {
		super.unregisterListeners();
		xmppConnectionService.getNotificationService().setOpenConversation(null);
	}

	@SuppressLint("NewApi")
	private static List<Uri> extractUriFromIntent(final Intent intent) {
		List<Uri> uris = new ArrayList<>();
		if (intent == null) {
			return uris;
		}
		Uri uri = intent.getData();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 && uri == null) {
			final ClipData clipData = intent.getClipData();
			if (clipData != null) {
				for (int i = 0; i < clipData.getItemCount(); ++i) {
					uris.add(clipData.getItemAt(i).getUri());
				}
			}
		} else {
			uris.add(uri);
		}
		return uris;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode == RESULT_OK) {
			if (requestCode == REQUEST_DECRYPT_PGP) {
				mConversationFragment.onActivityResult(requestCode, resultCode, data);
			} else if (requestCode == REQUEST_CHOOSE_PGP_ID) {
				// the user chose OpenPGP for encryption and selected his key in the PGP provider
				if (xmppConnectionServiceBound) {
					if (data.getExtras().containsKey(OpenPgpApi.EXTRA_SIGN_KEY_ID)) {
						// associate selected PGP keyId with the account
						mSelectedConversation.getAccount().setPgpSignId(data.getExtras().getLong(OpenPgpApi.EXTRA_SIGN_KEY_ID));
						// we need to announce the key as described in XEP-027
						announcePgp(mSelectedConversation.getAccount(), null, onOpenPGPKeyPublished);
					} else {
						choosePgpSignId(mSelectedConversation.getAccount());
					}
					this.mPostponedActivityResult = null;
				} else {
					this.mPostponedActivityResult = new Pair<>(requestCode, data);
				}
			} else if (requestCode == REQUEST_ANNOUNCE_PGP) {
				if (xmppConnectionServiceBound) {
					announcePgp(mSelectedConversation.getAccount(), mSelectedConversation, onOpenPGPKeyPublished);
					this.mPostponedActivityResult = null;
				} else {
					this.mPostponedActivityResult = new Pair<>(requestCode, data);
				}
			} else if (requestCode == ATTACHMENT_CHOICE_CHOOSE_IMAGE) {
				mPendingImageUris.clear();
				mPendingImageUris.addAll(extractUriFromIntent(data));
				if (xmppConnectionServiceBound) {
					for (Iterator<Uri> i = mPendingImageUris.iterator(); i.hasNext(); i.remove()) {
						Log.d(Config.LOGTAG,"ConversationsActivity.onActivityResult() - attaching image to conversations. CHOOSE_IMAGE");
						attachImageToConversation(getSelectedConversation(), i.next());
					}
				}
			} else if (requestCode == ATTACHMENT_CHOICE_CHOOSE_FILE || requestCode == ATTACHMENT_CHOICE_RECORD_VOICE) {
				final List<Uri> uris = extractUriFromIntent(data);
				final Conversation c = getSelectedConversation();
				final OnPresenceSelected callback = new OnPresenceSelected() {
					@Override
					public void onPresenceSelected() {
						mPendingFileUris.clear();
						mPendingFileUris.addAll(uris);
						if (xmppConnectionServiceBound) {
							for (Iterator<Uri> i = mPendingFileUris.iterator(); i.hasNext(); i.remove()) {
								Log.d(Config.LOGTAG,"ConversationsActivity.onActivityResult() - attaching file to conversations. CHOOSE_FILE/RECORD_VOICE");
								attachFileToConversation(c, i.next());
							}
						}
					}
				};
				if (c == null || c.getMode() == Conversation.MODE_MULTI
						|| FileBackend.allFilesUnderSize(this, uris, getMaxHttpUploadSize(c))
						|| c.getNextEncryption() == Message.ENCRYPTION_OTR) {
					callback.onPresenceSelected();
				} else {
					selectPresence(c, callback);
				}
			} else if (requestCode == ATTACHMENT_CHOICE_TAKE_PHOTO) {
				if (mPendingImageUris.size() == 1) {
					Uri uri = FileBackend.getIndexableTakePhotoUri(mPendingImageUris.get(0));
					mPendingImageUris.set(0, uri);
					if (xmppConnectionServiceBound) {
						Log.d(Config.LOGTAG,"ConversationsActivity.onActivityResult() - attaching image to conversations. TAKE_PHOTO");
						attachImageToConversation(getSelectedConversation(), uri);
						mPendingImageUris.clear();
					}
					if (!Config.ONLY_INTERNAL_STORAGE) {
						Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
						intent.setData(uri);
						sendBroadcast(intent);
					}
				} else {
					mPendingImageUris.clear();
				}
			} else if (requestCode == ATTACHMENT_CHOICE_LOCATION) {
				double latitude = data.getDoubleExtra("latitude", 0);
				double longitude = data.getDoubleExtra("longitude", 0);
				this.mPendingGeoUri = Uri.parse("geo:" + String.valueOf(latitude) + "," + String.valueOf(longitude));
				if (xmppConnectionServiceBound) {
					attachLocationToConversation(getSelectedConversation(), mPendingGeoUri);
					this.mPendingGeoUri = null;
				}
			} else if (requestCode == REQUEST_TRUST_KEYS_TEXT || requestCode == REQUEST_TRUST_KEYS_MENU) {
				this.forbidProcessingPendings = !xmppConnectionServiceBound;
				if (xmppConnectionServiceBound) {
					mConversationFragment.onActivityResult(requestCode, resultCode, data);
					this.mPostponedActivityResult = null;
				} else {
					this.mPostponedActivityResult = new Pair<>(requestCode, data);
				}

			}
		} else {
			mPendingImageUris.clear();
			mPendingFileUris.clear();
			if (requestCode == ConversationActivity.REQUEST_DECRYPT_PGP) {
				mConversationFragment.onActivityResult(requestCode, resultCode, data);
			}
			if (requestCode == REQUEST_BATTERY_OP) {
				setNeverAskForBatteryOptimizationsAgain();
			}
		}
	}

	private long getMaxHttpUploadSize(Conversation conversation) {
		final XmppConnection connection = conversation.getAccount().getXmppConnection();
		return connection == null ? -1 : connection.getFeatures().getMaxHttpUploadSize();
	}

	private void setNeverAskForBatteryOptimizationsAgain() {
		getPreferences().edit().putBoolean("show_battery_optimization", false).commit();
	}

	private void openBatteryOptimizationDialogIfNeeded() {
		if (hasAccountWithoutPush()
				&& isOptimizingBattery()
				&& getPreferences().getBoolean("show_battery_optimization", true)) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(R.string.battery_optimizations_enabled);
			builder.setMessage(R.string.battery_optimizations_enabled_dialog);
			builder.setPositiveButton(R.string.next, new OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
					Uri uri = Uri.parse("package:" + getPackageName());
					intent.setData(uri);
					try {
						startActivityForResult(intent, REQUEST_BATTERY_OP);
					} catch (ActivityNotFoundException e) {
						Toast.makeText(ConversationActivity.this, R.string.device_does_not_support_battery_op, Toast.LENGTH_SHORT).show();
					}
				}
			});
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
				builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
					@Override
					public void onDismiss(DialogInterface dialog) {
						setNeverAskForBatteryOptimizationsAgain();
					}
				});
			}
			builder.create().show();
		}
	}

	private boolean hasAccountWithoutPush() {
		for(Account account : xmppConnectionService.getAccounts()) {
			if (account.getStatus() != Account.State.DISABLED
					&& !xmppConnectionService.getPushManagementService().availableAndUseful(account)) {
				return true;
			}
		}
		return false;
	}

	private void attachLocationToConversation(Conversation conversation, Uri uri) {
		if (conversation == null) {
			return;
		}
		xmppConnectionService.attachLocationToConversation(conversation,uri, new UiCallback<Message>() {

			@Override
			public void success(Message message) {
				xmppConnectionService.sendMessage(message);
			}

			@Override
			public void error(int errorCode, Message object) {

			}

			@Override
			public void userInputRequried(PendingIntent pi, Message object) {

			}
		});
	}

	private void attachFileToConversation(Conversation conversation, Uri uri) {
		if (conversation == null) {
			return;
		}
		final Toast prepareFileToast = Toast.makeText(getApplicationContext(),getText(R.string.preparing_file), Toast.LENGTH_LONG);
		prepareFileToast.show();
		xmppConnectionService.attachFileToConversation(conversation, uri, new UiInformableCallback<Message>() {
			@Override
			public void inform(final String text) {
				hidePrepareFileToast(prepareFileToast);
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						replaceToast(text);
					}
				});
			}

			@Override
			public void success(Message message) {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						hideToast();
					}
				});
				hidePrepareFileToast(prepareFileToast);
				xmppConnectionService.sendMessage(message);
			}

			@Override
			public void error(final int errorCode, Message message) {
				hidePrepareFileToast(prepareFileToast);
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						replaceToast(getString(errorCode));
					}
				});

			}

			@Override
			public void userInputRequried(PendingIntent pi, Message message) {
				hidePrepareFileToast(prepareFileToast);
			}
		});
	}

	public void attachImageToConversation(Uri uri) {
		this.attachImageToConversation(getSelectedConversation(), uri);
	}

	private void attachImageToConversation(Conversation conversation, Uri uri) {
		if (conversation == null) {
			return;
		}
		final Toast prepareFileToast = Toast.makeText(getApplicationContext(),getText(R.string.preparing_image), Toast.LENGTH_LONG);
		prepareFileToast.show();
		xmppConnectionService.attachImageToConversation(conversation, uri,
				new UiCallback<Message>() {

					@Override
					public void userInputRequried(PendingIntent pi, Message object) {
						hidePrepareFileToast(prepareFileToast);
					}

					@Override
					public void success(Message message) {
						hidePrepareFileToast(prepareFileToast);
						xmppConnectionService.sendMessage(message);
					}

					@Override
					public void error(final int error, Message message) {
						hidePrepareFileToast(prepareFileToast);
						runOnUiThread(new Runnable() {
							@Override
							public void run() {
								replaceToast(getString(error));
							}
						});
					}
				});
	}

	private void hidePrepareFileToast(final Toast prepareFileToast) {
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
		if (swipedConversation != null) {
			if (swipedConversation.isRead()) {
				conversationList.remove(swipedConversation);
			} else {
				listView.discardUndo();
			}
		}
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
					public void userInputRequried(PendingIntent pi,Message message) {
						ConversationActivity.this.runIntent(pi,ConversationActivity.REQUEST_SEND_MESSAGE);
					}

					@Override
					public void success(Message message) {
						message.setEncryption(Message.ENCRYPTION_DECRYPTED);
						xmppConnectionService.sendMessage(message);
						if (mConversationFragment != null) {
							mConversationFragment.messageSent();
						}
					}

					@Override
					public void error(final int error, Message message) {
						runOnUiThread(new Runnable() {
							@Override
							public void run() {
								Toast.makeText(ConversationActivity.this,
										R.string.unable_to_connect_to_keychain,
										Toast.LENGTH_SHORT
								).show();
							}
						});
						if (mConversationFragment != null) {
							mConversationFragment.doneSendingPgpMessage();
						}
					}
				});
	}

	public boolean useSendButtonToIndicateStatus() {
		return getPreferences().getBoolean("send_button_status", false);
	}

	public boolean indicateReceived() {
		return getPreferences().getBoolean("indicate_received", false);
	}

	public boolean useGreenBackground() {
		return getPreferences().getBoolean("use_green_background",true);
	}

	protected boolean trustKeysIfNeeded(int requestCode) {
		return trustKeysIfNeeded(requestCode, ATTACHMENT_CHOICE_INVALID);
	}

	protected boolean trustKeysIfNeeded(int requestCode, int attachmentChoice) {
		AxolotlService axolotlService = mSelectedConversation.getAccount().getAxolotlService();
		final List<Jid> targets = axolotlService.getCryptoTargets(mSelectedConversation);
		boolean hasUnaccepted = !mSelectedConversation.getAcceptedCryptoTargets().containsAll(targets);
		boolean hasUndecidedOwn = !axolotlService.getKeysWithTrust(FingerprintStatus.createActiveUndecided()).isEmpty();
		boolean hasUndecidedContacts = !axolotlService.getKeysWithTrust(FingerprintStatus.createActiveUndecided(), targets).isEmpty();
		boolean hasPendingKeys = !axolotlService.findDevicesWithoutSession(mSelectedConversation).isEmpty();
		boolean hasNoTrustedKeys = axolotlService.anyTargetHasNoTrustedKeys(targets);
		if(hasUndecidedOwn || hasUndecidedContacts || hasPendingKeys || hasNoTrustedKeys || hasUnaccepted) {
			axolotlService.createSessionsIfNeeded(mSelectedConversation);
			Intent intent = new Intent(getApplicationContext(), TrustKeysActivity.class);
			String[] contacts = new String[targets.size()];
			for(int i = 0; i < contacts.length; ++i) {
				contacts[i] = targets.get(i).toString();
			}
			intent.putExtra("contacts", contacts);
			intent.putExtra(EXTRA_ACCOUNT, mSelectedConversation.getAccount().getJid().toBareJid().toString());
			intent.putExtra("choice", attachmentChoice);
			intent.putExtra("conversation",mSelectedConversation.getUuid());
			startActivityForResult(intent, requestCode);
			return true;
		} else {
			return false;
		}
	}

	@Override
	protected void refreshUiReal() {
		updateConversationList();
		if (conversationList.size() > 0) {
			if (!this.mConversationFragment.isAdded()) {
				Log.d(Config.LOGTAG,"fragment NOT added to activity. detached="+Boolean.toString(mConversationFragment.isDetached()));
			}
			ConversationActivity.this.mConversationFragment.updateMessages();
			updateActionBarTitle();
			invalidateOptionsMenu();
		} else {
			Log.d(Config.LOGTAG,"not updating conversations fragment because conversations list size was 0");
		}
	}

	@Override
	public void onAccountUpdate() {
		this.refreshUi();
	}

	@Override
	public void onConversationUpdate() {
		this.refreshUi();
	}

	@Override
	public void onRosterUpdate() {
		this.refreshUi();
	}

	@Override
	public void OnUpdateBlocklist(Status status) {
		this.refreshUi();
	}

	public void unblockConversation(final Blockable conversation) {
		xmppConnectionService.sendUnblockRequest(conversation);
	}

	public boolean enterIsSend() {
		return getPreferences().getBoolean("enter_is_send",getResources().getBoolean(R.bool.enter_is_send));
	}

	@Override
	public void onShowErrorToast(final int resId) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(ConversationActivity.this,resId,Toast.LENGTH_SHORT).show();
			}
		});
	}

	public boolean highlightSelectedConversations() {
		return !isConversationsOverviewHideable() || this.conversationWasSelectedByKeyboard;
	}
}
