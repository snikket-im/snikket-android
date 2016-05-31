package eu.siacs.conversations.utils;

import android.os.Looper;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class SerialSingleThreadExecutor implements Executor {

	final Executor executor = Executors.newSingleThreadExecutor();
	protected final Queue<Runnable> tasks = new ArrayDeque();
	Runnable active;

	public SerialSingleThreadExecutor() {
		this(false);
	}

	public SerialSingleThreadExecutor(boolean prepareLooper) {
		if (prepareLooper) {
			execute(new Runnable() {
				@Override
				public void run() {
					Looper.prepare();
				}
			});
		}
	}

	public synchronized void execute(final Runnable r) {
		tasks.offer(new Runnable() {
			public void run() {
				try {
					r.run();
				} finally {
					scheduleNext();
				}
			}
		});
		if (active == null) {
			scheduleNext();
		}
	}

	protected synchronized void scheduleNext() {
		if ((active =  tasks.poll()) != null) {
			executor.execute(active);
		}
	}
}