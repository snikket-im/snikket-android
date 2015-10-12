package eu.siacs.conversations.xmpp.jingle;

import eu.siacs.conversations.entities.DownloadableFile;

public interface OnFileTransmissionStatusChanged {
	void onFileTransmitted(DownloadableFile file);

	void onFileTransferAborted();
}
