package eu.siacs.conversations.ui;

import android.annotation.SuppressLint;
import android.support.v7.app.AlertDialog;
import android.app.FragmentTransaction;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.widget.SlidingPaneLayout;
import android.support.v4.widget.SlidingPaneLayout.PanelSlideListener;
import android.support.v7.app.ActionBar;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Toast;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import de.timroes.android.listview.EnhancedListView;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.crypto.axolotl.FingerprintStatus;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Blockable;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.services.XmppConnectionService.OnAccountUpdate;
import eu.siacs.conversations.services.XmppConnectionService.OnConversationUpdate;
import eu.siacs.conversations.services.XmppConnectionService.OnRosterUpdate;
import eu.siacs.conversations.ui.adapter.ConversationAdapter;
import eu.siacs.conversations.ui.service.EmojiService;
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

	private static final String STATE_OPEN_CONVERSATION = "state_open_conversation";
	private static final String STATE_PANEL_OPEN = "state_panel_open";
	private static final String STATE_PENDING_URI = "state_pending_uri";
	private static final String STATE_FIRST_VISIBLE = "first_visible";
	private static final String STATE_OFFSET_FROM_TOP = "offset_from_top";

	private String mOpenConversation = null;
	private boolean mPanelOpen = true;
	private AtomicBoolean mShouldPanelBeOpen = new AtomicBoolean(false);
	private Pair<Integer, Integer> mScrollPosition = null;
	private boolean forbidProcessingPendings = false;

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
	private boolean mUnprocessedNewIntent = false;

	public Conversation getSelectedConversation() {
		return this.mSelectedConversation;
	}

	public void setSelectedConversation(Conversation conversation) {
		this.mSelectedConversation = conversation;
	}

	public void showConversationsOverview() {
		if (mConversationFragment != null) {
			mConversationFragment.stopScrolling();
		}
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
		new EmojiService(this).init();
		if (savedInstanceState != null) {
			mOpenConversation = savedInstanceState.getString(STATE_OPEN_CONVERSATION, null);
			mPanelOpen = savedInstanceState.getBoolean(STATE_PANEL_OPEN, true);
			int pos = savedInstanceState.getInt(STATE_FIRST_VISIBLE, -1);
			int offset = savedInstanceState.getInt(STATE_OFFSET_FROM_TOP, 1);
			if (pos >= 0 && offset <= 0) {
				Log.d(Config.LOGTAG, "retrieved scroll position from instanceState " + pos + ":" + offset);
				mScrollPosition = new Pair<>(pos, offset);
			} else {
				mScrollPosition = null;
			}
		}

		setContentView(R.layout.fragment_conversations_overview);

		this.mConversationFragment = new ConversationFragment();
		FragmentTransaction transaction = getFragmentManager().beginTransaction();
		transaction.replace(R.id.selected_conversation, this.mConversationFragment, "conversation");
		transaction.commit();

		this.listView = findViewById(R.id.list);
		this.listAdapter = new ConversationAdapter(this, conversationList);
		this.listView.setAdapter(this.listAdapter);
		this.listView.setSwipeDirection(EnhancedListView.SwipeDirection.END);

		final ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_TITLE);
		}

		listView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View clickedView,
			                        int position, long arg3) {
				if (getSelectedConversation() != conversationList.get(position)) {
					ConversationActivity.this.mConversationFragment.stopScrolling();
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
		runOnUiThread(() -> {
			ConversationActivity.this.mConversationFragment.reInit(getSelectedConversation());
			openConversation();
		});
	}

	private void updateActionBarTitle() {
		updateActionBarTitle(isConversationsOverviewHideable() && !isConversationsOverviewVisable());
	}

	private void updateActionBarTitle(boolean titleShouldBeName) {
		final ActionBar ab = getSupportActionBar();
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
		getMenuInflater().inflate(R.menu.activity_conversations, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			showConversationsOverview();
			return true;
		} else if (item.getItemId() == R.id.action_add) {
			startActivity(new Intent(this, StartConversationActivity.class));
			return true;
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
			this.mConversationFragment.stopScrolling();
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
			Pair<Integer, Integer> scrollPosition = mConversationFragment.getScrollPosition();
			if (scrollPosition != null) {
				savedInstanceState.putInt(STATE_FIRST_VISIBLE, scrollPosition.first);
				savedInstanceState.putInt(STATE_OFFSET_FROM_TOP, scrollPosition.second);
			}
		} else {
			savedInstanceState.remove(STATE_OPEN_CONVERSATION);
		}
		savedInstanceState.putBoolean(STATE_PANEL_OPEN, isConversationsOverviewVisable());
		/*if (this.mPendingImageUris.size() >= 1) {
			Log.d(Config.LOGTAG, "ConversationsActivity.onSaveInstanceState() - saving pending image uri");
			savedInstanceState.putString(STATE_PENDING_URI, this.mPendingImageUris.get(0).toString());
		} else {
			savedInstanceState.remove(STATE_PENDING_URI);
		}*/
		super.onSaveInstanceState(savedInstanceState);
	}

	private void clearPending() {
		mConversationFragment.clearPending();
	}

	private void redirectToStartConversationActivity(boolean noAnimation) {
		Account pendingAccount = xmppConnectionService.getPendingAccount();
		if (pendingAccount == null) {
			Intent startConversationActivity = new Intent(this, StartConversationActivity.class);
			startConversationActivity.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
			if (noAnimation) {
				startConversationActivity.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
			}
			startConversationActivity.putExtra("init", true);
			startActivity(startConversationActivity);
			if (noAnimation) {
				overridePendingTransition(0, 0);
			}
		} else {
			switchToAccount(pendingAccount, true);
		}
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
					Intent redirectionIntent = new Intent(this, ManageAccountActivity.class);
					redirectionIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
					startActivity(redirectionIntent);
					overridePendingTransition(0, 0);
				} else if (Config.MAGIC_CREATE_DOMAIN != null) {
					WelcomeActivity.launch(this);
				} else {
					Intent editAccount = new Intent(this, EditAccountActivity.class);
					editAccount.putExtra("init", true);
					editAccount.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
					startActivity(editAccount);
					overridePendingTransition(0, 0);
				}
			}
		} else if (conversationList.size() <= 0) {
			if (mRedirected.compareAndSet(false, true)) {
				redirectToStartConversationActivity(true);
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
				Log.d(Config.LOGTAG, "setting scroll position on fragment");
				this.mConversationFragment.setScrollPosition(mScrollPosition);
			}
			mOpenConversation = null;
		} else if (intent != null && ACTION_VIEW_CONVERSATION.equals(intent.getAction())) {
			clearPending();
			handleViewConversationIntent(intent);
			intent.setAction(Intent.ACTION_MAIN);
		} else if (getSelectedConversation() == null) {
			reInitLatestConversation();
		} else {
			this.mConversationFragment.messageListAdapter.updatePreferences();
			this.mConversationFragment.messagesView.invalidateViews();
			this.mConversationFragment.setupIme();
		}

		mConversationFragment.onBackendConnected();

		if (!ExceptionHelper.checkForCrash(this, this.xmppConnectionService) && !mRedirected.get()) {
			openBatteryOptimizationDialogIfNeeded();
		}
		if (isConversationsOverviewVisable() && isConversationsOverviewHideable()) {
			xmppConnectionService.getNotificationService().setOpenConversation(null);
		} else {
			xmppConnectionService.getNotificationService().setOpenConversation(getSelectedConversation());
		}
	}

	private boolean isStopping() {
		if (Build.VERSION.SDK_INT >= 17) {
			return isFinishing() || isDestroyed();
		} else {
			return isFinishing();
		}
	}

	private void reInitLatestConversation() {
		showConversationsOverview();
		clearPending();
		setSelectedConversation(conversationList.get(0));
		this.mConversationFragment.reInit(getSelectedConversation());
	}

	private void handleViewConversationIntent(final Intent intent) {
		final String uuid = intent.getStringExtra(CONVERSATION);
		final String downloadUuid = intent.getStringExtra(EXTRA_DOWNLOAD_UUID);
		final String text = intent.getStringExtra(TEXT);
		final String nick = intent.getStringExtra(NICK);
		final boolean pm = intent.getBooleanExtra(PRIVATE_MESSAGE, false);
		this.mConversationFragment.stopScrolling();
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
					//startDownloadable(message);
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

	@Override
	protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode != RESULT_OK) {
			if (requestCode == REQUEST_BATTERY_OP) {
				setNeverAskForBatteryOptimizationsAgain();
			}
		}
	}

	public long getMaxHttpUploadSize(Conversation conversation) {
		final XmppConnection connection = conversation.getAccount().getXmppConnection();
		return connection == null ? -1 : connection.getFeatures().getMaxHttpUploadSize();
	}

	private String getBatteryOptimizationPreferenceKey() {
		@SuppressLint("HardwareIds") String device = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
		return "show_battery_optimization" + (device == null ? "" : device);
	}

	private void setNeverAskForBatteryOptimizationsAgain() {
		getPreferences().edit().putBoolean(getBatteryOptimizationPreferenceKey(), false).apply();
	}

	private void openBatteryOptimizationDialogIfNeeded() {
		if (hasAccountWithoutPush()
				&& isOptimizingBattery()
				&& getPreferences().getBoolean(getBatteryOptimizationPreferenceKey(), true)) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(R.string.battery_optimizations_enabled);
			builder.setMessage(R.string.battery_optimizations_enabled_dialog);
			builder.setPositiveButton(R.string.next, (dialog, which) -> {
				Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
				Uri uri = Uri.parse("package:" + getPackageName());
				intent.setData(uri);
				try {
					startActivityForResult(intent, REQUEST_BATTERY_OP);
				} catch (ActivityNotFoundException e) {
					Toast.makeText(ConversationActivity.this, R.string.device_does_not_support_battery_op, Toast.LENGTH_SHORT).show();
				}
			});
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
				builder.setOnDismissListener(dialog -> setNeverAskForBatteryOptimizationsAgain());
			}
			AlertDialog dialog = builder.create();
			dialog.setCanceledOnTouchOutside(false);
			dialog.show();
		}
	}

	private boolean hasAccountWithoutPush() {
		for (Account account : xmppConnectionService.getAccounts()) {
			if (account.getStatus() == Account.State.ONLINE && !xmppConnectionService.getPushManagementService().available(account)) {
				return true;
			}
		}
		return false;
	}

	public void updateConversationList() {
		xmppConnectionService.populateWithOrderedConversations(conversationList);
		if (!conversationList.contains(mSelectedConversation)) {
			mSelectedConversation = null;
		}
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

	public boolean useSendButtonToIndicateStatus() {
		return getPreferences().getBoolean("send_button_status", getResources().getBoolean(R.bool.send_button_status));
	}

	public boolean indicateReceived() {
		return getPreferences().getBoolean("indicate_received", getResources().getBoolean(R.bool.indicate_received));
	}

	public boolean useGreenBackground() {
		return getPreferences().getBoolean("use_green_background", getResources().getBoolean(R.bool.use_green_background));
	}

	@Override
	protected void refreshUiReal() {
		updateConversationList();
		if (conversationList.size() > 0) {
			if (!this.mConversationFragment.isAdded()) {
				Log.d(Config.LOGTAG, "fragment NOT added to activity. detached=" + Boolean.toString(mConversationFragment.isDetached()));
			}
			if (getSelectedConversation() == null) {
				reInitLatestConversation();
			} else {
				ConversationActivity.this.mConversationFragment.updateMessages();
				updateActionBarTitle();
				invalidateOptionsMenu();
			}
		} else {
			if (!isStopping() && mRedirected.compareAndSet(false, true)) {
				redirectToStartConversationActivity(false);
			}
			Log.d(Config.LOGTAG, "not updating conversations fragment because conversations list size was 0");
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


	public boolean enterIsSend() {
		return getPreferences().getBoolean("enter_is_send", getResources().getBoolean(R.bool.enter_is_send));
	}

	@Override
	public void onShowErrorToast(final int resId) {
		runOnUiThread(() -> Toast.makeText(ConversationActivity.this, resId, Toast.LENGTH_SHORT).show());
	}

	public boolean highlightSelectedConversations() {
		return !isConversationsOverviewHideable() || this.conversationWasSelectedByKeyboard;
	}
}
