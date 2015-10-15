package eu.siacs.conversations.crypto;

import android.app.PendingIntent;

import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.UiCallback;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class PgpDecryptionService {

	private final XmppConnectionService xmppConnectionService;
	private final ConcurrentHashMap<String, List<Message>> messages = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Boolean> decryptingMessages = new ConcurrentHashMap<>();
	private Boolean keychainLocked = false;
	private final Object keychainLockedLock = new Object();

	public PgpDecryptionService(XmppConnectionService xmppConnectionService) {
		this.xmppConnectionService = xmppConnectionService;
	}

	public void add(Message message) {
		if (isRunning()) {
			decryptDirectly(message);
		} else {
			store(message);
		}
	}

	public void addAll(List<Message> messagesList) {
		if (!messagesList.isEmpty()) {
			String conversationUuid = messagesList.get(0).getConversation().getUuid();
			if (!messages.containsKey(conversationUuid)) {
				List<Message> list = Collections.synchronizedList(new LinkedList<Message>());
				messages.put(conversationUuid, list);
			}
			synchronized (messages.get(conversationUuid)) {
				messages.get(conversationUuid).addAll(messagesList);
			}
			decryptAllMessages();
		}
	}

	public void onKeychainUnlocked() {
		synchronized (keychainLockedLock) {
			keychainLocked = false;
		}
		decryptAllMessages();
	}

	public void onKeychainLocked() {
		synchronized (keychainLockedLock) {
			keychainLocked = true;
		}
		xmppConnectionService.updateConversationUi();
	}

	public void onOpenPgpServiceBound() {
		decryptAllMessages();
	}

	public boolean isRunning() {
		synchronized (keychainLockedLock) {
			return !keychainLocked;
		}
	}

	private void store(Message message) {
		if (messages.containsKey(message.getConversation().getUuid())) {
			messages.get(message.getConversation().getUuid()).add(message);
		} else {
			List<Message> messageList = Collections.synchronizedList(new LinkedList<Message>());
			messageList.add(message);
			messages.put(message.getConversation().getUuid(), messageList);
		}
	}

	private void decryptAllMessages() {
		for (String uuid : messages.keySet()) {
			decryptMessages(uuid);
		}
	}

	private void decryptMessages(final String uuid) {
		synchronized (decryptingMessages) {
			Boolean decrypting = decryptingMessages.get(uuid);
			if ((decrypting != null && !decrypting) || decrypting == null) {
				decryptingMessages.put(uuid, true);
				decryptMessage(uuid);
			}
		}
	}

	private void decryptMessage(final String uuid) {
		Message message = null;
		synchronized (messages.get(uuid)) {
			while (!messages.get(uuid).isEmpty()) {
				if (messages.get(uuid).get(0).getEncryption() == Message.ENCRYPTION_PGP) {
					if (isRunning()) {
						message = messages.get(uuid).remove(0);
					}
					break;
				} else {
					messages.get(uuid).remove(0);
				}
			}
			if (message != null && xmppConnectionService.getPgpEngine() != null) {
				xmppConnectionService.getPgpEngine().decrypt(message, new UiCallback<Message>() {

					@Override
					public void userInputRequried(PendingIntent pi, Message message) {
						messages.get(uuid).add(0, message);
						decryptingMessages.put(uuid, false);
					}

					@Override
					public void success(Message message) {
						xmppConnectionService.updateConversationUi();
						decryptMessage(uuid);
					}

					@Override
					public void error(int error, Message message) {
						message.setEncryption(Message.ENCRYPTION_DECRYPTION_FAILED);
						xmppConnectionService.updateConversationUi();
						decryptMessage(uuid);
					}
				});
			} else {
				decryptingMessages.put(uuid, false);
			}
		}
	}

	private void decryptDirectly(final Message message) {
		if (message.getEncryption() == Message.ENCRYPTION_PGP && xmppConnectionService.getPgpEngine() != null) {
			xmppConnectionService.getPgpEngine().decrypt(message, new UiCallback<Message>() {

				@Override
				public void userInputRequried(PendingIntent pi, Message message) {
					store(message);
				}

				@Override
				public void success(Message message) {
					xmppConnectionService.updateConversationUi();
					xmppConnectionService.getNotificationService().updateNotification(false);
				}

				@Override
				public void error(int error, Message message) {
					message.setEncryption(Message.ENCRYPTION_DECRYPTION_FAILED);
					xmppConnectionService.updateConversationUi();
				}
			});
		}
	}
}
