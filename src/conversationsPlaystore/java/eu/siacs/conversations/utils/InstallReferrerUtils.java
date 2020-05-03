package eu.siacs.conversations.utils;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;

import com.android.installreferrer.api.InstallReferrerClient;
import com.android.installreferrer.api.InstallReferrerStateListener;
import com.android.installreferrer.api.ReferrerDetails;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.ui.WelcomeActivity;

public class InstallReferrerUtils implements InstallReferrerStateListener {

    private static final String PROCESSED_INSTALL_REFERRER = "processed_install_referrer";


    private final WelcomeActivity welcomeActivity;
    private final InstallReferrerClient installReferrerClient;


    public InstallReferrerUtils(WelcomeActivity welcomeActivity) {
        this.welcomeActivity = welcomeActivity;
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(welcomeActivity);
        if (preferences.getBoolean(PROCESSED_INSTALL_REFERRER, false)) {
            Log.d(Config.LOGTAG, "install referrer already processed");
            this.installReferrerClient = null;
            return;
        }
        this.installReferrerClient = InstallReferrerClient.newBuilder(welcomeActivity).build();
        try {
            this.installReferrerClient.startConnection(this);
        } catch (SecurityException e) {
            Log.e(Config.LOGTAG, "unable to start connection to InstallReferrerClient", e);
        }
    }

    public static void markInstallReferrerExecuted(final Activity context) {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        preferences.edit().putBoolean(PROCESSED_INSTALL_REFERRER, true).apply();
    }

    @Override
    public void onInstallReferrerSetupFinished(int responseCode) {
        if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
            try {
                final ReferrerDetails referrerDetails = installReferrerClient.getInstallReferrer();
                final String referrer = referrerDetails.getInstallReferrer();
                welcomeActivity.onInstallReferrerDiscovered(referrer);
            } catch (final RemoteException e) {
                Log.d(Config.LOGTAG, "unable to get install referrer", e);
            }
        } else {
            Log.d(Config.LOGTAG, "unable to setup install referrer client. code=" + responseCode);
        }
    }

    @Override
    public void onInstallReferrerServiceDisconnected() {

    }
}