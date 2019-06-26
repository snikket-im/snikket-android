package eu.siacs.conversations.services;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;

public class PushManagementService {

	protected final XmppConnectionService mXmppConnectionService;

	public PushManagementService(XmppConnectionService service) {
		this.mXmppConnectionService = service;
	}

	void registerPushTokenOnServer(Account account) {
		//stub implementation. only affects playstore flavor
	}

	void registerPushTokenOnServer(Conversation conversation) {
		//stub implementation. only affects playstore flavor
	}

	void unregisterChannel(Account account, String hash) {
		//stub implementation. only affects playstore flavor
	}

	void disablePushOnServer(Conversation conversation) {
		//stub implementation. only affects playstore flavor
	}

	public boolean available(Account account) {
		return false;
	}

	public boolean isStub() {
		return true;
	}

	public boolean availableAndUseful(Account account) {
		return false;
	}
}
