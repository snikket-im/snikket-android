package eu.siacs.conversations.ui.adapter;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.util.Linkify;
import android.util.DisplayMetrics;
import android.util.Patterns;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.XmppAxolotlSession;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Message.FileParams;
import eu.siacs.conversations.entities.Transferable;
import eu.siacs.conversations.ui.ConversationActivity;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.GeoHelper;
import eu.siacs.conversations.utils.UIHelper;

public class MessageAdapter extends ArrayAdapter<Message> {

	private static final int SENT = 0;
	private static final int RECEIVED = 1;
	private static final int STATUS = 2;
	private static final Pattern XMPP_PATTERN = Pattern
			.compile("xmpp\\:(?:(?:["
					+ Patterns.GOOD_IRI_CHAR
					+ "\\;\\/\\?\\@\\&\\=\\#\\~\\-\\.\\+\\!\\*\\'\\(\\)\\,\\_])"
					+ "|(?:\\%[a-fA-F0-9]{2}))+");

	private ConversationActivity activity;

	private DisplayMetrics metrics;

	private OnContactPictureClicked mOnContactPictureClickedListener;
	private OnContactPictureLongClicked mOnContactPictureLongClickedListener;

	private OnLongClickListener openContextMenu = new OnLongClickListener() {

		@Override
		public boolean onLongClick(View v) {
			v.showContextMenu();
			return true;
		}
	};
	private boolean mIndicateReceived = false;
	private boolean mUseGreenBackground = false;

	public MessageAdapter(ConversationActivity activity, List<Message> messages) {
		super(activity, 0, messages);
		this.activity = activity;
		metrics = getContext().getResources().getDisplayMetrics();
		updatePreferences();
	}

	public void setOnContactPictureClicked(OnContactPictureClicked listener) {
		this.mOnContactPictureClickedListener = listener;
	}

	public void setOnContactPictureLongClicked(
			OnContactPictureLongClicked listener) {
		this.mOnContactPictureLongClickedListener = listener;
			}

	@Override
	public int getViewTypeCount() {
		return 3;
	}

	public int getItemViewType(Message message) {
		if (message.getType() == Message.TYPE_STATUS) {
			return STATUS;
		} else if (message.getStatus() <= Message.STATUS_RECEIVED) {
			return RECEIVED;
		}

		return SENT;
	}

	@Override
	public int getItemViewType(int position) {
		return this.getItemViewType(getItem(position));
	}

	private int getMessageTextColor(boolean onDark, boolean primary) {
		if (onDark) {
			return activity.getResources().getColor(primary ? R.color.white : R.color.white70);
		} else {
			return activity.getResources().getColor(primary ? R.color.black87 : R.color.black54);
		}
	}

