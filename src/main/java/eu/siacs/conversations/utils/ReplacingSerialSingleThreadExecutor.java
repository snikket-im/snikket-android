package eu.siacs.conversations.utils;

public class ReplacingSerialSingleThreadExecutor extends SerialSingleThreadExecutor {

	public ReplacingSerialSingleThreadExecutor(String name) {
		super(name);
	}

	@Override
	public synchronized void execute(final Runnable r) {
		tasks.clear();
		if (active instanceof Cancellable) {
			((Cancellable) active).cancel();
		}
		super.execute(r);
	}

	public synchronized void cancelRunningTasks() {
		tasks.clear();
		if (active instanceof Cancellable) {
			((Cancellable) active).cancel();
		}
	}
}
