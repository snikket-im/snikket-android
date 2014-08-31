package eu.siacs.conversations.xmpp.jingle;

public interface OnFileTransmissionStatusChanged {
	public void onFileTransmitted(JingleFile file);

	public void onFileTransferAborted();
}
