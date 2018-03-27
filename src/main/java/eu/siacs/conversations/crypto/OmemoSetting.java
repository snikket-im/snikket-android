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

package eu.siacs.conversations.crypto;


import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.ui.SettingsActivity;

public class OmemoSetting {

	private static boolean always = false;
	private static int encryption = Message.ENCRYPTION_AXOLOTL;

	public static boolean isAlways() {
		return always;
	}

	public static int getEncryption() {
		return encryption;
	}

	public static void load(final Context context, final SharedPreferences sharedPreferences) {
		final String value = sharedPreferences.getString(SettingsActivity.OMEMO_SETTING, context.getResources().getString(R.string.omemo_setting_default));
		switch (value) {
			case "always":
				always = true;
				encryption = Message.ENCRYPTION_AXOLOTL;
				break;
			case "default_off":
				always = false;
				encryption = Message.ENCRYPTION_NONE;
				break;
			default:
				always = false;
				encryption = Message.ENCRYPTION_AXOLOTL;
				break;

		}
	}

	public static void load(final Context context) {
		load(context, PreferenceManager.getDefaultSharedPreferences(context));
	}
}
