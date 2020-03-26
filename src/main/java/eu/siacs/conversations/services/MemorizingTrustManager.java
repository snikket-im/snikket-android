/* MemorizingTrustManager - a TrustManager which asks the user about invalid
 *  certificates and memorizes their decision.
 *
 * Copyright (c) 2010 Georg Lukas <georg@op-co.de>
 *
 * MemorizingTrustManager.java contains the actual trust manager and interface
 * code to create a MemorizingActivity and obtain the results.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package eu.siacs.conversations.services;

import android.app.Application;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.util.Log;
import android.util.SparseArray;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.DomainHostnameVerifier;
import eu.siacs.conversations.entities.MTMDecision;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.ui.MemorizingActivity;

/**
 * A X509 trust manager implementation which asks the user about invalid
 * certificates and memorizes their decision.
 * <p>
 * The certificate validity is checked using the system default X509
 * TrustManager, creating a query Dialog if the check fails.
 * <p>
 * <b>WARNING:</b> This only works if a dedicated thread is used for
 * opening sockets!
 */
public class MemorizingTrustManager {


    final static String DECISION_INTENT = "de.duenndns.ssl.DECISION";
    public final static String DECISION_INTENT_ID = DECISION_INTENT + ".decisionId";
    public final static String DECISION_INTENT_CERT = DECISION_INTENT + ".cert";
    public final static String DECISION_TITLE_ID = DECISION_INTENT + ".titleId";
    final static String DECISION_INTENT_CHOICE = DECISION_INTENT + ".decisionChoice";
    final static String NO_TRUST_ANCHOR = "Trust anchor for certification path not found.";
    private static final Pattern PATTERN_IPV4 = Pattern.compile("\\A(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}\\z");
    private static final Pattern PATTERN_IPV6_HEX4DECCOMPRESSED = Pattern.compile("\\A((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?) ::((?:[0-9A-Fa-f]{1,4}:)*)(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}\\z");
    private static final Pattern PATTERN_IPV6_6HEX4DEC = Pattern.compile("\\A((?:[0-9A-Fa-f]{1,4}:){6,6})(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}\\z");
    private static final Pattern PATTERN_IPV6_HEXCOMPRESSED = Pattern.compile("\\A((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)::((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)\\z");
    private static final Pattern PATTERN_IPV6 = Pattern.compile("\\A(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}\\z");
    private final static Logger LOGGER = Logger.getLogger(MemorizingTrustManager.class.getName());
    private final static int NOTIFICATION_ID = 100509;
    static String KEYSTORE_DIR = "KeyStore";
    static String KEYSTORE_FILE = "KeyStore.bks";
    private static int decisionId = 0;
    private static SparseArray<MTMDecision> openDecisions = new SparseArray<MTMDecision>();
    Context master;
    AppCompatActivity foregroundAct;
    NotificationManager notificationManager;
    Handler masterHandler;
    private File keyStoreFile;
    private KeyStore appKeyStore;
    private X509TrustManager defaultTrustManager;
    private X509TrustManager appTrustManager;
    private String poshCacheDir;

    /**
     * Creates an instance of the MemorizingTrustManager class that falls back to a custom TrustManager.
     * <p>
     * You need to supply the application context. This has to be one of:
     * - Application
     * - Activity
     * - Service
     * <p>
     * The context is used for file management, to display the dialog /
     * notification and for obtaining translated strings.
     *
     * @param m                   Context for the application.
     * @param defaultTrustManager Delegate trust management to this TM. If null, the user must accept every certificate.
     */
    public MemorizingTrustManager(Context m, X509TrustManager defaultTrustManager) {
        init(m);
        this.appTrustManager = getTrustManager(appKeyStore);
        this.defaultTrustManager = defaultTrustManager;
    }

