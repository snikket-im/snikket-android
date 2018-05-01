package eu.siacs.conversations.utils;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Conversational;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.ui.ShareLocationActivity;

public class GeoHelper {

	private static final String SHARE_LOCATION_PACKAGE_NAME = "eu.siacs.conversations.location.request";

	public static Pattern GEO_URI = Pattern.compile("geo:([\\-0-9.]+),([\\-0-9.]+)(?:,([\\-0-9.]+))?(?:\\?(.*))?", Pattern.CASE_INSENSITIVE);

	public static boolean isLocationPluginInstalled(Activity activity) {
		return new Intent(SHARE_LOCATION_PACKAGE_NAME).resolveActivity(activity.getPackageManager()) != null;
	}

	public static boolean isLocationPluginInstalledAndDesired(Activity activity) {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
		final boolean configured = preferences.getBoolean("use_share_location_plugin", activity.getResources().getBoolean(R.bool.use_share_location_plugin));
		return configured && isLocationPluginInstalled(activity);
	}

	public static Intent getFetchIntent(Activity activity) {
		if (isLocationPluginInstalledAndDesired(activity)) {
			return new Intent(SHARE_LOCATION_PACKAGE_NAME);
		} else {
			return new Intent(activity, ShareLocationActivity.class);
		}
	}

	public static ArrayList<Intent> createGeoIntentsFromMessage(Message message) {
		final ArrayList<Intent> intents = new ArrayList<>();
		Matcher matcher = GEO_URI.matcher(message.getBody());
		if (!matcher.matches()) {
			return intents;
		}
		double latitude;
		double longitude;
		try {
			latitude = Double.parseDouble(matcher.group(1));
			if (latitude > 90.0 || latitude < -90.0) {
				return intents;
			}
			longitude = Double.parseDouble(matcher.group(2));
			if (longitude > 180.0 || longitude < -180.0) {
				return intents;
			}
		} catch (NumberFormatException nfe) {
			return intents;
		}
		final Conversational conversation = message.getConversation();
		String label;
		if (conversation instanceof Conversation && conversation.getMode() == Conversation.MODE_SINGLE && message.getStatus() == Message.STATUS_RECEIVED) {
			try {
				label = "(" + URLEncoder.encode(((Conversation)conversation).getName().toString(), "UTF-8") + ")";
			} catch (UnsupportedEncodingException e) {
				label = "";
			}
		} else {
			label = "";
		}

		Intent locationPluginIntent = new Intent("eu.siacs.conversations.location.show");
		locationPluginIntent.putExtra("latitude",latitude);
		locationPluginIntent.putExtra("longitude",longitude);
		if (message.getStatus() != Message.STATUS_RECEIVED) {
			locationPluginIntent.putExtra("jid",conversation.getAccount().getJid().toString());
			locationPluginIntent.putExtra("name",conversation.getAccount().getJid().getLocal());
		} else {
			Contact contact = message.getContact();
			if (contact != null) {
				locationPluginIntent.putExtra("name", contact.getDisplayName());
				locationPluginIntent.putExtra("jid", contact.getJid().toString());
			} else {
				locationPluginIntent.putExtra("name", UIHelper.getDisplayedMucCounterpart(message.getCounterpart()));
			}
		}
		intents.add(locationPluginIntent);

		Intent geoIntent = new Intent(Intent.ACTION_VIEW);
		geoIntent.setData(Uri.parse("geo:" + String.valueOf(latitude) + "," + String.valueOf(longitude) + "?q=" + String.valueOf(latitude) + "," + String.valueOf(longitude) + label));
		intents.add(geoIntent);

		Intent httpIntent = new Intent(Intent.ACTION_VIEW);
		httpIntent.setData(Uri.parse("https://maps.google.com/maps?q=loc:"+String.valueOf(latitude) + "," + String.valueOf(longitude) +label));
		intents.add(httpIntent);
		return intents;
	}
}
