package eu.siacs.conversations.entities;

public interface Downloadable {

	public final String[] VALID_IMAGE_EXTENSIONS = {"webp", "jpeg", "jpg", "png", "jpe"};
	public final String[] VALID_CRYPTO_EXTENSIONS = {"pgp", "gpg", "otr"};

	public static final int STATUS_UNKNOWN = 0x200;
	public static final int STATUS_CHECKING = 0x201;
	public static final int STATUS_FAILED = 0x202;
	public static final int STATUS_OFFER = 0x203;
	public static final int STATUS_DOWNLOADING = 0x204;
	public static final int STATUS_DELETED = 0x205;
	public static final int STATUS_OFFER_CHECK_FILESIZE = 0x206;
	public static final int STATUS_UPLOADING = 0x207;

	public boolean start();

	public int getStatus();

	public long getFileSize();

	public int getProgress();

	public String getMimeType();

	public void cancel();
}
