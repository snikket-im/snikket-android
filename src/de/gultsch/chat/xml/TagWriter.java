package de.gultsch.chat.xml;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import android.util.Log;

public class TagWriter {
	
	OutputStreamWriter writer;
	
	public TagWriter() {
		
	}
	
	public TagWriter(OutputStream out) {
		this.setOutputStream(out);
	}
	
	public void setOutputStream(OutputStream out) {
		this.writer = new OutputStreamWriter(out);
	}
	
	public TagWriter beginDocument() throws IOException {
		writer.write("<?xml version='1.0'?>");
		return this;
	}
	
	public TagWriter writeTag(Tag tag) throws IOException {
		writer.write(tag.toString());
		return this;
	}
	
	public void flush() throws IOException {
		writer.flush();
	}

	public void writeString(String string) throws IOException {
		writer.write(string);
	}

	public void writeElement(Element element) throws IOException {
		writer.write(element.toString());
	}
}
