package eu.siacs.conversations.utils;

public class ReplacingSerialSingleThreadExecutor extends SerialSingleThreadExecutor {

    public ReplacingSerialSingleThreadExecutor(boolean prepareLooper) {
        super(ReplacingSerialSingleThreadExecutor.class.getName(), prepareLooper);
    }

    @Override
    public synchronized void execute(final Runnable r) {
        tasks.clear();
        super.execute(r);
    }
}
