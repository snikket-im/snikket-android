package eu.siacs.conversations.xmpp.jingle;

import eu.siacs.conversations.entities.DownloadableFile;

public interface OnFileTransmissionStatusChanged {
	public void onFileTransmitted(DownloadableFile file);

	public void onFileTransferAborted();
}
