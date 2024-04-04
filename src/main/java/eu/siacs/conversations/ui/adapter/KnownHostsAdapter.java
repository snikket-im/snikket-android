package eu.siacs.conversations.ui.adapter;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Filter;

import androidx.annotation.NonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;

import eu.siacs.conversations.Config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class KnownHostsAdapter extends ArrayAdapter<String> {

    private static final Pattern E164_PATTERN = Pattern.compile("^\\+[1-9]\\d{1,14}$");

    private List<String> domains;
    private final Filter domainFilter = new Filter() {

        @Override
        protected FilterResults performFiltering(final CharSequence constraint) {
            final ImmutableList.Builder<String> builder = new ImmutableList.Builder<>();
            final String[] split = constraint == null ? new String[0] : constraint.toString().split("@");
            if (split.length == 1) {
                final String local = split[0].toLowerCase(Locale.ENGLISH);
                if (Config.QUICKSY_DOMAIN != null && E164_PATTERN.matcher(local).matches()) {
                    builder.add(local + '@' + Config.QUICKSY_DOMAIN.toEscapedString());
                } else {
                    for (String domain : domains) {
                        builder.add(local + '@' + domain);
                    }
                }
            } else if (split.length == 2) {
                final String localPart = split[0].toLowerCase(Locale.ENGLISH);
                final String domainPart = split[1].toLowerCase(Locale.ENGLISH);
                if (domains.contains(domainPart)) {
                    return new FilterResults();
                }
                for (final String domain : domains) {
                    if (domain.contains(domainPart)) {
                        builder.add(localPart + "@" + domain);
                    }
                }
            } else {
                return new FilterResults();
            }
            final var suggestions = builder.build();
            final FilterResults filterResults = new FilterResults();
            filterResults.values = suggestions;
            filterResults.count = suggestions.size();
            return filterResults;
        }

        @Override
        protected void publishResults(final CharSequence constraint, final FilterResults results) {
            final ImmutableList.Builder<String> suggestions = new ImmutableList.Builder<>();
            if (results.values instanceof Collection<?> collection) {
                for(final Object item : collection) {
                    if (item instanceof String string) {
                        suggestions.add(string);
                    }
                }
            }
            clear();
            addAll(suggestions.build());
            notifyDataSetChanged();
        }
    };

    public KnownHostsAdapter(final Context context, final int viewResourceId, final Collection<String> knownHosts) {
        super(context, viewResourceId, new ArrayList<>());
        domains =  Ordering.natural().sortedCopy(knownHosts);
    }

    public KnownHostsAdapter(final Context context, final int viewResourceId) {
        super(context, viewResourceId, new ArrayList<>());
        domains = ImmutableList.of();
    }

    public void refresh(final Collection<String> knownHosts) {
        this.domains = Ordering.natural().sortedCopy(knownHosts);
        notifyDataSetChanged();
    }

    @Override
    @NonNull
    public Filter getFilter() {
        return domainFilter;
    }
}