	private void displayStatus(ViewHolder viewHolder, Message message, int type, boolean darkBackground) {
		String filesize = null;
		String info = null;
		boolean error = false;
		if (viewHolder.indicatorReceived != null) {
			viewHolder.indicatorReceived.setVisibility(View.GONE);
		}

		if (viewHolder.edit_indicator != null) {
			if (message.edited()) {
				viewHolder.edit_indicator.setVisibility(View.VISIBLE);
				viewHolder.edit_indicator.setImageResource(darkBackground ? R.drawable.ic_mode_edit_white_18dp : R.drawable.ic_mode_edit_black_18dp);
				viewHolder.edit_indicator.setAlpha(darkBackground ? 0.7f : 0.57f);
			} else {
				viewHolder.edit_indicator.setVisibility(View.GONE);
			}
		}
		boolean multiReceived = message.getConversation().getMode() == Conversation.MODE_MULTI
			&& message.getMergedStatus() <= Message.STATUS_RECEIVED;
		if (message.getType() == Message.TYPE_IMAGE || message.getType() == Message.TYPE_FILE || message.getTransferable() != null) {
			FileParams params = message.getFileParams();
			if (params.size > (1.5 * 1024 * 1024)) {
				filesize = params.size / (1024 * 1024)+ " MiB";
			} else if (params.size > 0) {
				filesize = params.size / 1024 + " KiB";
			}
			if (message.getTransferable() != null && message.getTransferable().getStatus() == Transferable.STATUS_FAILED) {
				error = true;
			}
		}
		switch (message.getMergedStatus()) {
			case Message.STATUS_WAITING:
				info = getContext().getString(R.string.waiting);
				break;
			case Message.STATUS_UNSEND:
				Transferable d = message.getTransferable();
				if (d!=null) {
					info = getContext().getString(R.string.sending_file,d.getProgress());
				} else {
					info = getContext().getString(R.string.sending);
				}
				break;
			case Message.STATUS_OFFERED:
				info = getContext().getString(R.string.offering);
				break;
			case Message.STATUS_SEND_RECEIVED:
				if (mIndicateReceived) {
					viewHolder.indicatorReceived.setVisibility(View.VISIBLE);
				}
				break;
			case Message.STATUS_SEND_DISPLAYED:
				if (mIndicateReceived) {
					viewHolder.indicatorReceived.setVisibility(View.VISIBLE);
				}
				break;
			case Message.STATUS_SEND_FAILED:
				info = getContext().getString(R.string.send_failed);
				error = true;
				break;
			default:
				if (multiReceived) {
					info = UIHelper.getMessageDisplayName(message);
				}
				break;
		}
		if (error && type == SENT) {
			viewHolder.time.setTextColor(activity.getWarningTextColor());
		} else {
			viewHolder.time.setTextColor(this.getMessageTextColor(darkBackground,false));
		}
		if (message.getEncryption() == Message.ENCRYPTION_NONE) {
			viewHolder.indicator.setVisibility(View.GONE);
		} else {
			viewHolder.indicator.setImageResource(darkBackground ? R.drawable.ic_lock_white_18dp : R.drawable.ic_lock_black_18dp);
			viewHolder.indicator.setVisibility(View.VISIBLE);
			if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL) {
				XmppAxolotlSession.Trust trust = message.getConversation()
						.getAccount().getAxolotlService().getFingerprintTrust(
								message.getFingerprint());

				if(trust == null || (!trust.trusted() && !trust.trustedInactive())) {
					viewHolder.indicator.setColorFilter(activity.getWarningTextColor());
					viewHolder.indicator.setAlpha(1.0f);
				} else {
					viewHolder.indicator.clearColorFilter();
					if (darkBackground) {
						viewHolder.indicator.setAlpha(0.7f);
					} else {
						viewHolder.indicator.setAlpha(0.57f);
					}
				}
			} else {
				viewHolder.indicator.clearColorFilter();
				if (darkBackground) {
					viewHolder.indicator.setAlpha(0.7f);
				} else {
					viewHolder.indicator.setAlpha(0.57f);
				}
			}
		}

