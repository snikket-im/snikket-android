package eu.siacs.conversations.ui;

import static eu.siacs.conversations.utils.StringUtils.changed;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import de.gultsch.common.Linkify;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityMucDetailsBinding;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Conversational;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.MucOptions.User;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.services.XmppConnectionService.OnConversationUpdate;
import eu.siacs.conversations.services.XmppConnectionService.OnMucRosterUpdate;
import eu.siacs.conversations.ui.adapter.MediaAdapter;
import eu.siacs.conversations.ui.adapter.UserPreviewAdapter;
import eu.siacs.conversations.ui.interfaces.OnMediaLoaded;
import eu.siacs.conversations.ui.text.FixedURLSpan;
import eu.siacs.conversations.ui.util.Attachment;
import eu.siacs.conversations.ui.util.AvatarWorkerTask;
import eu.siacs.conversations.ui.util.GridManager;
import eu.siacs.conversations.ui.util.MenuDoubleTabUtil;
import eu.siacs.conversations.ui.util.MucConfiguration;
import eu.siacs.conversations.ui.util.MucDetailsContextMenuHelper;
import eu.siacs.conversations.ui.util.SoftKeyboardUtils;
import eu.siacs.conversations.utils.AccountUtils;
import eu.siacs.conversations.utils.Compatibility;
import eu.siacs.conversations.utils.StylingHelper;
import eu.siacs.conversations.utils.XmppUri;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.manager.BookmarkManager;
import eu.siacs.conversations.xmpp.manager.MultiUserChatManager;
import im.conversations.android.model.Bookmark;
import im.conversations.android.xmpp.model.muc.Affiliation;
import im.conversations.android.xmpp.model.muc.Role;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import me.drakeet.support.toast.ToastCompat;

