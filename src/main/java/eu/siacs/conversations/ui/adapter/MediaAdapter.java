package eu.siacs.conversations.ui.adapter;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.DimenRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.widget.ImageViewCompat;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.google.common.base.Strings;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ItemMediaBinding;
import eu.siacs.conversations.services.ExportBackupService;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.ui.util.Attachment;
import eu.siacs.conversations.ui.util.ViewUtil;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

public class MediaAdapter extends RecyclerView.Adapter<MediaAdapter.MediaViewHolder> {

    public static final List<String> DOCUMENT_MIMES =
            Arrays.asList(
                    "application/pdf",
                    "application/vnd.oasis.opendocument.text",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "text/x-tex",
                    "text/plain");
    public static final List<String> CODE_MIMES = Arrays.asList("text/html", "text/xml");

    private final ArrayList<Attachment> attachments = new ArrayList<>();

    private final XmppActivity activity;

    private int mediaSize = 0;

    public MediaAdapter(XmppActivity activity, @DimenRes int mediaSize) {
        this.activity = activity;
        this.mediaSize = Math.round(activity.getResources().getDimension(mediaSize));
    }

    @SuppressWarnings("rawtypes")
    public static void setMediaSize(final RecyclerView recyclerView, int mediaSize) {
        final RecyclerView.Adapter adapter = recyclerView.getAdapter();
        if (adapter instanceof MediaAdapter mediaAdapter) {
            mediaAdapter.setMediaSize(mediaSize);
        }
    }

    public static @DrawableRes int getImageDrawable(final Attachment attachment) {
        if (attachment.getType() == Attachment.Type.LOCATION) {
            return R.drawable.ic_location_pin_48dp;
        } else if (attachment.getType() == Attachment.Type.RECORDING) {
            return R.drawable.ic_mic_48dp;
        } else {
            return getImageDrawable(attachment.getMime());
        }
    }

    private static @DrawableRes int getImageDrawable(final String mime) {

        // TODO ideas for more mime types: XML, HTML documents, GPG/PGP files, eml files,
        // spreadsheets (table symbol)

        // add bz2 and tar.gz to archive detection

        if (Strings.isNullOrEmpty(mime)) {
            return R.drawable.ic_help_center_48dp;
        } else if (mime.equals("audio/x-m4b")) {
            return R.drawable.ic_play_lesson_48dp;
        } else if (mime.startsWith("audio/")) {
            return R.drawable.ic_headphones_48dp;
        } else if (mime.equals("text/calendar") || (mime.equals("text/x-vcalendar"))) {
            return R.drawable.ic_event_48dp;
        } else if (mime.equals("text/x-vcard")) {
            return R.drawable.ic_person_48dp;
        } else if (mime.equals("application/vnd.android.package-archive")) {
            return R.drawable.ic_adb_48dp;
        } else if (mime.equals("application/zip") || mime.equals("application/rar")) {
            return R.drawable.ic_archive_48dp;
        } else if (mime.equals("application/epub+zip")
                || mime.equals("application/vnd.amazon.mobi8-ebook")) {
            return R.drawable.ic_book_48dp;
        } else if (mime.equals(ExportBackupService.MIME_TYPE)) {
            return R.drawable.ic_backup_48dp;
        } else if (DOCUMENT_MIMES.contains(mime)) {
            return R.drawable.ic_description_48dp;
        } else if (mime.equals("application/gpx+xml")) {
            return R.drawable.ic_tour_48dp;
        } else if (mime.startsWith("image/")) {
            return R.drawable.ic_image_48dp;
        } else if (mime.startsWith("video/")) {
            return R.drawable.ic_movie_48dp;
        } else if (CODE_MIMES.contains(mime)) {
            return R.drawable.ic_code_48dp;
        } else if (mime.equals("message/rfc822")) {
            return R.drawable.ic_email_48dp;
        } else {
            return R.drawable.ic_help_center_48dp;
        }
    }

    static void renderPreview(final Attachment attachment, final ImageView imageView) {
        ImageViewCompat.setImageTintList(
                imageView,
                ColorStateList.valueOf(
                        MaterialColors.getColor(
                                imageView, com.google.android.material.R.attr.colorOnSurface)));
        imageView.setImageResource(getImageDrawable(attachment));
        imageView.setBackgroundColor(
                MaterialColors.getColor(
                        imageView,
                        com.google.android.material.R.attr.colorSurfaceContainerHighest));
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

    @NonNull
    @Override
    public MediaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        ItemMediaBinding binding =
                DataBindingUtil.inflate(layoutInflater, R.layout.item_media, parent, false);
        return new MediaViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull MediaViewHolder holder, int position) {
        final Attachment attachment = attachments.get(position);
        if (attachment.renderThumbnail()) {
            loadPreview(attachment, holder.binding.media);
        } else {
            cancelPotentialWork(attachment, holder.binding.media);
            renderPreview(attachment, holder.binding.media);
        }
        holder.binding.getRoot().setOnClickListener(v -> ViewUtil.view(activity, attachment));
    }

    public void setAttachments(final List<Attachment> attachments) {
        this.attachments.clear();
        this.attachments.addAll(attachments);
        notifyDataSetChanged();
    }

    private void setMediaSize(int mediaSize) {
        this.mediaSize = mediaSize;
    }

    private void loadPreview(Attachment attachment, ImageView imageView) {
        if (cancelPotentialWork(attachment, imageView)) {
            final Bitmap bm =
                    activity.xmppConnectionService
                            .getFileBackend()
                            .getPreviewForUri(attachment, mediaSize, true);
            if (bm != null) {
                cancelPotentialWork(attachment, imageView);
                imageView.setImageBitmap(bm);
                imageView.setBackgroundColor(Color.TRANSPARENT);
            } else {
                // TODO consider if this is still a good, general purpose loading color
                imageView.setBackgroundColor(0xff333333);
                imageView.setImageDrawable(null);
                final BitmapWorkerTask task = new BitmapWorkerTask(mediaSize, imageView);
                final AsyncDrawable asyncDrawable =
                        new AsyncDrawable(activity.getResources(), null, task);
                imageView.setImageDrawable(asyncDrawable);
                try {
                    task.execute(attachment);
                } catch (final RejectedExecutionException ignored) {
                }
            }
        }
    }

    @Override
    public int getItemCount() {
        return attachments.size();
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

    static class MediaViewHolder extends RecyclerView.ViewHolder {

        private final ItemMediaBinding binding;

        MediaViewHolder(ItemMediaBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    private static class BitmapWorkerTask extends AsyncTask<Attachment, Void, Bitmap> {
        private final WeakReference<ImageView> imageViewReference;
        private Attachment attachment = null;
        private final int mediaSize;

        BitmapWorkerTask(int mediaSize, ImageView imageView) {
            this.mediaSize = mediaSize;
            imageViewReference = new WeakReference<>(imageView);
        }

        @Override
        protected Bitmap doInBackground(final Attachment... params) {
            this.attachment = params[0];
            final XmppActivity activity = XmppActivity.find(imageViewReference);
            if (activity == null) {
                return null;
            }
            return activity.xmppConnectionService
                    .getFileBackend()
                    .getPreviewForUri(this.attachment, mediaSize, false);
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