    /**
     * Creates an instance of the MemorizingTrustManager class using the system X509TrustManager.
     * <p>
     * You need to supply the application context. This has to be one of:
     * - Application
     * - Activity
     * - Service
     * <p>
     * The context is used for file management, to display the dialog /
     * notification and for obtaining translated strings.
     *
     * @param m Context for the application.
     */
    public MemorizingTrustManager(Context m) {
        init(m);
        this.appTrustManager = getTrustManager(appKeyStore);
        this.defaultTrustManager = getTrustManager(null);
    }

    /**
     * Changes the path for the KeyStore file.
     * <p>
     * The actual filename relative to the app's directory will be
     * <code>app_<i>dirname</i>/<i>filename</i></code>.
     *
     * @param dirname  directory to store the KeyStore.
     * @param filename file name for the KeyStore.
     */
    public static void setKeyStoreFile(String dirname, String filename) {
        KEYSTORE_DIR = dirname;
        KEYSTORE_FILE = filename;
    }

    private static boolean isIp(final String server) {
        return server != null && (
                PATTERN_IPV4.matcher(server).matches()
                        || PATTERN_IPV6.matcher(server).matches()
                        || PATTERN_IPV6_6HEX4DEC.matcher(server).matches()
                        || PATTERN_IPV6_HEX4DECCOMPRESSED.matcher(server).matches()
                        || PATTERN_IPV6_HEXCOMPRESSED.matcher(server).matches());
    }

