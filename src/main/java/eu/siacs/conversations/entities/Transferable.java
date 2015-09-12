package eu.siacs.conversations.entities;

public interface Transferable {

	String[] VALID_IMAGE_EXTENSIONS = {"webp", "jpeg", "jpg", "png", "jpe"};
	String[] VALID_CRYPTO_EXTENSIONS = {"pgp", "gpg", "otr"};
	String[] WELL_KNOWN_EXTENSIONS = {"pdf","m4a","mp4"};

	int STATUS_UNKNOWN = 0x200;
	int STATUS_CHECKING = 0x201;
	int STATUS_FAILED = 0x202;
	int STATUS_OFFER = 0x203;
	int STATUS_DOWNLOADING = 0x204;
	int STATUS_DELETED = 0x205;
	int STATUS_OFFER_CHECK_FILESIZE = 0x206;
	int STATUS_UPLOADING = 0x207;


	boolean start();

	int getStatus();

	long getFileSize();

	int getProgress();

	void cancel();
}
