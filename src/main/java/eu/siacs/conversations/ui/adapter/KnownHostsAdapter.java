package eu.siacs.conversations.ui.adapter;

import android.content.Context;
import android.support.annotation.NonNull;
import android.widget.ArrayAdapter;
import android.widget.Filter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public class KnownHostsAdapter extends ArrayAdapter<String> {
	private ArrayList<String> domains;
	private Filter domainFilter = new Filter() {

		@Override
		protected FilterResults performFiltering(CharSequence constraint) {
			if (constraint != null) {
				ArrayList<String> suggestions = new ArrayList<>();
				final String[] split = constraint.toString().split("@");
				if (split.length == 1) {
					for (String domain : domains) {
						suggestions.add(split[0].toLowerCase(Locale
								.getDefault()) + "@" + domain);
					}
				} else if (split.length == 2) {
					for (String domain : domains) {
						if (domain.contentEquals(split[1])) {
							suggestions.clear();
							break;
						} else if (domain.contains(split[1])) {
							suggestions.add(split[0].toLowerCase(Locale
									.getDefault()) + "@" + domain);
						}
					}
				} else {
					return new FilterResults();
				}
				FilterResults filterResults = new FilterResults();
				filterResults.values = suggestions;
				filterResults.count = suggestions.size();
				return filterResults;
			} else {
				return new FilterResults();
			}
		}

		@Override
		protected void publishResults(CharSequence constraint, FilterResults results) {
			ArrayList filteredList = (ArrayList) results.values;
			if (results.count > 0) {
				clear();
				for (Object c : filteredList) {
					add((String) c);
				}
				notifyDataSetChanged();
			}
		}
	};

	public KnownHostsAdapter(Context context, int viewResourceId, Collection<String> mKnownHosts) {
		super(context, viewResourceId, new ArrayList<>());
		domains = new ArrayList<>(mKnownHosts);
	}

	public KnownHostsAdapter(Context context, int viewResourceId) {
		super(context, viewResourceId, new ArrayList<>());
		domains = new ArrayList<>();
	}

	public void refresh(Collection<String> knownHosts) {
		domains = new ArrayList<>(knownHosts);
		notifyDataSetChanged();
	}

	@Override
	@NonNull
	public Filter getFilter() {
		return domainFilter;
	}
}