public class ConferenceDetailsActivity extends XmppActivity
        implements OnConversationUpdate,
                OnMucRosterUpdate,
                XmppConnectionService.OnAffiliationChanged,
                TextWatcher,
                OnMediaLoaded {
    public static final String ACTION_VIEW_MUC = "view_muc";

    private Conversation mConversation;
    private ActivityMucDetailsBinding binding;
    private MediaAdapter mMediaAdapter;
    private UserPreviewAdapter mUserPreviewAdapter;
    private String uuid = null;

    private boolean mAdvancedMode = false;

    private FutureCallback<Void> renameCallback =
            new FutureCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    displayToast(getString(R.string.your_nick_has_been_changed));
                    updateView();
                }

                @Override
                public void onFailure(Throwable t) {

                    // TODO check for NickInUseException and NickInvalid exception

                }
            };

    public static void open(final Activity activity, final Conversation conversation) {
        Intent intent = new Intent(activity, ConferenceDetailsActivity.class);
        intent.setAction(ConferenceDetailsActivity.ACTION_VIEW_MUC);
        intent.putExtra("uuid", conversation.getUuid());
        activity.startActivity(intent);
    }

    private final OnClickListener mNotifyStatusClickListener =
            new OnClickListener() {
                @Override
                public void onClick(View v) {
                    final MaterialAlertDialogBuilder builder =
                            new MaterialAlertDialogBuilder(ConferenceDetailsActivity.this);
                    builder.setTitle(R.string.pref_notification_settings);
                    String[] choices = {
                        getString(R.string.notify_on_all_messages),
                        getString(R.string.notify_only_when_highlighted),
                        getString(R.string.notify_never)
                    };
                    final AtomicInteger choice;
                    if (mConversation.getLongAttribute(Conversation.ATTRIBUTE_MUTED_TILL, 0)
                            == Long.MAX_VALUE) {
                        choice = new AtomicInteger(2);
                    } else {
                        choice = new AtomicInteger(mConversation.alwaysNotify() ? 0 : 1);
                    }
                    builder.setSingleChoiceItems(
                            choices, choice.get(), (dialog, which) -> choice.set(which));
                    builder.setNegativeButton(R.string.cancel, null);
                    builder.setPositiveButton(
                            R.string.ok,
                            (dialog, which) -> {
                                if (choice.get() == 2) {
                                    mConversation.setMutedTill(Long.MAX_VALUE);
                                } else {
                                    mConversation.setMutedTill(0);
                                    mConversation.setAttribute(
                                            Conversation.ATTRIBUTE_ALWAYS_NOTIFY,
                                            String.valueOf(choice.get() == 0));
                                }
                                xmppConnectionService.updateConversation(mConversation);
                                updateView();
                            });
                    builder.create().show();
                }
            };

    private final FutureCallback<Void> onConfigurationPushed =
            new FutureCallback<Void>() {

                @Override
                public void onSuccess(Void result) {
                    displayToast(getString(R.string.modified_conference_options));
                }

                @Override
                public void onFailure(Throwable t) {
                    displayToast(getString(R.string.could_not_modify_conference_options));
                }
            };

    private final OnClickListener mChangeConferenceSettings =
            new OnClickListener() {
                @Override
                public void onClick(View v) {
                    final MucOptions mucOptions = mConversation.getMucOptions();
                    final MaterialAlertDialogBuilder builder =
                            new MaterialAlertDialogBuilder(ConferenceDetailsActivity.this);
                    MucConfiguration configuration =
                            MucConfiguration.get(
                                    ConferenceDetailsActivity.this, mAdvancedMode, mucOptions);
                    builder.setTitle(configuration.title);
                    final boolean[] values = configuration.values;
                    builder.setMultiChoiceItems(
                            configuration.names,
                            values,
                            (dialog, which, isChecked) -> values[which] = isChecked);
                    builder.setNegativeButton(R.string.cancel, null);
                    builder.setPositiveButton(
                            R.string.confirm,
                            (dialog, which) -> {
                                final var options = configuration.toBundle(values);
                                final var future =
                                        mConversation
                                                .getAccount()
                                                .getXmppConnection()
                                                .getManager(MultiUserChatManager.class)
                                                .pushConfiguration(mConversation, options);
                                Futures.addCallback(
                                        future,
                                        onConfigurationPushed,
                                        ContextCompat.getMainExecutor(getApplication()));
                            });
                    builder.create().show();
                }
            };

    @Override
    public void onConversationUpdate() {
        refreshUi();
    }

    @Override
    public void onMucRosterUpdate() {
        refreshUi();
    }

    @Override
    protected void refreshUiReal() {
        updateView();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_muc_details);
        Activities.setStatusAndNavigationBarColors(this, binding.getRoot());
        this.binding.changeConferenceButton.setOnClickListener(this.mChangeConferenceSettings);
        setSupportActionBar(binding.toolbar);
        configureActionBar(getSupportActionBar());
        this.binding.editNickButton.setOnClickListener(
                v ->
                        quickEdit(
                                mConversation.getMucOptions().getActualNick(),
                                R.string.nickname,
                                value -> {
                                    if (mConversation.getMucOptions().createJoinJid(value)
                                            == null) {
                                        return getString(R.string.invalid_muc_nick);
                                    }
                                    final var future =
                                            mConversation
                                                    .getAccount()
                                                    .getXmppConnection()
                                                    .getManager(MultiUserChatManager.class)
                                                    .changeUsername(mConversation, value);
                                    Futures.addCallback(
                                            future,
                                            renameCallback,
                                            ContextCompat.getMainExecutor(this));
                                    return null;
                                }));
        this.mAdvancedMode = getPreferences().getBoolean("advanced_muc_mode", false);
        this.binding.mucInfoMore.setVisibility(this.mAdvancedMode ? View.VISIBLE : View.GONE);
        this.binding.notificationStatusButton.setOnClickListener(this.mNotifyStatusClickListener);
        this.binding.yourPhoto.setOnClickListener(
                v -> {
                    final MucOptions mucOptions = mConversation.getMucOptions();
                    if (!mucOptions.hasVCards()) {
                        Toast.makeText(
                                        this,
                                        R.string.host_does_not_support_group_chat_avatars,
                                        Toast.LENGTH_SHORT)
                                .show();
                        return;
                    }
                    if (!mucOptions.getSelf().ranks(Affiliation.OWNER)) {
                        Toast.makeText(
                                        this,
                                        R.string.only_the_owner_can_change_group_chat_avatar,
                                        Toast.LENGTH_SHORT)
                                .show();
                        return;
                    }
                    final Intent intent =
                            new Intent(this, PublishGroupChatProfilePictureActivity.class);
                    intent.putExtra("uuid", mConversation.getUuid());
                    startActivity(intent);
                });
        this.binding.editMucNameButton.setContentDescription(
                getString(R.string.edit_name_and_topic));
        this.binding.editMucNameButton.setOnClickListener(this::onMucEditButtonClicked);
        this.binding.mucEditTitle.addTextChangedListener(this);
        this.binding.mucEditSubject.addTextChangedListener(this);
        this.binding.mucEditSubject.addTextChangedListener(
                new StylingHelper.MessageEditorStyler(this.binding.mucEditSubject));
        this.mMediaAdapter = new MediaAdapter(this, R.dimen.media_size);
        this.mUserPreviewAdapter = new UserPreviewAdapter();
        this.binding.media.setAdapter(mMediaAdapter);
        this.binding.users.setAdapter(mUserPreviewAdapter);
        GridManager.setupLayoutManager(this, this.binding.media, R.dimen.media_size);
        GridManager.setupLayoutManager(this, this.binding.users, R.dimen.media_size);
        this.binding.invite.setOnClickListener(v -> inviteToConversation(mConversation));
        this.binding.showUsers.setOnClickListener(
                v -> {
                    Intent intent = new Intent(this, MucUsersActivity.class);
                    intent.putExtra("uuid", mConversation.getUuid());
                    startActivity(intent);
                });
    }

    @Override
    public void onStart() {
        super.onStart();
        binding.mediaWrapper.setVisibility(
                Compatibility.hasStoragePermission(this) ? View.VISIBLE : View.GONE);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (MenuDoubleTabUtil.shouldIgnoreTap()) {
            return false;
        }
        switch (menuItem.getItemId()) {
            case android.R.id.home:
                finish();
                break;
            case R.id.action_share_http:
                shareLink(true);
                break;
            case R.id.action_share_uri:
                shareLink(false);
                break;
            case R.id.action_save_as_bookmark:
                saveAsBookmark();
                break;
            case R.id.action_destroy_room:
                destroyRoom();
                break;
            case R.id.action_advanced_mode:
                this.mAdvancedMode = !menuItem.isChecked();
                menuItem.setChecked(this.mAdvancedMode);
                getPreferences().edit().putBoolean("advanced_muc_mode", mAdvancedMode).apply();
                final boolean online =
                        mConversation != null && mConversation.getMucOptions().online();
                this.binding.mucInfoMore.setVisibility(
                        this.mAdvancedMode && online ? View.VISIBLE : View.GONE);
                invalidateOptionsMenu();
                updateView();
                break;
            case R.id.action_custom_notifications:
                if (mConversation != null) {
                    configureCustomNotifications(mConversation);
                }
                break;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    private void configureCustomNotifications(final Conversation conversation) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R
                || conversation.getMode() != Conversational.MODE_MULTI) {
            return;
        }
        final var shortcut =
                xmppConnectionService
                        .getShortcutService()
                        .getShortcutInfo(conversation.getMucOptions());
        configureCustomNotification(shortcut);
    }

    @Override
    public boolean onContextItemSelected(@NonNull final MenuItem item) {
        final User user = mUserPreviewAdapter.getSelectedUser();
        if (user == null) {
            Toast.makeText(this, R.string.unable_to_perform_this_action, Toast.LENGTH_SHORT).show();
            return true;
        }
        if (!MucDetailsContextMenuHelper.onContextItemSelected(
                item, mUserPreviewAdapter.getSelectedUser(), this)) {
            return super.onContextItemSelected(item);
        }
        return true;
    }

    public void onMucEditButtonClicked(View v) {
        if (this.binding.mucEditor.getVisibility() == View.GONE) {
            final MucOptions mucOptions = mConversation.getMucOptions();
            this.binding.mucEditor.setVisibility(View.VISIBLE);
            this.binding.mucDisplay.setVisibility(View.GONE);
            this.binding.editMucNameButton.setImageResource(R.drawable.ic_cancel_24dp);
            this.binding.editMucNameButton.setContentDescription(getString(R.string.cancel));
            final String name = mucOptions.getName();
            this.binding.mucEditTitle.setText("");
            final boolean owner = mucOptions.getSelf().ranks(Affiliation.OWNER);
            if (owner || Bookmark.printableValue(name)) {
                this.binding.mucEditTitle.setVisibility(View.VISIBLE);
                if (name != null) {
                    this.binding.mucEditTitle.append(name);
                }
            } else {
                this.binding.mucEditTitle.setVisibility(View.GONE);
            }
            this.binding.mucEditTitle.setEnabled(owner);
            final String subject = mucOptions.getSubject();
            this.binding.mucEditSubject.setText("");
            if (subject != null) {
                this.binding.mucEditSubject.append(subject);
            }
            this.binding.mucEditSubject.setEnabled(mucOptions.canChangeSubject());
            if (!owner) {
                this.binding.mucEditSubject.requestFocus();
            }
        } else {
            String subject =
                    this.binding.mucEditSubject.isEnabled()
                            ? this.binding.mucEditSubject.getEditableText().toString().trim()
                            : null;
            String name =
                    this.binding.mucEditTitle.isEnabled()
                            ? this.binding.mucEditTitle.getEditableText().toString().trim()
                            : null;
            onMucInfoUpdated(subject, name);
            SoftKeyboardUtils.hideSoftKeyboard(this);
            hideEditor();
        }
    }

    private void hideEditor() {
        this.binding.mucEditor.setVisibility(View.GONE);
        this.binding.mucDisplay.setVisibility(View.VISIBLE);
        this.binding.editMucNameButton.setImageResource(R.drawable.ic_edit_24dp);
        this.binding.editMucNameButton.setContentDescription(
                getString(R.string.edit_name_and_topic));
    }

    private void onMucInfoUpdated(String subject, String name) {
        final var account = mConversation.getAccount();
        final MucOptions mucOptions = mConversation.getMucOptions();
        if (mucOptions.canChangeSubject() && changed(mucOptions.getSubject(), subject)) {
            xmppConnectionService.pushSubjectToConference(mConversation, subject);
        }
        if (mucOptions.getSelf().ranks(Affiliation.OWNER) && changed(mucOptions.getName(), name)) {
            final var options =
                    new ImmutableMap.Builder<String, Object>()
                            .put("muc#roomconfig_persistentroom", true)
                            .put("muc#roomconfig_roomname", Strings.nullToEmpty(name))
                            .build();
            final var future =
                    account.getXmppConnection()
                            .getManager(MultiUserChatManager.class)
                            .pushConfiguration(mConversation, options);
            Futures.addCallback(
                    future, onConfigurationPushed, ContextCompat.getMainExecutor(getApplication()));
        }
    }

    @Override
    protected String getShareableUri(boolean http) {
        if (mConversation != null) {
            if (http) {
                return "https://conversations.im/j/"
                        + XmppUri.lameUrlEncode(mConversation.getAddress().asBareJid().toString());
            } else {
                return "xmpp:" + mConversation.getAddress().asBareJid() + "?join";
            }
        } else {
            return null;
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        final MenuItem menuItemSaveBookmark = menu.findItem(R.id.action_save_as_bookmark);
        final MenuItem menuItemAdvancedMode = menu.findItem(R.id.action_advanced_mode);
        final MenuItem menuItemDestroyRoom = menu.findItem(R.id.action_destroy_room);
        menuItemAdvancedMode.setChecked(mAdvancedMode);
        if (mConversation == null) {
            return true;
        }
        menuItemSaveBookmark.setVisible(mConversation.getBookmark() == null);
        menuItemDestroyRoom.setVisible(
                mConversation.getMucOptions().getSelf().ranks(Affiliation.OWNER));
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        final boolean groupChat = mConversation != null && mConversation.isPrivateAndNonAnonymous();
        getMenuInflater().inflate(R.menu.muc_details, menu);
        final MenuItem share = menu.findItem(R.id.action_share);
        share.setVisible(!groupChat);
        final MenuItem destroy = menu.findItem(R.id.action_destroy_room);
        destroy.setTitle(groupChat ? R.string.destroy_room : R.string.destroy_channel);
        AccountUtils.showHideMenuItems(menu);
        final MenuItem customNotifications = menu.findItem(R.id.action_custom_notifications);
        customNotifications.setVisible(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onMediaLoaded(final List<Attachment> attachments) {
        runOnUiThread(
                () -> {
                    final int limit = GridManager.getCurrentColumnCount(binding.media);
                    mMediaAdapter.setAttachments(
                            attachments.subList(0, Math.min(limit, attachments.size())));
                    binding.mediaWrapper.setVisibility(
                            attachments.isEmpty() ? View.GONE : View.VISIBLE);
                });
    }

    protected void saveAsBookmark() {
        final var account = mConversation.getAccount();
        account.getXmppConnection()
                .getManager(BookmarkManager.class)
                .save(mConversation, mConversation.getMucOptions().getName());
    }

    protected void destroyRoom() {
        final var destroyCallBack =
                new FutureCallback<Void>() {

                    @Override
                    public void onSuccess(Void result) {
                        finish();
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        final boolean groupChat =
                                mConversation != null && mConversation.isPrivateAndNonAnonymous();
                        // TODO show toast directly
                        displayToast(
                                getString(
                                        groupChat
                                                ? R.string.could_not_destroy_room
                                                : R.string.could_not_destroy_channel));
                    }
                };
        final boolean groupChat = mConversation != null && mConversation.isPrivateAndNonAnonymous();
        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(groupChat ? R.string.destroy_room : R.string.destroy_channel);
        builder.setMessage(
                groupChat ? R.string.destroy_room_dialog : R.string.destroy_channel_dialog);
        builder.setPositiveButton(
                R.string.ok,
                (dialog, which) -> {
                    final var future = xmppConnectionService.destroyRoom(mConversation);
                    Futures.addCallback(
                            future,
                            destroyCallBack,
                            ContextCompat.getMainExecutor(getApplication()));
                });
        builder.setNegativeButton(R.string.cancel, null);
        final AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

    @Override
    protected void onBackendConnected() {
        if (mPendingConferenceInvite != null) {
            mPendingConferenceInvite.execute(this);
            mPendingConferenceInvite = null;
        }
        if (getIntent().getAction().equals(ACTION_VIEW_MUC)) {
            this.uuid = getIntent().getExtras().getString("uuid");
        }
        if (uuid != null) {
            this.mConversation = xmppConnectionService.findConversationByUuid(uuid);
            if (this.mConversation != null) {
                if (Compatibility.hasStoragePermission(this)) {
                    final int limit = GridManager.getCurrentColumnCount(this.binding.media);
                    xmppConnectionService.getAttachments(this.mConversation, limit, this);
                    this.binding.showMedia.setOnClickListener(
                            (v) -> MediaBrowserActivity.launch(this, mConversation));
                }
                updateView();
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (this.binding.mucEditor.getVisibility() == View.VISIBLE) {
            hideEditor();
        } else {
            super.onBackPressed();
        }
    }

    private void updateView() {
        invalidateOptionsMenu();
        if (mConversation == null) {
            return;
        }
        final MucOptions mucOptions = mConversation.getMucOptions();
        final User self = mucOptions.getSelf();
        final String account = mConversation.getAccount().getJid().asBareJid().toString();
        setTitle(
                mucOptions.isPrivateAndNonAnonymous()
                        ? R.string.action_muc_details
                        : R.string.channel_details);
        this.binding.editMucNameButton.setVisibility(
                (self.ranks(Affiliation.OWNER) || mucOptions.canChangeSubject())
                        ? View.VISIBLE
                        : View.GONE);
        this.binding.detailsAccount.setText(getString(R.string.using_account, account));
        if (mConversation.isPrivateAndNonAnonymous()) {
            this.binding.jid.setText(
                    getString(R.string.hosted_on, mConversation.getAddress().getDomain()));
        } else {
            this.binding.jid.setText(mConversation.getAddress().asBareJid().toString());
        }
        AvatarWorkerTask.loadAvatar(
                mConversation, binding.yourPhoto, R.dimen.avatar_on_details_screen_size);
        String roomName = mucOptions.getName();
        String subject = mucOptions.getSubject();
        final boolean hasTitle;
        if (Bookmark.printableValue(roomName)) {
            this.binding.mucTitle.setText(roomName);
            this.binding.mucTitle.setVisibility(View.VISIBLE);
            hasTitle = true;
        } else if (!Bookmark.printableValue(subject)) {
            this.binding.mucTitle.setText(mConversation.getName());
            hasTitle = true;
            this.binding.mucTitle.setVisibility(View.VISIBLE);
        } else {
            hasTitle = false;
            this.binding.mucTitle.setVisibility(View.GONE);
        }
        if (Bookmark.printableValue(subject)) {
            final var spannable = new SpannableString(subject);
            StylingHelper.format(spannable, this.binding.mucSubject.getCurrentTextColor());
            Linkify.addLinks(spannable);
            FixedURLSpan.fix(spannable);
            this.binding.mucSubject.setText(spannable);
            this.binding.mucSubject.setTextAppearance(
                    subject.length() > (hasTitle ? 128 : 196)
                            ? com.google.android.material.R.style
                                    .TextAppearance_Material3_BodyMedium
                            : com.google.android.material.R.style
                                    .TextAppearance_Material3_BodyLarge);
            this.binding.mucSubject.setVisibility(View.VISIBLE);
            this.binding.mucSubject.setMovementMethod(LinkMovementMethod.getInstance());
        } else {
            this.binding.mucSubject.setVisibility(View.GONE);
        }
        this.binding.mucYourNick.setText(mucOptions.getActualNick());
        if (mucOptions.online()) {
            this.binding.usersWrapper.setVisibility(View.VISIBLE);
            this.binding.mucInfoMore.setVisibility(this.mAdvancedMode ? View.VISIBLE : View.GONE);
            this.binding.mucRole.setVisibility(View.VISIBLE);
            this.binding.mucRole.setText(getStatus(self));
            if (mucOptions.getSelf().ranks(Affiliation.OWNER)) {
                this.binding.mucSettings.setVisibility(View.VISIBLE);
                this.binding.mucConferenceType.setText(MucConfiguration.describe(this, mucOptions));
            } else if (!mucOptions.isPrivateAndNonAnonymous() && mucOptions.nonanonymous()) {
                this.binding.mucSettings.setVisibility(View.VISIBLE);
                this.binding.mucConferenceType.setText(
                        R.string.group_chat_will_make_your_jabber_id_public);
            } else {
                this.binding.mucSettings.setVisibility(View.GONE);
            }
            if (mucOptions.mamSupport()) {
                this.binding.mucInfoMam.setText(R.string.server_info_available);
            } else {
                this.binding.mucInfoMam.setText(R.string.server_info_unavailable);
            }
            if (self.ranks(Affiliation.OWNER)) {
                this.binding.changeConferenceButton.setVisibility(View.VISIBLE);
            } else {
                this.binding.changeConferenceButton.setVisibility(View.INVISIBLE);
            }
        } else {
            this.binding.usersWrapper.setVisibility(View.GONE);
            this.binding.mucInfoMore.setVisibility(View.GONE);
            this.binding.mucSettings.setVisibility(View.GONE);
        }

        final long mutedTill = mConversation.getLongAttribute(Conversation.ATTRIBUTE_MUTED_TILL, 0);
        if (mutedTill == Long.MAX_VALUE) {
            this.binding.notificationStatusText.setText(R.string.notify_never);
            this.binding.notificationStatusButton.setImageResource(
                    R.drawable.ic_notifications_off_24dp);
        } else if (System.currentTimeMillis() < mutedTill) {
            this.binding.notificationStatusText.setText(R.string.notify_paused);
            this.binding.notificationStatusButton.setImageResource(
                    R.drawable.ic_notifications_paused_24dp);
        } else if (mConversation.alwaysNotify()) {
            this.binding.notificationStatusText.setText(R.string.notify_on_all_messages);
            this.binding.notificationStatusButton.setImageResource(
                    R.drawable.ic_notifications_24dp);
        } else {
            this.binding.notificationStatusText.setText(R.string.notify_only_when_highlighted);
            this.binding.notificationStatusButton.setImageResource(
                    R.drawable.ic_notifications_none_24dp);
        }
        final List<User> users = mucOptions.getUsers();
        Collections.sort(
                users,
                (a, b) -> {
                    if (b.outranks(a.getAffiliation())) {
                        return 1;
                    } else if (a.outranks(b.getAffiliation())) {
                        return -1;
                    } else {
                        if (a.getAvatar() != null && b.getAvatar() == null) {
                            return -1;
                        } else if (a.getAvatar() == null && b.getAvatar() != null) {
                            return 1;
                        } else {
                            return a.getComparableName().compareToIgnoreCase(b.getComparableName());
                        }
                    }
                });
        this.binding.users.post(
                () -> {
                    final var list =
                            MucOptions.sub(users, GridManager.getCurrentColumnCount(binding.users));
                    this.mUserPreviewAdapter.submitList(list);
                });
        this.binding.invite.setVisibility(mucOptions.canInvite() ? View.VISIBLE : View.GONE);
        this.binding.showUsers.setVisibility(users.size() > 0 ? View.VISIBLE : View.GONE);
        this.binding.showUsers.setText(
                getResources().getQuantityString(R.plurals.view_users, users.size(), users.size()));
        this.binding.usersWrapper.setVisibility(
                users.size() > 0 || mucOptions.canInvite() ? View.VISIBLE : View.GONE);
        if (users.size() == 0) {
            this.binding.noUsersHints.setText(
                    mucOptions.isPrivateAndNonAnonymous()
                            ? R.string.no_users_hint_group_chat
                            : R.string.no_users_hint_channel);
            this.binding.noUsersHints.setVisibility(View.VISIBLE);
        } else {
            this.binding.noUsersHints.setVisibility(View.GONE);
        }
    }

    public static String getStatus(Context context, User user, final boolean advanced) {
        if (advanced) {
            return String.format(
                    "%s (%s)",
                    context.getString(affiliationToStringRes(user.getAffiliation())),
                    context.getString(roleToStringRes(user.getRole())));
        } else {
            return context.getString(affiliationToStringRes(user.getAffiliation()));
        }
    }

    private static @StringRes int affiliationToStringRes(final Affiliation affiliation) {
        return switch (affiliation) {
            case OWNER -> R.string.owner;
            case ADMIN -> R.string.admin;
            case MEMBER -> R.string.member;
            case NONE -> R.string.no_affiliation;
            case OUTCAST -> R.string.outcast;
        };
    }

    private static @StringRes int roleToStringRes(final Role role) {
        return switch (role) {
            case MODERATOR -> R.string.moderator;
            case VISITOR -> R.string.visitor;
            case PARTICIPANT -> R.string.participant;
            case NONE -> R.string.no_role;
        };
    }

    private String getStatus(User user) {
        return getStatus(this, user, mAdvancedMode);
    }

    @Override
    public void onAffiliationChangedSuccessful(Jid jid) {
        refreshUi();
    }

    @Override
    public void onAffiliationChangeFailed(Jid jid, int resId) {
        displayToast(getString(resId, jid.asBareJid().toString()));
    }

    private void displayToast(final String msg) {
        runOnUiThread(
                () -> {
                    if (isFinishing()) {
                        return;
                    }
                    ToastCompat.makeText(this, msg, Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {}

    @Override
    public void afterTextChanged(Editable s) {
        if (mConversation == null) {
            return;
        }
        final MucOptions mucOptions = mConversation.getMucOptions();
        if (this.binding.mucEditor.getVisibility() == View.VISIBLE) {
            boolean subjectChanged =
                    changed(
                            binding.mucEditSubject.getEditableText().toString(),
                            mucOptions.getSubject());
            boolean nameChanged =
                    changed(
                            binding.mucEditTitle.getEditableText().toString(),
                            mucOptions.getName());
            if (subjectChanged || nameChanged) {
                this.binding.editMucNameButton.setImageResource(R.drawable.ic_save_24dp);
                this.binding.editMucNameButton.setContentDescription(getString(R.string.save));
            } else {
                this.binding.editMucNameButton.setImageResource(R.drawable.ic_cancel_24dp);
                this.binding.editMucNameButton.setContentDescription(getString(R.string.cancel));
            }
        }
    }
}
