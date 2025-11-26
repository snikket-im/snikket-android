package eu.siacs.conversations.xml;

import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import eu.siacs.conversations.Config;
import im.conversations.android.xmpp.model.StreamElement;

public class TagWriter {

    private OutputStreamWriter outputStream;
    private boolean finished = false;

    private final LinkedBlockingQueue<StreamElement> writeQueue = new LinkedBlockingQueue<>();
    private CountDownLatch stanzaWriterCountDownLatch = null;

    private final Thread asyncStanzaWriter = new Thread() {

        @Override
        public void run() {
            stanzaWriterCountDownLatch = new CountDownLatch(1);
            while (!isInterrupted()) {
                if (finished && writeQueue.isEmpty()) {
                    break;
                }
                try {
                    final var output = writeQueue.take();
                    outputStream.write(output.toString());
                    if (writeQueue.isEmpty()) {
                        outputStream.flush();
                    }
                } catch (Exception e) {
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
    }

    public void writeTag(final Tag tag) throws IOException {
        writeTag(tag, true);
    }

    public synchronized void writeTag(final Tag tag, final boolean flush) throws IOException {
        if (outputStream == null) {
            throw new IOException("output stream was null");
        }
        outputStream.write(tag.toString());
        if (flush) {
            outputStream.flush();
        }
    }

    public synchronized void writeElement(final StreamElement element) throws IOException {
        if (outputStream == null) {
            throw new IOException("output stream was null");
        }
        outputStream.write(element.toString());
        outputStream.flush();
    }

    public void writeStanzaAsync(StreamElement stanza) {
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
