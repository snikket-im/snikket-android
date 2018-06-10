package eu.siacs.conversations.utils;

import android.app.Activity;
import android.content.Context;
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
import eu.siacs.conversations.ui.ShowLocationActivity;

public class GeoHelper {

	private static final String SHARE_LOCATION_PACKAGE_NAME = "eu.siacs.conversations.location.request";
	private static final String SHOW_LOCATION_PACKAGE_NAME = "eu.siacs.conversations.location.show";

	public static Pattern GEO_URI = Pattern.compile("geo:(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?)(?:,-?\\d+(?:\\.\\d+)?)?(?:;crs=[\\w-]+)?(?:;u=\\d+(?:\\.\\d+)?)?(?:;[\\w-]+=(?:[\\w-_.!~*'()]|%[\\da-f][\\da-f])+)*", Pattern.CASE_INSENSITIVE);

	public static boolean isLocationPluginInstalled(Context context) {
		return new Intent(SHARE_LOCATION_PACKAGE_NAME).resolveActivity(context.getPackageManager()) != null;
	}

	public static boolean isLocationPluginInstalledAndDesired(Context context) {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
		final boolean configured = preferences.getBoolean("use_share_location_plugin", context.getResources().getBoolean(R.bool.use_share_location_plugin));
		return configured && isLocationPluginInstalled(context);
	}

	public static Intent getFetchIntent(Context context) {
		if (isLocationPluginInstalledAndDesired(context)) {
			return new Intent(SHARE_LOCATION_PACKAGE_NAME);
		} else {
			return new Intent(context, ShareLocationActivity.class);
		}
	}

	public static ArrayList<Intent> createGeoIntentsFromMessage(Context context, Message message) {
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

		if (isLocationPluginInstalledAndDesired(context)) {
			Intent locationPluginIntent = new Intent(SHOW_LOCATION_PACKAGE_NAME);
			locationPluginIntent.putExtra("latitude", latitude);
			locationPluginIntent.putExtra("longitude", longitude);
			if (message.getStatus() != Message.STATUS_RECEIVED) {
				locationPluginIntent.putExtra("jid", conversation.getAccount().getJid().toString());
				locationPluginIntent.putExtra("name", conversation.getAccount().getJid().getLocal());
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
		} else {
			Intent intent = new Intent(context, ShowLocationActivity.class);
			intent.setAction(SHOW_LOCATION_PACKAGE_NAME);
			intent.putExtra("latitude", latitude);
			intent.putExtra("longitude", longitude);
			intents.add(intent);
		}

		Intent geoIntent = new Intent(Intent.ACTION_VIEW);
		geoIntent.setData(Uri.parse("geo:" + String.valueOf(latitude) + "," + String.valueOf(longitude) + "?q=" + String.valueOf(latitude) + "," + String.valueOf(longitude) + label));
		intents.add(geoIntent);

		Intent httpIntent = new Intent(Intent.ACTION_VIEW);
		httpIntent.setData(Uri.parse("https://maps.google.com/maps?q=loc:"+String.valueOf(latitude) + "," + String.valueOf(longitude) +label));
		intents.add(httpIntent);
		return intents;
	}
}
