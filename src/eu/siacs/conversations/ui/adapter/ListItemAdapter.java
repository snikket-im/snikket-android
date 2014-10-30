package eu.siacs.conversations.ui.adapter;

import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.ListItem;
import eu.siacs.conversations.ui.XmppActivity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class ListItemAdapter extends ArrayAdapter<ListItem> {

	protected XmppActivity activity;

	public ListItemAdapter(XmppActivity activity, List<ListItem> objects) {
		super(activity, 0, objects);
		this.activity = activity;
	}

	@Override
	public View getView(int position, View view, ViewGroup parent) {
		LayoutInflater inflater = (LayoutInflater) getContext()
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		ListItem item = getItem(position);
		if (view == null) {
			view = inflater.inflate(R.layout.contact, parent, false);
		}
		TextView name = (TextView) view.findViewById(R.id.contact_display_name);
		TextView jid = (TextView) view.findViewById(R.id.contact_jid);
		ImageView picture = (ImageView) view.findViewById(R.id.contact_photo);

		jid.setText(item.getJid());
		name.setText(item.getDisplayName());
		picture.setImageBitmap(activity.avatarService().get(item,
				activity.getPixel(48)));
		return view;
	}

}
