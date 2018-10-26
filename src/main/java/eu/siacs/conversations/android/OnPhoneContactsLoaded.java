package eu.siacs.conversations.android;

import java.util.Collection;

public interface OnPhoneContactsLoaded<T extends  AbstractPhoneContact> {

    void onPhoneContactsLoaded(Collection<T> contacts);
}
