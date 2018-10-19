package eu.siacs.conversations.utils;

import android.content.Context;
import android.telephony.TelephonyManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.michaelrocks.libphonenumber.android.PhoneNumberUtil;

public class PhoneNumberUtilWrapper {

    private static volatile PhoneNumberUtil instance;


    public static String getCountryForCode(String code) {
        Locale locale = new Locale("", code);
        return locale.getDisplayCountry();
    }

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

    public static PhoneNumberUtil getInstance(final Context context) {
        PhoneNumberUtil localInstance = instance;
        if (localInstance == null) {
            synchronized (PhoneNumberUtilWrapper.class){
                localInstance = instance;
                if (localInstance == null) {
                    instance = localInstance = PhoneNumberUtil.createInstance(context);
                }

            }
        }
        return localInstance;
    }

    public static List<Country> getCountries(final Context context) {
        List<Country> countries = new ArrayList<>();
        for(String region : getInstance(context).getSupportedRegions()) {
            countries.add(new Country(region, getInstance(context).getCountryCodeForRegion(region)));
        }
        return countries;

    }

    public static class Country implements Comparable<Country> {
        private final String name;
        private final String region;
        private final int code;

        Country(String region, int code ) {
            this.name = getCountryForCode(region);
            this.region = region;
            this.code = code;
        }

        public String getName() {
            return name;
        }

        public String getRegion() {
            return region;
        }

        public String getCode() {
            return '+'+String.valueOf(code);
        }

        @Override
        public int compareTo(Country o) {
            return name.compareTo(o.name);
        }
    }

}
