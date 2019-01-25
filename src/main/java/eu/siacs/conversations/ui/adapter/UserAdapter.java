package eu.siacs.conversations.ui.adapter;

import android.app.PendingIntent;
import android.content.IntentSender;
import android.databinding.DataBindingUtil;
import android.support.annotation.NonNull;
import android.support.v7.recyclerview.extensions.ListAdapter;
import android.support.v7.util.DiffUtil;
import android.support.v7.widget.RecyclerView;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;

import org.openintents.openpgp.util.OpenPgpUtils;

import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.databinding.ContactBinding;
import eu.siacs.conversations.databinding.UserPreviewBinding;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.ConferenceDetailsActivity;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.ui.util.AvatarWorkerTask;
import eu.siacs.conversations.ui.util.MucDetailsContextMenuHelper;

public class UserAdapter extends ListAdapter<MucOptions.User, UserAdapter.ViewHolder> implements View.OnCreateContextMenuListener {

    private MucOptions.User selectedUser = null;

    static final DiffUtil.ItemCallback<MucOptions.User> DIFF = new DiffUtil.ItemCallback<MucOptions.User>() {
        @Override
        public boolean areItemsTheSame(@NonNull MucOptions.User a, @NonNull MucOptions.User b) {
            return a == b;
        }

        @Override
        public boolean areContentsTheSame(@NonNull MucOptions.User a, @NonNull MucOptions.User b) {
            return a.equals(b);
        }
    };
    private final boolean advancedMode;

    public UserAdapter(final boolean advancedMode) {
        super(DIFF);
        this.advancedMode = advancedMode;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int position) {
        return new ViewHolder(DataBindingUtil.inflate(LayoutInflater.from(viewGroup.getContext()), R.layout.contact, viewGroup, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, int position) {
        final MucOptions.User user = getItem(position);
        AvatarWorkerTask.loadAvatar(user, viewHolder.binding.contactPhoto, R.dimen.avatar);
        viewHolder.binding.getRoot().setOnClickListener(v -> {
            final XmppActivity activity = XmppActivity.find(v);
            if (activity != null) {
                activity.highlightInMuc(user.getConversation(), user.getName());
            }
        });
        viewHolder.binding.getRoot().setTag(user);
        viewHolder.binding.getRoot().setOnCreateContextMenuListener(this);
        viewHolder.binding.getRoot().setOnLongClickListener(v -> {
            selectedUser = user;
            return false;
        });
        final String name = user.getName();
        final Contact contact = user.getContact();
        if (contact != null) {
            viewHolder.binding.contactDisplayName.setText(contact.getDisplayName());
            if (name != null) {
                viewHolder.binding.contactJid.setText(String.format("%s \u2022 %s", name, ConferenceDetailsActivity.getStatus(viewHolder.binding.getRoot().getContext(), user, advancedMode)));
            } else {
                viewHolder.binding.contactJid.setText(ConferenceDetailsActivity.getStatus(viewHolder.binding.getRoot().getContext(), user, advancedMode));
            }
        } else {
            viewHolder.binding.contactDisplayName.setText(name == null ? "" : name);
            viewHolder.binding.contactJid.setText(ConferenceDetailsActivity.getStatus(viewHolder.binding.getRoot().getContext(), user, advancedMode));
        }
        if (advancedMode && user.getPgpKeyId() != 0) {
            viewHolder.binding.key.setVisibility(View.VISIBLE);
            viewHolder.binding.key.setOnClickListener(v -> {
                final XmppActivity activity = XmppActivity.find(v);
                final XmppConnectionService service = activity == null ? null : activity.xmppConnectionService;
                final PgpEngine pgpEngine = service == null ? null : service.getPgpEngine();
                if (pgpEngine != null) {
                    PendingIntent intent = pgpEngine.getIntentForKey(user.getPgpKeyId());
                    if (intent != null) {
                        try {
                            activity.startIntentSenderForResult(intent.getIntentSender(), 0, null, 0, 0, 0);
                        } catch (IntentSender.SendIntentException ignored) {

                        }
                    }
                }
            });
            viewHolder.binding.key.setText(OpenPgpUtils.convertKeyIdToHex(user.getPgpKeyId()));
        }


    }

    public MucOptions.User getSelectedUser() {
        return selectedUser;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        final XmppActivity activity = XmppActivity.find(v);
        final Object tag = v.getTag();
        if (tag instanceof MucOptions.User && activity != null) {
            activity.getMenuInflater().inflate(R.menu.muc_details_context, menu);
            final MucOptions.User user = (MucOptions.User) tag;
            String name;
            final Contact contact = user.getContact();
            if (contact != null && contact.showInContactList()) {
                name = contact.getDisplayName();
            } else if (user.getRealJid() != null) {
                name = user.getRealJid().asBareJid().toString();
            } else {
                name = user.getName();
            }
            menu.setHeaderTitle(name);
            MucDetailsContextMenuHelper.configureMucDetailsContextMenu(activity, menu, user.getConversation(), user);
        }
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        private final ContactBinding binding;

        private ViewHolder(ContactBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
