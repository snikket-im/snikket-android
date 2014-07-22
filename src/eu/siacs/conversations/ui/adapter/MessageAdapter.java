package eu.siacs.conversations.ui.adapter;

import java.util.HashMap;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.ImageProvider;
import eu.siacs.conversations.ui.ConversationActivity;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xmpp.jingle.JingleConnection;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MessageAdapter extends ArrayAdapter<Message> {

	private static final int SENT = 0;
	private static final int RECIEVED = 1;
	private static final int STATUS = 2;

	private ConversationActivity activity;

	private Bitmap selfBitmap2;

	private BitmapCache mBitmapCache = new BitmapCache();
	private DisplayMetrics metrics;

	private boolean useSubject = true;

	private OnContactPictureClicked mOnContactPictureClickedListener;

	public MessageAdapter(ConversationActivity activity, List<Message> messages) {
		super(activity, 0, messages);
		this.activity = activity;
		metrics = getContext().getResources().getDisplayMetrics();
		SharedPreferences preferences = PreferenceManager
				.getDefaultSharedPreferences(getContext());
		useSubject = preferences.getBoolean("use_subject_in_muc", true);
	}

	private Bitmap getSelfBitmap() {
		if (this.selfBitmap2 == null) {

			if (getCount() > 0) {
				SharedPreferences preferences = PreferenceManager
						.getDefaultSharedPreferences(getContext());
				boolean showPhoneSelfContactPicture = preferences.getBoolean(
						"show_phone_selfcontact_picture", true);

				this.selfBitmap2 = UIHelper.getSelfContactPicture(getItem(0)
						.getConversation().getAccount(), 48,
						showPhoneSelfContactPicture, getContext());
			}
		}
		return this.selfBitmap2;
	}

	public void setOnContactPictureClicked(OnContactPictureClicked listener) {
		this.mOnContactPictureClickedListener = listener;
	}

	@Override
	public int getViewTypeCount() {
		return 3;
	}

	@Override
	public int getItemViewType(int position) {
		if (getItem(position).getType() == Message.TYPE_STATUS) {
			return STATUS;
		} else if (getItem(position).getStatus() <= Message.STATUS_RECIEVED) {
			return RECIEVED;
		} else {
			return SENT;
		}
	}

	private void displayStatus(ViewHolder viewHolder, Message message) {
		String filesize = null;
		String info = null;
		boolean error = false;
		boolean multiReceived = message.getConversation().getMode() == Conversation.MODE_MULTI
				&& message.getStatus() <= Message.STATUS_RECIEVED;
		if (message.getType() == Message.TYPE_IMAGE) {
			String[] fileParams = message.getBody().split(",");
			try {
				long size = Long.parseLong(fileParams[0]);
				filesize = size / 1024 + " KB";
			} catch (NumberFormatException e) {
				filesize = "0 KB";
			}
		}
		switch (message.getStatus()) {
		case Message.STATUS_WAITING:
			info = getContext().getString(R.string.waiting);
			break;
		case Message.STATUS_UNSEND:
			info = getContext().getString(R.string.sending);
			break;
		case Message.STATUS_OFFERED:
			info = getContext().getString(R.string.offering);
			break;
		case Message.STATUS_SEND_FAILED:
			info = getContext().getString(R.string.send_failed);
			error = true;
			break;
		case Message.STATUS_SEND_REJECTED:
			info = getContext().getString(R.string.send_rejected);
			error = true;
			break;
		case Message.STATUS_RECEPTION_FAILED:
			info = getContext().getString(R.string.reception_failed);
			error = true;
		default:
			if (multiReceived) {
				info = message.getCounterpart();
			}
			break;
		}
		if (error) {
			viewHolder.time.setTextColor(0xFFe92727);
		} else {
			viewHolder.time.setTextColor(activity.getSecondaryTextColor());
		}
		if (message.getEncryption() == Message.ENCRYPTION_NONE) {
			viewHolder.indicator.setVisibility(View.GONE);
		} else {
			viewHolder.indicator.setVisibility(View.VISIBLE);
		}

		String formatedTime = UIHelper.readableTimeDifference(getContext(),
				message.getTimeSent());
		if (message.getStatus() <= Message.STATUS_RECIEVED) {
			if ((filesize != null) && (info != null)) {
				viewHolder.time.setText(filesize + " \u00B7 " + info);
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

	private void displayInfoMessage(ViewHolder viewHolder, int r) {
		if (viewHolder.download_button != null) {
			viewHolder.download_button.setVisibility(View.GONE);
		}
		viewHolder.image.setVisibility(View.GONE);
		viewHolder.messageBody.setVisibility(View.VISIBLE);
		viewHolder.messageBody.setText(getContext().getString(r));
		viewHolder.messageBody.setTextColor(0xff33B5E5);
		viewHolder.messageBody.setTypeface(null, Typeface.ITALIC);
		viewHolder.messageBody.setTextIsSelectable(false);
	}

	private void displayDecryptionFailed(ViewHolder viewHolder) {
		if (viewHolder.download_button != null) {
			viewHolder.download_button.setVisibility(View.GONE);
		}
		viewHolder.image.setVisibility(View.GONE);
		viewHolder.messageBody.setVisibility(View.VISIBLE);
		viewHolder.messageBody.setText(getContext().getString(
				R.string.decryption_failed));
		viewHolder.messageBody.setTextColor(0xFFe92727);
		viewHolder.messageBody.setTypeface(null, Typeface.NORMAL);
		viewHolder.messageBody.setTextIsSelectable(false);
	}

	private void displayTextMessage(ViewHolder viewHolder, String text) {
		if (viewHolder.download_button != null) {
			viewHolder.download_button.setVisibility(View.GONE);
		}
		viewHolder.image.setVisibility(View.GONE);
		viewHolder.messageBody.setVisibility(View.VISIBLE);
		if (text != null) {
			viewHolder.messageBody.setText(text.trim());
		} else {
			viewHolder.messageBody.setText("");
		}
		viewHolder.messageBody.setTextColor(activity.getPrimaryTextColor());
		viewHolder.messageBody.setTypeface(null, Typeface.NORMAL);
		viewHolder.messageBody.setTextIsSelectable(true);
	}

	private void displayImageMessage(ViewHolder viewHolder,
			final Message message) {
		if (viewHolder.download_button != null) {
			viewHolder.download_button.setVisibility(View.GONE);
		}
		viewHolder.messageBody.setVisibility(View.GONE);
		viewHolder.image.setVisibility(View.VISIBLE);
		String[] fileParams = message.getBody().split(",");
		if (fileParams.length == 3) {
			double target = metrics.density * 288;
			int w = Integer.parseInt(fileParams[1]);
			int h = Integer.parseInt(fileParams[2]);
			int scalledW;
			int scalledH;
			if (w <= h) {
				scalledW = (int) (w / ((double) h / target));
				scalledH = (int) target;
			} else {
				scalledW = (int) target;
				scalledH = (int) (h / ((double) w / target));
			}
			viewHolder.image.setLayoutParams(new LinearLayout.LayoutParams(
					scalledW, scalledH));
		}
		activity.loadBitmap(message, viewHolder.image);
		viewHolder.image.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				Intent intent = new Intent(Intent.ACTION_VIEW);
				intent.setDataAndType(ImageProvider.getContentUri(message),
						"image/*");
				getContext().startActivity(intent);
			}
		});
		viewHolder.image.setOnLongClickListener(new OnLongClickListener() {

			@Override
			public boolean onLongClick(View v) {
				Intent shareIntent = new Intent();
				shareIntent.setAction(Intent.ACTION_SEND);
				shareIntent.putExtra(Intent.EXTRA_STREAM,
						ImageProvider.getContentUri(message));
				shareIntent.setType("image/webp");
				getContext().startActivity(
						Intent.createChooser(shareIntent,
								getContext().getText(R.string.share_with)));
				return true;
			}
		});
	}

	@Override
	public View getView(int position, View view, ViewGroup parent) {
		final Message item = getItem(position);
		int type = getItemViewType(position);
		ViewHolder viewHolder;
		if (view == null) {
			viewHolder = new ViewHolder();
			switch (type) {
			case SENT:
				view = (View) activity.getLayoutInflater().inflate(
						R.layout.message_sent, null);
				viewHolder.message_box = (LinearLayout) view
						.findViewById(R.id.message_box);
				viewHolder.contact_picture = (ImageView) view
						.findViewById(R.id.message_photo);
				viewHolder.contact_picture.setImageBitmap(getSelfBitmap());
				viewHolder.indicator = (ImageView) view
						.findViewById(R.id.security_indicator);
				viewHolder.image = (ImageView) view
						.findViewById(R.id.message_image);
				viewHolder.messageBody = (TextView) view
						.findViewById(R.id.message_body);
				viewHolder.time = (TextView) view
						.findViewById(R.id.message_time);
				view.setTag(viewHolder);
				break;
			case RECIEVED:
				view = (View) activity.getLayoutInflater().inflate(
						R.layout.message_recieved, null);
				viewHolder.message_box = (LinearLayout) view
						.findViewById(R.id.message_box);
				viewHolder.contact_picture = (ImageView) view
						.findViewById(R.id.message_photo);

				viewHolder.download_button = (Button) view
						.findViewById(R.id.download_button);

				if (item.getConversation().getMode() == Conversation.MODE_SINGLE) {

					viewHolder.contact_picture.setImageBitmap(mBitmapCache.get(
							item.getConversation().getName(useSubject), item
									.getConversation().getContact(),
							getContext()));

				}
				viewHolder.indicator = (ImageView) view
						.findViewById(R.id.security_indicator);
				viewHolder.image = (ImageView) view
						.findViewById(R.id.message_image);
				viewHolder.messageBody = (TextView) view
						.findViewById(R.id.message_body);
				viewHolder.time = (TextView) view
						.findViewById(R.id.message_time);
				view.setTag(viewHolder);
				break;
			case STATUS:
				view = (View) activity.getLayoutInflater().inflate(
						R.layout.message_status, null);
				viewHolder.contact_picture = (ImageView) view
						.findViewById(R.id.message_photo);
				if (item.getConversation().getMode() == Conversation.MODE_SINGLE) {

					viewHolder.contact_picture.setImageBitmap(mBitmapCache.get(
							item.getConversation().getName(useSubject), item
									.getConversation().getContact(),
							getContext()));
					viewHolder.contact_picture.setAlpha(128);
					viewHolder.contact_picture
							.setOnClickListener(new OnClickListener() {

								@Override
								public void onClick(View v) {
									Toast.makeText(
											getContext(),
											R.string.contact_has_read_up_to_this_point,
											Toast.LENGTH_SHORT).show();
								}
							});

				}
				break;
			default:
				viewHolder = null;
				break;
			}
		} else {
			viewHolder = (ViewHolder) view.getTag();
		}

		if (type == STATUS) {
			return view;
		}

		if (type == RECIEVED) {
			if (item.getConversation().getMode() == Conversation.MODE_MULTI) {
				viewHolder.contact_picture.setImageBitmap(mBitmapCache.get(
						item.getCounterpart(), null, getContext()));
				viewHolder.contact_picture
						.setOnClickListener(new OnClickListener() {

							@Override
							public void onClick(View v) {
								if (MessageAdapter.this.mOnContactPictureClickedListener != null) {
									MessageAdapter.this.mOnContactPictureClickedListener
											.onContactPictureClicked(item);
									;
								}

							}
						});
			}
		}

		if (item.getType() == Message.TYPE_IMAGE) {
			if (item.getStatus() == Message.STATUS_RECIEVING) {
				displayInfoMessage(viewHolder, R.string.receiving_image);
			} else if (item.getStatus() == Message.STATUS_RECEIVED_OFFER) {
				viewHolder.image.setVisibility(View.GONE);
				viewHolder.messageBody.setVisibility(View.GONE);
				viewHolder.download_button.setVisibility(View.VISIBLE);
				viewHolder.download_button
						.setOnClickListener(new OnClickListener() {

							@Override
							public void onClick(View v) {
								JingleConnection connection = item
										.getJingleConnection();
								if (connection != null) {
									connection.accept();
								}
							}
						});
			} else if ((item.getEncryption() == Message.ENCRYPTION_DECRYPTED)
					|| (item.getEncryption() == Message.ENCRYPTION_NONE)
					|| (item.getEncryption() == Message.ENCRYPTION_OTR)) {
				displayImageMessage(viewHolder, item);
			} else if (item.getEncryption() == Message.ENCRYPTION_PGP) {
				displayInfoMessage(viewHolder, R.string.encrypted_message);
			} else {
				displayDecryptionFailed(viewHolder);
			}
		} else {
			if (item.getEncryption() == Message.ENCRYPTION_PGP) {
				if (activity.hasPgp()) {
					displayInfoMessage(viewHolder, R.string.encrypted_message);
				} else {
					displayInfoMessage(viewHolder,
							R.string.install_openkeychain);
					viewHolder.message_box
							.setOnClickListener(new OnClickListener() {

								@Override
								public void onClick(View v) {
									activity.showInstallPgpDialog();
								}
							});
				}
			} else if (item.getEncryption() == Message.ENCRYPTION_DECRYPTION_FAILED) {
				displayDecryptionFailed(viewHolder);
			} else {
				displayTextMessage(viewHolder, item.getBody());
			}
		}

		displayStatus(viewHolder, item);

		return view;
	}

	private static class ViewHolder {

		protected LinearLayout message_box;
		protected Button download_button;
		protected ImageView image;
		protected ImageView indicator;
		protected TextView time;
		protected TextView messageBody;
		protected ImageView contact_picture;

	}

	private class BitmapCache {
		private HashMap<String, Bitmap> bitmaps = new HashMap<String, Bitmap>();

		public Bitmap get(String name, Contact contact, Context context) {
			if (bitmaps.containsKey(name)) {
				return bitmaps.get(name);
			} else {
				Bitmap bm;
				if (contact != null) {
					bm = UIHelper
							.getContactPicture(contact, 48, context, false);
				} else {
					bm = UIHelper.getContactPicture(name, 48, context, false);
				}
				bitmaps.put(name, bm);
				return bm;
			}
		}
	}

	public interface OnContactPictureClicked {
		public void onContactPictureClicked(Message message);
	}
}
