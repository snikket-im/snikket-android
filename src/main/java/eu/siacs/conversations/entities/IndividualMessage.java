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

import java.util.Collection;
import java.util.Set;

import eu.siacs.conversations.ui.adapter.MessageAdapter;
import eu.siacs.conversations.xmpp.Jid;

public class IndividualMessage extends Message {


	private IndividualMessage(Conversational conversation) {
		super(conversation);
	}

	private IndividualMessage(Conversational conversation, String uuid, String conversationUUid, Jid counterpart, Jid trueCounterpart, String body, long timeSent, int encryption, int status, int type, boolean carbon, String remoteMsgId, String relativeFilePath, String serverMsgId, String fingerprint, boolean read, String edited, boolean oob, String errorMessage, Set<ReadByMarker> readByMarkers, boolean markable, boolean deleted, String bodyLanguage, String occupantId, Collection<Reaction> reactions) {
		super(conversation, uuid, conversationUUid, counterpart, trueCounterpart, body, timeSent, encryption, status, type, carbon, remoteMsgId, relativeFilePath, serverMsgId, fingerprint, read, edited, oob, errorMessage, readByMarkers, markable, deleted, bodyLanguage, occupantId, reactions);
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
			String value = cursor.getString(cursor.getColumnIndexOrThrow(COUNTERPART));
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
			String value = cursor.getString(cursor.getColumnIndexOrThrow(TRUE_COUNTERPART));
			if (value != null) {
				trueCounterpart = Jid.of(value);
			} else {
				trueCounterpart = null;
			}
		} catch (IllegalArgumentException e) {
			trueCounterpart = null;
		}
		return new IndividualMessage(conversation,
				cursor.getString(cursor.getColumnIndexOrThrow(UUID)),
				cursor.getString(cursor.getColumnIndexOrThrow(CONVERSATION)),
				jid,
				trueCounterpart,
				cursor.getString(cursor.getColumnIndexOrThrow(BODY)),
				cursor.getLong(cursor.getColumnIndexOrThrow(TIME_SENT)),
				cursor.getInt(cursor.getColumnIndexOrThrow(ENCRYPTION)),
				cursor.getInt(cursor.getColumnIndexOrThrow(STATUS)),
				cursor.getInt(cursor.getColumnIndexOrThrow(TYPE)),
				cursor.getInt(cursor.getColumnIndexOrThrow(CARBON)) > 0,
				cursor.getString(cursor.getColumnIndexOrThrow(REMOTE_MSG_ID)),
				cursor.getString(cursor.getColumnIndexOrThrow(RELATIVE_FILE_PATH)),
				cursor.getString(cursor.getColumnIndexOrThrow(SERVER_MSG_ID)),
				cursor.getString(cursor.getColumnIndexOrThrow(FINGERPRINT)),
				cursor.getInt(cursor.getColumnIndexOrThrow(READ)) > 0,
				cursor.getString(cursor.getColumnIndexOrThrow(EDITED)),
				cursor.getInt(cursor.getColumnIndexOrThrow(OOB)) > 0,
				cursor.getString(cursor.getColumnIndexOrThrow(ERROR_MESSAGE)),
				ReadByMarker.fromJsonString(cursor.getString(cursor.getColumnIndexOrThrow(READ_BY_MARKERS))),
				cursor.getInt(cursor.getColumnIndexOrThrow(MARKABLE)) > 0,
				cursor.getInt(cursor.getColumnIndexOrThrow(DELETED)) > 0,
				cursor.getString(cursor.getColumnIndexOrThrow(BODY_LANGUAGE)),
				cursor.getString(cursor.getColumnIndexOrThrow(OCCUPANT_ID)),
				Reaction.fromString(cursor.getString(cursor.getColumnIndexOrThrow(REACTIONS)))
		);
	}
}