		String formatedTime = UIHelper.readableTimeDifferenceFull(getContext(),
				message.getMergedTimeSent());
		if (message.getStatus() <= Message.STATUS_RECEIVED) {
			if ((filesize != null) && (info != null)) {
				viewHolder.time.setText(formatedTime + " \u00B7 " + filesize +" \u00B7 " + info);
			} else if ((filesize == null) && (info != null)) {
				viewHolder.time.setText(formatedTime + " \u00B7 " + info);
			} else if ((filesize != null) && (info == null)) {
				viewHolder.time.setText(formatedTime + " \u00B7 " + filesize);
			} else {
				viewHolder.time.setText(formatedTime);
			}
		} else {
			if ((filesize != null) && (info != null)) {
				viewHolder.time.setText(filesize + " \u00B7 " + info);
			} else if ((filesize == null) && (info != null)) {
				if (error) {
					viewHolder.time.setText(info + " \u00B7 " + formatedTime);
				} else {
					viewHolder.time.setText(info);
				}
			} else if ((filesize != null) && (info == null)) {
				viewHolder.time.setText(filesize + " \u00B7 " + formatedTime);
			} else {
				viewHolder.time.setText(formatedTime);
			}
		}
	}

	private void displayInfoMessage(ViewHolder viewHolder, String text, boolean darkBackground) {
		if (viewHolder.download_button != null) {
			viewHolder.download_button.setVisibility(View.GONE);
		}
		viewHolder.image.setVisibility(View.GONE);
		viewHolder.messageBody.setVisibility(View.VISIBLE);
		viewHolder.messageBody.setText(text);
		viewHolder.messageBody.setTextColor(getMessageTextColor(darkBackground, false));
		viewHolder.messageBody.setTypeface(null, Typeface.ITALIC);
		viewHolder.messageBody.setTextIsSelectable(false);
	}

	private void displayDecryptionFailed(ViewHolder viewHolder, boolean darkBackground) {
		if (viewHolder.download_button != null) {
			viewHolder.download_button.setVisibility(View.GONE);
		}
		viewHolder.image.setVisibility(View.GONE);
		viewHolder.messageBody.setVisibility(View.VISIBLE);
		viewHolder.messageBody.setText(getContext().getString(
				R.string.decryption_failed));
		viewHolder.messageBody.setTextColor(getMessageTextColor(darkBackground, false));
		viewHolder.messageBody.setTypeface(null, Typeface.NORMAL);
		viewHolder.messageBody.setTextIsSelectable(false);
	}

	private void displayHeartMessage(final ViewHolder viewHolder, final String body) {
		if (viewHolder.download_button != null) {
			viewHolder.download_button.setVisibility(View.GONE);
		}
		viewHolder.image.setVisibility(View.GONE);
		viewHolder.messageBody.setVisibility(View.VISIBLE);
		viewHolder.messageBody.setIncludeFontPadding(false);
		Spannable span = new SpannableString(body);
		span.setSpan(new RelativeSizeSpan(4.0f), 0, body.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		span.setSpan(new ForegroundColorSpan(activity.getWarningTextColor()), 0, body.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		viewHolder.messageBody.setText(span);
	}

	private void displayTextMessage(final ViewHolder viewHolder, final Message message, boolean darkBackground, int type) {
		if (viewHolder.download_button != null) {
			viewHolder.download_button.setVisibility(View.GONE);
		}
		viewHolder.image.setVisibility(View.GONE);
		viewHolder.messageBody.setVisibility(View.VISIBLE);
		viewHolder.messageBody.setIncludeFontPadding(true);
		if (message.getBody() != null) {
			final String nick = UIHelper.getMessageDisplayName(message);
			String body;
			try {
				body = message.getMergedBody().replaceAll("^" + Message.ME_COMMAND, nick + " ");
			} catch (ArrayIndexOutOfBoundsException e) {
				body = message.getMergedBody();
			}
			if (body.length() > Config.MAX_DISPLAY_MESSAGE_CHARS) {
				body = body.substring(0, Config.MAX_DISPLAY_MESSAGE_CHARS)+"\u2026";
			}
			final SpannableString formattedBody = new SpannableString(body);
			int i = body.indexOf(Message.MERGE_SEPARATOR);
			while(i >= 0) {
				final int end = i + Message.MERGE_SEPARATOR.length();
				formattedBody.setSpan(new RelativeSizeSpan(0.3f),i,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
				i = body.indexOf(Message.MERGE_SEPARATOR,end);
			}
			if (message.getType() != Message.TYPE_PRIVATE) {
				if (message.hasMeCommand()) {
					final Spannable span = new SpannableString(formattedBody);
					span.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), 0, nick.length(),
							Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
					viewHolder.messageBody.setText(span);
				} else {
					viewHolder.messageBody.setText(formattedBody);
				}
			} else {
				String privateMarker;
				if (message.getStatus() <= Message.STATUS_RECEIVED) {
					privateMarker = activity
						.getString(R.string.private_message);
				} else {
					final String to;
					if (message.getCounterpart() != null) {
						to = message.getCounterpart().getResourcepart();
					} else {
						to = "";
					}
					privateMarker = activity.getString(R.string.private_message_to, to);
				}
				final Spannable span = new SpannableString(privateMarker + " "
						+ formattedBody);
				span.setSpan(new ForegroundColorSpan(getMessageTextColor(darkBackground,false)), 0, privateMarker
						.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				span.setSpan(new StyleSpan(Typeface.BOLD), 0,
						privateMarker.length(),
						Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				if (message.hasMeCommand()) {
					span.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), privateMarker.length() + 1,
							privateMarker.length() + 1 + nick.length(),
							Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				}
				viewHolder.messageBody.setText(span);
			}
			int urlCount = 0;
			final Matcher matcher = Patterns.WEB_URL.matcher(body);
			int beginWebURL = Integer.MAX_VALUE;
			int endWebURL = 0;
			while (matcher.find()) {
				MatchResult result = matcher.toMatchResult();
				beginWebURL = result.start();
				endWebURL = result.end();
				urlCount++;
			}
			final Matcher geoMatcher = GeoHelper.GEO_URI.matcher(body);
			while (geoMatcher.find()) {
				urlCount++;
			}
			final Matcher xmppMatcher = XMPP_PATTERN.matcher(body);
			while (xmppMatcher.find()) {
				MatchResult result = xmppMatcher.toMatchResult();
				if (beginWebURL < result.start() || endWebURL > result.end()) {
					urlCount++;
				}
			}
			viewHolder.messageBody.setTextIsSelectable(urlCount <= 1);
			viewHolder.messageBody.setAutoLinkMask(0);
			Linkify.addLinks(viewHolder.messageBody, Linkify.WEB_URLS);
			Linkify.addLinks(viewHolder.messageBody, XMPP_PATTERN, "xmpp");
			Linkify.addLinks(viewHolder.messageBody, GeoHelper.GEO_URI, "geo");
		} else {
			viewHolder.messageBody.setText("");
			viewHolder.messageBody.setTextIsSelectable(false);
		}
		viewHolder.messageBody.setTextColor(this.getMessageTextColor(darkBackground, true));
		viewHolder.messageBody.setLinkTextColor(this.getMessageTextColor(darkBackground, true));
		viewHolder.messageBody.setHighlightColor(activity.getResources().getColor(darkBackground ? (type == SENT || !mUseGreenBackground ? R.color.black26 : R.color.grey800) : R.color.grey500));
		viewHolder.messageBody.setTypeface(null, Typeface.NORMAL);
		viewHolder.messageBody.setOnLongClickListener(openContextMenu);
	}

	private void displayDownloadableMessage(ViewHolder viewHolder,
			final Message message, String text) {
		viewHolder.image.setVisibility(View.GONE);
		viewHolder.messageBody.setVisibility(View.GONE);
		viewHolder.download_button.setVisibility(View.VISIBLE);
		viewHolder.download_button.setText(text);
		viewHolder.download_button.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				activity.startDownloadable(message);
			}
		});
		viewHolder.download_button.setOnLongClickListener(openContextMenu);
	}

	private void displayOpenableMessage(ViewHolder viewHolder,final Message message) {
		viewHolder.image.setVisibility(View.GONE);
		viewHolder.messageBody.setVisibility(View.GONE);
		viewHolder.download_button.setVisibility(View.VISIBLE);
		viewHolder.download_button.setText(activity.getString(R.string.open_x_file, UIHelper.getFileDescriptionString(activity, message)));
		viewHolder.download_button.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				openDownloadable(message);
			}
		});
		viewHolder.download_button.setOnLongClickListener(openContextMenu);
	}

	private void displayLocationMessage(ViewHolder viewHolder, final Message message) {
		viewHolder.image.setVisibility(View.GONE);
		viewHolder.messageBody.setVisibility(View.GONE);
		viewHolder.download_button.setVisibility(View.VISIBLE);
		viewHolder.download_button.setText(R.string.show_location);
		viewHolder.download_button.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				showLocation(message);
			}
		});
		viewHolder.download_button.setOnLongClickListener(openContextMenu);
	}

	private void displayImageMessage(ViewHolder viewHolder,
			final Message message) {
		if (viewHolder.download_button != null) {
			viewHolder.download_button.setVisibility(View.GONE);
		}
		viewHolder.messageBody.setVisibility(View.GONE);
		viewHolder.image.setVisibility(View.VISIBLE);
		FileParams params = message.getFileParams();
		double target = metrics.density * 288;
		int scalledW;
		int scalledH;
		if (params.width <= params.height) {
			scalledW = (int) (params.width / ((double) params.height / target));
			scalledH = (int) target;
		} else {
			scalledW = (int) target;
			scalledH = (int) (params.height / ((double) params.width / target));
		}
		LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(scalledW, scalledH);
		layoutParams.setMargins(0, (int) (metrics.density * 4), 0, (int) (metrics.density * 4));
		viewHolder.image.setLayoutParams(layoutParams);
		activity.loadBitmap(message, viewHolder.image);
		viewHolder.image.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				openDownloadable(message);
			}
		});
		viewHolder.image.setOnLongClickListener(openContextMenu);
	}

	private void loadMoreMessages(Conversation conversation) {
		conversation.setLastClearHistory(0);
		conversation.setHasMessagesLeftOnServer(true);
		conversation.setFirstMamReference(null);
		long timestamp = conversation.getLastMessageTransmitted();
		if (timestamp == 0) {
			timestamp = System.currentTimeMillis();
		}
		activity.setMessagesLoaded();
		activity.xmppConnectionService.getMessageArchiveService().query(conversation, 0, timestamp);
		Toast.makeText(activity, R.string.fetching_history_from_server,Toast.LENGTH_LONG).show();
	}

	@Override
	public View getView(int position, View view, ViewGroup parent) {
		final Message message = getItem(position);
		final boolean isInValidSession = message.isValidInSession();
		final Conversation conversation = message.getConversation();
		final Account account = conversation.getAccount();
		final int type = getItemViewType(position);
		ViewHolder viewHolder;
		if (view == null) {
			viewHolder = new ViewHolder();
			switch (type) {
				case SENT:
					view = activity.getLayoutInflater().inflate(
							R.layout.message_sent, parent, false);
					viewHolder.message_box = (LinearLayout) view
						.findViewById(R.id.message_box);
					viewHolder.contact_picture = (ImageView) view
						.findViewById(R.id.message_photo);
					viewHolder.download_button = (Button) view
						.findViewById(R.id.download_button);
					viewHolder.indicator = (ImageView) view
						.findViewById(R.id.security_indicator);
					viewHolder.edit_indicator = (ImageView) view.findViewById(R.id.edit_indicator);
					viewHolder.image = (ImageView) view
						.findViewById(R.id.message_image);
					viewHolder.messageBody = (TextView) view
						.findViewById(R.id.message_body);
					viewHolder.time = (TextView) view
						.findViewById(R.id.message_time);
					viewHolder.indicatorReceived = (ImageView) view
						.findViewById(R.id.indicator_received);
					break;
				case RECEIVED:
					view = activity.getLayoutInflater().inflate(
							R.layout.message_received, parent, false);
					viewHolder.message_box = (LinearLayout) view
						.findViewById(R.id.message_box);
					viewHolder.contact_picture = (ImageView) view
						.findViewById(R.id.message_photo);
					viewHolder.download_button = (Button) view
						.findViewById(R.id.download_button);
					viewHolder.indicator = (ImageView) view
						.findViewById(R.id.security_indicator);
					viewHolder.edit_indicator = (ImageView) view.findViewById(R.id.edit_indicator);
					viewHolder.image = (ImageView) view
						.findViewById(R.id.message_image);
					viewHolder.messageBody = (TextView) view
						.findViewById(R.id.message_body);
					viewHolder.time = (TextView) view
						.findViewById(R.id.message_time);
					viewHolder.indicatorReceived = (ImageView) view
						.findViewById(R.id.indicator_received);
					viewHolder.encryption = (TextView) view.findViewById(R.id.message_encryption);
					break;
				case STATUS:
					view = activity.getLayoutInflater().inflate(R.layout.message_status, parent, false);
					viewHolder.contact_picture = (ImageView) view.findViewById(R.id.message_photo);
					viewHolder.status_message = (TextView) view.findViewById(R.id.status_message);
					viewHolder.load_more_messages = (Button) view.findViewById(R.id.load_more_messages);
					break;
				default:
					viewHolder = null;
					break;
			}
			view.setTag(viewHolder);
		} else {
			viewHolder = (ViewHolder) view.getTag();
			if (viewHolder == null) {
				return view;
			}
		}

		boolean darkBackground = type == RECEIVED && (!isInValidSession || mUseGreenBackground) || activity.isDarkTheme();

		if (type == STATUS) {
			if ("LOAD_MORE".equals(message.getBody())) {
				viewHolder.status_message.setVisibility(View.GONE);
				viewHolder.contact_picture.setVisibility(View.GONE);
				viewHolder.load_more_messages.setVisibility(View.VISIBLE);
				viewHolder.load_more_messages.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						loadMoreMessages(message.getConversation());
					}
				});
			} else {
				viewHolder.status_message.setVisibility(View.VISIBLE);
				viewHolder.contact_picture.setVisibility(View.VISIBLE);
				viewHolder.load_more_messages.setVisibility(View.GONE);
				if (conversation.getMode() == Conversation.MODE_SINGLE) {
					viewHolder.contact_picture.setImageBitmap(activity
							.avatarService().get(conversation.getContact(),
									activity.getPixel(32)));
					viewHolder.contact_picture.setAlpha(0.5f);
				}
				viewHolder.status_message.setText(message.getBody());
			}
			return view;
		} else {
			loadAvatar(message, viewHolder.contact_picture);
		}

		viewHolder.contact_picture
			.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					if (MessageAdapter.this.mOnContactPictureClickedListener != null) {
						MessageAdapter.this.mOnContactPictureClickedListener
								.onContactPictureClicked(message);
					}

				}
			});
		viewHolder.contact_picture
			.setOnLongClickListener(new OnLongClickListener() {

				@Override
				public boolean onLongClick(View v) {
					if (MessageAdapter.this.mOnContactPictureLongClickedListener != null) {
						MessageAdapter.this.mOnContactPictureLongClickedListener
								.onContactPictureLongClicked(message);
						return true;
					} else {
						return false;
					}
				}
			});

		final Transferable transferable = message.getTransferable();
		if (transferable != null && transferable.getStatus() != Transferable.STATUS_UPLOADING) {
			if (transferable.getStatus() == Transferable.STATUS_OFFER) {
				displayDownloadableMessage(viewHolder,message,activity.getString(R.string.download_x_file, UIHelper.getFileDescriptionString(activity, message)));
			} else if (transferable.getStatus() == Transferable.STATUS_OFFER_CHECK_FILESIZE) {
				displayDownloadableMessage(viewHolder, message, activity.getString(R.string.check_x_filesize, UIHelper.getFileDescriptionString(activity, message)));
			} else {
				displayInfoMessage(viewHolder, UIHelper.getMessagePreview(activity, message).first,darkBackground);
			}
		} else if (message.getType() == Message.TYPE_IMAGE && message.getEncryption() != Message.ENCRYPTION_PGP && message.getEncryption() != Message.ENCRYPTION_DECRYPTION_FAILED) {
			displayImageMessage(viewHolder, message);
		} else if (message.getType() == Message.TYPE_FILE && message.getEncryption() != Message.ENCRYPTION_PGP && message.getEncryption() != Message.ENCRYPTION_DECRYPTION_FAILED) {
			if (message.getFileParams().width > 0) {
				displayImageMessage(viewHolder,message);
			} else {
				displayOpenableMessage(viewHolder, message);
			}
		} else if (message.getEncryption() == Message.ENCRYPTION_PGP) {
			if (activity.hasPgp()) {
				if (account.getPgpDecryptionService().isRunning()) {
					displayInfoMessage(viewHolder, activity.getString(R.string.message_decrypting), darkBackground);
				} else {
					displayInfoMessage(viewHolder, activity.getString(R.string.pgp_message), darkBackground);
				}
			} else {
				displayInfoMessage(viewHolder,activity.getString(R.string.install_openkeychain),darkBackground);
				if (viewHolder != null) {
					viewHolder.message_box
						.setOnClickListener(new OnClickListener() {

							@Override
							public void onClick(View v) {
								activity.showInstallPgpDialog();
							}
						});
				}
			}
		} else if (message.getEncryption() == Message.ENCRYPTION_DECRYPTION_FAILED) {
			displayDecryptionFailed(viewHolder,darkBackground);
		} else {
			if (GeoHelper.isGeoUri(message.getBody())) {
				displayLocationMessage(viewHolder,message);
			} else if (message.bodyIsHeart()) {
				displayHeartMessage(viewHolder, message.getBody().trim());
			} else if (message.treatAsDownloadable() == Message.Decision.MUST) {
				try {
					URL url = new URL(message.getBody());
					displayDownloadableMessage(viewHolder,
							message,
							activity.getString(R.string.check_x_filesize_on_host,
									UIHelper.getFileDescriptionString(activity, message),
									url.getHost()));
				} catch (Exception e) {
					displayDownloadableMessage(viewHolder,
							message,
							activity.getString(R.string.check_x_filesize,
									UIHelper.getFileDescriptionString(activity, message)));
				}
			} else {
				displayTextMessage(viewHolder, message, darkBackground, type);
			}
		}

		if (type == RECEIVED) {
			if(isInValidSession) {
				if (!mUseGreenBackground) {
					int bubble = activity.getThemeResource(R.attr.message_bubble_received_monochrome, R.drawable.message_bubble_received_white);
					viewHolder.message_box.setBackgroundResource(bubble);
				} else {
					viewHolder.message_box.setBackgroundResource(R.drawable.message_bubble_received);
				}
				viewHolder.encryption.setVisibility(View.GONE);
			} else {
				viewHolder.message_box.setBackgroundResource(R.drawable.message_bubble_received_warning);
				viewHolder.encryption.setVisibility(View.VISIBLE);
				viewHolder.encryption.setText(CryptoHelper.encryptionTypeToText(message.getEncryption()));
			}
		}

		displayStatus(viewHolder, message, type, darkBackground);

		return view;
	}

	public void openDownloadable(Message message) {
		DownloadableFile file = activity.xmppConnectionService.getFileBackend().getFile(message);
		if (!file.exists()) {
			Toast.makeText(activity,R.string.file_deleted,Toast.LENGTH_SHORT).show();
			return;
		}
		Intent openIntent = new Intent(Intent.ACTION_VIEW);
		String mime = file.getMimeType();
		if (mime == null) {
			mime = "*/*";
		}
		openIntent.setDataAndType(Uri.fromFile(file), mime);
		PackageManager manager = activity.getPackageManager();
		List<ResolveInfo> infos = manager.queryIntentActivities(openIntent, 0);
		if (infos.size() == 0) {
			openIntent.setDataAndType(Uri.fromFile(file),"*/*");
		}
		try {
			getContext().startActivity(openIntent);
			return;
		}  catch (ActivityNotFoundException e) {
			//ignored
		}
		Toast.makeText(activity,R.string.no_application_found_to_open_file,Toast.LENGTH_SHORT).show();
	}

	public void showLocation(Message message) {
		for(Intent intent : GeoHelper.createGeoIntentsFromMessage(message)) {
			if (intent.resolveActivity(getContext().getPackageManager()) != null) {
				getContext().startActivity(intent);
				return;
			}
		}
		Toast.makeText(activity,R.string.no_application_found_to_display_location,Toast.LENGTH_SHORT).show();
	}

	public void updatePreferences() {
		this.mIndicateReceived = activity.indicateReceived();
		this.mUseGreenBackground = activity.useGreenBackground();
	}

	public interface OnContactPictureClicked {
		void onContactPictureClicked(Message message);
	}

	public interface OnContactPictureLongClicked {
		void onContactPictureLongClicked(Message message);
	}

	private static class ViewHolder {

		protected LinearLayout message_box;
		protected Button download_button;
		protected ImageView image;
		protected ImageView indicator;
		protected ImageView indicatorReceived;
		protected TextView time;
		protected TextView messageBody;
		protected ImageView contact_picture;
		protected TextView status_message;
		protected TextView encryption;
		public Button load_more_messages;
		public ImageView edit_indicator;
	}

	class BitmapWorkerTask extends AsyncTask<Message, Void, Bitmap> {
		private final WeakReference<ImageView> imageViewReference;
		private Message message = null;

		public BitmapWorkerTask(ImageView imageView) {
			imageViewReference = new WeakReference<>(imageView);
		}

		@Override
		protected Bitmap doInBackground(Message... params) {
			return activity.avatarService().get(params[0], activity.getPixel(48), isCancelled());
		}

		@Override
		protected void onPostExecute(Bitmap bitmap) {
			if (bitmap != null && !isCancelled()) {
				final ImageView imageView = imageViewReference.get();
				if (imageView != null) {
					imageView.setImageBitmap(bitmap);
					imageView.setBackgroundColor(0x00000000);
				}
			}
		}
	}

	public void loadAvatar(Message message, ImageView imageView) {
		if (cancelPotentialWork(message, imageView)) {
			final Bitmap bm = activity.avatarService().get(message, activity.getPixel(48), true);
			if (bm != null) {
				cancelPotentialWork(message, imageView);
				imageView.setImageBitmap(bm);
				imageView.setBackgroundColor(0x00000000);
			} else {
				imageView.setBackgroundColor(UIHelper.getColorForName(UIHelper.getMessageDisplayName(message)));
				imageView.setImageDrawable(null);
				final BitmapWorkerTask task = new BitmapWorkerTask(imageView);
				final AsyncDrawable asyncDrawable = new AsyncDrawable(activity.getResources(), null, task);
				imageView.setImageDrawable(asyncDrawable);
				try {
					task.execute(message);
				} catch (final RejectedExecutionException ignored) {
				}
			}
		}
	}

	public static boolean cancelPotentialWork(Message message, ImageView imageView) {
		final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

		if (bitmapWorkerTask != null) {
			final Message oldMessage = bitmapWorkerTask.message;
			if (oldMessage == null || message != oldMessage) {
				bitmapWorkerTask.cancel(true);
			} else {
				return false;
			}
		}
		return true;
	}

	private static BitmapWorkerTask getBitmapWorkerTask(ImageView imageView) {
		if (imageView != null) {
			final Drawable drawable = imageView.getDrawable();
			if (drawable instanceof AsyncDrawable) {
				final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
				return asyncDrawable.getBitmapWorkerTask();
			}
		}
		return null;
	}

	static class AsyncDrawable extends BitmapDrawable {
		private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;

		public AsyncDrawable(Resources res, Bitmap bitmap, BitmapWorkerTask bitmapWorkerTask) {
			super(res, bitmap);
			bitmapWorkerTaskReference = new WeakReference<>(bitmapWorkerTask);
		}

		public BitmapWorkerTask getBitmapWorkerTask() {
			return bitmapWorkerTaskReference.get();
		}
	}
}
