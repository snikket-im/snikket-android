package eu.siacs.conversations.xmpp.pep;

import android.util.Base64;

public class Avatar {
	public String type;
	public String sha1sum;
	public String image;
	public byte[] getImageAsBytes() {
		return Base64.decode(image, Base64.DEFAULT);
	}
	public String getFilename() {
		if (type==null) {
			return sha1sum;
		} else if (type.equalsIgnoreCase("image/webp")) {
			return sha1sum+".webp";
		} else if (type.equalsIgnoreCase("image/png")) {
			return sha1sum+".png";
		} else {
			return sha1sum;
		}
	}
}
