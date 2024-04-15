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

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.preference.PreferenceManager;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Presence;
import eu.siacs.conversations.ui.Activities;
import eu.siacs.conversations.ui.ConversationFragment;
import eu.siacs.conversations.utils.UIHelper;

public class SendButtonTool {

    public static SendButtonAction getAction(
            final Activity activity, final Conversation c, final String text) {
        if (activity == null) {
            return SendButtonAction.TEXT;
        }
        final SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(activity);
        final boolean empty = text.isEmpty();
        final boolean conference = c.getMode() == Conversation.MODE_MULTI;
        if (c.getCorrectingMessage() != null
                && (empty || text.equals(c.getCorrectingMessage().getBody()))) {
            return SendButtonAction.CANCEL;
        } else if (conference && !c.getAccount().httpUploadAvailable()) {
            if (empty && c.getNextCounterpart() != null) {
                return SendButtonAction.CANCEL;
            } else {
                return SendButtonAction.TEXT;
            }
        } else {
            if (empty) {
                if (conference && c.getNextCounterpart() != null) {
                    return SendButtonAction.CANCEL;
                } else {
                    String setting =
                            preferences.getString(
                                    "quick_action",
                                    activity.getResources().getString(R.string.quick_action));
                    if (!"none".equals(setting)
                            && UIHelper.receivedLocationQuestion(c.getLatestMessage())) {
                        return SendButtonAction.SEND_LOCATION;
                    } else {
                        if ("recent".equals(setting)) {
                            setting =
                                    preferences.getString(
                                            ConversationFragment.RECENTLY_USED_QUICK_ACTION,
                                            SendButtonAction.TEXT.toString());
                            return SendButtonAction.valueOfOrDefault(setting);
                        } else {
                            return SendButtonAction.valueOfOrDefault(setting);
                        }
                    }
                }
            } else {
                return SendButtonAction.TEXT;
            }
        }
    }

    public @DrawableRes static int getSendButtonImageResource(final SendButtonAction action) {
        return switch (action) {
            case TEXT -> R.drawable.ic_send_24dp;
            case TAKE_PHOTO -> R.drawable.ic_camera_alt_24dp;
            case SEND_LOCATION -> R.drawable.ic_location_pin_24dp;
            case CHOOSE_PICTURE -> R.drawable.ic_image_24dp;
            case RECORD_VIDEO -> R.drawable.ic_videocam_24dp;
            case RECORD_VOICE -> R.drawable.ic_mic_24dp;
            case CANCEL -> R.drawable.ic_cancel_24dp;
        };
    }

    public @ColorInt static int getSendButtonColor(final View view, final Presence.Status status) {
        final boolean nightMode = Activities.isNightMode(view.getContext());
        return switch (status) {
            case OFFLINE -> MaterialColors.getColor(
                    view, com.google.android.material.R.attr.colorOnSurface);
            case ONLINE, CHAT -> MaterialColors.harmonizeWithPrimary(
                    view.getContext(),
                    ContextCompat.getColor(
                            view.getContext(), nightMode ? R.color.green_300 : R.color.green_800));
            case AWAY -> MaterialColors.harmonizeWithPrimary(
                    view.getContext(),
                    ContextCompat.getColor(
                            view.getContext(), nightMode ? R.color.amber_300 : R.color.amber_800));
            case XA -> MaterialColors.harmonizeWithPrimary(
                    view.getContext(),
                    ContextCompat.getColor(
                            view.getContext(),
                            nightMode ? R.color.orange_300 : R.color.orange_800));
            case DND -> MaterialColors.harmonizeWithPrimary(
                    view.getContext(),
                    ContextCompat.getColor(
                            view.getContext(), nightMode ? R.color.red_300 : R.color.red_800));
        };
    }
}
