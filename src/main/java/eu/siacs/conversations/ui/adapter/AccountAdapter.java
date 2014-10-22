package eu.siacs.conversations.ui.adapter;

import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.ui.XmppActivity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class AccountAdapter extends ArrayAdapter<Account> {

	private XmppActivity activity;

	public AccountAdapter(XmppActivity activity, List<Account> objects) {
		super(activity, 0, objects);
		this.activity = activity;
	}

	@Override
	public View getView(int position, View view, ViewGroup parent) {
		Account account = getItem(position);
		if (view == null) {
			LayoutInflater inflater = (LayoutInflater) getContext()
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			view = (View) inflater.inflate(R.layout.account_row, parent, false);
		}
		TextView jid = (TextView) view.findViewById(R.id.account_jid);
		jid.setText(account.getJid());
		TextView statusView = (TextView) view.findViewById(R.id.account_status);
		ImageView imageView = (ImageView) view.findViewById(R.id.account_image);
		imageView.setImageBitmap(activity.avatarService().get(account,
				activity.getPixel(48)));
		switch (account.getStatus()) {
		case Account.STATUS_DISABLED:
			statusView.setText(getContext().getString(
					R.string.account_status_disabled));
			statusView.setTextColor(activity.getSecondaryTextColor());
			break;
		case Account.STATUS_ONLINE:
			statusView.setText(getContext().getString(
					R.string.account_status_online));
			statusView.setTextColor(activity.getPrimaryColor());
			break;
		case Account.STATUS_CONNECTING:
			statusView.setText(getContext().getString(
					R.string.account_status_connecting));
			statusView.setTextColor(activity.getSecondaryTextColor());
			break;
		case Account.STATUS_OFFLINE:
			statusView.setText(getContext().getString(
					R.string.account_status_offline));
			statusView.setTextColor(activity.getWarningTextColor());
			break;
		case Account.STATUS_UNAUTHORIZED:
			statusView.setText(getContext().getString(
					R.string.account_status_unauthorized));
			statusView.setTextColor(activity.getWarningTextColor());
			break;
		case Account.STATUS_SERVER_NOT_FOUND:
			statusView.setText(getContext().getString(
					R.string.account_status_not_found));
			statusView.setTextColor(activity.getWarningTextColor());
			break;
		case Account.STATUS_NO_INTERNET:
			statusView.setText(getContext().getString(
					R.string.account_status_no_internet));
			statusView.setTextColor(activity.getWarningTextColor());
			break;
		case Account.STATUS_REGISTRATION_FAILED:
			statusView.setText(getContext().getString(
					R.string.account_status_regis_fail));
			statusView.setTextColor(activity.getWarningTextColor());
			break;
		case Account.STATUS_REGISTRATION_CONFLICT:
			statusView.setText(getContext().getString(
					R.string.account_status_regis_conflict));
			statusView.setTextColor(activity.getWarningTextColor());
			break;
		case Account.STATUS_REGISTRATION_SUCCESSFULL:
			statusView.setText(getContext().getString(
					R.string.account_status_regis_success));
			statusView.setTextColor(activity.getSecondaryTextColor());
			break;
		case Account.STATUS_REGISTRATION_NOT_SUPPORTED:
			statusView.setText(getContext().getString(
					R.string.account_status_regis_not_sup));
			statusView.setTextColor(activity.getWarningTextColor());
			break;
		default:
			statusView.setText("");
			break;
		}

		return view;
	}
}
