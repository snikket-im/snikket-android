package eu.siacs.conversations.services;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.persistance.FileBackend;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

public class ImageProvider extends ContentProvider {

	@Override
	public ParcelFileDescriptor openFile(Uri uri, String mode)
			throws FileNotFoundException {
		ParcelFileDescriptor pfd;
		FileBackend fileBackend = new FileBackend(getContext());
		if ("r".equals(mode)) {
			DatabaseBackend databaseBackend = DatabaseBackend
					.getInstance(getContext());
			String uuids = uri.getPath();
			Log.d("xmppService", "uuids = " + uuids+" mode="+mode);
			if (uuids == null) {
				throw new FileNotFoundException();
			}
			String[] uuidsSplited = uuids.split("/");
			if (uuidsSplited.length != 3) {
				throw new FileNotFoundException();
			}
			String conversationUuid = uuidsSplited[1];
			String messageUuid = uuidsSplited[2];
	
			Conversation conversation = databaseBackend
					.findConversationByUuid(conversationUuid);
			if (conversation == null) {
				throw new FileNotFoundException("conversation " + conversationUuid
						+ " could not be found");
			}
			Message message = databaseBackend.findMessageByUuid(messageUuid);
			if (message == null) {
				throw new FileNotFoundException("message " + messageUuid
						+ " could not be found");
			}
	
			Account account = databaseBackend.findAccountByUuid(conversation
					.getAccountUuid());
			if (account == null) {
				throw new FileNotFoundException("account "
						+ conversation.getAccountUuid() + " cound not be found");
			}
			message.setConversation(conversation);
			conversation.setAccount(account);
	
			File file = fileBackend.getJingleFile(message);
			pfd = ParcelFileDescriptor.open(file,
					ParcelFileDescriptor.MODE_READ_ONLY);
			return pfd;
		} else if ("w".equals(mode)){
			File file = fileBackend.getIncomingFile();
			try {
				file.createNewFile();
			} catch (IOException e) {
				throw new FileNotFoundException();
			}
			pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE);
			return pfd;
		} else {
			throw new FileNotFoundException();
		}
	}

	@Override
	public int delete(Uri arg0, String arg1, String[] arg2) {
		return 0;
	}

	@Override
	public String getType(Uri arg0) {
		return null;
	}

	@Override
	public Uri insert(Uri arg0, ContentValues arg1) {
		return null;
	}

	@Override
	public boolean onCreate() {
		return false;
	}

	@Override
	public Cursor query(Uri arg0, String[] arg1, String arg2, String[] arg3,
			String arg4) {
		return null;
	}

	@Override
	public int update(Uri arg0, ContentValues arg1, String arg2, String[] arg3) {
		return 0;
	}
	
	public static Uri getContentUri(Message message) {
		return Uri
				.parse("content://eu.siacs.conversations.images/"
						+ message.getConversationUuid()
						+ "/"
						+ message.getUuid());
	}

	public static Uri getIncomingContentUri() {
		return Uri.parse("content://eu.siacs.conversations.images/incoming");
	}
}