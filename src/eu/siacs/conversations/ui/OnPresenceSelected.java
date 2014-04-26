package eu.siacs.conversations.ui;

public interface OnPresenceSelected {
	public void onPresenceSelected(boolean success, String presence);
	public void onSendPlainTextInstead();
}
