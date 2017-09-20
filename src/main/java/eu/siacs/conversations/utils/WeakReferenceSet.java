package eu.siacs.conversations.utils;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Iterator;

public class WeakReferenceSet<T> extends HashSet<WeakReference<T>> {

	public void removeWeakReferenceTo(T reference) {
		for (Iterator<WeakReference<T>> iterator = iterator(); iterator.hasNext(); ) {
			if (reference == iterator.next().get()) {
				iterator.remove();
			}
		}
	}


	public void addWeakReferenceTo(T reference) {
		for (WeakReference<T> weakReference : this) {
			if (reference == weakReference.get()) {
				return;
			}
		}
		this.add(new WeakReference<>(reference));
	}
}
