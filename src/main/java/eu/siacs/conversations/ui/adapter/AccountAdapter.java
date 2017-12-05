package eu.siacs.conversations.ui.adapter;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.ui.ManageAccountActivity;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.ui.widget.Switch;
import eu.siacs.conversations.utils.UIHelper;

public class AccountAdapter extends ArrayAdapter<Account> {

	private XmppActivity activity;

	public AccountAdapter(XmppActivity activity, List<Account> objects) {
		super(activity, 0, objects);
		this.activity = activity;
	}

	@Override
	public View getView(int position, View view, ViewGroup parent) {
		final Account account = getItem(position);
		if (view == null) {
			LayoutInflater inflater = (LayoutInflater) getContext()
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			view = inflater.inflate(R.layout.account_row, parent, false);
		}
		TextView jid = (TextView) view.findViewById(R.id.account_jid);
		if (Config.DOMAIN_LOCK != null) {
			jid.setText(account.getJid().getLocalpart());
		} else {
			jid.setText(account.getJid().toBareJid().toString());
		}
		TextView statusView = (TextView) view.findViewById(R.id.account_status);
		ImageView imageView = (ImageView) view.findViewById(R.id.account_image);
		loadAvatar(account,imageView);
		statusView.setText(getContext().getString(account.getStatus().getReadableId()));
		switch (account.getStatus()) {
			case ONLINE:
				statusView.setTextColor(activity.getOnlineColor());
				break;
			case DISABLED:
			case CONNECTING:
				statusView.setTextColor(activity.getSecondaryTextColor());
				break;
			default:
				statusView.setTextColor(activity.getWarningTextColor());
				break;
		}
		final Switch tglAccountState = (Switch) view.findViewById(R.id.tgl_account_status);
		final boolean isDisabled = (account.getStatus() == Account.State.DISABLED);
		tglAccountState.setChecked(!isDisabled,false);
		tglAccountState.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
				if (b == isDisabled && activity instanceof ManageAccountActivity) {
					((ManageAccountActivity) activity).onClickTglAccountState(account,b);
				}
			}
		});
		return view;
	}

	class BitmapWorkerTask extends AsyncTask<Account, Void, Bitmap> {
		private final WeakReference<ImageView> imageViewReference;
		private Account account = null;

		public BitmapWorkerTask(ImageView imageView) {
			imageViewReference = new WeakReference<>(imageView);
		}

		@Override
		protected Bitmap doInBackground(Account... params) {
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

	public void loadAvatar(Account account, ImageView imageView) {
		if (cancelPotentialWork(account, imageView)) {
			final Bitmap bm = activity.avatarService().get(account,activity.getPixel(48),true);
			if (bm != null) {
				cancelPotentialWork(account, imageView);
				imageView.setImageBitmap(bm);
				imageView.setBackgroundColor(0x00000000);
			} else {
				imageView.setBackgroundColor(UIHelper.getColorForName(account.getJid().toBareJid().toString()));
				imageView.setImageDrawable(null);
				final BitmapWorkerTask task = new BitmapWorkerTask(imageView);
				final AsyncDrawable asyncDrawable = new AsyncDrawable(activity.getResources(), null, task);
				imageView.setImageDrawable(asyncDrawable);
				try {
					task.execute(account);
				} catch (final RejectedExecutionException ignored) {
				}
			}
		}
	}

	public static boolean cancelPotentialWork(Account account, ImageView imageView) {
		final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

		if (bitmapWorkerTask != null) {
			final Account oldAccount = bitmapWorkerTask.account;
			if (oldAccount == null || account != oldAccount) {
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
