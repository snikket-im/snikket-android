package eu.siacs.conversations.ui;

public interface UiInformableCallback<T> extends UiCallback<T> {
    void inform(String text);
}
