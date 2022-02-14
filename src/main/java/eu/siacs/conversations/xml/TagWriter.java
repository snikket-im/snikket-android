package eu.siacs.conversations.xml;

import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.xmpp.stanzas.AbstractStanza;

public class TagWriter {

    private static final int FLUSH_DELAY = 400;

    private OutputStreamWriter outputStream;
    private boolean finished = false;
    private final LinkedBlockingQueue<AbstractStanza> writeQueue = new LinkedBlockingQueue<AbstractStanza>();
    private CountDownLatch stanzaWriterCountDownLatch = null;

    private final Thread asyncStanzaWriter = new Thread() {

        private final AtomicInteger batchStanzaCount = new AtomicInteger(0);

        @Override
        public void run() {
            stanzaWriterCountDownLatch = new CountDownLatch(1);
            while (!isInterrupted()) {
                if (finished && writeQueue.size() == 0) {
                    break;
                }
                try {
                    final AbstractStanza stanza = writeQueue.poll(FLUSH_DELAY, TimeUnit.MILLISECONDS);
                    if (stanza != null) {
                        batchStanzaCount.incrementAndGet();
                        outputStream.write(stanza.toString());
                    } else {
                        final int batch = batchStanzaCount.getAndSet(0);
                        if (batch > 1) {
                            Log.d(Config.LOGTAG, "flushing " + batch + " stanzas");
                        }
                        outputStream.flush();
                        final AbstractStanza nextStanza = writeQueue.take();
                        batchStanzaCount.incrementAndGet();
                        outputStream.write(nextStanza.toString());
                    }
                } catch (final Exception e) {
                    break;
                }
            }
            stanzaWriterCountDownLatch.countDown();
        }

    };

    public TagWriter() {
    }

    public synchronized void setOutputStream(OutputStream out) throws IOException {
        if (out == null) {
            throw new IOException();
        }
        this.outputStream = new OutputStreamWriter(out);
    }

    public void beginDocument() throws IOException {
        if (outputStream == null) {
            throw new IOException("output stream was null");
        }
        outputStream.write("<?xml version='1.0'?>");
        outputStream.flush();
    }

    public synchronized void writeTag(Tag tag) throws IOException {
        if (outputStream == null) {
            throw new IOException("output stream was null");
        }
        outputStream.write(tag.toString());
        outputStream.flush();
    }

    public synchronized void writeElement(Element element) throws IOException {
        if (outputStream == null) {
            throw new IOException("output stream was null");
        }
        outputStream.write(element.toString());
        outputStream.flush();
    }

    public void writeStanzaAsync(AbstractStanza stanza) {
        if (finished) {
            Log.d(Config.LOGTAG, "attempting to write stanza to finished TagWriter");
        } else {
            if (!asyncStanzaWriter.isAlive()) {
                try {
                    asyncStanzaWriter.start();
                } catch (IllegalThreadStateException e) {
                    // already started
                }
            }
            writeQueue.add(stanza);
        }
    }

    public void finish() {
        this.finished = true;
    }

    public boolean await(long timeout, TimeUnit timeunit) throws InterruptedException {
        if (stanzaWriterCountDownLatch == null) {
            return true;
        } else {
            return stanzaWriterCountDownLatch.await(timeout, timeunit);
        }
    }

    public boolean isActive() {
        return outputStream != null;
    }

    public synchronized void forceClose() {
        asyncStanzaWriter.interrupt();
        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (IOException e) {
                //ignoring
            }
        }
        outputStream = null;
    }
}
