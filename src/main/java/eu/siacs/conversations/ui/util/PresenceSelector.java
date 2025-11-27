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
import android.content.Context;
import android.util.Pair;
import android.widget.Toast;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.collect.Iterables;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Presences;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.jingle.RtpCapability;
import im.conversations.android.xmpp.model.stanza.Presence;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class PresenceSelector {

    public static void showPresenceSelectionDialog(
            Activity activity, final Conversation conversation, final OnPresenceSelected listener) {
        final Contact contact = conversation.getContact();
        showPresenceSelectionDialog(
                activity,
                contact,
                contact.getPresences(),
                fullJid -> {
                    conversation.setNextCounterpart(fullJid);
                    listener.onPresenceSelected();
                });
    }

    public static void selectFullJidForDirectRtpConnection(
            final Activity activity,
            final Contact contact,
            final RtpCapability.Capability required,
            final OnFullJidSelected onFullJidSelected) {
        final var resources = RtpCapability.filterPresences(contact, required);
        if (resources.isEmpty()) {
            Toast.makeText(activity, R.string.rtp_state_contact_offline, Toast.LENGTH_LONG).show();
        } else if (resources.size() == 1) {
            onFullJidSelected.onFullJidSelected(Iterables.getFirst(resources, null).getFrom());
        } else {
            showPresenceSelectionDialog(activity, contact, resources, onFullJidSelected);
        }
    }

    private static void showPresenceSelectionDialog(
            final Activity activity,
            final Contact contact,
            final List<Presence> presences,
            final OnFullJidSelected onFullJidSelected) {
        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
        builder.setTitle(activity.getString(R.string.choose_presence));
        Pair<Map<Jid, String>, Map<Jid, String>> typeAndName =
                Presences.toTypeAndNameMap(contact.getAccount(), presences);
        final Map<Jid, String> resourceTypeMap = typeAndName.first;
        final Map<Jid, String> resourceNameMap = typeAndName.second;
        final String[] readableIdentities = new String[presences.size()];
        final AtomicInteger selectedResource = new AtomicInteger(0);
        int i = 0;
        for (final var presence : presences) {
            String resource = presence.getFrom().getResource();
            if (resource.equals(contact.getLastResource())) {
                selectedResource.set(i);
            }
            String type = resourceTypeMap.get(presence.getFrom());
            String name = resourceNameMap.get(presence.getFrom());
            if (type != null) {
                if (Collections.frequency(resourceTypeMap.values(), type) == 1) {
                    readableIdentities[i] = translateType(activity, type);
                } else if (name != null) {
                    if (Collections.frequency(resourceNameMap.values(), name) == 1
                            || CryptoHelper.UUID_PATTERN.matcher(resource).matches()) {
                        readableIdentities[i] = translateType(activity, type) + "  (" + name + ")";
                    } else {
                        readableIdentities[i] =
                                translateType(activity, type)
                                        + " ("
                                        + name
                                        + " / "
                                        + resource
                                        + ")";
                    }
                } else {
                    readableIdentities[i] = translateType(activity, type) + " (" + resource + ")";
                }
            } else {
                readableIdentities[i] = resource;
            }
            ++i;
        }
        builder.setSingleChoiceItems(
                readableIdentities,
                selectedResource.get(),
                (dialog, which) -> selectedResource.set(which));
        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(
                R.string.ok,
                (dialog, which) -> {
                    final var selectedPresence = presences.get(selectedResource.get());
                    onFullJidSelected.onFullJidSelected(selectedPresence.getFrom());
                });
        builder.create().show();
    }

    public static Jid getNextCounterpart(final Contact contact, final String resource) {
        return getNextCounterpart(contact.getAddress(), resource);
    }

    public static Jid getNextCounterpart(final Jid jid, final String resource) {
        if (resource.isEmpty()) {
            return jid.asBareJid();
        } else {
            return jid.withResource(resource);
        }
    }

    public static void warnMutualPresenceSubscription(
            final Activity activity,
            final Conversation conversation,
            final OnPresenceSelected listener) {
        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
        builder.setTitle(conversation.getContact().getAddress().toString());
        builder.setMessage(R.string.without_mutual_presence_updates);
        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(
                R.string.ignore,
                (dialog, which) -> {
                    conversation.setNextCounterpart(null);
                    if (listener != null) {
                        listener.onPresenceSelected();
                    }
                });
        builder.create().show();
    }

    private static String translateType(Context context, String type) {
        switch (type.toLowerCase()) {
            case "pc":
                return context.getString(R.string.type_pc);
            case "phone":
                return context.getString(R.string.type_phone);
            case "tablet":
                return context.getString(R.string.type_tablet);
            case "web":
                return context.getString(R.string.type_web);
            case "console":
                return context.getString(R.string.type_console);
            default:
                return type;
        }
    }

    public interface OnPresenceSelected {
        void onPresenceSelected();
    }

    public interface OnFullJidSelected {
        void onFullJidSelected(Jid jid);
    }
}
