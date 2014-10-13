package eu.siacs.conversations.entities;

public interface Downloadable {
	
	public final String[] VALID_EXTENSIONS = { "webp", "jpeg", "jpg", "png" };
	public final String[] VALID_CRYPTO_EXTENSIONS = { "pgp", "gpg", "otr" };
	
	public void start();
}
