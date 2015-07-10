package eu.siacs.conversations.entities;

public class TransferablePlaceholder implements Transferable {

	private int status;

	public TransferablePlaceholder(int status) {
		this.status = status;
	}
	@Override
	public boolean start() {
		return false;
	}

	@Override
	public int getStatus() {
		return status;
	}

	@Override
	public long getFileSize() {
		return 0;
	}

	@Override
	public int getProgress() {
		return 0;
	}

	@Override
	public void cancel() {

	}
}
