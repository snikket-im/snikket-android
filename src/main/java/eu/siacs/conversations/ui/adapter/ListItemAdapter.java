package eu.siacs.conversations.ui.adapter;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.wefika.flowlayout.FlowLayout;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.ListItem;
import eu.siacs.conversations.ui.SettingsActivity;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.utils.EmojiWrapper;
import eu.siacs.conversations.utils.UIHelper;

public class ListItemAdapter extends ArrayAdapter<ListItem> {

	protected XmppActivity activity;
	protected boolean showDynamicTags = false;
	private OnTagClickedListener mOnTagClickedListener = null;
	private View.OnClickListener onTagTvClick = new View.OnClickListener() {
		@Override
		public void onClick(View view) {
			if (view instanceof TextView && mOnTagClickedListener != null) {
				TextView tv = (TextView) view;
				final String tag = tv.getText().toString();
				mOnTagClickedListener.onTagClicked(tag);
			}
		}
	};

	public ListItemAdapter(XmppActivity activity, List<ListItem> objects) {
		super(activity, 0, objects);
		this.activity = activity;
	}

	public static boolean cancelPotentialWork(ListItem item, ImageView imageView) {
		final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

		if (bitmapWorkerTask != null) {
			final ListItem oldItem = bitmapWorkerTask.item;
			if (oldItem == null || item != oldItem) {
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

	public void refreshSettings() {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
		this.showDynamicTags = preferences.getBoolean(SettingsActivity.SHOW_DYNAMIC_TAGS, false);
	}

	@Override
	public View getView(int position, View view, ViewGroup parent) {
		LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		ListItem item = getItem(position);
		if (view == null) {
			view = inflater.inflate(R.layout.contact, parent, false);
		}

		ViewHolder viewHolder = ViewHolder.get(view);

		List<ListItem.Tag> tags = item.getTags(activity);
		if (tags.size() == 0 || !this.showDynamicTags) {
			viewHolder.tags.setVisibility(View.GONE);
		} else {
			viewHolder.tags.setVisibility(View.VISIBLE);
			viewHolder.tags.removeAllViewsInLayout();
			for (ListItem.Tag tag : tags) {
				TextView tv = (TextView) inflater.inflate(R.layout.list_item_tag, viewHolder.tags, false);
				tv.setText(tag.getName());
				tv.setBackgroundColor(tag.getColor());
				tv.setOnClickListener(this.onTagTvClick);
				viewHolder.tags.addView(tv);
			}
		}
		final String jid = item.getDisplayJid();
		if (jid != null) {
			viewHolder.jid.setVisibility(View.VISIBLE);
			viewHolder.jid.setText(jid);
		} else {
			viewHolder.jid.setVisibility(View.GONE);
		}
		viewHolder.name.setText(EmojiWrapper.transform(item.getDisplayName()));
		loadAvatar(item, viewHolder.avatar);
		return view;
	}

	public void setOnTagClickedListener(OnTagClickedListener listener) {
		this.mOnTagClickedListener = listener;
	}

	public void loadAvatar(ListItem item, ImageView imageView) {
		if (cancelPotentialWork(item, imageView)) {
			final Bitmap bm = activity.avatarService().get(item, activity.getPixel(48), true);
			if (bm != null) {
				cancelPotentialWork(item, imageView);
				imageView.setImageBitmap(bm);
				imageView.setBackgroundColor(0x00000000);
			} else {
				String seed = item.getJid() != null ? item.getJid().toBareJid().toString() : item.getDisplayName();
				imageView.setBackgroundColor(UIHelper.getColorForName(seed));
				imageView.setImageDrawable(null);
				final BitmapWorkerTask task = new BitmapWorkerTask(imageView);
				final AsyncDrawable asyncDrawable = new AsyncDrawable(activity.getResources(), null, task);
				imageView.setImageDrawable(asyncDrawable);
				try {
					task.execute(item);
				} catch (final RejectedExecutionException ignored) {
				}
			}
		}
	}

	public interface OnTagClickedListener {
		void onTagClicked(String tag);
	}

	private static class ViewHolder {
		private TextView name;
		private TextView jid;
		private ImageView avatar;
		private FlowLayout tags;

		private ViewHolder() {

		}

		public static ViewHolder get(View layout) {
			ViewHolder viewHolder = (ViewHolder) layout.getTag();
			if (viewHolder == null) {
				viewHolder = new ViewHolder();
				viewHolder.name = layout.findViewById(R.id.contact_display_name);
				viewHolder.jid = layout.findViewById(R.id.contact_jid);
				viewHolder.avatar = layout.findViewById(R.id.contact_photo);
				viewHolder.tags = layout.findViewById(R.id.tags);
				layout.setTag(viewHolder);
			}
			return viewHolder;
		}
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

	class BitmapWorkerTask extends AsyncTask<ListItem, Void, Bitmap> {
		private final WeakReference<ImageView> imageViewReference;
		private ListItem item = null;

		public BitmapWorkerTask(ImageView imageView) {
			imageViewReference = new WeakReference<>(imageView);
		}

		@Override
		protected Bitmap doInBackground(ListItem... params) {
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

}
