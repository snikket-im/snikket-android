package eu.siacs.conversations.services;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.aztec.AztecWriter;
import com.google.zxing.common.BitMatrix;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Hashtable;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;

public class BarcodeProvider extends ContentProvider implements ServiceConnection {

    private static final String AUTHORITY = "eu.siacs.conversations.barcodes";

    private final Object lock = new Object();

    private XmppConnectionService mXmppConnectionService;

    @Override
    public boolean onCreate() {
        File barcodeDirectory = new File(getContext().getCacheDir().getAbsolutePath() + "/barcodes/");
        if (barcodeDirectory.exists() && barcodeDirectory.isDirectory()) {
            for (File file : barcodeDirectory.listFiles()) {
                if (file.isFile() && !file.isHidden()) {
                    Log.d(Config.LOGTAG, "deleting old barcode file " + file.getAbsolutePath());
                    file.delete();
                }
            }
        }
        return true;
    }

    @Nullable
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public String getType(Uri uri) {
        return "image/png";
    }

    @Nullable
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return openFile(uri, mode, null);
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode, CancellationSignal signal) throws FileNotFoundException {
        Log.d(Config.LOGTAG, "opening file with uri (normal): " + uri.toString());
        String path = uri.getPath();
        if (path != null && path.endsWith(".png") && path.length() >= 5) {
            String jid = path.substring(1).substring(0, path.length() - 4);
            Log.d(Config.LOGTAG, "account:" + jid);
            if (connectAndWait()) {
                Log.d(Config.LOGTAG, "connected to background service");
                try {
                    Account account = mXmppConnectionService.findAccountByJid(Jid.fromString(jid));
                    if (account != null) {
                        String shareableUri = account.getShareableUri();
                        String hash = CryptoHelper.getFingerprint(shareableUri);
                        File file = new File(getContext().getCacheDir().getAbsolutePath() + "/barcodes/" + hash);
                        if (!file.exists()) {
                            file.getParentFile().mkdirs();
                            file.createNewFile();
                            Bitmap bitmap = createAztecBitmap(account.getShareableUri(), 1024);
                            OutputStream outputStream = new FileOutputStream(file);
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                            outputStream.close();
                            outputStream.flush();
                        }
                        return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);
                    }
                } catch (Exception e) {
                    throw new FileNotFoundException();
                }
            }
        }
        throw new FileNotFoundException();
    }

    private boolean connectAndWait() {
        Intent intent = new Intent(getContext(), XmppConnectionService.class);
        intent.setAction("contact_chooser");
        Context context = getContext();
        if (context != null) {
            context.startService(intent);
            context.bindService(intent, this, Context.BIND_AUTO_CREATE);
            try {
                waitForService();
                Log.d(Config.LOGTAG, "service initialized");
                return true;
            } catch (InterruptedException e) {
                return false;
            }
        } else {
            Log.d(Config.LOGTAG, "context was null");
            return false;
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        XmppConnectionService.XmppConnectionBinder binder = (XmppConnectionService.XmppConnectionBinder) service;
        mXmppConnectionService = binder.getService();
        synchronized (this.lock) {
            lock.notifyAll();
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        mXmppConnectionService = null;
    }

    private void waitForService() throws InterruptedException {
        if (mXmppConnectionService == null) {
            synchronized (this.lock) {
                lock.wait();
            }
        }
    }

    public static Uri getUriForAccount(Account account) {
        return Uri.parse("content://" + AUTHORITY + "/" + account.getJid().toBareJid() + ".png");
    }

    public static Bitmap createAztecBitmap(String input, int size) {
        try {
            final AztecWriter AZTEC_WRITER = new AztecWriter();
            final Hashtable<EncodeHintType, Object> hints = new Hashtable<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, 10);
            final BitMatrix result = AZTEC_WRITER.encode(input, BarcodeFormat.AZTEC, size, size, hints);
            final int width = result.getWidth();
            final int height = result.getHeight();
            final int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                final int offset = y * width;
                for (int x = 0; x < width; x++) {
                    pixels[offset + x] = result.get(x, y) ? Color.BLACK : Color.TRANSPARENT;
                }
            }
            final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            return bitmap;
        } catch (final Exception e) {
            return null;
        }
    }

    static class TransferThread extends Thread {
        InputStream in;
        OutputStream out;

        TransferThread(InputStream in, OutputStream out) {
            this.in = in;
            this.out = out;
        }

        @Override
        public void run() {
            byte[] buf = new byte[1024];
            int len;

            try {
                while ((len = in.read(buf)) >= 0) {
                    out.write(buf, 0, len);
                }

                in.close();
                out.flush();
                out.close();
            } catch (IOException e) {
                Log.e(Config.LOGTAG, "Exception transferring file", e);
            }
        }
    }
}
