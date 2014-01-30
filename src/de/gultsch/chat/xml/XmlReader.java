package de.gultsch.chat.xml;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.util.Xml;

public class XmlReader {
	private static final String LOGTAG = "xmppService";
	private XmlPullParser parser;
	private PowerManager.WakeLock wakeLock;
	private InputStream is;

	public XmlReader(WakeLock wakeLock) {
		this.parser = Xml.newPullParser();
		try {
			this.parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,true);
		} catch (XmlPullParserException e) {
			Log.d(LOGTAG,"error setting namespace feature on parser");
		}
		this.wakeLock = wakeLock;
	}
	
	public void setInputStream(InputStream inputStream) {
		this.is = inputStream;
		try {
			parser.setInput(new InputStreamReader(this.is));
		} catch (XmlPullParserException e) {
			Log.d(LOGTAG,"error setting input stream");
		}
	}
	
	public void reset() {
		try {
			parser.setInput(new InputStreamReader(this.is));
		} catch (XmlPullParserException e) {
			Log.d(LOGTAG,"error resetting input stream");
		}
	}
	
	public Tag readTag() throws XmlPullParserException, IOException {
		if (wakeLock.isHeld()) {
			//Log.d(LOGTAG,"there was a wake lock. releasing it till next event");
			wakeLock.release(); //release wake look while waiting on next parser event
		}
		while(parser.next() != XmlPullParser.END_DOCUMENT) {
				//Log.d(LOGTAG,"found new event. acquiring wake lock");
				wakeLock.acquire();
				if (parser.getEventType() == XmlPullParser.START_TAG) {
					Tag tag = Tag.start(parser.getName());
					for(int i = 0; i < parser.getAttributeCount(); ++i) {
						tag.setAttribute(parser.getAttributeName(i), parser.getAttributeValue(i));
					}
					return tag;
				} else if (parser.getEventType() == XmlPullParser.END_TAG) {
					Tag tag = Tag.end(parser.getName());
					return tag;
				} else if (parser.getEventType() == XmlPullParser.TEXT) {
					Tag tag = Tag.no(parser.getText());
					return tag;
				}
			}
		if (wakeLock.isHeld()) {
			wakeLock.release();
		}
		return null; //end document;
	}

	public Element readElement(Tag currentTag) throws XmlPullParserException, IOException {
		Element element = new Element(currentTag.getName());
		element.setAttributes(currentTag.getAttributes());
		Tag nextTag = this.readTag();
		if(nextTag.isNo()) {
			element.setContent(nextTag.getName());
			nextTag = this.readTag();
		}
		while(!nextTag.isEnd(element.getName())) {
			Element child = this.readElement(nextTag);
			element.addChild(child);
			nextTag = this.readTag();
		}
		return element;
	}
}
