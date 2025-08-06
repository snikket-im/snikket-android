package eu.siacs.conversations.ui.util;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.widget.ImageView;
import androidx.annotation.DimenRes;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.AvatarService;
import eu.siacs.conversations.ui.XmppActivity;
import java.lang.ref.WeakReference;
import java.util.concurrent.RejectedExecutionException;

public class AvatarWorkerTask extends AsyncTask<AvatarService.Avatar, Void, Bitmap> {
    private final WeakReference<ImageView> imageViewReference;
    private AvatarService.Avatar avatar = null;
    private @DimenRes final int size;

    public AvatarWorkerTask(ImageView imageView, @DimenRes int size) {
        imageViewReference = new WeakReference<>(imageView);
        this.size = size;
    }

    @Override
    protected Bitmap doInBackground(AvatarService.Avatar... params) {
        this.avatar = params[0];
        final XmppActivity activity = XmppActivity.find(imageViewReference);
        if (activity == null) {
            return null;
        }
        return activity.avatarService()
                .get(avatar, (int) activity.getResources().getDimension(size), isCancelled());
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

    public static boolean cancelPotentialWork(AvatarService.Avatar avatar, ImageView imageView) {
        final AvatarWorkerTask workerTask = getBitmapWorkerTask(imageView);

        if (workerTask != null) {
            final AvatarService.Avatar old = workerTask.avatar;
            if (old == null || avatar != old) {
                workerTask.cancel(true);
            } else {
                return false;
            }
        }
        return true;
    }

    public static AvatarWorkerTask getBitmapWorkerTask(ImageView imageView) {
        if (imageView != null) {
            final Drawable drawable = imageView.getDrawable();
            if (drawable instanceof AsyncDrawable asyncDrawable) {
                return asyncDrawable.getAvatarWorkerTask();
            }
        }
        return null;
    }

    public static void loadAvatar(
            final AvatarService.Avatar avatar,
            final ImageView imageView,
            final @DimenRes int size) {
        if (cancelPotentialWork(avatar, imageView)) {
            final XmppActivity activity = XmppActivity.find(imageView);
            if (activity == null) {
                return;
            }
            final Bitmap bm =
                    activity.avatarService()
                            .get(avatar, (int) activity.getResources().getDimension(size), true);
            setContentDescription(avatar, imageView);
            if (bm != null) {
                cancelPotentialWork(avatar, imageView);
                imageView.setImageBitmap(bm);
                imageView.setBackgroundColor(0x00000000);
            } else {
                imageView.setBackgroundColor(avatar.getAvatarBackgroundColor());
                imageView.setImageDrawable(null);
                final AvatarWorkerTask task = new AvatarWorkerTask(imageView, size);
                final AsyncDrawable asyncDrawable =
                        new AsyncDrawable(activity.getResources(), null, task);
                imageView.setImageDrawable(asyncDrawable);
                try {
                    task.execute(avatar);
                } catch (final RejectedExecutionException ignored) {
                }
            }
        }
    }

    private static void setContentDescription(
            final AvatarService.Avatar avatar, final ImageView imageView) {
        final Context context = imageView.getContext();
        if (avatar instanceof Account) {
            imageView.setContentDescription(context.getString(R.string.your_avatar));
        } else if (avatar instanceof Message m && m.getType() == Message.TYPE_STATUS) {
            imageView.setContentDescription(null);
            return;
        } else {
            imageView.setContentDescription(
                    context.getString(R.string.avatar_for_x, avatar.getAvatarName()));
        }
    }

    static class AsyncDrawable extends BitmapDrawable {
        private final WeakReference<AvatarWorkerTask> avatarWorkerTaskReference;

        AsyncDrawable(Resources res, Bitmap bitmap, AvatarWorkerTask workerTask) {
            super(res, bitmap);
            avatarWorkerTaskReference = new WeakReference<>(workerTask);
        }

        AvatarWorkerTask getAvatarWorkerTask() {
            return avatarWorkerTaskReference.get();
        }
    }
}
