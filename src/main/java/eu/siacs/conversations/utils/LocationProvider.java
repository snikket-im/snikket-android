package eu.siacs.conversations.utils;

import android.content.Context;
import android.telephony.TelephonyManager;
import android.util.Log;

import org.osmdroid.util.GeoPoint;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Locale;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;

public class LocationProvider {

    public static final GeoPoint FALLBACK = new GeoPoint(0.0,0.0);

    public static String getUserCountry(Context context) {
        try {
            final TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            final String simCountry = tm.getSimCountryIso();
            if (simCountry != null && simCountry.length() == 2) { // SIM country code is available
                return simCountry.toUpperCase(Locale.US);
            } else if (tm.getPhoneType() != TelephonyManager.PHONE_TYPE_CDMA) { // device is not 3G (would be unreliable)
                String networkCountry = tm.getNetworkCountryIso();
                if (networkCountry != null && networkCountry.length() == 2) { // network country code is available
                    return networkCountry.toUpperCase(Locale.US);
                }
            }
        } catch (Exception e) {
            // fallthrough
        }
        Locale locale = Locale.getDefault();
        return locale.getCountry();
    }

    public static GeoPoint getGeoPoint(Context context) {
        return getGeoPoint(context, getUserCountry(context));
    }


    public static synchronized GeoPoint getGeoPoint(Context context, String country) {
        try {
            BufferedReader reader =  new BufferedReader(new InputStreamReader(context.getResources().openRawResource(R.raw.countries)));
            String line;
            while((line = reader.readLine()) != null) {
                String[] parts = line.split("\\s+",4);
                if (parts.length == 4) {
                    if (country.equalsIgnoreCase(parts[0])) {
                        try {
                            return new GeoPoint(Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
                        } catch (NumberFormatException e) {
                            return FALLBACK;
                        }
                    }
                } else {
                    Log.d(Config.LOGTAG,"unable to parse line="+line);
                }
            }
        } catch (IOException e) {
            Log.d(Config.LOGTAG,e.getMessage());
        }
        return FALLBACK;
    }

}
