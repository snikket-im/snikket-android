package eu.siacs.conversations.services;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Pair;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicLong;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.utils.Compatibility;

public class AbstractConnectionManager {

    private static final String KEYTYPE = "AES";
    private static final String CIPHERMODE = "AES/GCM/NoPadding";
    private static final String PROVIDER = "BC";
    private static final int UI_REFRESH_THRESHOLD = 250;
    private static final AtomicLong LAST_UI_UPDATE_CALL = new AtomicLong(0);
    protected XmppConnectionService mXmppConnectionService;

    public AbstractConnectionManager(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    public static Pair<InputStream, Integer> createInputStream(DownloadableFile file, boolean gcm) throws FileNotFoundException {
        FileInputStream is;
        int size;
        is = new FileInputStream(file);
        size = (int) file.getSize();
        if (file.getKey() == null) {
            return new Pair<>(is, size);
        }
        try {
            if (gcm) {
                Cipher cipher = Compatibility.twentyTwo() ? Cipher.getInstance(CIPHERMODE) : Cipher.getInstance(CIPHERMODE, PROVIDER);
                SecretKeySpec keySpec = new SecretKeySpec(file.getKey(), KEYTYPE);
                IvParameterSpec ivSpec = new IvParameterSpec(file.getIv());
                cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
                return new Pair<>(new CipherInputStream(is, cipher), cipher.getOutputSize(size));
            } else {
                IvParameterSpec ips = new IvParameterSpec(file.getIv());
                Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(file.getKey(), KEYTYPE), ips);
                return new Pair<>(new CipherInputStream(is, cipher), (size / 16 + 1) * 16);
            }
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public static OutputStream createAppendedOutputStream(DownloadableFile file) {
        return createOutputStream(file, false, true);
    }

    public static OutputStream createOutputStream(DownloadableFile file, boolean gcm) {
        return createOutputStream(file, gcm, false);
    }

    private static OutputStream createOutputStream(DownloadableFile file, boolean gcm, boolean append) {
        FileOutputStream os;
        try {
            os = new FileOutputStream(file, append);
            if (file.getKey() == null) {
                return os;
            }
        } catch (FileNotFoundException e) {
            return null;
        }
        try {
            if (gcm) {
                Cipher cipher = Compatibility.twentyTwo() ? Cipher.getInstance(CIPHERMODE) : Cipher.getInstance(CIPHERMODE, PROVIDER);
                SecretKeySpec keySpec = new SecretKeySpec(file.getKey(), KEYTYPE);
                IvParameterSpec ivSpec = new IvParameterSpec(file.getIv());
                cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
                return new CipherOutputStream(os, cipher);
            } else {
                IvParameterSpec ips = new IvParameterSpec(file.getIv());
                Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(file.getKey(), KEYTYPE), ips);
                return new CipherOutputStream(os, cipher);
            }
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public XmppConnectionService getXmppConnectionService() {
        return this.mXmppConnectionService;
    }

    public long getAutoAcceptFileSize() {
        return this.mXmppConnectionService.getLongPreference("auto_accept_file_size", R.integer.auto_accept_filesize);
    }

    public boolean hasStoragePermission() {
        if (!Config.ONLY_INTERNAL_STORAGE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return mXmppConnectionService.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    public void updateConversationUi(boolean force) {
        synchronized (LAST_UI_UPDATE_CALL) {
            if (force || SystemClock.elapsedRealtime() - LAST_UI_UPDATE_CALL.get() >= UI_REFRESH_THRESHOLD) {
                LAST_UI_UPDATE_CALL.set(SystemClock.elapsedRealtime());
                mXmppConnectionService.updateConversationUi();
            }
        }
    }

    public PowerManager.WakeLock createWakeLock(String name) {
        PowerManager powerManager = (PowerManager) mXmppConnectionService.getSystemService(Context.POWER_SERVICE);
        return powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, name);
    }
}
