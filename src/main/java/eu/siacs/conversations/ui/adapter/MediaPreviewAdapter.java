package eu.siacs.conversations.ui.adapter;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.databinding.DataBindingUtil;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.support.annotation.AttrRes;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.MediaPreviewBinding;
import eu.siacs.conversations.ui.ConversationFragment;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.ui.util.Attachment;
import eu.siacs.conversations.ui.util.StyledAttributes;

public class MediaPreviewAdapter extends RecyclerView.Adapter<MediaPreviewAdapter.MediaPreviewViewHolder> {

    private static final List<String> DOCUMENT_MIMES = Arrays.asList(
            "application/pdf",
            "application/vnd.oasis.opendocument.text",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/x-tex",
            "text/plain"
    );

    private final ArrayList<Attachment> mediaPreviews = new ArrayList<>();

    private final ConversationFragment conversationFragment;

    public MediaPreviewAdapter(ConversationFragment fragment) {
        this.conversationFragment = fragment;
    }

    @NonNull
    @Override
    public MediaPreviewViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        MediaPreviewBinding binding = DataBindingUtil.inflate(layoutInflater, R.layout.media_preview, parent, false);
        return new MediaPreviewViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull MediaPreviewViewHolder holder, int position) {
        final Context context = conversationFragment.getActivity();
        final Attachment attachment = mediaPreviews.get(position);
        if (attachment.renderThumbnail()) {
            holder.binding.mediaPreview.setImageAlpha(255);
            loadPreview(attachment, holder.binding.mediaPreview);
        } else {
            cancelPotentialWork(attachment, holder.binding.mediaPreview);
            holder.binding.mediaPreview.setBackgroundColor(StyledAttributes.getColor(context, R.attr.color_background_tertiary));
            holder.binding.mediaPreview.setImageAlpha(Math.round(StyledAttributes.getFloat(context, R.attr.icon_alpha) * 255));
            final @AttrRes int attr;
            if (attachment.getType() == Attachment.Type.LOCATION) {
                attr = R.attr.media_preview_location;
            } else if (attachment.getType() == Attachment.Type.RECORDING) {
                attr = R.attr.media_preview_recording;
            } else {
                final String mime = attachment.getMime();
                if (mime == null) {
                    attr = R.attr.media_preview_unknown;
                } else if (mime.startsWith("audio/")) {
                    attr = R.attr.media_preview_audio;
                } else if (mime.equals("text/calendar") || (mime.equals("text/x-vcalendar"))) {
                    attr = R.attr.media_preview_calendar;
                } else if (mime.equals("text/x-vcard")) {
                    attr = R.attr.media_preview_contact;
                } else if (mime.equals("application/vnd.android.package-archive")) {
                    attr = R.attr.media_preview_app;
                } else if (mime.equals("application/zip") || mime.equals("application/rar")) {
                    attr = R.attr.media_preview_archive;
                } else if (DOCUMENT_MIMES.contains(mime)) {
                    attr = R.attr.media_preview_document;
                } else {
                    attr = R.attr.media_preview_unknown;
                }
            }
            holder.binding.mediaPreview.setImageDrawable(StyledAttributes.getDrawable(context, attr));
        }
        holder.binding.deleteButton.setOnClickListener(v -> {
            int pos = mediaPreviews.indexOf(attachment);
            mediaPreviews.remove(pos);
            notifyItemRemoved(pos);
            conversationFragment.toggleInputMethod();
        });
    }

    public void addMediaPreviews(List<Attachment> attachments) {
        this.mediaPreviews.addAll(attachments);
        notifyDataSetChanged();
    }

    private void loadPreview(Attachment attachment, ImageView imageView) {
        if (cancelPotentialWork(attachment, imageView)) {
            XmppActivity activity = (XmppActivity) conversationFragment.getActivity();
            final Bitmap bm = activity.xmppConnectionService.getFileBackend().getPreviewForUri(attachment,Math.round(activity.getResources().getDimension(R.dimen.media_preview_size)),true);
            if (bm != null) {
                cancelPotentialWork(attachment, imageView);
                imageView.setImageBitmap(bm);
                imageView.setBackgroundColor(0x00000000);
            } else {
                imageView.setBackgroundColor(0xff333333);
                imageView.setImageDrawable(null);
                final BitmapWorkerTask task = new BitmapWorkerTask(imageView);
                final AsyncDrawable asyncDrawable = new AsyncDrawable(conversationFragment.getActivity().getResources(), null, task);
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
            if (drawable instanceof AsyncDrawable) {
                final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
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
        return mediaPreviews.size() > 0;
    }

    public ArrayList<Attachment> getAttachments() {
        return mediaPreviews;
    }

    class MediaPreviewViewHolder extends RecyclerView.ViewHolder {

        private final MediaPreviewBinding binding;

        MediaPreviewViewHolder(MediaPreviewBinding binding) {
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

    class BitmapWorkerTask extends AsyncTask<Attachment, Void, Bitmap> {
        private final WeakReference<ImageView> imageViewReference;
        private Attachment attachment = null;

        BitmapWorkerTask(ImageView imageView) {
            imageViewReference = new WeakReference<>(imageView);
        }

        @Override
        protected Bitmap doInBackground(Attachment... params) {
            Activity activity = conversationFragment.getActivity();
            if (activity instanceof XmppActivity) {
                final XmppActivity xmppActivity = (XmppActivity) activity;
                this.attachment = params[0];
                return xmppActivity.xmppConnectionService.getFileBackend().getPreviewForUri(this.attachment, Math.round(xmppActivity.getResources().getDimension(R.dimen.media_preview_size)), false);
            } else {
                return null;
            }
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
