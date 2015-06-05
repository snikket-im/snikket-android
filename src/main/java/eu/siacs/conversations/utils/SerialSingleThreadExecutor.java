package eu.siacs.conversations.utils;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class SerialSingleThreadExecutor implements Executor {

	final Executor executor = Executors.newSingleThreadExecutor();
	final Queue<Runnable> tasks = new ArrayDeque();
	Runnable active;

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