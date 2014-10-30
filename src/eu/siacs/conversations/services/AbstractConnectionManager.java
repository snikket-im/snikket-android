package eu.siacs.conversations.services;

public class AbstractConnectionManager {
	protected XmppConnectionService mXmppConnectionService;

	public AbstractConnectionManager(XmppConnectionService service) {
		this.mXmppConnectionService = service;
	}

	public XmppConnectionService getXmppConnectionService() {
		return this.mXmppConnectionService;
	}

	public long getAutoAcceptFileSize() {
		String config = this.mXmppConnectionService.getPreferences().getString(
				"auto_accept_file_size", "524288");
		try {
			return Long.parseLong(config);
		} catch (NumberFormatException e) {
			return 524288;
		}
	}
}
