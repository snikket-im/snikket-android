package eu.siacs.conversations.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

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

		if (blockable.getJid().isDomainJid() || blockable.getAccount().isBlocked(blockable.getJid().toDomainJid())) {
			builder.setTitle(isBlocked ? R.string.action_unblock_domain : R.string.action_block_domain);
			builder.setMessage(context.getResources().getString(isBlocked ? R.string.unblock_domain_text : R.string.block_domain_text,
						blockable.getJid().toDomainJid()));
		} else {
			builder.setTitle(isBlocked ? R.string.action_unblock_contact : R.string.action_block_contact);
			builder.setMessage(context.getResources().getString(isBlocked ? R.string.unblock_contact_text : R.string.block_contact_text,
						blockable.getJid().toBareJid()));
		}
		builder.setPositiveButton(isBlocked ? R.string.unblock : R.string.block, new DialogInterface.OnClickListener() {

			@Override
			public void onClick(final DialogInterface dialog, final int which) {
				if (isBlocked) {
					xmppConnectionService.sendUnblockRequest(blockable);
				} else {
					xmppConnectionService.sendBlockRequest(blockable);
				}
			}
		});
		builder.create().show();
	}
}
