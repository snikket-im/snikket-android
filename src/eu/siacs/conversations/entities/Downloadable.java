package eu.siacs.conversations.entities;

public interface Downloadable {

	public final String[] VALID_EXTENSIONS = { "webp", "jpeg", "jpg", "png" };
	public final String[] VALID_CRYPTO_EXTENSIONS = { "pgp", "gpg", "otr" };

	public static final int STATUS_UNKNOWN = 0x200;
	public static final int STATUS_CHECKING = 0x201;
	public static final int STATUS_FAILED = 0x202;
	public static final int STATUS_OFFER = 0x203;
	public static final int STATUS_DOWNLOADING = 0x204;
	public static final int STATUS_DELETED = 0x205;

	public boolean start();

	public int getStatus();

	public long getFileSize();
}