    private static String getBase64Hash(X509Certificate certificate, String digest) throws CertificateEncodingException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance(digest);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
        md.update(certificate.getEncoded());
        return Base64.encodeToString(md.digest(), Base64.NO_WRAP);
    }

    private static String hexString(byte[] data) {
        StringBuffer si = new StringBuffer();
        for (int i = 0; i < data.length; i++) {
            si.append(String.format("%02x", data[i]));
            if (i < data.length - 1)
                si.append(":");
        }
        return si.toString();
    }

    private static String certHash(final X509Certificate cert, String digest) {
        try {
            MessageDigest md = MessageDigest.getInstance(digest);
            md.update(cert.getEncoded());
            return hexString(md.digest());
        } catch (java.security.cert.CertificateEncodingException e) {
            return e.getMessage();
        } catch (java.security.NoSuchAlgorithmException e) {
            return e.getMessage();
        }
    }

    public static void interactResult(int decisionId, int choice) {
        MTMDecision d;
        synchronized (openDecisions) {
            d = openDecisions.get(decisionId);
            openDecisions.remove(decisionId);
        }
        if (d == null) {
            LOGGER.log(Level.SEVERE, "interactResult: aborting due to stale decision reference!");
            return;
        }
        synchronized (d) {
            d.state = choice;
            d.notify();
        }
    }

    void init(Context m) {
        master = m;
        masterHandler = new Handler(m.getMainLooper());
        notificationManager = (NotificationManager) master.getSystemService(Context.NOTIFICATION_SERVICE);

        Application app;
        if (m instanceof Application) {
            app = (Application) m;
        } else if (m instanceof Service) {
            app = ((Service) m).getApplication();
        } else if (m instanceof AppCompatActivity) {
            app = ((AppCompatActivity) m).getApplication();
        } else
            throw new ClassCastException("MemorizingTrustManager context must be either Activity or Service!");

        File dir = app.getDir(KEYSTORE_DIR, Context.MODE_PRIVATE);
        keyStoreFile = new File(dir + File.separator + KEYSTORE_FILE);

        poshCacheDir = app.getCacheDir().getAbsolutePath() + "/posh_cache/";

        appKeyStore = loadAppKeyStore();
    }

    /**
     * Binds an Activity to the MTM for displaying the query dialog.
     * <p>
     * This is useful if your connection is run from a service that is
     * triggered by user interaction -- in such cases the activity is
     * visible and the user tends to ignore the service notification.
     * <p>
     * You should never have a hidden activity bound to MTM! Use this
     * function in onResume() and @see unbindDisplayActivity in onPause().
     *
     * @param act Activity to be bound
     */
    public void bindDisplayActivity(AppCompatActivity act) {
        foregroundAct = act;
    }

    /**
     * Removes an Activity from the MTM display stack.
     * <p>
     * Always call this function when the Activity added with
     * {@link #bindDisplayActivity(AppCompatActivity)} is hidden.
     *
     * @param act Activity to be unbound
     */
    public void unbindDisplayActivity(AppCompatActivity act) {
        // do not remove if it was overridden by a different activity
        if (foregroundAct == act)
            foregroundAct = null;
    }

    /**
     * Get a list of all certificate aliases stored in MTM.
     *
     * @return an {@link Enumeration} of all certificates
     */
    public Enumeration<String> getCertificates() {
        try {
            return appKeyStore.aliases();
        } catch (KeyStoreException e) {
            // this should never happen, however...
            throw new RuntimeException(e);
        }
    }

    /**
     * Get a certificate for a given alias.
     *
     * @param alias the certificate's alias as returned by {@link #getCertificates()}.
     * @return the certificate associated with the alias or <tt>null</tt> if none found.
     */
    public Certificate getCertificate(String alias) {
        try {
            return appKeyStore.getCertificate(alias);
        } catch (KeyStoreException e) {
            // this should never happen, however...
            throw new RuntimeException(e);
        }
    }

    /**
     * Removes the given certificate from MTMs key store.
     *
     * <p>
     * <b>WARNING</b>: this does not immediately invalidate the certificate. It is
     * well possible that (a) data is transmitted over still existing connections or
     * (b) new connections are created using TLS renegotiation, without a new cert
     * check.
     * </p>
     *
     * @param alias the certificate's alias as returned by {@link #getCertificates()}.
     * @throws KeyStoreException if the certificate could not be deleted.
     */
    public void deleteCertificate(String alias) throws KeyStoreException {
        appKeyStore.deleteEntry(alias);
        keyStoreUpdated();
    }

    /**
     * Creates a new hostname verifier supporting user interaction.
     *
     * <p>This method creates a new {@link HostnameVerifier} that is bound to
     * the given instance of {@link MemorizingTrustManager}, and leverages an
     * existing {@link HostnameVerifier}. The returned verifier performs the
     * following steps, returning as soon as one of them succeeds:
     * /p>
     * <ol>
     * <li>Success, if the wrapped defaultVerifier accepts the certificate.</li>
     * <li>Success, if the server certificate is stored in the keystore under the given hostname.</li>
     * <li>Ask the user and return accordingly.</li>
     * <li>Failure on exception.</li>
     * </ol>
     *
     * @param defaultVerifier the {@link HostnameVerifier} that should perform the actual check
     * @return a new hostname verifier using the MTM's key store
     * @throws IllegalArgumentException if the defaultVerifier parameter is null
     */
    public DomainHostnameVerifier wrapHostnameVerifier(final HostnameVerifier defaultVerifier, final boolean interactive) {
        if (defaultVerifier == null)
            throw new IllegalArgumentException("The default verifier may not be null");

        return new MemorizingHostnameVerifier(defaultVerifier, interactive);
    }

    X509TrustManager getTrustManager(KeyStore ks) {
        try {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
            tmf.init(ks);
            for (TrustManager t : tmf.getTrustManagers()) {
                if (t instanceof X509TrustManager) {
                    return (X509TrustManager) t;
                }
            }
        } catch (Exception e) {
            // Here, we are covering up errors. It might be more useful
            // however to throw them out of the constructor so the
            // embedding app knows something went wrong.
            LOGGER.log(Level.SEVERE, "getTrustManager(" + ks + ")", e);
        }
        return null;
    }

    KeyStore loadAppKeyStore() {
        KeyStore ks;
        try {
            ks = KeyStore.getInstance(KeyStore.getDefaultType());
        } catch (KeyStoreException e) {
            LOGGER.log(Level.SEVERE, "getAppKeyStore()", e);
            return null;
        }
        FileInputStream fileInputStream = null;
        try {
            ks.load(null, null);
            fileInputStream = new FileInputStream(keyStoreFile);
            ks.load(fileInputStream, "MTM".toCharArray());
        } catch (java.io.FileNotFoundException e) {
            LOGGER.log(Level.INFO, "getAppKeyStore(" + keyStoreFile + ") - file does not exist");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "getAppKeyStore(" + keyStoreFile + ")", e);
        } finally {
            FileBackend.close(fileInputStream);
        }
        return ks;
    }

    void storeCert(String alias, Certificate cert) {
        try {
            appKeyStore.setCertificateEntry(alias, cert);
        } catch (KeyStoreException e) {
            LOGGER.log(Level.SEVERE, "storeCert(" + cert + ")", e);
            return;
        }
        keyStoreUpdated();
    }

    void storeCert(X509Certificate cert) {
        storeCert(cert.getSubjectDN().toString(), cert);
    }

    void keyStoreUpdated() {
        // reload appTrustManager
        appTrustManager = getTrustManager(appKeyStore);

        // store KeyStore to file
        java.io.FileOutputStream fos = null;
        try {
            fos = new java.io.FileOutputStream(keyStoreFile);
            appKeyStore.store(fos, "MTM".toCharArray());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "storeCert(" + keyStoreFile + ")", e);
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "storeCert(" + keyStoreFile + ")", e);
                }
            }
        }
    }

    // if the certificate is stored in the app key store, it is considered "known"
    private boolean isCertKnown(X509Certificate cert) {
        try {
            return appKeyStore.getCertificateAlias(cert) != null;
        } catch (KeyStoreException e) {
            return false;
        }
    }

    private boolean isExpiredException(Throwable e) {
        do {
            if (e instanceof CertificateExpiredException)
                return true;
            e = e.getCause();
        } while (e != null);
        return false;
    }

    public void checkCertTrusted(X509Certificate[] chain, String authType, String domain, boolean isServer, boolean interactive)
            throws CertificateException {
        LOGGER.log(Level.FINE, "checkCertTrusted(" + chain + ", " + authType + ", " + isServer + ")");
        try {
            LOGGER.log(Level.FINE, "checkCertTrusted: trying appTrustManager");
            if (isServer)
                appTrustManager.checkServerTrusted(chain, authType);
            else
                appTrustManager.checkClientTrusted(chain, authType);
        } catch (CertificateException ae) {
            LOGGER.log(Level.FINER, "checkCertTrusted: appTrustManager failed", ae);
            // if the cert is stored in our appTrustManager, we ignore expiredness
            if (isExpiredException(ae)) {
                LOGGER.log(Level.INFO, "checkCertTrusted: accepting expired certificate from keystore");
                return;
            }
            if (isCertKnown(chain[0])) {
                LOGGER.log(Level.INFO, "checkCertTrusted: accepting cert already stored in keystore");
                return;
            }
            try {
                if (defaultTrustManager == null)
                    throw ae;
                LOGGER.log(Level.FINE, "checkCertTrusted: trying defaultTrustManager");
                if (isServer)
                    defaultTrustManager.checkServerTrusted(chain, authType);
                else
                    defaultTrustManager.checkClientTrusted(chain, authType);
            } catch (CertificateException e) {
                boolean trustSystemCAs = !PreferenceManager.getDefaultSharedPreferences(master).getBoolean("dont_trust_system_cas", false);
                if (domain != null && isServer && trustSystemCAs && !isIp(domain)) {
                    final String hash = getBase64Hash(chain[0], "SHA-256");
                    final List<String> fingerprints = getPoshFingerprints(domain);
                    if (hash != null && fingerprints.size() > 0) {
                        if (fingerprints.contains(hash)) {
                            Log.d("mtm", "trusted cert fingerprint of " + domain + " via posh");
                            return;
                        }
                        if (getPoshCacheFile(domain).delete()) {
                            Log.d("mtm", "deleted posh file for " + domain + " after not being able to verify");
                        }
                    }
                }
                if (interactive) {
                    interactCert(chain, authType, e);
                } else {
                    throw e;
                }
            }
        }
    }

    private List<String> getPoshFingerprints(String domain) {
        List<String> cached = getPoshFingerprintsFromCache(domain);
        if (cached == null) {
            return getPoshFingerprintsFromServer(domain);
        } else {
            return cached;
        }
    }

    private List<String> getPoshFingerprintsFromServer(String domain) {
        return getPoshFingerprintsFromServer(domain, "https://" + domain + "/.well-known/posh/xmpp-client.json", -1, true);
    }

    private List<String> getPoshFingerprintsFromServer(String domain, String url, int maxTtl, boolean followUrl) {
        Log.d("mtm", "downloading json for " + domain + " from " + url);
        try {
            List<String> results = new ArrayList<>();
            HttpsURLConnection connection = (HttpsURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuilder builder = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                builder.append(inputLine);
            }
            JSONObject jsonObject = new JSONObject(builder.toString());
            in.close();
            int expires = jsonObject.getInt("expires");
            if (expires <= 0) {
                return new ArrayList<>();
            }
            if (maxTtl >= 0) {
                expires = Math.min(maxTtl, expires);
            }
            String redirect;
            try {
                redirect = jsonObject.getString("url");
            } catch (JSONException e) {
                redirect = null;
            }
            if (followUrl && redirect != null && redirect.toLowerCase().startsWith("https")) {
                return getPoshFingerprintsFromServer(domain, redirect, expires, false);
            }
            JSONArray fingerprints = jsonObject.getJSONArray("fingerprints");
            for (int i = 0; i < fingerprints.length(); i++) {
                JSONObject fingerprint = fingerprints.getJSONObject(i);
                String sha256 = fingerprint.getString("sha-256");
                if (sha256 != null) {
                    results.add(sha256);
                }
            }
            writeFingerprintsToCache(domain, results, 1000L * expires + System.currentTimeMillis());
            return results;
        } catch (Exception e) {
            Log.d("mtm", "error fetching posh " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private File getPoshCacheFile(String domain) {
        return new File(poshCacheDir + domain + ".json");
    }

    private void writeFingerprintsToCache(String domain, List<String> results, long expires) {
        File file = getPoshCacheFile(domain);
        file.getParentFile().mkdirs();
        try {
            file.createNewFile();
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("expires", expires);
            jsonObject.put("fingerprints", new JSONArray(results));
            FileOutputStream outputStream = new FileOutputStream(file);
            outputStream.write(jsonObject.toString().getBytes());
            outputStream.flush();
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<String> getPoshFingerprintsFromCache(String domain) {
        File file = getPoshCacheFile(domain);
        try {
            InputStream is = new FileInputStream(file);
            BufferedReader buf = new BufferedReader(new InputStreamReader(is));

            String line = buf.readLine();
            StringBuilder sb = new StringBuilder();

            while (line != null) {
                sb.append(line).append("\n");
                line = buf.readLine();
            }
            JSONObject jsonObject = new JSONObject(sb.toString());
            is.close();
            long expires = jsonObject.getLong("expires");
            long expiresIn = expires - System.currentTimeMillis();
            if (expiresIn < 0) {
                file.delete();
                return null;
            } else {
                Log.d("mtm", "posh fingerprints expire in " + (expiresIn / 1000) + "s");
            }
            List<String> result = new ArrayList<>();
            JSONArray jsonArray = jsonObject.getJSONArray("fingerprints");
            for (int i = 0; i < jsonArray.length(); ++i) {
                result.add(jsonArray.getString(i));
            }
            return result;
        } catch (FileNotFoundException e) {
            return null;
        } catch (IOException e) {
            return null;
        } catch (JSONException e) {
            file.delete();
            return null;
        }
    }

    private X509Certificate[] getAcceptedIssuers() {
        LOGGER.log(Level.FINE, "getAcceptedIssuers()");
        return defaultTrustManager.getAcceptedIssuers();
    }

    private int createDecisionId(MTMDecision d) {
        int myId;
        synchronized (openDecisions) {
            myId = decisionId;
            openDecisions.put(myId, d);
            decisionId += 1;
        }
        return myId;
    }

    private void certDetails(StringBuffer si, X509Certificate c) {
        SimpleDateFormat validityDateFormater = new SimpleDateFormat("yyyy-MM-dd");
        si.append("\n");
        si.append(c.getSubjectDN().toString());
        si.append("\n");
        si.append(validityDateFormater.format(c.getNotBefore()));
        si.append(" - ");
        si.append(validityDateFormater.format(c.getNotAfter()));
        si.append("\nSHA-256: ");
        si.append(certHash(c, "SHA-256"));
        si.append("\nSHA-1: ");
        si.append(certHash(c, "SHA-1"));
        si.append("\nSigned by: ");
        si.append(c.getIssuerDN().toString());
        si.append("\n");
    }

    private String certChainMessage(final X509Certificate[] chain, CertificateException cause) {
        Throwable e = cause;
        LOGGER.log(Level.FINE, "certChainMessage for " + e);
        StringBuffer si = new StringBuffer();
        if (e.getCause() != null) {
            e = e.getCause();
            // HACK: there is no sane way to check if the error is a "trust anchor
            // not found", so we use string comparison.
            if (NO_TRUST_ANCHOR.equals(e.getMessage())) {
                si.append(master.getString(R.string.mtm_trust_anchor));
            } else
                si.append(e.getLocalizedMessage());
            si.append("\n");
        }
        si.append("\n");
        si.append(master.getString(R.string.mtm_connect_anyway));
        si.append("\n\n");
        si.append(master.getString(R.string.mtm_cert_details));
        for (X509Certificate c : chain) {
            certDetails(si, c);
        }
        return si.toString();
    }

    private String hostNameMessage(X509Certificate cert, String hostname) {
        StringBuffer si = new StringBuffer();

        si.append(master.getString(R.string.mtm_hostname_mismatch, hostname));
        si.append("\n\n");
        try {
            Collection<List<?>> sans = cert.getSubjectAlternativeNames();
            if (sans == null) {
                si.append(cert.getSubjectDN());
                si.append("\n");
            } else for (List<?> altName : sans) {
                Object name = altName.get(1);
                if (name instanceof String) {
                    si.append("[");
                    si.append((Integer) altName.get(0));
                    si.append("] ");
                    si.append(name);
                    si.append("\n");
                }
            }
        } catch (CertificateParsingException e) {
            e.printStackTrace();
            si.append("<Parsing error: ");
            si.append(e.getLocalizedMessage());
            si.append(">\n");
        }
        si.append("\n");
        si.append(master.getString(R.string.mtm_connect_anyway));
        si.append("\n\n");
        si.append(master.getString(R.string.mtm_cert_details));
        certDetails(si, cert);
        return si.toString();
    }

    /**
     * Returns the top-most entry of the activity stack.
     *
     * @return the Context of the currently bound UI or the master context if none is bound
     */
    Context getUI() {
        return (foregroundAct != null) ? foregroundAct : master;
    }

    int interact(final String message, final int titleId) {
        /* prepare the MTMDecision blocker object */
        MTMDecision choice = new MTMDecision();
        final int myId = createDecisionId(choice);

        masterHandler.post(new Runnable() {
            public void run() {
                Intent ni = new Intent(master, MemorizingActivity.class);
                ni.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ni.setData(Uri.parse(MemorizingTrustManager.class.getName() + "/" + myId));
                ni.putExtra(DECISION_INTENT_ID, myId);
                ni.putExtra(DECISION_INTENT_CERT, message);
                ni.putExtra(DECISION_TITLE_ID, titleId);

                // we try to directly start the activity and fall back to
                // making a notification
                try {
                    getUI().startActivity(ni);
                } catch (Exception e) {
                    LOGGER.log(Level.FINE, "startActivity(MemorizingActivity)", e);
                }
            }
        });

        LOGGER.log(Level.FINE, "openDecisions: " + openDecisions + ", waiting on " + myId);
        try {
            synchronized (choice) {
                choice.wait();
            }
        } catch (InterruptedException e) {
            LOGGER.log(Level.FINER, "InterruptedException", e);
        }
        LOGGER.log(Level.FINE, "finished wait on " + myId + ": " + choice.state);
        return choice.state;
    }

    void interactCert(final X509Certificate[] chain, String authType, CertificateException cause)
            throws CertificateException {
        switch (interact(certChainMessage(chain, cause), R.string.mtm_accept_cert)) {
            case MTMDecision.DECISION_ALWAYS:
                storeCert(chain[0]); // only store the server cert, not the whole chain
            case MTMDecision.DECISION_ONCE:
                break;
            default:
                throw (cause);
        }
    }

    boolean interactHostname(X509Certificate cert, String hostname) {
        switch (interact(hostNameMessage(cert, hostname), R.string.mtm_accept_servername)) {
            case MTMDecision.DECISION_ALWAYS:
                storeCert(hostname, cert);
            case MTMDecision.DECISION_ONCE:
                return true;
            default:
                return false;
        }
    }

    public X509TrustManager getNonInteractive(String domain) {
        return new NonInteractiveMemorizingTrustManager(domain);
    }

    public X509TrustManager getInteractive(String domain) {
        return new InteractiveMemorizingTrustManager(domain);
    }

    public X509TrustManager getNonInteractive() {
        return new NonInteractiveMemorizingTrustManager(null);
    }

    public X509TrustManager getInteractive() {
        return new InteractiveMemorizingTrustManager(null);
    }

    class MemorizingHostnameVerifier implements DomainHostnameVerifier {
        private final HostnameVerifier defaultVerifier;
        private final boolean interactive;

        public MemorizingHostnameVerifier(HostnameVerifier wrapped, boolean interactive) {
            this.defaultVerifier = wrapped;
            this.interactive = interactive;
        }

        @Override
        public boolean verify(String domain, String hostname, SSLSession session) {
            LOGGER.log(Level.FINE, "hostname verifier for " + domain + ", trying default verifier first");
            // if the default verifier accepts the hostname, we are done
            if (defaultVerifier instanceof DomainHostnameVerifier) {
                if (((DomainHostnameVerifier) defaultVerifier).verify(domain, hostname, session)) {
                    return true;
                }
            } else {
                if (defaultVerifier.verify(domain, session)) {
                    return true;
                }
            }


            // otherwise, we check if the hostname is an alias for this cert in our keystore
            try {
                X509Certificate cert = (X509Certificate) session.getPeerCertificates()[0];
                //Log.d(TAG, "cert: " + cert);
                if (cert.equals(appKeyStore.getCertificate(domain.toLowerCase(Locale.US)))) {
                    LOGGER.log(Level.FINE, "certificate for " + domain + " is in our keystore. accepting.");
                    return true;
                } else {
                    LOGGER.log(Level.FINE, "server " + domain + " provided wrong certificate, asking user.");
                    if (interactive) {
                        return interactHostname(cert, domain);
                    } else {
                        return false;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }

        @Override
        public boolean verify(String domain, SSLSession sslSession) {
            return verify(domain, null, sslSession);
        }
    }

    private class NonInteractiveMemorizingTrustManager implements X509TrustManager {

        private final String domain;

        public NonInteractiveMemorizingTrustManager(String domain) {
            this.domain = domain;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            MemorizingTrustManager.this.checkCertTrusted(chain, authType, domain, false, false);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            MemorizingTrustManager.this.checkCertTrusted(chain, authType, domain, true, false);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return MemorizingTrustManager.this.getAcceptedIssuers();
        }

    }

    private class InteractiveMemorizingTrustManager implements X509TrustManager {
        private final String domain;

        public InteractiveMemorizingTrustManager(String domain) {
            this.domain = domain;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            MemorizingTrustManager.this.checkCertTrusted(chain, authType, domain, false, true);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            MemorizingTrustManager.this.checkCertTrusted(chain, authType, domain, true, true);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return MemorizingTrustManager.this.getAcceptedIssuers();
        }
    }
}
