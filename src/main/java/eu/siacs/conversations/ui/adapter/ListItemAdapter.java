package eu.siacs.conversations.ui.adapter;

import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.ListItem;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.xmpp.jid.Jid;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ListItemAdapter extends ArrayAdapter<ListItem> {

	protected XmppActivity activity;
	protected boolean showDynamicTags = false;

	public ListItemAdapter(XmppActivity activity, List<ListItem> objects) {
		super(activity, 0, objects);
		this.activity = activity;
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
		this.showDynamicTags = preferences.getBoolean("show_dynamic_tags",false);
	}

	@Override
	public View getView(int position, View view, ViewGroup parent) {
		LayoutInflater inflater = (LayoutInflater) getContext()
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		ListItem item = getItem(position);
		if (view == null) {
			view = inflater.inflate(R.layout.contact, parent, false);
		}
		TextView tvName = (TextView) view.findViewById(R.id.contact_display_name);
		TextView tvJid = (TextView) view.findViewById(R.id.contact_jid);
		ImageView picture = (ImageView) view.findViewById(R.id.contact_photo);
		LinearLayout tagLayout = (LinearLayout) view.findViewById(R.id.tags);

		List<ListItem.Tag> tags = item.getTags();
		if (tags.size() == 0 || !this.showDynamicTags) {
			tagLayout.setVisibility(View.GONE);
		} else {
			tagLayout.setVisibility(View.VISIBLE);
			tagLayout.removeAllViewsInLayout();
			for(ListItem.Tag tag : tags) {
				TextView tv = (TextView) inflater.inflate(R.layout.list_item_tag,tagLayout,false);
				tv.setText(tag.getName());
				tv.setBackgroundColor(tag.getColor());
				tagLayout.addView(tv);
			}
		}
		final Jid jid = item.getJid();
		if (jid != null) {
			tvJid.setText(jid.toString());
		} else {
			tvJid.setText("");
		}
		tvName.setText(item.getDisplayName());
		picture.setImageBitmap(activity.avatarService().get(item,
				activity.getPixel(48)));
		return view;
	}

}
