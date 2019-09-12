/*
 * Copyright (c) 2018, Daniel Gultsch All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package eu.siacs.conversations.entities;

import android.database.Cursor;

import java.util.Set;

import eu.siacs.conversations.ui.adapter.MessageAdapter;
import rocks.xmpp.addr.Jid;

public class IndividualMessage extends Message {


	private IndividualMessage(Conversational conversation) {
		super(conversation);
	}

	private IndividualMessage(Conversational conversation, String uuid, String conversationUUid, Jid counterpart, Jid trueCounterpart, String body, long timeSent, int encryption, int status, int type, boolean carbon, String remoteMsgId, String relativeFilePath, String serverMsgId, String fingerprint, boolean read, String edited, boolean oob, String errorMessage, Set<ReadByMarker> readByMarkers, boolean markable, boolean deleted, String bodyLanguage) {
		super(conversation, uuid, conversationUUid, counterpart, trueCounterpart, body, timeSent, encryption, status, type, carbon, remoteMsgId, relativeFilePath, serverMsgId, fingerprint, read, edited, oob, errorMessage, readByMarkers, markable, deleted, bodyLanguage);
	}

	@Override
	public Message next() {
		return null;
	}

	@Override
	public Message prev() {
		return null;
	}

	@Override
	public boolean isValidInSession() {
		return true;
	}

	public static Message createDateSeparator(Message message) {
		final Message separator = new IndividualMessage(message.getConversation());
		separator.setType(Message.TYPE_STATUS);
		separator.body = MessageAdapter.DATE_SEPARATOR_BODY;
		separator.setTime(message.getTimeSent());
		return separator;
	}

	public static Message fromCursor(Cursor cursor, Conversational conversation) {
		Jid jid;
		try {
			String value = cursor.getString(cursor.getColumnIndex(COUNTERPART));
			if (value != null) {
				jid = Jid.of(value);
			} else {
				jid = null;
			}
		} catch (IllegalArgumentException e) {
			jid = null;
		} catch (IllegalStateException e) {
			return null; // message too long?
		}
		Jid trueCounterpart;
		try {
			String value = cursor.getString(cursor.getColumnIndex(TRUE_COUNTERPART));
			if (value != null) {
				trueCounterpart = Jid.of(value);
			} else {
				trueCounterpart = null;
			}
		} catch (IllegalArgumentException e) {
			trueCounterpart = null;
		}
		return new IndividualMessage(conversation,
				cursor.getString(cursor.getColumnIndex(UUID)),
				cursor.getString(cursor.getColumnIndex(CONVERSATION)),
				jid,
				trueCounterpart,
				cursor.getString(cursor.getColumnIndex(BODY)),
				cursor.getLong(cursor.getColumnIndex(TIME_SENT)),
				cursor.getInt(cursor.getColumnIndex(ENCRYPTION)),
				cursor.getInt(cursor.getColumnIndex(STATUS)),
				cursor.getInt(cursor.getColumnIndex(TYPE)),
				cursor.getInt(cursor.getColumnIndex(CARBON)) > 0,
				cursor.getString(cursor.getColumnIndex(REMOTE_MSG_ID)),
				cursor.getString(cursor.getColumnIndex(RELATIVE_FILE_PATH)),
				cursor.getString(cursor.getColumnIndex(SERVER_MSG_ID)),
				cursor.getString(cursor.getColumnIndex(FINGERPRINT)),
				cursor.getInt(cursor.getColumnIndex(READ)) > 0,
				cursor.getString(cursor.getColumnIndex(EDITED)),
				cursor.getInt(cursor.getColumnIndex(OOB)) > 0,
				cursor.getString(cursor.getColumnIndex(ERROR_MESSAGE)),
				ReadByMarker.fromJsonString(cursor.getString(cursor.getColumnIndex(READ_BY_MARKERS))),
				cursor.getInt(cursor.getColumnIndex(MARKABLE)) > 0,
				cursor.getInt(cursor.getColumnIndex(DELETED)) > 0,
				cursor.getString(cursor.getColumnIndex(BODY_LANGUAGE))
		);
	}
}
