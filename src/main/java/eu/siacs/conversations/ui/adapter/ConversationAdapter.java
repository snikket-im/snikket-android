package eu.siacs.conversations.ui.adapter;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.widget.ImageViewCompat;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.google.common.base.Optional;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ItemConversationBinding;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Conversational;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.ui.ConversationFragment;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.ui.util.Attachment;
import eu.siacs.conversations.ui.util.AvatarWorkerTask;
import eu.siacs.conversations.utils.IrregularUnicodeDetector;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.jingle.OngoingRtpSession;

import java.util.List;

public class ConversationAdapter
        extends RecyclerView.Adapter<ConversationAdapter.ConversationViewHolder> {

    private final XmppActivity activity;
    private final List<Conversation> conversations;
    private OnConversationClickListener listener;

    public ConversationAdapter(XmppActivity activity, List<Conversation> conversations) {
        this.activity = activity;
        this.conversations = conversations;
    }

    @NonNull
    @Override
    public ConversationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ConversationViewHolder(
                DataBindingUtil.inflate(
                        LayoutInflater.from(parent.getContext()),
                        R.layout.item_conversation,
                        parent,
                        false));
    }

    @Override
    public void onBindViewHolder(@NonNull ConversationViewHolder viewHolder, int position) {
        Conversation conversation = conversations.get(position);
        if (conversation == null) {
            return;
        }
        CharSequence name = conversation.getName();
        if (name instanceof Jid) {
            viewHolder.binding.conversationName.setText(
                    IrregularUnicodeDetector.style(activity, (Jid) name));
        } else {
            viewHolder.binding.conversationName.setText(name);
        }

        if (conversation == ConversationFragment.getConversation(activity)) {
            viewHolder.binding.frame.setBackgroundResource(
                    R.drawable.background_selected_item_conversation);
            // viewHolder.binding.frame.setBackgroundColor(MaterialColors.getColor(viewHolder.binding.frame, com.google.android.material.R.attr.colorSurfaceDim));
        } else {
            viewHolder.binding.frame.setBackgroundColor(
                    MaterialColors.getColor(
                            viewHolder.binding.frame,
                            com.google.android.material.R.attr.colorSurface));
        }

        final Message message = conversation.getLatestMessage();
        final int status = message.getStatus();
        final int unreadCount = conversation.unreadCount();
        final boolean isRead = conversation.isRead();
        final @DrawableRes Integer messageStatusDrawable =
                MessageAdapter.getMessageStatusAsDrawable(message, status);
        if (message.getType() == Message.TYPE_RTP_SESSION) {
            viewHolder.binding.messageStatus.setVisibility(View.GONE);
        } else if (messageStatusDrawable == null) {
            if (status <= Message.STATUS_RECEIVED) {
                viewHolder.binding.messageStatus.setVisibility(View.GONE);
            } else {
                viewHolder.binding.messageStatus.setVisibility(View.INVISIBLE);
            }
        } else {
            viewHolder.binding.messageStatus.setImageResource(messageStatusDrawable);
            if (status == Message.STATUS_SEND_DISPLAYED) {
                viewHolder.binding.messageStatus.setImageResource(R.drawable.ic_done_all_bold_24dp);
                ImageViewCompat.setImageTintList(
                        viewHolder.binding.messageStatus,
                        ColorStateList.valueOf(
                                MaterialColors.getColor(
                                        viewHolder.binding.messageStatus,
                                        com.google.android.material.R.attr.colorPrimary)));
            } else {
                ImageViewCompat.setImageTintList(
                        viewHolder.binding.messageStatus,
                        ColorStateList.valueOf(
                                MaterialColors.getColor(
                                        viewHolder.binding.messageStatus,
                                        com.google.android.material.R.attr.colorControlNormal)));
            }
            viewHolder.binding.messageStatus.setVisibility(View.VISIBLE);
        }
        final Conversation.Draft draft = isRead ? conversation.getDraft() : null;
        if (unreadCount > 0) {
            viewHolder.binding.unreadCount.setVisibility(View.VISIBLE);
            viewHolder.binding.unreadCount.setUnreadCount(unreadCount);
        } else {
            viewHolder.binding.unreadCount.setVisibility(View.GONE);
        }

        if (isRead) {
            viewHolder.binding.conversationName.setTypeface(null, Typeface.NORMAL);
        } else {
            viewHolder.binding.conversationName.setTypeface(null, Typeface.BOLD);
        }

        if (draft != null) {
            viewHolder.binding.conversationLastmsgImg.setVisibility(View.GONE);
            viewHolder.binding.conversationLastmsg.setText(draft.getMessage());
            viewHolder.binding.senderName.setText(R.string.draft);
            viewHolder.binding.senderName.setVisibility(View.VISIBLE);
            viewHolder.binding.conversationLastmsg.setTypeface(null, Typeface.NORMAL);
            viewHolder.binding.senderName.setTypeface(null, Typeface.ITALIC);
        } else {
            final boolean fileAvailable = !message.isDeleted();
            final boolean showPreviewText;
            if (fileAvailable
                    && (message.isFileOrImage()
                            || message.treatAsDownloadable()
                            || message.isGeoUri())) {
                final var attachment = Attachment.of(message);
                final @DrawableRes int imageResource = MediaAdapter.getImageDrawable(attachment);
                showPreviewText = false;
                viewHolder.binding.conversationLastmsgImg.setImageResource(imageResource);
                viewHolder.binding.conversationLastmsgImg.setVisibility(View.VISIBLE);
            } else {
                viewHolder.binding.conversationLastmsgImg.setVisibility(View.GONE);
                showPreviewText = true;
            }
            final Pair<CharSequence, Boolean> preview =
                    UIHelper.getMessagePreview(
                            activity,
                            message,
                            viewHolder.binding.conversationLastmsg.getCurrentTextColor());
            if (showPreviewText) {
                viewHolder.binding.conversationLastmsg.setText(UIHelper.shorten(preview.first));
            } else {
                viewHolder.binding.conversationLastmsgImg.setContentDescription(preview.first);
            }
            viewHolder.binding.conversationLastmsg.setVisibility(
                    showPreviewText ? View.VISIBLE : View.GONE);
            if (preview.second) {
                if (isRead) {
                    viewHolder.binding.conversationLastmsg.setTypeface(null, Typeface.ITALIC);
                    viewHolder.binding.senderName.setTypeface(null, Typeface.NORMAL);
                } else {
                    viewHolder.binding.conversationLastmsg.setTypeface(null, Typeface.BOLD_ITALIC);
                    viewHolder.binding.senderName.setTypeface(null, Typeface.BOLD);
                }
            } else {
                if (isRead) {
                    viewHolder.binding.conversationLastmsg.setTypeface(null, Typeface.NORMAL);
                    viewHolder.binding.senderName.setTypeface(null, Typeface.NORMAL);
                } else {
                    viewHolder.binding.conversationLastmsg.setTypeface(null, Typeface.BOLD);
                    viewHolder.binding.senderName.setTypeface(null, Typeface.BOLD);
                }
            }
            if (status == Message.STATUS_RECEIVED) {
                if (conversation.getMode() == Conversation.MODE_MULTI) {
                    viewHolder.binding.senderName.setVisibility(View.VISIBLE);
                    viewHolder.binding.senderName.setText(
                            UIHelper.getMessageDisplayName(message).split("\\s+")[0] + ':');
                } else {
                    viewHolder.binding.senderName.setVisibility(View.GONE);
                }
            } else if (message.getType() != Message.TYPE_STATUS) {
                viewHolder.binding.senderName.setVisibility(View.VISIBLE);
                viewHolder.binding.senderName.setText(activity.getString(R.string.me) + ':');
            } else {
                viewHolder.binding.senderName.setVisibility(View.GONE);
            }
        }

        final Optional<OngoingRtpSession> ongoingCall;
        if (conversation.getMode() == Conversational.MODE_MULTI) {
            ongoingCall = Optional.absent();
        } else {
            ongoingCall =
                    activity.xmppConnectionService
                            .getJingleConnectionManager()
                            .getOngoingRtpConnection(conversation.getContact());
        }

        if (ongoingCall.isPresent()) {
            viewHolder.binding.notificationStatus.setVisibility(View.VISIBLE);
            viewHolder.binding.notificationStatus.setImageResource(
                    R.drawable.ic_phone_in_talk_24dp);
        } else {
            final long muted_till =
                    conversation.getLongAttribute(Conversation.ATTRIBUTE_MUTED_TILL, 0);
            if (muted_till == Long.MAX_VALUE) {
                viewHolder.binding.notificationStatus.setVisibility(View.VISIBLE);
                viewHolder.binding.notificationStatus.setImageResource(
                        R.drawable.ic_notifications_off_24dp);
            } else if (muted_till >= System.currentTimeMillis()) {
                viewHolder.binding.notificationStatus.setVisibility(View.VISIBLE);
                viewHolder.binding.notificationStatus.setImageResource(
                        R.drawable.ic_notifications_paused_24dp);
            } else if (conversation.alwaysNotify()) {
                viewHolder.binding.notificationStatus.setVisibility(View.GONE);
            } else {
                viewHolder.binding.notificationStatus.setVisibility(View.VISIBLE);
                viewHolder.binding.notificationStatus.setImageResource(
                        R.drawable.ic_notifications_none_24dp);
            }
        }

        long timestamp;
        if (draft != null) {
            timestamp = draft.getTimestamp();
        } else {
            timestamp = conversation.getLatestMessage().getTimeSent();
        }
        viewHolder.binding.pinnedOnTop.setVisibility(
                conversation.getBooleanAttribute(Conversation.ATTRIBUTE_PINNED_ON_TOP, false)
                        ? View.VISIBLE
                        : View.GONE);
        viewHolder.binding.conversationLastupdate.setText(
                UIHelper.readableTimeDifference(activity, timestamp));
        AvatarWorkerTask.loadAvatar(
                conversation,
                viewHolder.binding.conversationImage,
                R.dimen.avatar_on_conversation_overview);
        viewHolder.itemView.setOnClickListener(v -> listener.onConversationClick(v, conversation));
    }

    @Override
    public int getItemCount() {
        return conversations.size();
    }

    public void setConversationClickListener(OnConversationClickListener listener) {
        this.listener = listener;
    }

    public void insert(Conversation c, int position) {
        conversations.add(position, c);
        notifyDataSetChanged();
    }

    public void remove(Conversation conversation, int position) {
        conversations.remove(conversation);
        notifyItemRemoved(position);
    }

    public interface OnConversationClickListener {
        void onConversationClick(View view, Conversation conversation);
    }

    public static class ConversationViewHolder extends RecyclerView.ViewHolder {
        public final ItemConversationBinding binding;

        private ConversationViewHolder(final ItemConversationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
