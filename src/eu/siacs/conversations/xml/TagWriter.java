package eu.siacs.conversations.xml;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.concurrent.LinkedBlockingQueue;

import android.util.Log;

public class TagWriter {
	
	private OutputStreamWriter outputStream;
	private LinkedBlockingQueue<String> writeQueue = new LinkedBlockingQueue<String>();
	private Thread writer = new Thread() {
		public boolean shouldStop = false;
		@Override
		public void run() {
			while(!shouldStop) {
				try {
					String output = writeQueue.take();
					outputStream.write(output);
					outputStream.flush();
				} catch (IOException e) {
					Log.d("xmppService", "error writing to stream");
				} catch (InterruptedException e) {
					
				}
			}
		}
	};
	
	
	public TagWriter() {
		
	}
	
	public TagWriter(OutputStream out) {
		this.setOutputStream(out);
		writer.start();
	}
	
	public void setOutputStream(OutputStream out) {
		this.outputStream = new OutputStreamWriter(out);
		if (!writer.isAlive()) writer.start();
	}
	
	public TagWriter beginDocument() {
		writeQueue.add("<?xml version='1.0'?>");
		return this;
	}
	
	public TagWriter writeTag(Tag tag) {
		writeQueue.add(tag.toString());
		return this;
	}

	public void writeString(String string) {
		writeQueue.add(string);
	}

	public void writeElement(Element element) {
		writeQueue.add(element.toString());
	}
}
