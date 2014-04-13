package eu.siacs.conversations.xmpp.jingle;

import java.io.File;

public class JingleFile extends File {
	
	private static final long serialVersionUID = 2247012619505115863L;
	
	private long expectedSize = 0;
	private String sha1sum;
	
	public JingleFile(String path) {
		super(path);
	}
	
	public long getSize() {
		return super.length();
	}
	
	public long getExpectedSize() {
		return this.expectedSize;
	}
	
	public void setExpectedSize(long size) {
		this.expectedSize = size;
	}
	
	public String getSha1Sum() {
		return this.sha1sum;
	}
	
	public void setSha1Sum(String sum) {
		this.sha1sum = sum;
	}
}
