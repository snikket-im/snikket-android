package eu.siacs.conversations.xmpp;

public interface OnUpdateBlocklist {
	// Use an enum instead of a boolean to make sure we don't run into the boolean trap
	// (`onUpdateBlocklist(true)' doesn't read well, and could be confusing).
	public static enum Status {
		BLOCKED,
		UNBLOCKED
	}

	@SuppressWarnings("MethodNameSameAsClassName")
	public void OnUpdateBlocklist(final Status status);
}
