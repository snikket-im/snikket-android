package eu.siacs.conversations.utils;

import android.os.Looper;
import android.util.Log;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.services.AttachFileToConversationRunnable;

public class SerialSingleThreadExecutor implements Executor {

	private final Executor executor = Executors.newSingleThreadExecutor();
	final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
	protected Runnable active;
	private final String name;

	public SerialSingleThreadExecutor(String name) {
		this(name, false);
	}

	SerialSingleThreadExecutor(String name, boolean prepareLooper) {
		if (prepareLooper) {
			execute(Looper::prepare);
		}
		this.name = name;
	}

	public synchronized void execute(final Runnable r) {
		tasks.offer(new Runner(r));
		if (active == null) {
			scheduleNext();
		}
	}

	private synchronized void scheduleNext() {
		if ((active = tasks.poll()) != null) {
			executor.execute(active);
			int remaining = tasks.size();
			if (remaining > 0) {
				Log.d(Config.LOGTAG,remaining+" remaining tasks on executor '"+name+"'");
			}
		}
	}

	private class Runner implements Runnable, Cancellable {

		private final Runnable runnable;

		private Runner(Runnable runnable) {
			this.runnable = runnable;
		}

		@Override
		public void cancel() {
			if (runnable instanceof Cancellable) {
				((Cancellable) runnable).cancel();
			}
		}

		@Override
		public void run() {
			try {
				runnable.run();
			} finally {
				scheduleNext();
			}
		}
	}
}