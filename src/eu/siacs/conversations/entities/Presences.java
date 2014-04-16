package eu.siacs.conversations.entities;

import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map.Entry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import eu.siacs.conversations.xml.Element;

public class Presences {

	public static final int CHAT = -1;
	public static final int ONLINE = 0;
	public static final int AWAY = 1;
	public static final int XA = 2;
	public static final int DND = 3;
	public static final int OFFLINE = 4;
	
	private Hashtable<String, Integer> presences = new Hashtable<String, Integer>();

	public Hashtable<String, Integer> getPresences() {
		return this.presences;
	}

	public void updatePresence(String resource, int status) {
		this.presences.put(resource, status);
	}

	public void removePresence(String resource) {
		this.presences.remove(resource);
	}
	
	public void clearPresences() {
		this.presences.clear();
	}
	
	public int getMostAvailableStatus() {
		int status = OFFLINE;
		Iterator<Entry<String, Integer>> it = presences.entrySet().iterator();
		while (it.hasNext()) {
			Entry<String, Integer> entry = it.next();
			if (entry.getValue()<status) status = entry.getValue();
		}
		return status;
	}

	public String toJsonString() {
		JSONArray json = new JSONArray();
		Iterator<Entry<String, Integer>> it = presences.entrySet().iterator();

		while (it.hasNext()) {
			Entry<String, Integer> entry = it.next();
			JSONObject jObj = new JSONObject();
			try {
				jObj.put("resource", entry.getKey());
				jObj.put("status", entry.getValue());
			} catch (JSONException e) {
				
			}
			json.put(jObj);
		}
		return json.toString();
	}

	public static Presences fromJsonString(String jsonString) {
		Presences presences = new Presences();
		try {
			JSONArray json = new JSONArray(jsonString);
			for (int i = 0; i < json.length(); ++i) {
				JSONObject jObj = json.getJSONObject(i);
				presences.updatePresence(jObj.getString("resource"),
						jObj.getInt("status"));
			}
		} catch (JSONException e1) {

		}
		return presences;
	}

	public static int parseShow(Element show) {
		if (show == null) {
			return Presences.ONLINE;
		} else if (show.getContent().equals("away")) {
			return Presences.AWAY;
		} else if (show.getContent().equals("xa")) {
			return Presences.XA;
		} else if (show.getContent().equals("chat")) {
			return Presences.CHAT;
		} else if (show.getContent().equals("dnd")) {
			return 	Presences.DND;
		} else {
			return Presences.OFFLINE;
		}
	}
	
	public int size() {
		return presences.size();
	}
}
