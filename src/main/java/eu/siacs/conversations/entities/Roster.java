package eu.siacs.conversations.entities;

import androidx.annotation.NonNull;
import eu.siacs.conversations.android.AbstractPhoneContact;
import eu.siacs.conversations.xmpp.Jid;
import java.util.List;

public interface Roster {

    List<Contact> getContacts();

    List<Contact> getWithSystemAccounts(Class<? extends AbstractPhoneContact> clazz);

    Contact getContact(@NonNull final Jid jid);

    Contact getContactFromContactList(@NonNull final Jid jid);
}
