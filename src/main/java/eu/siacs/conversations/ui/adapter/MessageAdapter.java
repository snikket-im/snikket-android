package eu.siacs.conversations.ui.adapter;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Build;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.format.DateUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.databinding.DataBindingUtil;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import de.gultsch.common.Linkify;
import eu.siacs.conversations.AppSettings;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.FingerprintStatus;
import eu.siacs.conversations.databinding.ItemMessageDateBubbleBinding;
import eu.siacs.conversations.databinding.ItemMessageEndBinding;
import eu.siacs.conversations.databinding.ItemMessageRtpSessionBinding;
import eu.siacs.conversations.databinding.ItemMessageStartBinding;
import eu.siacs.conversations.databinding.ItemMessageStatusBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Conversational;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Message.FileParams;
import eu.siacs.conversations.entities.RtpSessionStatus;
import eu.siacs.conversations.entities.Transferable;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.NotificationService;
import eu.siacs.conversations.ui.Activities;
import eu.siacs.conversations.ui.BindingAdapters;
import eu.siacs.conversations.ui.ConversationFragment;
import eu.siacs.conversations.ui.ConversationsActivity;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.ui.service.AudioPlayer;
import eu.siacs.conversations.ui.text.DividerSpan;
import eu.siacs.conversations.ui.text.FixedURLSpan;
import eu.siacs.conversations.ui.text.QuoteSpan;
import eu.siacs.conversations.ui.util.Attachment;
import eu.siacs.conversations.ui.util.AvatarWorkerTask;
import eu.siacs.conversations.ui.util.QuoteHelper;
import eu.siacs.conversations.ui.util.ViewUtil;
import eu.siacs.conversations.ui.widget.ClickableMovementMethod;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.Emoticons;
import eu.siacs.conversations.utils.GeoHelper;
import eu.siacs.conversations.utils.MessageUtils;
import eu.siacs.conversations.utils.StylingHelper;
import eu.siacs.conversations.utils.TimeFrameUtils;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.mam.MamReference;
import eu.siacs.conversations.xmpp.manager.MessageArchiveManager;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageAdapter extends ArrayAdapter<Message> {

    public static final String DATE_SEPARATOR_BODY = "DATE_SEPARATOR";
    private static final int END = 0;
    private static final int START = 1;
    private static final int STATUS = 2;
    private static final int DATE_SEPARATOR = 3;
    private static final int RTP_SESSION = 4;
    private final XmppActivity activity;
    private final AudioPlayer audioPlayer;
    private List<String> highlightedTerm = null;
    private final DisplayMetrics metrics;
    private OnContactPictureClicked mOnContactPictureClickedListener;
    private OnContactPictureLongClicked mOnContactPictureLongClickedListener;
    private BubbleDesign bubbleDesign = new BubbleDesign(false, false, false, true, true);
    private final boolean mForceNames;

    public MessageAdapter(
            final XmppActivity activity, final List<Message> messages, final boolean forceNames) {
        super(activity, 0, messages);
        this.audioPlayer = new AudioPlayer(this);
        this.activity = activity;
        metrics = getContext().getResources().getDisplayMetrics();
        updatePreferences();
        this.mForceNames = forceNames;
    }

    public MessageAdapter(final XmppActivity activity, final List<Message> messages) {
        this(activity, messages, false);
    }

    private static void resetClickListener(View... views) {
        for (View view : views) {
            view.setOnClickListener(null);
        }
    }

    public void flagScreenOn() {
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    public void flagScreenOff() {
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    public void setVolumeControl(final int stream) {
        activity.setVolumeControlStream(stream);
    }

    public void setOnContactPictureClicked(OnContactPictureClicked listener) {
        this.mOnContactPictureClickedListener = listener;
    }

    public Activity getActivity() {
        return activity;
    }

    public void setOnContactPictureLongClicked(OnContactPictureLongClicked listener) {
        this.mOnContactPictureLongClickedListener = listener;
    }

    @Override
    public int getViewTypeCount() {
        return 5;
    }

    private static int getItemViewType(final Message message, final boolean alignStart) {
        if (message.getType() == Message.TYPE_STATUS) {
            if (DATE_SEPARATOR_BODY.equals(message.getBody())) {
                return DATE_SEPARATOR;
            } else {
                return STATUS;
            }
        } else if (message.getType() == Message.TYPE_RTP_SESSION) {
            return RTP_SESSION;
        } else if (message.getStatus() <= Message.STATUS_RECEIVED || alignStart) {
            return START;
        } else {
            return END;
        }
    }

    @Override
    public int getItemViewType(final int position) {
        return getItemViewType(getItem(position), bubbleDesign.alignStart);
    }

    private void displayStatus(
            final BubbleMessageItemViewHolder viewHolder,
            final Message message,
            final BubbleColor bubbleColor) {
        final int status = message.getStatus();
        final boolean error;
        final Transferable transferable = message.getTransferable();
        final boolean sent = status != Message.STATUS_RECEIVED;
        final boolean showUserNickname =
                message.getConversation().getMode() == Conversation.MODE_MULTI
                        && viewHolder instanceof StartBubbleMessageItemViewHolder;
        final String fileSize;
        if (message.isFileOrImage()
                || transferable != null
                || MessageUtils.unInitiatedButKnownSize(message)) {
            final FileParams params = message.getFileParams();
            fileSize = params.size != null ? UIHelper.filesizeToString(params.size) : null;
            if (message.getStatus() == Message.STATUS_SEND_FAILED
                    || (transferable != null
                            && (transferable.getStatus() == Transferable.STATUS_FAILED
                                    || transferable.getStatus()
                                            == Transferable.STATUS_CANCELLED))) {
                error = true;
            } else {
                error = message.getStatus() == Message.STATUS_SEND_FAILED;
            }
        } else {
            fileSize = null;
            error = message.getStatus() == Message.STATUS_SEND_FAILED;
        }

        if (sent) {
            final @DrawableRes Integer receivedIndicator =
                    getMessageStatusAsDrawable(message, status);
            if (receivedIndicator == null) {
                viewHolder.indicatorReceived().setVisibility(View.INVISIBLE);
            } else {
                viewHolder.indicatorReceived().setImageResource(receivedIndicator);
                if (status == Message.STATUS_SEND_FAILED) {
                    setImageTintError(viewHolder.indicatorReceived());
                } else {
                    setImageTint(viewHolder.indicatorReceived(), bubbleColor);
                }
                viewHolder.indicatorReceived().setVisibility(View.VISIBLE);
            }
        } else {
            viewHolder.indicatorReceived().setVisibility(View.GONE);
        }
        final var additionalStatusInfo = getAdditionalStatusInfo(message, status);

        if (error && sent) {
            viewHolder
                    .time()
                    .setTextColor(
                            MaterialColors.getColor(
                                    viewHolder.time(), androidx.appcompat.R.attr.colorError));
        } else {
            setTextColor(viewHolder.time(), bubbleColor);
        }
        if (message.getEncryption() == Message.ENCRYPTION_NONE) {
            viewHolder.indicatorSecurity().setVisibility(View.GONE);
        } else {
            boolean verified = false;
            if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL) {
                final FingerprintStatus fingerprintStatus =
                        message.getConversation()
                                .getAccount()
                                .getAxolotlService()
                                .getFingerprintTrust(message.getFingerprint());
                if (fingerprintStatus != null && fingerprintStatus.isVerified()) {
                    verified = true;
                }
            }
            if (verified) {
                viewHolder.indicatorSecurity().setImageResource(R.drawable.ic_verified_user_24dp);
            } else {
                viewHolder.indicatorSecurity().setImageResource(R.drawable.ic_lock_24dp);
            }
            if (error && sent) {
                setImageTintError(viewHolder.indicatorSecurity());
            } else {
                setImageTint(viewHolder.indicatorSecurity(), bubbleColor);
            }
            viewHolder.indicatorSecurity().setVisibility(View.VISIBLE);
        }

        if (message.edited()) {
            viewHolder.indicatorEdit().setVisibility(View.VISIBLE);
            if (error && sent) {
                setImageTintError(viewHolder.indicatorEdit());
            } else {
                setImageTint(viewHolder.indicatorEdit(), bubbleColor);
            }
        } else {
            viewHolder.indicatorEdit().setVisibility(View.GONE);
        }

        final String formattedTime =
                UIHelper.readableTimeDifferenceFull(getContext(), message.getTimeSent());
        final String bodyLanguage = message.getBodyLanguage();
        final ImmutableList.Builder<String> timeInfoBuilder = new ImmutableList.Builder<>();

        if (mForceNames || showUserNickname) {
            final String displayName = UIHelper.getMessageDisplayName(message);
            if (displayName != null) {
                timeInfoBuilder.add(displayName);
            }
        }
        if (fileSize != null) {
            timeInfoBuilder.add(fileSize);
        }
        if (bodyLanguage != null) {
            timeInfoBuilder.add(bodyLanguage.toUpperCase(Locale.US));
        }
        // for space reasons we display only 'additional status info' (send progress or concrete
        // failure reason) or the time
        if (additionalStatusInfo != null) {
            timeInfoBuilder.add(additionalStatusInfo);
        } else {
            timeInfoBuilder.add(formattedTime);
        }
        final var timeInfo = timeInfoBuilder.build();
        viewHolder.time().setText(Joiner.on(" · ").join(timeInfo));
    }

    public static @DrawableRes Integer getMessageStatusAsDrawable(
            final Message message, final int status) {
        final var transferable = message.getTransferable();
        return switch (status) {
            case Message.STATUS_WAITING -> R.drawable.ic_more_horiz_24dp;
            case Message.STATUS_UNSEND -> transferable == null ? null : R.drawable.ic_upload_24dp;
            case Message.STATUS_SEND -> R.drawable.ic_done_24dp;
            case Message.STATUS_SEND_RECEIVED, Message.STATUS_SEND_DISPLAYED ->
                    R.drawable.ic_done_all_24dp;
            case Message.STATUS_SEND_FAILED -> {
                final String errorMessage = message.getErrorMessage();
                if (Message.ERROR_MESSAGE_CANCELLED.equals(errorMessage)) {
                    yield R.drawable.ic_cancel_24dp;
                } else {
                    yield R.drawable.ic_error_24dp;
                }
            }
            case Message.STATUS_OFFERED -> R.drawable.ic_p2p_24dp;
            default -> null;
        };
    }

    @Nullable
    private String getAdditionalStatusInfo(final Message message, final int mergedStatus) {
        final String additionalStatusInfo;
        if (mergedStatus == Message.STATUS_SEND_FAILED) {
            final String errorMessage = Strings.nullToEmpty(message.getErrorMessage());
            final String[] errorParts = errorMessage.split("\\u001f", 2);
            if (errorParts.length == 2 && errorParts[0].equals("file-too-large")) {
                additionalStatusInfo = getContext().getString(R.string.file_too_large);
            } else {
                additionalStatusInfo = null;
            }
        } else if (mergedStatus == Message.STATUS_UNSEND) {
            final var transferable = message.getTransferable();
            if (transferable == null) {
                return null;
            }
            return getContext().getString(R.string.sending_file, transferable.getProgress());
        } else {
            additionalStatusInfo = null;
        }
        return additionalStatusInfo;
    }

    private void displayInfoMessage(
            BubbleMessageItemViewHolder viewHolder,
            CharSequence text,
            final BubbleColor bubbleColor) {
        viewHolder.downloadButton().setVisibility(View.GONE);
        viewHolder.audioPlayer().setVisibility(View.GONE);
        viewHolder.image().setVisibility(View.GONE);
        viewHolder.messageBody().setTypeface(null, Typeface.ITALIC);
        viewHolder.messageBody().setVisibility(View.VISIBLE);
        viewHolder.messageBody().setText(text);
        viewHolder
                .messageBody()
                .setTextColor(bubbleToOnSurfaceVariant(viewHolder.messageBody(), bubbleColor));
        viewHolder.messageBody().setTextIsSelectable(false);
    }

    private void displayEmojiMessage(
            final BubbleMessageItemViewHolder viewHolder,
            final String body,
            final BubbleColor bubbleColor) {
        viewHolder.downloadButton().setVisibility(View.GONE);
        viewHolder.audioPlayer().setVisibility(View.GONE);
        viewHolder.image().setVisibility(View.GONE);
        viewHolder.messageBody().setTypeface(null, Typeface.NORMAL);
        viewHolder.messageBody().setVisibility(View.VISIBLE);
        setTextColor(viewHolder.messageBody(), bubbleColor);
        final Spannable span = new SpannableString(body);
        float size = Emoticons.isEmoji(body) ? 3.0f : 2.0f;
        span.setSpan(
                new RelativeSizeSpan(size), 0, body.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        viewHolder.messageBody().setText(span);
    }

    private void applyQuoteSpan(
            final TextView textView,
            SpannableStringBuilder body,
            int start,
            int end,
            final BubbleColor bubbleColor) {
        if (start > 1 && !"\n\n".equals(body.subSequence(start - 2, start).toString())) {
            body.insert(start++, "\n");
            body.setSpan(
                    new DividerSpan(false), start - 2, start, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            end++;
        }
        if (end < body.length() - 1 && !"\n\n".equals(body.subSequence(end, end + 2).toString())) {
            body.insert(end, "\n");
            body.setSpan(new DividerSpan(false), end, end + 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        final DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
        body.setSpan(
                new QuoteSpan(bubbleToOnSurfaceVariant(textView, bubbleColor), metrics),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /**
     * Applies QuoteSpan to group of lines which starts with > or » characters. Appends likebreaks
     * and applies DividerSpan to them to show a padding between quote and text.
     */
    private boolean handleTextQuotes(
            final TextView textView,
            final SpannableStringBuilder body,
            final BubbleColor bubbleColor) {
        boolean startsWithQuote = false;
        int quoteDepth = 1;
        while (QuoteHelper.bodyContainsQuoteStart(body) && quoteDepth <= Config.QUOTE_MAX_DEPTH) {
            char previous = '\n';
            int lineStart = -1;
            int lineTextStart = -1;
            int quoteStart = -1;
            for (int i = 0; i <= body.length(); i++) {
                char current = body.length() > i ? body.charAt(i) : '\n';
                if (lineStart == -1) {
                    if (previous == '\n') {
                        if (i < body.length() && QuoteHelper.isPositionQuoteStart(body, i)) {
                            // Line start with quote
                            lineStart = i;
                            if (quoteStart == -1) quoteStart = i;
                            if (i == 0) startsWithQuote = true;
                        } else if (quoteStart >= 0) {
                            // Line start without quote, apply spans there
                            applyQuoteSpan(textView, body, quoteStart, i - 1, bubbleColor);
                            quoteStart = -1;
                        }
                    }
                } else {
                    // Remove extra spaces between > and first character in the line
                    // > character will be removed too
                    if (current != ' ' && lineTextStart == -1) {
                        lineTextStart = i;
                    }
                    if (current == '\n') {
                        body.delete(lineStart, lineTextStart);
                        i -= lineTextStart - lineStart;
                        if (i == lineStart) {
                            // Avoid empty lines because span over empty line can be hidden
                            body.insert(i++, " ");
                        }
                        lineStart = -1;
                        lineTextStart = -1;
                    }
                }
                previous = current;
            }
            if (quoteStart >= 0) {
                // Apply spans to finishing open quote
                applyQuoteSpan(textView, body, quoteStart, body.length(), bubbleColor);
            }
            quoteDepth++;
        }
        return startsWithQuote;
    }

    private void displayTextMessage(
            final BubbleMessageItemViewHolder viewHolder,
            final Message message,
            final BubbleColor bubbleColor) {
        viewHolder.downloadButton().setVisibility(View.GONE);
        viewHolder.image().setVisibility(View.GONE);
        viewHolder.audioPlayer().setVisibility(View.GONE);
        viewHolder.messageBody().setTypeface(null, Typeface.NORMAL);
        viewHolder.messageBody().setVisibility(View.VISIBLE);
        setTextColor(viewHolder.messageBody(), bubbleColor);
        setTextSize(viewHolder.messageBody(), this.bubbleDesign.largeFont);
        viewHolder.messageBody().setTypeface(null, Typeface.NORMAL);
        final var rawBody = message.getBody();
        if (Strings.isNullOrEmpty(rawBody)) {
            viewHolder.messageBody().setText("");
            viewHolder.messageBody().setTextIsSelectable(false);
            return;
        }
        final String nick = UIHelper.getMessageDisplayName(message);
        final boolean hasMeCommand = message.hasMeCommand();
        final var trimmedBody = rawBody.trim();
        final SpannableStringBuilder body;
        if (trimmedBody.length() > Config.MAX_DISPLAY_MESSAGE_CHARS) {
            body = new SpannableStringBuilder(trimmedBody, 0, Config.MAX_DISPLAY_MESSAGE_CHARS);
            body.append("…");
        } else {
            body = new SpannableStringBuilder(trimmedBody);
        }
        if (hasMeCommand) {
            body.replace(0, Message.ME_COMMAND.length(), String.format("%s ", nick));
        }
        boolean startsWithQuote = handleTextQuotes(viewHolder.messageBody(), body, bubbleColor);
        if (!message.isPrivateMessage()) {
            if (hasMeCommand) {
                body.setSpan(
                        new StyleSpan(Typeface.BOLD_ITALIC),
                        0,
                        nick.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        } else {
            String privateMarker;
            if (message.getStatus() <= Message.STATUS_RECEIVED) {
                privateMarker = activity.getString(R.string.private_message);
            } else {
                Jid cp = message.getCounterpart();
                privateMarker =
                        activity.getString(
                                R.string.private_message_to,
                                Strings.nullToEmpty(cp == null ? null : cp.getResource()));
            }
            body.insert(0, privateMarker);
            int privateMarkerIndex = privateMarker.length();
            if (startsWithQuote) {
                body.insert(privateMarkerIndex, "\n\n");
                body.setSpan(
                        new DividerSpan(false),
                        privateMarkerIndex,
                        privateMarkerIndex + 2,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                body.insert(privateMarkerIndex, " ");
            }
            body.setSpan(
                    new ForegroundColorSpan(
                            bubbleToOnSurfaceVariant(viewHolder.messageBody(), bubbleColor)),
                    0,
                    privateMarkerIndex,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            body.setSpan(
                    new StyleSpan(Typeface.BOLD),
                    0,
                    privateMarkerIndex,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (hasMeCommand) {
                body.setSpan(
                        new StyleSpan(Typeface.BOLD_ITALIC),
                        privateMarkerIndex + 1,
                        privateMarkerIndex + 1 + nick.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        if (message.getConversation().getMode() == Conversation.MODE_MULTI
                && message.getStatus() == Message.STATUS_RECEIVED) {
            if (message.getConversation() instanceof Conversation conversation) {
                Pattern pattern =
                        NotificationService.generateNickHighlightPattern(
                                conversation.getMucOptions().getActualNick());
                Matcher matcher = pattern.matcher(body);
                while (matcher.find()) {
                    body.setSpan(
                            new StyleSpan(Typeface.BOLD),
                            matcher.start(),
                            matcher.end(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        }
        Matcher matcher = Emoticons.getEmojiPattern(body).matcher(body);
        while (matcher.find()) {
            if (matcher.start() < matcher.end()) {
                body.setSpan(
                        new RelativeSizeSpan(1.2f),
                        matcher.start(),
                        matcher.end(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        StylingHelper.format(body, viewHolder.messageBody().getCurrentTextColor());
        Linkify.addLinks(body);
        FixedURLSpan.fix(body);
        if (highlightedTerm != null) {
            StylingHelper.highlight(viewHolder.messageBody(), body, highlightedTerm);
        }
        viewHolder.messageBody().setAutoLinkMask(0);
        viewHolder.messageBody().setText(body);
        viewHolder.messageBody().setMovementMethod(ClickableMovementMethod.getInstance());
    }

    private void displayDownloadableMessage(
            final BubbleMessageItemViewHolder viewHolder,
            final Message message,
            final String text,
            final BubbleColor bubbleColor) {
        toggleWhisperInfo(viewHolder, message, bubbleColor);
        viewHolder.image().setVisibility(View.GONE);
        viewHolder.audioPlayer().setVisibility(View.GONE);
        viewHolder.downloadButton().setVisibility(View.VISIBLE);
        viewHolder.downloadButton().setText(text);
        final var attachment = Attachment.of(message);
        final @DrawableRes int imageResource = MediaAdapter.getImageDrawable(attachment);
        viewHolder.downloadButton().setIconResource(imageResource);
        viewHolder
                .downloadButton()
                .setOnClickListener(v -> ConversationFragment.downloadFile(activity, message));
    }

    private void displayOpenableMessage(
            final BubbleMessageItemViewHolder viewHolder,
            final Message message,
            final BubbleColor bubbleColor) {
        toggleWhisperInfo(viewHolder, message, bubbleColor);
        viewHolder.image().setVisibility(View.GONE);
        viewHolder.audioPlayer().setVisibility(View.GONE);
        viewHolder.downloadButton().setVisibility(View.VISIBLE);
        viewHolder
                .downloadButton()
                .setText(
                        activity.getString(
                                R.string.open_x_file,
                                UIHelper.getFileDescriptionString(activity, message)));
        final var attachment = Attachment.of(message);
        final @DrawableRes int imageResource = MediaAdapter.getImageDrawable(attachment);
        viewHolder.downloadButton().setIconResource(imageResource);
        viewHolder.downloadButton().setOnClickListener(v -> openDownloadable(message));
    }

    private void displayLocationMessage(
            final BubbleMessageItemViewHolder viewHolder,
            final Message message,
            final BubbleColor bubbleColor) {
        toggleWhisperInfo(viewHolder, message, bubbleColor);
        viewHolder.image().setVisibility(View.GONE);
        viewHolder.audioPlayer().setVisibility(View.GONE);
        viewHolder.downloadButton().setVisibility(View.VISIBLE);
        viewHolder.downloadButton().setText(R.string.show_location);
        final var attachment = Attachment.of(message);
        final @DrawableRes int imageResource = MediaAdapter.getImageDrawable(attachment);
        viewHolder.downloadButton().setIconResource(imageResource);
        viewHolder.downloadButton().setOnClickListener(v -> showLocation(message));
    }

    private void displayAudioMessage(
            final BubbleMessageItemViewHolder viewHolder,
            Message message,
            final BubbleColor bubbleColor) {
        toggleWhisperInfo(viewHolder, message, bubbleColor);
        viewHolder.image().setVisibility(View.GONE);
        viewHolder.downloadButton().setVisibility(View.GONE);
        final RelativeLayout audioPlayer = viewHolder.audioPlayer();
        audioPlayer.setVisibility(View.VISIBLE);
        AudioPlayer.ViewHolder.get(audioPlayer).setBubbleColor(bubbleColor);
        this.audioPlayer.init(audioPlayer, message);
    }

    private void displayMediaPreviewMessage(
            final BubbleMessageItemViewHolder viewHolder,
            final Message message,
            final BubbleColor bubbleColor) {
        toggleWhisperInfo(viewHolder, message, bubbleColor);
        viewHolder.downloadButton().setVisibility(View.GONE);
        viewHolder.audioPlayer().setVisibility(View.GONE);
        viewHolder.image().setVisibility(View.VISIBLE);
        final FileParams params = message.getFileParams();
        final float target = activity.getResources().getDimension(R.dimen.image_preview_width);
        final int scaledW;
        final int scaledH;
        if (Math.max(params.height, params.width) * metrics.density <= target) {
            scaledW = (int) (params.width * metrics.density);
            scaledH = (int) (params.height * metrics.density);
        } else if (Math.max(params.height, params.width) <= target) {
            scaledW = params.width;
            scaledH = params.height;
        } else if (params.width <= params.height) {
            scaledW = (int) (params.width / ((double) params.height / target));
            scaledH = (int) target;
        } else {
            scaledW = (int) target;
            scaledH = (int) (params.height / ((double) params.width / target));
        }
        final LinearLayout.LayoutParams layoutParams =
                new LinearLayout.LayoutParams(scaledW, scaledH);
        viewHolder.image().setLayoutParams(layoutParams);
        activity.loadBitmap(message, viewHolder.image());
        viewHolder.image().setOnClickListener(v -> openDownloadable(message));
    }

    private void toggleWhisperInfo(
            final BubbleMessageItemViewHolder viewHolder,
            final Message message,
            final BubbleColor bubbleColor) {
        if (message.isPrivateMessage()) {
            final String privateMarker;
            if (message.getStatus() <= Message.STATUS_RECEIVED) {
                privateMarker = activity.getString(R.string.private_message);
            } else {
                Jid cp = message.getCounterpart();
                privateMarker =
                        activity.getString(
                                R.string.private_message_to,
                                Strings.nullToEmpty(cp == null ? null : cp.getResource()));
            }
            final SpannableString body = new SpannableString(privateMarker);
            body.setSpan(
                    new ForegroundColorSpan(
                            bubbleToOnSurfaceVariant(viewHolder.messageBody(), bubbleColor)),
                    0,
                    privateMarker.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            body.setSpan(
                    new StyleSpan(Typeface.BOLD),
                    0,
                    privateMarker.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            viewHolder.messageBody().setText(body);
            viewHolder.messageBody().setTypeface(null, Typeface.NORMAL);
            viewHolder.messageBody().setVisibility(View.VISIBLE);
        } else {
            viewHolder.messageBody().setVisibility(View.GONE);
        }
    }

    private void loadMoreMessages(final Conversation conversation) {
        final var connection = conversation.getAccount().getXmppConnection();
        conversation.setLastClearHistory(0, null);
        activity.xmppConnectionService.updateConversation(conversation);
        conversation.setHasMessagesLeftOnServer(true);
        conversation.setFirstMamReference(null);
        long timestamp = conversation.getLastMessageTransmitted().getTimestamp();
        if (timestamp == 0) {
            timestamp = System.currentTimeMillis();
        }
        conversation.messagesLoaded.set(true);
        final var query =
                connection
                        .getManager(MessageArchiveManager.class)
                        .query(conversation, new MamReference(0), timestamp, false);
        if (query != null) {
            Toast.makeText(activity, R.string.fetching_history_from_server, Toast.LENGTH_LONG)
                    .show();
        } else {
            Toast.makeText(
                            activity,
                            R.string.not_fetching_history_retention_period,
                            Toast.LENGTH_SHORT)
                    .show();
        }
    }

    private MessageItemViewHolder getViewHolder(
            final View view, final @NonNull ViewGroup parent, final int type) {
        if (view != null && view.getTag() instanceof MessageItemViewHolder messageItemViewHolder) {
            return messageItemViewHolder;
        } else {
            final MessageItemViewHolder viewHolder =
                    switch (type) {
                        case RTP_SESSION ->
                                new RtpSessionMessageItemViewHolder(
                                        DataBindingUtil.inflate(
                                                LayoutInflater.from(parent.getContext()),
                                                R.layout.item_message_rtp_session,
                                                parent,
                                                false));
                        case DATE_SEPARATOR ->
                                new DateSeperatorMessageItemViewHolder(
                                        DataBindingUtil.inflate(
                                                LayoutInflater.from(parent.getContext()),
                                                R.layout.item_message_date_bubble,
                                                parent,
                                                false));
                        case STATUS ->
                                new StatusMessageItemViewHolder(
                                        DataBindingUtil.inflate(
                                                LayoutInflater.from(parent.getContext()),
                                                R.layout.item_message_status,
                                                parent,
                                                false));
                        case END ->
                                new EndBubbleMessageItemViewHolder(
                                        DataBindingUtil.inflate(
                                                LayoutInflater.from(parent.getContext()),
                                                R.layout.item_message_end,
                                                parent,
                                                false));
                        case START ->
                                new StartBubbleMessageItemViewHolder(
                                        DataBindingUtil.inflate(
                                                LayoutInflater.from(parent.getContext()),
                                                R.layout.item_message_start,
                                                parent,
                                                false));
                        default -> throw new AssertionError("Unable to create ViewHolder for type");
                    };
            viewHolder.itemView.setTag(viewHolder);
            return viewHolder;
        }
    }

    @NonNull
    @Override
    public View getView(final int position, final View view, final @NonNull ViewGroup parent) {
        final Message message = getItem(position);
        final int type = getItemViewType(message, bubbleDesign.alignStart);
        final MessageItemViewHolder viewHolder = getViewHolder(view, parent, type);

        if (type == DATE_SEPARATOR
                && viewHolder instanceof DateSeperatorMessageItemViewHolder messageItemViewHolder) {
            return render(message, messageItemViewHolder);
        }

        if (type == RTP_SESSION
                && viewHolder instanceof RtpSessionMessageItemViewHolder messageItemViewHolder) {
            return render(message, messageItemViewHolder);
        }

        if (type == STATUS
                && viewHolder instanceof StatusMessageItemViewHolder messageItemViewHolder) {
            return render(message, messageItemViewHolder);
        }

        if ((type == END || type == START)
                && viewHolder instanceof BubbleMessageItemViewHolder messageItemViewHolder) {
            return render(position, message, messageItemViewHolder);
        }

        throw new AssertionError();
    }

    private View render(
            final int position,
            final Message message,
            final BubbleMessageItemViewHolder viewHolder) {
        final boolean omemoEncryption = message.getEncryption() == Message.ENCRYPTION_AXOLOTL;
        final boolean isInValidSession =
                message.isValidInSession() && (!omemoEncryption || message.isTrusted());
        final Conversational conversation = message.getConversation();
        final Account account = conversation.getAccount();

        final boolean colorfulBackground = this.bubbleDesign.colorfulChatBubbles;
        final boolean received = message.getStatus() == Message.STATUS_RECEIVED;
        final BubbleColor bubbleColor;
        if (received) {
            if (isInValidSession) {
                bubbleColor = colorfulBackground ? BubbleColor.SECONDARY : BubbleColor.SURFACE;
            } else {
                bubbleColor = BubbleColor.WARNING;
            }
        } else {
            bubbleColor = colorfulBackground ? BubbleColor.TERTIARY : BubbleColor.SURFACE_HIGH;
        }

        final var mergeIntoTop = mergeIntoTop(position, message);
        final var mergeIntoBottom = mergeIntoBottom(position, message);
        final boolean showAvatar;
        if (viewHolder instanceof StartBubbleMessageItemViewHolder) {
            showAvatar =
                    bubbleDesign.showAvatars11
                            || message.getConversation().getMode() == Conversation.MODE_MULTI;
        } else if (viewHolder instanceof EndBubbleMessageItemViewHolder) {
            showAvatar = bubbleDesign.showAvatarsAccounts;
        } else {
            throw new IllegalStateException("Unrecognized BubbleMessageItemViewHolder");
        }
        setBubblePadding(viewHolder.root(), mergeIntoTop, mergeIntoBottom);
        if (showAvatar) {
            final var requiresAvatar =
                    viewHolder instanceof StartBubbleMessageItemViewHolder
                            ? !mergeIntoTop
                            : !mergeIntoBottom;
            setRequiresAvatar(viewHolder, requiresAvatar);
            AvatarWorkerTask.loadAvatar(message, viewHolder.contactPicture(), R.dimen.avatar);
        } else {
            viewHolder.contactPicture().setVisibility(View.GONE);
        }
        setAvatarDistance(viewHolder.messageBox(), viewHolder.getClass(), showAvatar);
        viewHolder.messageBox().setClipToOutline(true);

        resetClickListener(viewHolder.messageBox(), viewHolder.messageBody());

        viewHolder
                .contactPicture()
                .setOnClickListener(
                        v -> {
                            if (MessageAdapter.this.mOnContactPictureClickedListener != null) {
                                MessageAdapter.this.mOnContactPictureClickedListener
                                        .onContactPictureClicked(message);
                            }
                        });
        viewHolder
                .contactPicture()
                .setOnLongClickListener(
                        v -> {
                            if (MessageAdapter.this.mOnContactPictureLongClickedListener != null) {
                                MessageAdapter.this.mOnContactPictureLongClickedListener
                                        .onContactPictureLongClicked(v, message);
                                return true;
                            } else {
                                return false;
                            }
                        });

        final Transferable transferable = message.getTransferable();
        final boolean unInitiatedButKnownSize = MessageUtils.unInitiatedButKnownSize(message);
        if (unInitiatedButKnownSize
                || message.isDeleted()
                || (transferable != null
                        && transferable.getStatus() != Transferable.STATUS_UPLOADING)) {
            if (unInitiatedButKnownSize
                    || transferable != null
                            && transferable.getStatus() == Transferable.STATUS_OFFER) {
                displayDownloadableMessage(
                        viewHolder,
                        message,
                        activity.getString(
                                R.string.download_x_file,
                                UIHelper.getFileDescriptionString(activity, message)),
                        bubbleColor);
            } else if (transferable != null
                    && transferable.getStatus() == Transferable.STATUS_OFFER_CHECK_FILESIZE) {
                displayDownloadableMessage(
                        viewHolder,
                        message,
                        activity.getString(
                                R.string.check_x_filesize,
                                UIHelper.getFileDescriptionString(activity, message)),
                        bubbleColor);
            } else {
                displayInfoMessage(
                        viewHolder,
                        UIHelper.getMessagePreview(activity, message).first,
                        bubbleColor);
            }
        } else if (message.isFileOrImage()
                && message.getEncryption() != Message.ENCRYPTION_PGP
                && message.getEncryption() != Message.ENCRYPTION_DECRYPTION_FAILED) {
            if (message.getFileParams().width > 0 && message.getFileParams().height > 0) {
                displayMediaPreviewMessage(viewHolder, message, bubbleColor);
            } else if (message.getFileParams().runtime > 0) {
                displayAudioMessage(viewHolder, message, bubbleColor);
            } else {
                displayOpenableMessage(viewHolder, message, bubbleColor);
            }
        } else if (message.getEncryption() == Message.ENCRYPTION_PGP) {
            if (account.isPgpDecryptionServiceConnected()) {
                if (conversation instanceof Conversation
                        && !account.hasPendingPgpIntent((Conversation) conversation)) {
                    displayInfoMessage(
                            viewHolder,
                            activity.getString(R.string.message_decrypting),
                            bubbleColor);
                } else {
                    displayInfoMessage(
                            viewHolder, activity.getString(R.string.pgp_message), bubbleColor);
                }
            } else {
                displayInfoMessage(
                        viewHolder, activity.getString(R.string.install_openkeychain), bubbleColor);
                viewHolder.messageBox().setOnClickListener(this::promptOpenKeychainInstall);
                viewHolder.messageBody().setOnClickListener(this::promptOpenKeychainInstall);
            }
        } else if (message.getEncryption() == Message.ENCRYPTION_DECRYPTION_FAILED) {
            displayInfoMessage(
                    viewHolder, activity.getString(R.string.decryption_failed), bubbleColor);
        } else if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL_NOT_FOR_THIS_DEVICE) {
            displayInfoMessage(
                    viewHolder,
                    activity.getString(R.string.not_encrypted_for_this_device),
                    bubbleColor);
        } else if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL_FAILED) {
            displayInfoMessage(
                    viewHolder, activity.getString(R.string.omemo_decryption_failed), bubbleColor);
        } else {
            if (message.isGeoUri()) {
                displayLocationMessage(viewHolder, message, bubbleColor);
            } else if (message.bodyIsOnlyEmojis() && message.getType() != Message.TYPE_PRIVATE) {
                displayEmojiMessage(viewHolder, message.getBody().trim(), bubbleColor);
            } else if (message.treatAsDownloadable()) {
                try {
                    final URI uri = new URI(message.getBody());
                    displayDownloadableMessage(
                            viewHolder,
                            message,
                            activity.getString(
                                    R.string.check_x_filesize_on_host,
                                    UIHelper.getFileDescriptionString(activity, message),
                                    uri.getHost()),
                            bubbleColor);
                } catch (Exception e) {
                    displayDownloadableMessage(
                            viewHolder,
                            message,
                            activity.getString(
                                    R.string.check_x_filesize,
                                    UIHelper.getFileDescriptionString(activity, message)),
                            bubbleColor);
                }
            } else {
                displayTextMessage(viewHolder, message, bubbleColor);
            }
        }

        setBackgroundTint(viewHolder.messageBox(), bubbleColor);
        setTextColor(viewHolder.messageBody(), bubbleColor);

        if (received && viewHolder instanceof StartBubbleMessageItemViewHolder startViewHolder) {
            setTextColor(startViewHolder.encryption(), bubbleColor);
            if (isInValidSession) {
                startViewHolder.encryption().setVisibility(View.GONE);
            } else {
                startViewHolder.encryption().setVisibility(View.VISIBLE);
                if (omemoEncryption && !message.isTrusted()) {
                    startViewHolder.encryption().setText(R.string.not_trusted);
                } else {
                    startViewHolder
                            .encryption()
                            .setText(CryptoHelper.encryptionTypeToText(message.getEncryption()));
                }
            }
            BindingAdapters.setReactionsOnReceived(
                    viewHolder.reactions(),
                    message.getAggregatedReactions(),
                    reactions -> sendReactions(message, reactions),
                    emoji -> showDetailedReaction(message, emoji),
                    () -> addReaction(message));
        } else {
            if (viewHolder instanceof StartBubbleMessageItemViewHolder startViewHolder) {
                startViewHolder.encryption().setVisibility(View.GONE);
            }
            BindingAdapters.setReactionsOnSent(
                    viewHolder.reactions(),
                    message.getAggregatedReactions(),
                    reactions -> sendReactions(message, reactions),
                    emoji -> showDetailedReaction(message, emoji));
        }

        displayStatus(viewHolder, message, bubbleColor);
        return viewHolder.root();
    }

    private View render(
            final Message message, final DateSeperatorMessageItemViewHolder viewHolder) {
        final boolean colorfulBackground = this.bubbleDesign.colorfulChatBubbles;
        if (UIHelper.today(message.getTimeSent())) {
            viewHolder.binding.messageBody.setText(R.string.today);
        } else if (UIHelper.yesterday(message.getTimeSent())) {
            viewHolder.binding.messageBody.setText(R.string.yesterday);
        } else {
            viewHolder.binding.messageBody.setText(
                    DateUtils.formatDateTime(
                            activity,
                            message.getTimeSent(),
                            DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR));
        }
        if (colorfulBackground) {
            setBackgroundTint(viewHolder.binding.messageBox, BubbleColor.PRIMARY);
            setTextColor(viewHolder.binding.messageBody, BubbleColor.PRIMARY);
        } else {
            setBackgroundTint(viewHolder.binding.messageBox, BubbleColor.SURFACE_HIGH);
            setTextColor(viewHolder.binding.messageBody, BubbleColor.SURFACE_HIGH);
        }
        return viewHolder.binding.getRoot();
    }

    private View render(final Message message, final RtpSessionMessageItemViewHolder viewHolder) {
        final boolean colorfulBackground = this.bubbleDesign.colorfulChatBubbles;
        final boolean received = message.getStatus() <= Message.STATUS_RECEIVED;
        final RtpSessionStatus rtpSessionStatus = RtpSessionStatus.of(message.getBody());
        final long duration = rtpSessionStatus.duration;
        if (received) {
            if (duration > 0) {
                viewHolder.binding.messageBody.setText(
                        activity.getString(
                                R.string.incoming_call_duration_timestamp,
                                TimeFrameUtils.resolve(activity, duration),
                                UIHelper.readableTimeDifferenceFull(
                                        activity, message.getTimeSent())));
            } else if (rtpSessionStatus.successful) {
                viewHolder.binding.messageBody.setText(R.string.incoming_call);
            } else {
                viewHolder.binding.messageBody.setText(
                        activity.getString(
                                R.string.missed_call_timestamp,
                                UIHelper.readableTimeDifferenceFull(
                                        activity, message.getTimeSent())));
            }
        } else {
            if (duration > 0) {
                viewHolder.binding.messageBody.setText(
                        activity.getString(
                                R.string.outgoing_call_duration_timestamp,
                                TimeFrameUtils.resolve(activity, duration),
                                UIHelper.readableTimeDifferenceFull(
                                        activity, message.getTimeSent())));
            } else {
                viewHolder.binding.messageBody.setText(
                        activity.getString(
                                R.string.outgoing_call_timestamp,
                                UIHelper.readableTimeDifferenceFull(
                                        activity, message.getTimeSent())));
            }
        }
        if (colorfulBackground) {
            setBackgroundTint(viewHolder.binding.messageBox, BubbleColor.SECONDARY);
            setTextColor(viewHolder.binding.messageBody, BubbleColor.SECONDARY);
            setImageTint(viewHolder.binding.indicatorReceived, BubbleColor.SECONDARY);
        } else {
            setBackgroundTint(viewHolder.binding.messageBox, BubbleColor.SURFACE_HIGH);
            setTextColor(viewHolder.binding.messageBody, BubbleColor.SURFACE_HIGH);
            setImageTint(viewHolder.binding.indicatorReceived, BubbleColor.SURFACE_HIGH);
        }
        viewHolder.binding.indicatorReceived.setImageResource(
                RtpSessionStatus.getDrawable(received, rtpSessionStatus.successful));
        return viewHolder.binding.getRoot();
    }

    private View render(final Message message, final StatusMessageItemViewHolder viewHolder) {
        final var conversation = message.getConversation();
        if ("LOAD_MORE".equals(message.getBody())) {
            viewHolder.binding.statusMessage.setVisibility(View.GONE);
            viewHolder.binding.messagePhoto.setVisibility(View.GONE);
            viewHolder.binding.loadMoreMessages.setVisibility(View.VISIBLE);
            viewHolder.binding.loadMoreMessages.setOnClickListener(
                    v -> loadMoreMessages((Conversation) message.getConversation()));
        } else {
            viewHolder.binding.statusMessage.setVisibility(View.VISIBLE);
            viewHolder.binding.loadMoreMessages.setVisibility(View.GONE);
            viewHolder.binding.statusMessage.setText(message.getBody());
            boolean showAvatar;
            if (conversation.getMode() == Conversation.MODE_SINGLE) {
                showAvatar = true;
                AvatarWorkerTask.loadAvatar(
                        message, viewHolder.binding.messagePhoto, R.dimen.avatar_on_status_message);
            } else if (message.getCounterpart() != null
                    || message.getTrueCounterpart() != null
                    || (message.getCounterparts() != null
                            && !message.getCounterparts().isEmpty())) {
                showAvatar = true;
                AvatarWorkerTask.loadAvatar(
                        message, viewHolder.binding.messagePhoto, R.dimen.avatar_on_status_message);
            } else {
                showAvatar = false;
            }
            if (showAvatar) {
                viewHolder.binding.messagePhoto.setAlpha(0.5f);
                viewHolder.binding.messagePhoto.setVisibility(View.VISIBLE);
            } else {
                viewHolder.binding.messagePhoto.setVisibility(View.GONE);
            }
        }
        return viewHolder.binding.getRoot();
    }

    private void setAvatarDistance(
            final LinearLayout messageBox,
            final Class<? extends BubbleMessageItemViewHolder> clazz,
            final boolean showAvatar) {
        final ViewGroup.MarginLayoutParams layoutParams =
                (ViewGroup.MarginLayoutParams) messageBox.getLayoutParams();
        if (showAvatar) {
            final var resources = messageBox.getResources();
            if (clazz == StartBubbleMessageItemViewHolder.class) {
                layoutParams.setMarginStart(
                        resources.getDimensionPixelSize(R.dimen.bubble_avatar_distance));
                layoutParams.setMarginEnd(0);
            } else if (clazz == EndBubbleMessageItemViewHolder.class) {
                layoutParams.setMarginStart(0);
                layoutParams.setMarginEnd(
                        resources.getDimensionPixelSize(R.dimen.bubble_avatar_distance));
            } else {
                throw new AssertionError("Avatar distances are not available on this view type");
            }
        } else {
            layoutParams.setMarginStart(0);
            layoutParams.setMarginEnd(0);
        }
        messageBox.setLayoutParams(layoutParams);
    }

    private void setBubblePadding(
            final ConstraintLayout root,
            final boolean mergeIntoTop,
            final boolean mergeIntoBottom) {
        final var resources = root.getResources();
        final var horizontal = resources.getDimensionPixelSize(R.dimen.bubble_horizontal_padding);
        final int top =
                resources.getDimensionPixelSize(
                        mergeIntoTop
                                ? R.dimen.bubble_vertical_padding_minimum
                                : R.dimen.bubble_vertical_padding);
        final int bottom =
                resources.getDimensionPixelSize(
                        mergeIntoBottom
                                ? R.dimen.bubble_vertical_padding_minimum
                                : R.dimen.bubble_vertical_padding);
        root.setPadding(horizontal, top, horizontal, bottom);
    }

    private void setRequiresAvatar(
            final BubbleMessageItemViewHolder viewHolder, final boolean requiresAvatar) {
        final var layoutParams = viewHolder.contactPicture().getLayoutParams();
        if (requiresAvatar) {
            final var resources = viewHolder.contactPicture().getResources();
            final var avatarSize = resources.getDimensionPixelSize(R.dimen.bubble_avatar_size);
            layoutParams.height = avatarSize;
            viewHolder.contactPicture().setVisibility(View.VISIBLE);
            viewHolder.messageBox().setMinimumHeight(avatarSize);
        } else {
            layoutParams.height = 0;
            viewHolder.contactPicture().setVisibility(View.INVISIBLE);
            viewHolder.messageBox().setMinimumHeight(0);
        }
        viewHolder.contactPicture().setLayoutParams(layoutParams);
    }

    private boolean mergeIntoTop(final int position, final Message message) {
        if (position < 0) {
            return false;
        }
        final var top = getItem(position - 1);
        return merge(top, message);
    }

    private boolean mergeIntoBottom(final int position, final Message message) {
        final Message bottom;
        try {
            bottom = getItem(position + 1);
        } catch (final IndexOutOfBoundsException e) {
            return false;
        }
        return merge(message, bottom);
    }

    private static boolean merge(final Message a, final Message b) {
        if (getItemViewType(a, false) != getItemViewType(b, false)) {
            return false;
        }
        final var receivedA = a.getStatus() == Message.STATUS_RECEIVED;
        final var receivedB = b.getStatus() == Message.STATUS_RECEIVED;
        if (receivedA != receivedB) {
            return false;
        }
        if (a.getConversation().getMode() == Conversation.MODE_MULTI
                && a.getStatus() == Message.STATUS_RECEIVED) {
            final var occupantIdA = a.getOccupantId();
            final var occupantIdB = b.getOccupantId();
            if (occupantIdA != null && occupantIdB != null) {
                if (!occupantIdA.equals(occupantIdB)) {
                    return false;
                }
            }
            final var counterPartA = a.getCounterpart();
            final var counterPartB = b.getCounterpart();
            if (counterPartA == null || !counterPartA.equals(counterPartB)) {
                return false;
            }
        }
        return b.getTimeSent() - a.getTimeSent() <= Config.MESSAGE_MERGE_WINDOW;
    }

    private boolean showDetailedReaction(final Message message, final String emoji) {
        final var c = message.getConversation();
        if (c instanceof Conversation conversation && c.getMode() == Conversational.MODE_MULTI) {
            final var reactions =
                    Collections2.filter(
                            message.getReactions(), r -> r.normalizedReaction().equals(emoji));
            final var mucOptions = conversation.getMucOptions();
            final var users = mucOptions.getUsersOrStubs(reactions);
            if (users.isEmpty()) {
                return true;
            }
            final MaterialAlertDialogBuilder dialogBuilder =
                    new MaterialAlertDialogBuilder(activity);
            dialogBuilder.setTitle(emoji);
            dialogBuilder.setMessage(UIHelper.concatNames(users));
            dialogBuilder.create().show();
            return true;
        } else {
            return false;
        }
    }

    private void sendReactions(final Message message, final Collection<String> reactions) {
        if (activity.xmppConnectionService.sendReactions(message, reactions)) {
            return;
        }
        Toast.makeText(activity, R.string.could_not_add_reaction, Toast.LENGTH_LONG).show();
    }

    private void addReaction(final Message message) {
        activity.addReaction(
                message,
                reactions -> {
                    if (activity.xmppConnectionService.sendReactions(message, reactions)) {
                        return;
                    }
                    Toast.makeText(activity, R.string.could_not_add_reaction, Toast.LENGTH_LONG)
                            .show();
                });
    }

    private void promptOpenKeychainInstall(View view) {
        activity.showInstallPgpDialog();
    }

    public FileBackend getFileBackend() {
        return activity.xmppConnectionService.getFileBackend();
    }

    public void stopAudioPlayer() {
        audioPlayer.stop();
    }

    public void unregisterListenerInAudioPlayer() {
        audioPlayer.unregisterListener();
    }

    public void startStopPending() {
        audioPlayer.startStopPending();
    }

    public void openDownloadable(Message message) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(
                                activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            ConversationFragment.registerPendingMessage(activity, message);
            ActivityCompat.requestPermissions(
                    activity,
                    new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    ConversationsActivity.REQUEST_OPEN_MESSAGE);
            return;
        }
        final DownloadableFile file =
                activity.xmppConnectionService.getFileBackend().getFile(message);
        ViewUtil.view(activity, file);
    }

    private void showLocation(Message message) {
        for (Intent intent : GeoHelper.createGeoIntentsFromMessage(activity, message)) {
            if (intent.resolveActivity(getContext().getPackageManager()) != null) {
                getContext().startActivity(intent);
                return;
            }
        }
        Toast.makeText(
                        activity,
                        R.string.no_application_found_to_display_location,
                        Toast.LENGTH_SHORT)
                .show();
    }

    public void updatePreferences() {
        final AppSettings appSettings = new AppSettings(activity);
        this.bubbleDesign =
                new BubbleDesign(
                        appSettings.isColorfulChatBubbles(),
                        appSettings.isAlignStart(),
                        appSettings.isLargeFont(),
                        appSettings.isShowAvatars11(),
                        appSettings.isShowAvatarsAccounts());
    }

    public void setHighlightedTerm(List<String> terms) {
        this.highlightedTerm = terms == null ? null : StylingHelper.filterHighlightedWords(terms);
    }

    public interface OnContactPictureClicked {
        void onContactPictureClicked(Message message);
    }

    public interface OnContactPictureLongClicked {
        void onContactPictureLongClicked(View v, Message message);
    }

    private static void setBackgroundTint(final LinearLayout view, final BubbleColor bubbleColor) {
        view.setBackgroundTintList(bubbleToColorStateList(view, bubbleColor));
    }

    private static ColorStateList bubbleToColorStateList(
            final View view, final BubbleColor bubbleColor) {
        final @AttrRes int colorAttributeResId =
                switch (bubbleColor) {
                    case SURFACE ->
                            Activities.isNightMode(view.getContext())
                                    ? com.google.android.material.R.attr.colorSurfaceContainerHigh
                                    : com.google.android.material.R.attr.colorSurfaceContainerLow;
                    case SURFACE_HIGH ->
                            Activities.isNightMode(view.getContext())
                                    ? com.google.android.material.R.attr
                                            .colorSurfaceContainerHighest
                                    : com.google.android.material.R.attr.colorSurfaceContainerHigh;
                    case PRIMARY -> com.google.android.material.R.attr.colorPrimaryContainer;
                    case SECONDARY -> com.google.android.material.R.attr.colorSecondaryContainer;
                    case TERTIARY -> com.google.android.material.R.attr.colorTertiaryContainer;
                    case WARNING -> com.google.android.material.R.attr.colorErrorContainer;
                };
        return ColorStateList.valueOf(MaterialColors.getColor(view, colorAttributeResId));
    }

    public static void setImageTint(final ImageView imageView, final BubbleColor bubbleColor) {
        ImageViewCompat.setImageTintList(
                imageView, bubbleToOnSurfaceColorStateList(imageView, bubbleColor));
    }

    public static void setImageTintError(final ImageView imageView) {
        ImageViewCompat.setImageTintList(
                imageView,
                ColorStateList.valueOf(
                        MaterialColors.getColor(imageView, androidx.appcompat.R.attr.colorError)));
    }

    public static void setTextColor(final TextView textView, final BubbleColor bubbleColor) {
        final var color = bubbleToOnSurfaceColor(textView, bubbleColor);
        textView.setTextColor(color);
        if (BubbleColor.SURFACES.contains(bubbleColor)) {
            textView.setLinkTextColor(
                    MaterialColors.getColor(textView, androidx.appcompat.R.attr.colorPrimary));
        } else {
            textView.setLinkTextColor(color);
        }
    }

    private static void setTextSize(final TextView textView, final boolean largeFont) {
        if (largeFont) {
            textView.setTextAppearance(
                    com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        } else {
            textView.setTextAppearance(
                    com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        }
    }

    private static @ColorInt int bubbleToOnSurfaceVariant(
            final View view, final BubbleColor bubbleColor) {
        final @AttrRes int colorAttributeResId;
        if (BubbleColor.SURFACES.contains(bubbleColor)) {
            colorAttributeResId = com.google.android.material.R.attr.colorOnSurfaceVariant;
        } else {
            colorAttributeResId = bubbleToOnSurface(bubbleColor);
        }
        return MaterialColors.getColor(view, colorAttributeResId);
    }

    private static @ColorInt int bubbleToOnSurfaceColor(
            final View view, final BubbleColor bubbleColor) {
        return MaterialColors.getColor(view, bubbleToOnSurface(bubbleColor));
    }

    public static ColorStateList bubbleToOnSurfaceColorStateList(
            final View view, final BubbleColor bubbleColor) {
        return ColorStateList.valueOf(bubbleToOnSurfaceColor(view, bubbleColor));
    }

    private static @AttrRes int bubbleToOnSurface(final BubbleColor bubbleColor) {
        return switch (bubbleColor) {
            case SURFACE, SURFACE_HIGH -> com.google.android.material.R.attr.colorOnSurface;
            case PRIMARY -> com.google.android.material.R.attr.colorOnPrimaryContainer;
            case SECONDARY -> com.google.android.material.R.attr.colorOnSecondaryContainer;
            case TERTIARY -> com.google.android.material.R.attr.colorOnTertiaryContainer;
            case WARNING -> com.google.android.material.R.attr.colorOnErrorContainer;
        };
    }

    public enum BubbleColor {
        SURFACE,
        SURFACE_HIGH,
        PRIMARY,
        SECONDARY,
        TERTIARY,
        WARNING;

        private static final Collection<BubbleColor> SURFACES =
                Arrays.asList(BubbleColor.SURFACE, BubbleColor.SURFACE_HIGH);
    }

    private static class BubbleDesign {
        public final boolean colorfulChatBubbles;
        public final boolean alignStart;
        public final boolean largeFont;
        public final boolean showAvatars11;
        public final boolean showAvatarsAccounts;

        private BubbleDesign(
                final boolean colorfulChatBubbles,
                final boolean alignStart,
                final boolean largeFont,
                final boolean showAvatars11,
                final boolean showAvatarsAccounts) {
            this.colorfulChatBubbles = colorfulChatBubbles;
            this.alignStart = alignStart;
            this.largeFont = largeFont;
            this.showAvatars11 = showAvatars11;
            this.showAvatarsAccounts = showAvatarsAccounts;
        }
    }

    private abstract static class MessageItemViewHolder /*extends RecyclerView.ViewHolder*/ {

        private final View itemView;

        private MessageItemViewHolder(@NonNull View itemView) {
            this.itemView = itemView;
        }
    }

    private abstract static class BubbleMessageItemViewHolder extends MessageItemViewHolder {

        private BubbleMessageItemViewHolder(@NonNull View itemView) {
            super(itemView);
        }

        public abstract ConstraintLayout root();

        protected abstract ImageView indicatorEdit();

        protected abstract RelativeLayout audioPlayer();

        protected abstract LinearLayout messageBox();

        protected abstract MaterialButton downloadButton();

        protected abstract ImageView image();

        protected abstract ImageView indicatorSecurity();

        protected abstract ImageView indicatorReceived();

        protected abstract TextView time();

        protected abstract TextView messageBody();

        protected abstract ImageView contactPicture();

        protected abstract ChipGroup reactions();
    }

    private static class StartBubbleMessageItemViewHolder extends BubbleMessageItemViewHolder {

        private final ItemMessageStartBinding binding;

        public StartBubbleMessageItemViewHolder(@NonNull ItemMessageStartBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        @Override
        public ConstraintLayout root() {
            return (ConstraintLayout) this.binding.getRoot();
        }

        @Override
        protected ImageView indicatorEdit() {
            return this.binding.editIndicator;
        }

        @Override
        protected RelativeLayout audioPlayer() {
            return this.binding.messageContent.audioPlayer;
        }

        @Override
        protected LinearLayout messageBox() {
            return this.binding.messageBox;
        }

        @Override
        protected MaterialButton downloadButton() {
            return this.binding.messageContent.downloadButton;
        }

        @Override
        protected ImageView image() {
            return this.binding.messageContent.messageImage;
        }

        protected ImageView indicatorSecurity() {
            return this.binding.securityIndicator;
        }

        @Override
        protected ImageView indicatorReceived() {
            return this.binding.indicatorReceived;
        }

        @Override
        protected TextView time() {
            return this.binding.messageTime;
        }

        @Override
        protected TextView messageBody() {
            return this.binding.messageContent.messageBody;
        }

        protected TextView encryption() {
            return this.binding.messageEncryption;
        }

        @Override
        protected ImageView contactPicture() {
            return this.binding.messagePhoto;
        }

        @Override
        protected ChipGroup reactions() {
            return this.binding.reactions;
        }
    }

    private static class EndBubbleMessageItemViewHolder extends BubbleMessageItemViewHolder {

        private final ItemMessageEndBinding binding;

        private EndBubbleMessageItemViewHolder(@NonNull ItemMessageEndBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        @Override
        public ConstraintLayout root() {
            return (ConstraintLayout) this.binding.getRoot();
        }

        @Override
        protected ImageView indicatorEdit() {
            return this.binding.editIndicator;
        }

        @Override
        protected RelativeLayout audioPlayer() {
            return this.binding.messageContent.audioPlayer;
        }

        @Override
        protected LinearLayout messageBox() {
            return this.binding.messageBox;
        }

        @Override
        protected MaterialButton downloadButton() {
            return this.binding.messageContent.downloadButton;
        }

        @Override
        protected ImageView image() {
            return this.binding.messageContent.messageImage;
        }

        @Override
        protected ImageView indicatorSecurity() {
            return this.binding.securityIndicator;
        }

        @Override
        protected ImageView indicatorReceived() {
            return this.binding.indicatorReceived;
        }

        @Override
        protected TextView time() {
            return this.binding.messageTime;
        }

        @Override
        protected TextView messageBody() {
            return this.binding.messageContent.messageBody;
        }

        @Override
        protected ImageView contactPicture() {
            return this.binding.messagePhoto;
        }

        @Override
        protected ChipGroup reactions() {
            return this.binding.reactions;
        }
    }

    private static class DateSeperatorMessageItemViewHolder extends MessageItemViewHolder {

        private final ItemMessageDateBubbleBinding binding;

        private DateSeperatorMessageItemViewHolder(@NonNull ItemMessageDateBubbleBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    private static class RtpSessionMessageItemViewHolder extends MessageItemViewHolder {

        private final ItemMessageRtpSessionBinding binding;

        private RtpSessionMessageItemViewHolder(@NonNull ItemMessageRtpSessionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    private static class StatusMessageItemViewHolder extends MessageItemViewHolder {

        private final ItemMessageStatusBinding binding;

        private StatusMessageItemViewHolder(@NonNull ItemMessageStatusBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
