package eu.siacs.conversations.ui.adapter;

import android.databinding.DataBindingUtil;
import android.support.annotation.NonNull;
import android.support.v7.recyclerview.extensions.ListAdapter;
import android.support.v7.util.DiffUtil;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.SearchResultItemBinding;
import eu.siacs.conversations.entities.ChannelSearchResult;
import eu.siacs.conversations.ui.util.AvatarWorkerTask;

public class ChannelSearchResultAdapter extends ListAdapter<ChannelSearchResult, ChannelSearchResultAdapter.ViewHolder> {

    private OnChannelSearchResultSelected listener;

    private static final DiffUtil.ItemCallback<ChannelSearchResult> DIFF = new DiffUtil.ItemCallback<ChannelSearchResult>() {
        @Override
        public boolean areItemsTheSame(@NonNull ChannelSearchResult a, @NonNull ChannelSearchResult b) {
            return false;
        }

        @Override
        public boolean areContentsTheSame(@NonNull ChannelSearchResult a, @NonNull ChannelSearchResult b) {
            return a.equals(b);
        }
    };

    public ChannelSearchResultAdapter() {
        super(DIFF);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        return new ViewHolder(DataBindingUtil.inflate(LayoutInflater.from(viewGroup.getContext()), R.layout.search_result_item,viewGroup,false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, int position) {
        final ChannelSearchResult searchResult = getItem(position);
        viewHolder.binding.name.setText(searchResult.getName());
        final String description = searchResult.getDescription();
        if (TextUtils.isEmpty(description)) {
            viewHolder.binding.description.setVisibility(View.GONE);
        } else {
            viewHolder.binding.description.setText(description);
            viewHolder.binding.description.setVisibility(View.VISIBLE);
        }
        viewHolder.binding.room.setText(searchResult.getRoom().asBareJid().toString());
        AvatarWorkerTask.loadAvatar(searchResult, viewHolder.binding.avatar, R.dimen.avatar);
        viewHolder.binding.getRoot().setOnClickListener(v -> listener.onChannelSearchResult(searchResult));
    }

    public void setOnChannelSearchResultSelectedListener(OnChannelSearchResultSelected listener) {
        this.listener = listener;
    }


    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final SearchResultItemBinding binding;

        private ViewHolder(SearchResultItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    public interface OnChannelSearchResultSelected {
        void onChannelSearchResult(ChannelSearchResult result);
    }
}
