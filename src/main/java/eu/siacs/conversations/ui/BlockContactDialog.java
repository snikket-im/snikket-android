package eu.siacs.conversations.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.TypefaceSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Blockable;
import eu.siacs.conversations.services.XmppConnectionService;

public final class BlockContactDialog {
	public static void show(final Context context,
			final XmppConnectionService xmppConnectionService,
			final Blockable blockable) {
		final AlertDialog.Builder builder = new AlertDialog.Builder(context);
		final boolean isBlocked = blockable.isBlocked();
		builder.setNegativeButton(R.string.cancel, null);
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		LinearLayout view = (LinearLayout) inflater.inflate(R.layout.dialog_block_contact,null);
		TextView message = (TextView) view.findViewById(R.id.text);
		final CheckBox report = (CheckBox) view.findViewById(R.id.report_spam);
		final boolean reporting = blockable.getAccount().getXmppConnection().getFeatures().spamReporting();
		report.setVisibility(!isBlocked && reporting ? View.VISIBLE : View.GONE);
		builder.setView(view);

		String value;
		SpannableString spannable;
		if (blockable.getJid().isDomainJid() || blockable.getAccount().isBlocked(blockable.getJid().toDomainJid())) {
			builder.setTitle(isBlocked ? R.string.action_unblock_domain : R.string.action_block_domain);
			value = blockable.getJid().toDomainJid().toString();
			spannable = new SpannableString(context.getString(isBlocked ? R.string.unblock_domain_text : R.string.block_domain_text, value));
			message.setText(spannable);
		} else {
			builder.setTitle(isBlocked ? R.string.action_unblock_contact : R.string.action_block_contact);
			value = blockable.getJid().toBareJid().toString();
			spannable = new SpannableString(context.getString(isBlocked ? R.string.unblock_contact_text : R.string.block_contact_text, value));
		}
		int start = spannable.toString().indexOf(value);
		if (start >= 0) {
			spannable.setSpan(new TypefaceSpan("monospace"),start,start + value.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		}
		message.setText(spannable);
		builder.setPositiveButton(isBlocked ? R.string.unblock : R.string.block, new DialogInterface.OnClickListener() {

			@Override
			public void onClick(final DialogInterface dialog, final int which) {
				if (isBlocked) {
					xmppConnectionService.sendUnblockRequest(blockable);
				} else {
					xmppConnectionService.sendBlockRequest(blockable, report.isChecked());
				}
			}
		});
		builder.create().show();
	}
}
