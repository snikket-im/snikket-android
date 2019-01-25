package eu.siacs.conversations.ui.adapter;

import android.databinding.DataBindingUtil;
import android.support.annotation.NonNull;
import android.support.v7.recyclerview.extensions.ListAdapter;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.ViewGroup;
import android.widget.PopupMenu;

import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.UserPreviewBinding;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.ui.util.AvatarWorkerTask;
import eu.siacs.conversations.ui.util.MucDetailsContextMenuHelper;

public class UserPreviewAdapter extends ListAdapter<MucOptions.User,UserPreviewAdapter.ViewHolder> {

    public UserPreviewAdapter() {
        super(UserAdapter.DIFF);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int position) {
        return new ViewHolder(DataBindingUtil.inflate(LayoutInflater.from(viewGroup.getContext()), R.layout.user_preview, viewGroup, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, int position) {
        final MucOptions.User user = getItem(position);
        AvatarWorkerTask.loadAvatar(user, viewHolder.binding.avatar, R.dimen.media_size);
        viewHolder.binding.getRoot().setOnClickListener(v -> {
            final XmppActivity activity = XmppActivity.find(v);
            if (activity != null) {
                activity.highlightInMuc(user.getConversation(), user.getName());
            }
        });
        viewHolder.binding.getRoot().setOnLongClickListener(v -> {
            final XmppActivity activity = XmppActivity.find(v);
            if (activity == null) {
                return true;
            }
            final PopupMenu popupMenu = new PopupMenu(activity, v);
            popupMenu.inflate(R.menu.muc_details_context);
            final Menu menu = popupMenu.getMenu();
            MucDetailsContextMenuHelper.configureMucDetailsContextMenu(activity, menu, user.getConversation(), user);
            popupMenu.setOnMenuItemClickListener(menuItem -> MucDetailsContextMenuHelper.onContextItemSelected(menuItem, user, user.getConversation(), activity));
            popupMenu.show();
            return true;
        });
    }

    public void setUserList(List<MucOptions.User> users) {
        submitList(users);
    }


    class ViewHolder extends RecyclerView.ViewHolder {

        private final UserPreviewBinding binding;

        private ViewHolder(UserPreviewBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
