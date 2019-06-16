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

package eu.siacs.conversations.ui.util;

import static eu.siacs.conversations.ui.ConversationFragment.ATTACHMENT_CHOICE_CHOOSE_IMAGE;
import static eu.siacs.conversations.ui.ConversationFragment.ATTACHMENT_CHOICE_LOCATION;
import static eu.siacs.conversations.ui.ConversationFragment.ATTACHMENT_CHOICE_RECORD_VIDEO;
import static eu.siacs.conversations.ui.ConversationFragment.ATTACHMENT_CHOICE_RECORD_VOICE;
import static eu.siacs.conversations.ui.ConversationFragment.ATTACHMENT_CHOICE_TAKE_PHOTO;

public enum SendButtonAction {
	TEXT, TAKE_PHOTO, SEND_LOCATION, RECORD_VOICE, CANCEL, CHOOSE_PICTURE, RECORD_VIDEO;

	public static SendButtonAction valueOfOrDefault(final String setting) {
		if (setting == null) {
			return TEXT;
		}
		try {
			return valueOf(setting);
		} catch (IllegalArgumentException e) {
			return TEXT;
		}
	}

	public static SendButtonAction of(int attachmentChoice) {
		switch (attachmentChoice) {
			case ATTACHMENT_CHOICE_LOCATION:
				return SEND_LOCATION;
			case ATTACHMENT_CHOICE_RECORD_VOICE:
				return RECORD_VOICE;
			case ATTACHMENT_CHOICE_RECORD_VIDEO:
				return RECORD_VIDEO;
			case ATTACHMENT_CHOICE_TAKE_PHOTO:
				return TAKE_PHOTO;
			case ATTACHMENT_CHOICE_CHOOSE_IMAGE:
				return CHOOSE_PICTURE;
			default:
				throw new IllegalArgumentException("Not a known attachment choice");
		}
	}

	public int toChoice() {
		switch (this) {
			case TAKE_PHOTO:
				return ATTACHMENT_CHOICE_TAKE_PHOTO;
			case SEND_LOCATION:
				return ATTACHMENT_CHOICE_LOCATION;
			case RECORD_VOICE:
				return ATTACHMENT_CHOICE_RECORD_VOICE;
			case CHOOSE_PICTURE:
				return ATTACHMENT_CHOICE_CHOOSE_IMAGE;
			case RECORD_VIDEO:
				return ATTACHMENT_CHOICE_RECORD_VIDEO;
			default:
				return 0;
		}
	}
}