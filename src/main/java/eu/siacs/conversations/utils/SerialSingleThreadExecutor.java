package eu.siacs.conversations.utils;

import android.util.Log;

import java.util.ArrayDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import eu.siacs.conversations.Config;

public class SerialSingleThreadExecutor implements Executor {

    final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final String name;
    protected Runnable active;


    public SerialSingleThreadExecutor(String name) {
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
                Log.d(Config.LOGTAG, remaining + " remaining tasks on executor '" + name + "'");
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