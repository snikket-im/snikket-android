package eu.siacs.conversations.ui.adapter;

import android.content.res.Resources;
import android.databinding.DataBindingUtil;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.AccountRowBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.ui.util.StyledAttributes;
import eu.siacs.conversations.utils.UIHelper;

public class AccountAdapter extends ArrayAdapter<Account> {

    private XmppActivity activity;
    private boolean showStateButton;

    public AccountAdapter(XmppActivity activity, List<Account> objects, boolean showStateButton) {
        super(activity, 0, objects);
        this.activity = activity;
        this.showStateButton = showStateButton;
    }

    public AccountAdapter(XmppActivity activity, List<Account> objects) {
        super(activity, 0, objects);
        this.activity = activity;
        this.showStateButton = true;
    }

    @Override
    public View getView(int position, View view, @NonNull ViewGroup parent) {
        final Account account = getItem(position);
        final ViewHolder viewHolder;
        if (view == null) {
            AccountRowBinding binding = DataBindingUtil.inflate(LayoutInflater.from(parent.getContext()), R.layout.account_row, parent, false);
            view = binding.getRoot();
            viewHolder = new ViewHolder(binding);
            view.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) view.getTag();
        }
        if (Config.DOMAIN_LOCK != null) {
            viewHolder.binding.accountJid.setText(account.getJid().getLocal());
        } else {
            viewHolder.binding.accountJid.setText(account.getJid().asBareJid().toString());
        }
        loadAvatar(account, viewHolder.binding.accountImage);
        viewHolder.binding.accountStatus.setText(getContext().getString(account.getStatus().getReadableId()));
        switch (account.getStatus()) {
            case ONLINE:
                viewHolder.binding.accountStatus.setTextColor(StyledAttributes.getColor(activity, R.attr.TextColorOnline));
                break;
            case DISABLED:
            case CONNECTING:
                viewHolder.binding.accountStatus.setTextColor(StyledAttributes.getColor(activity, android.R.attr.textColorSecondary));
                break;
            default:
                viewHolder.binding.accountStatus.setTextColor(StyledAttributes.getColor(activity, R.attr.TextColorError));
                break;
        }
        final boolean isDisabled = (account.getStatus() == Account.State.DISABLED);
        viewHolder.binding.tglAccountStatus.setOnCheckedChangeListener(null);
        viewHolder.binding.tglAccountStatus.setChecked(!isDisabled);
        if (this.showStateButton) {
            viewHolder.binding.tglAccountStatus.setVisibility(View.VISIBLE);
        } else {
            viewHolder.binding.tglAccountStatus.setVisibility(View.GONE);
        }
        viewHolder.binding.tglAccountStatus.setOnCheckedChangeListener((compoundButton, b) -> {
            if (b == isDisabled && activity instanceof OnTglAccountState) {
                ((OnTglAccountState) activity).onClickTglAccountState(account, b);
            }
        });
        return view;
    }

    private static class ViewHolder {
        private final AccountRowBinding binding;

        private ViewHolder(AccountRowBinding binding) {
            this.binding = binding;
        }
    }

    class BitmapWorkerTask extends AsyncTask<Account, Void, Bitmap> {
        private final WeakReference<ImageView> imageViewReference;
        private Account account = null;

        public BitmapWorkerTask(ImageView imageView) {
            imageViewReference = new WeakReference<>(imageView);
        }

        @Override
        protected Bitmap doInBackground(Account... params) {
            this.account = params[0];
            return activity.avatarService().get(this.account, activity.getPixel(48), isCancelled());
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
            final Bitmap bm = activity.avatarService().get(account, activity.getPixel(48), true);
            if (bm != null) {
                cancelPotentialWork(account, imageView);
                imageView.setImageBitmap(bm);
                imageView.setBackgroundColor(0x00000000);
            } else {
                imageView.setBackgroundColor(UIHelper.getColorForName(account.getJid().asBareJid().toString()));
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


    public interface OnTglAccountState {
        void onClickTglAccountState(Account account, boolean state);
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
