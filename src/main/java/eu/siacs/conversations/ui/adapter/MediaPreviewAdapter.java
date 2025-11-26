package eu.siacs.conversations.ui.adapter;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.RecyclerView;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ItemMediaPreviewBinding;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.ui.ConversationFragment;
import eu.siacs.conversations.ui.ShowLocationActivity;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.ui.util.Attachment;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.RejectedExecutionException;

public class MediaPreviewAdapter
        extends RecyclerView.Adapter<MediaPreviewAdapter.MediaPreviewViewHolder> {

    private final ArrayList<Attachment> mediaPreviews = new ArrayList<>();

    private final ConversationFragment conversationFragment;

    public MediaPreviewAdapter(ConversationFragment fragment) {
        this.conversationFragment = fragment;
    }

    @NonNull
    @Override
    public MediaPreviewViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        ItemMediaPreviewBinding binding =
                DataBindingUtil.inflate(layoutInflater, R.layout.item_media_preview, parent, false);
        return new MediaPreviewViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull MediaPreviewViewHolder holder, int position) {
        final Context context = conversationFragment.getActivity();
        final Attachment attachment = mediaPreviews.get(position);
        if (attachment.renderThumbnail()) {
            ImageViewCompat.setImageTintList(holder.binding.mediaPreview, null);
            loadPreview(attachment, holder.binding.mediaPreview);
        } else {
            cancelPotentialWork(attachment, holder.binding.mediaPreview);
            MediaAdapter.renderPreview(attachment, holder.binding.mediaPreview);
        }
        holder.binding.deleteButton.setOnClickListener(
                v -> {
                    final int pos = mediaPreviews.indexOf(attachment);
                    mediaPreviews.remove(pos);
                    notifyItemRemoved(pos);
                    conversationFragment.toggleInputMethod();
                });
        holder.binding.mediaPreview.setOnClickListener(v -> view(context, attachment));
    }

    private static void view(final Context context, final Attachment attachment) {
        final Intent view = new Intent(Intent.ACTION_VIEW);
        if (attachment.getType() == Attachment.Type.LOCATION) {
            view.setClass(context, ShowLocationActivity.class);
            view.setData(attachment.getUri());
        } else {
            final Uri uri = FileBackend.getUriForUri(context, attachment.getUri());
            view.setDataAndType(uri, attachment.getMime());
            view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        try {
            context.startActivity(view);
        } catch (final ActivityNotFoundException e) {
            Toast.makeText(context, R.string.no_application_found_to_open_file, Toast.LENGTH_SHORT)
                    .show();
        } catch (final SecurityException e) {
            Toast.makeText(
                            context,
                            R.string.sharing_application_not_grant_permission,
                            Toast.LENGTH_SHORT)
                    .show();
        }
    }

    public void addMediaPreviews(List<Attachment> attachments) {
        this.mediaPreviews.addAll(attachments);
        notifyDataSetChanged();
    }

    private void loadPreview(Attachment attachment, ImageView imageView) {
        if (cancelPotentialWork(attachment, imageView)) {
            XmppActivity activity = (XmppActivity) conversationFragment.getActivity();
            final Bitmap bm =
                    activity.xmppConnectionService
                            .getFileBackend()
                            .getPreviewForUri(
                                    attachment,
                                    Math.round(
                                            activity.getResources()
                                                    .getDimension(R.dimen.media_preview_size)),
                                    true);
            if (bm != null) {
                cancelPotentialWork(attachment, imageView);
                imageView.setImageBitmap(bm);
                imageView.setBackgroundColor(0x00000000);
            } else {
                imageView.setBackgroundColor(
                        ContextCompat.getColor(imageView.getContext(), R.color.gray_800));
                imageView.setImageDrawable(null);
                final BitmapWorkerTask task = new BitmapWorkerTask(imageView);
                final AsyncDrawable asyncDrawable =
                        new AsyncDrawable(
                                conversationFragment.getActivity().getResources(), null, task);
                imageView.setImageDrawable(asyncDrawable);
                try {
                    task.execute(attachment);
                } catch (final RejectedExecutionException ignored) {
                }
            }
        }
    }

    private static boolean cancelPotentialWork(Attachment attachment, ImageView imageView) {
        final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

        if (bitmapWorkerTask != null) {
            final Attachment oldAttachment = bitmapWorkerTask.attachment;
            if (oldAttachment == null || !oldAttachment.equals(attachment)) {
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
            if (drawable instanceof AsyncDrawable asyncDrawable) {
                return asyncDrawable.getBitmapWorkerTask();
            }
        }
        return null;
    }

    @Override
    public int getItemCount() {
        return mediaPreviews.size();
    }

    public boolean hasAttachments() {
        return !mediaPreviews.isEmpty();
    }

    public ArrayList<Attachment> getAttachments() {
        return mediaPreviews;
    }

    public void clearPreviews() {
        this.mediaPreviews.clear();
    }

    static class MediaPreviewViewHolder extends RecyclerView.ViewHolder {

        private final ItemMediaPreviewBinding binding;

        MediaPreviewViewHolder(ItemMediaPreviewBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    static class AsyncDrawable extends BitmapDrawable {
        private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;

        AsyncDrawable(Resources res, Bitmap bitmap, BitmapWorkerTask bitmapWorkerTask) {
            super(res, bitmap);
            bitmapWorkerTaskReference = new WeakReference<>(bitmapWorkerTask);
        }

        BitmapWorkerTask getBitmapWorkerTask() {
            return bitmapWorkerTaskReference.get();
        }
    }

    private static class BitmapWorkerTask extends AsyncTask<Attachment, Void, Bitmap> {
        private final WeakReference<ImageView> imageViewReference;
        private Attachment attachment = null;

        BitmapWorkerTask(ImageView imageView) {
            imageViewReference = new WeakReference<>(imageView);
        }

        @Override
        protected Bitmap doInBackground(Attachment... params) {
            this.attachment = params[0];
            final XmppActivity activity = XmppActivity.find(imageViewReference);
            if (activity == null) {
                return null;
            }
            return activity.xmppConnectionService
                    .getFileBackend()
                    .getPreviewForUri(
                            this.attachment,
                            Math.round(
                                    activity.getResources()
                                            .getDimension(R.dimen.media_preview_size)),
                            false);
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
