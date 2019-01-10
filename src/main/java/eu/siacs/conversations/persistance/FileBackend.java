package eu.siacs.conversations.persistance;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.MediaMetadataRetriever;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.support.annotation.RequiresApi;
import android.support.v4.content.FileProvider;
import android.system.Os;
import android.system.StructStat;
import android.util.Base64;
import android.util.Base64OutputStream;
import android.util.Log;
import android.util.LruCache;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URL;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.RecordingActivity;
import eu.siacs.conversations.ui.util.Attachment;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.ExifHelper;
import eu.siacs.conversations.utils.FileUtils;
import eu.siacs.conversations.utils.FileWriterException;
import eu.siacs.conversations.utils.MimeUtils;
import eu.siacs.conversations.xmpp.pep.Avatar;

public class FileBackend {

    private static final Object THUMBNAIL_LOCK = new Object();

    private static final SimpleDateFormat IMAGE_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);

    private static final String FILE_PROVIDER = ".files";

    private XmppConnectionService mXmppConnectionService;

    private static final float IGNORE_PADDING = 0.15f;

    public FileBackend(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    private static boolean isInDirectoryThatShouldNotBeScanned(Context context, File file) {
        return isInDirectoryThatShouldNotBeScanned(context, file.getAbsolutePath());
    }

    public static boolean isInDirectoryThatShouldNotBeScanned(Context context, String path) {
        for (String type : new String[]{RecordingActivity.STORAGE_DIRECTORY_TYPE_NAME, "Files"}) {
            if (path.startsWith(getConversationsDirectory(context, type))) {
                return true;
            }
        }
        return false;
    }

    public static long getFileSize(Context context, Uri uri) {
        try {
            final Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                long size = cursor.getLong(cursor.getColumnIndex(OpenableColumns.SIZE));
                cursor.close();
                return size;
            } else {
                return -1;
            }
        } catch (Exception e) {
            return -1;
        }
    }

    public static boolean allFilesUnderSize(Context context, List<Attachment> attachments, long max) {
        if (max <= 0) {
            Log.d(Config.LOGTAG, "server did not report max file size for http upload");
            return true; //exception to be compatible with HTTP Upload < v0.2
        }
        for (Attachment attachment : attachments) {
            if (attachment.getType() != Attachment.Type.FILE) {
                continue;
            }
            String mime = attachment.getMime();
            if (mime != null && mime.startsWith("video/")) {
                try {
                    Dimensions dimensions = FileBackend.getVideoDimensions(context, attachment.getUri());
                    if (dimensions.getMin() > 720) {
                        Log.d(Config.LOGTAG, "do not consider video file with min width larger than 720 for size check");
                        continue;
                    }
                } catch (NotAVideoFile notAVideoFile) {
                    //ignore and fall through
                }
            }
            if (FileBackend.getFileSize(context, attachment.getUri()) > max) {
                Log.d(Config.LOGTAG, "not all files are under " + max + " bytes. suggesting falling back to jingle");
                return false;
            }
        }
        return true;
    }

    public static String getConversationsDirectory(Context context, final String type) {
        if (Config.ONLY_INTERNAL_STORAGE) {
            return context.getFilesDir().getAbsolutePath() + "/" + type + "/";
        } else {
            return getAppMediaDirectory(context) + context.getString(R.string.app_name) + " " + type + "/";
        }
    }

    public static String getAppMediaDirectory(Context context) {
        return Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + context.getString(R.string.app_name) + "/Media/";
    }

    public static String getConversationsLogsDirectory() {
        return Environment.getExternalStorageDirectory().getAbsolutePath() + "/Conversations/";
    }

    private static Bitmap rotate(Bitmap bitmap, int degree) {
        if (degree == 0) {
            return bitmap;
        }
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        Matrix mtx = new Matrix();
        mtx.postRotate(degree);
        Bitmap result = Bitmap.createBitmap(bitmap, 0, 0, w, h, mtx, true);
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
        return result;
    }

    public static boolean isPathBlacklisted(String path) {
        final String androidDataPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Android/data/";
        return path.startsWith(androidDataPath);
    }

    private static Paint createAntiAliasingPaint() {
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
        paint.setDither(true);
        return paint;
    }

    private static String getTakePhotoPath() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) + "/Camera/";
    }

    public static Uri getUriForFile(Context context, File file) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N || Config.ONLY_INTERNAL_STORAGE) {
            try {
                return FileProvider.getUriForFile(context, getAuthority(context), file);
            } catch (IllegalArgumentException e) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    throw new SecurityException(e);
                } else {
                    return Uri.fromFile(file);
                }
            }
        } else {
            return Uri.fromFile(file);
        }
    }

    public static String getAuthority(Context context) {
        return context.getPackageName() + FILE_PROVIDER;
    }

    private static boolean hasAlpha(final Bitmap bitmap) {
        for (int x = 0; x < bitmap.getWidth(); ++x) {
            for (int y = 0; y < bitmap.getWidth(); ++y) {
                if (Color.alpha(bitmap.getPixel(x, y)) < 255) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int calcSampleSize(File image, int size) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(image.getAbsolutePath(), options);
        return calcSampleSize(options, size);
    }


    private static int calcSampleSize(BitmapFactory.Options options, int size) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;

        if (height > size || width > size) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) > size
                    && (halfWidth / inSampleSize) > size) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    public Bitmap getPreviewForUri(Attachment attachment, int size, boolean cacheOnly) {
        final String key = "attachment_"+attachment.getUuid().toString()+"_"+String.valueOf(size);
        final LruCache<String, Bitmap> cache = mXmppConnectionService.getBitmapCache();
        Bitmap bitmap = cache.get(key);
        if (bitmap != null || cacheOnly) {
            return bitmap;
        }
        if (attachment.getMime() != null && attachment.getMime().startsWith("video/")) {
            bitmap = cropCenterSquareVideo(attachment.getUri(), size);
            drawOverlay(bitmap, paintOverlayBlack(bitmap) ? R.drawable.play_video_black : R.drawable.play_video_white, 0.75f);
        } else {
            bitmap = cropCenterSquare(attachment.getUri(), size);
            if (bitmap != null && "image/gif".equals(attachment.getMime())) {
                Bitmap withGifOverlay = bitmap.copy(Bitmap.Config.ARGB_8888, true);
                drawOverlay(withGifOverlay, paintOverlayBlack(withGifOverlay) ? R.drawable.play_gif_black : R.drawable.play_gif_white, 1.0f);
                bitmap.recycle();
                bitmap = withGifOverlay;
            }
        }
        if (bitmap != null) {
            cache.put(key, bitmap);
        }
        return bitmap;
    }

    private static Dimensions getVideoDimensions(Context context, Uri uri) throws NotAVideoFile {
        MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
        try {
            mediaMetadataRetriever.setDataSource(context, uri);
        } catch (RuntimeException e) {
            throw new NotAVideoFile(e);
        }
        return getVideoDimensions(mediaMetadataRetriever);
    }

    private static Dimensions getVideoDimensionsOfFrame(MediaMetadataRetriever mediaMetadataRetriever) {
        Bitmap bitmap = null;
        try {
            bitmap = mediaMetadataRetriever.getFrameAtTime();
            return new Dimensions(bitmap.getHeight(), bitmap.getWidth());
        } catch (Exception e) {
            return null;
        } finally {
            if (bitmap != null) {
                bitmap.recycle();
                ;
            }
        }
    }

    private static Dimensions getVideoDimensions(MediaMetadataRetriever metadataRetriever) throws NotAVideoFile {
        String hasVideo = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO);
        if (hasVideo == null) {
            throw new NotAVideoFile();
        }
        Dimensions dimensions = getVideoDimensionsOfFrame(metadataRetriever);
        if (dimensions != null) {
            return dimensions;
        }
        final int rotation;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
            rotation = extractRotationFromMediaRetriever(metadataRetriever);
        } else {
            rotation = 0;
        }
        boolean rotated = rotation == 90 || rotation == 270;
        int height;
        try {
            String h = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            height = Integer.parseInt(h);
        } catch (Exception e) {
            height = -1;
        }
        int width;
        try {
            String w = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            width = Integer.parseInt(w);
        } catch (Exception e) {
            width = -1;
        }
        metadataRetriever.release();
        Log.d(Config.LOGTAG, "extracted video dims " + width + "x" + height);
        return rotated ? new Dimensions(width, height) : new Dimensions(height, width);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    private static int extractRotationFromMediaRetriever(MediaMetadataRetriever metadataRetriever) {
        String r = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
        try {
            return Integer.parseInt(r);
        } catch (Exception e) {
            return 0;
        }
    }

    public static void close(Closeable stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException e) {
            }
        }
    }

    public static void close(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
            }
        }
    }

    public static boolean weOwnFile(Context context, Uri uri) {
        if (uri == null || !ContentResolver.SCHEME_FILE.equals(uri.getScheme())) {
            return false;
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return fileIsInFilesDir(context, uri);
        } else {
            return weOwnFileLollipop(uri);
        }
    }

    /**
     * This is more than hacky but probably way better than doing nothing
     * Further 'optimizations' might contain to get the parents of CacheDir and NoBackupDir
     * and check against those as well
     */
    private static boolean fileIsInFilesDir(Context context, Uri uri) {
        try {
            final String haystack = context.getFilesDir().getParentFile().getCanonicalPath();
            final String needle = new File(uri.getPath()).getCanonicalPath();
            return needle.startsWith(haystack);
        } catch (IOException e) {
            return false;
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static boolean weOwnFileLollipop(Uri uri) {
        try {
            File file = new File(uri.getPath());
            FileDescriptor fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).getFileDescriptor();
            StructStat st = Os.fstat(fd);
            return st.st_uid == android.os.Process.myUid();
        } catch (FileNotFoundException e) {
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    private void createNoMedia(File diretory) {
        final File noMedia = new File(diretory, ".nomedia");
        if (!noMedia.exists()) {
            try {
                if (!noMedia.createNewFile()) {
                    Log.d(Config.LOGTAG, "created nomedia file " + noMedia.getAbsolutePath());
                }
            } catch (Exception e) {
                Log.d(Config.LOGTAG, "could not create nomedia file");
            }
        }
    }

    public static Uri getMediaUri(Context context, File file) {
        final String filePath = file.getAbsolutePath();
        final Cursor cursor = context.getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[] { MediaStore.Images.Media._ID },
                MediaStore.Images.Media.DATA + "=? ",
                new String[] { filePath }, null);
        if (cursor != null && cursor.moveToFirst()) {
            final int id = cursor.getInt(cursor.getColumnIndex(MediaStore.MediaColumns._ID));
            cursor.close();
            return Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
        } else {
            return null;
        }
    }

    public void updateMediaScanner(File file) {
        updateMediaScanner(file, null);
    }

    public void updateMediaScanner(File file, final Runnable callback) {
        if (!isInDirectoryThatShouldNotBeScanned(mXmppConnectionService, file)) {
            MediaScannerConnection.scanFile(mXmppConnectionService, new String[]{file.getAbsolutePath()}, null, new MediaScannerConnection.MediaScannerConnectionClient() {
                @Override
                public void onMediaScannerConnected() {

                }

                @Override
                public void onScanCompleted(String path, Uri uri) {
                    if (callback != null && file.getAbsolutePath().equals(path)) {
                        callback.run();
                    } else {
                        Log.d(Config.LOGTAG,"media scanner scanned wrong file");
                        if (callback != null) {
                            callback.run();
                        }
                    }
                }
            });
            return;
            /*Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            intent.setData(Uri.fromFile(file));
            mXmppConnectionService.sendBroadcast(intent);*/
        } else if (file.getAbsolutePath().startsWith(getAppMediaDirectory(mXmppConnectionService))) {
            createNoMedia(file.getParentFile());
        }
        if (callback != null) {
            callback.run();
        }
    }

    public boolean deleteFile(Message message) {
        File file = getFile(message);
        if (file.delete()) {
            updateMediaScanner(file);
            return true;
        } else {
            return false;
        }
    }

    public DownloadableFile getFile(Message message) {
        return getFile(message, true);
    }

    public DownloadableFile getFileForPath(String path) {
        return getFileForPath(path,MimeUtils.guessMimeTypeFromExtension(MimeUtils.extractRelevantExtension(path)));
    }

    public DownloadableFile getFileForPath(String path, String mime) {
        final DownloadableFile file;
        if (path.startsWith("/")) {
            file = new DownloadableFile(path);
        } else {
            if (mime != null && mime.startsWith("image/")) {
                file = new DownloadableFile(getConversationsDirectory("Images") + path);
            } else if (mime != null && mime.startsWith("video/")) {
                file = new DownloadableFile(getConversationsDirectory("Videos") + path);
            } else {
                file = new DownloadableFile(getConversationsDirectory("Files") + path);
            }
        }
        return file;
    }

    public boolean isInternalFile(final File file) {
        final File internalFile = getFileForPath(file.getName());
        return file.getAbsolutePath().equals(internalFile.getAbsolutePath());
    }

    public DownloadableFile getFile(Message message, boolean decrypted) {
        final boolean encrypted = !decrypted
                && (message.getEncryption() == Message.ENCRYPTION_PGP
                || message.getEncryption() == Message.ENCRYPTION_DECRYPTED);
        String path = message.getRelativeFilePath();
        if (path == null) {
            path = message.getUuid();
        }
        final DownloadableFile file = getFileForPath(path, message.getMimeType());
        if (encrypted) {
            return new DownloadableFile(getConversationsDirectory("Files") + file.getName() + ".pgp");
        } else {
            return file;
        }
    }

    public List<Attachment> convertToAttachments(List<DatabaseBackend.FilePath> relativeFilePaths) {
        List<Attachment> attachments = new ArrayList<>();
        for(DatabaseBackend.FilePath relativeFilePath : relativeFilePaths) {
            final String mime = MimeUtils.guessMimeTypeFromExtension(MimeUtils.extractRelevantExtension(relativeFilePath.path));
            final File file = getFileForPath(relativeFilePath.path, mime);
            attachments.add(Attachment.of(relativeFilePath.uuid, file, mime));
        }
        return attachments;
    }

    private String getConversationsDirectory(final String type) {
        return getConversationsDirectory(mXmppConnectionService, type);
    }

    private Bitmap resize(final Bitmap originalBitmap, int size) throws IOException {
        int w = originalBitmap.getWidth();
        int h = originalBitmap.getHeight();
        if (w <= 0 || h <= 0) {
            throw new IOException("Decoded bitmap reported bounds smaller 0");
        } else if (Math.max(w, h) > size) {
            int scalledW;
            int scalledH;
            if (w <= h) {
                scalledW = Math.max((int) (w / ((double) h / size)), 1);
                scalledH = size;
            } else {
                scalledW = size;
                scalledH = Math.max((int) (h / ((double) w / size)), 1);
            }
            final Bitmap result = Bitmap.createScaledBitmap(originalBitmap, scalledW, scalledH, true);
            if (!originalBitmap.isRecycled()) {
                originalBitmap.recycle();
            }
            return result;
        } else {
            return originalBitmap;
        }
    }

    public boolean useImageAsIs(Uri uri) {
        String path = getOriginalPath(uri);
        if (path == null || isPathBlacklisted(path)) {
            return false;
        }
        File file = new File(path);
        long size = file.length();
        if (size == 0 || size >= mXmppConnectionService.getResources().getInteger(R.integer.auto_accept_filesize)) {
            return false;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try {
            BitmapFactory.decodeStream(mXmppConnectionService.getContentResolver().openInputStream(uri), null, options);
            if (options.outMimeType == null || options.outHeight <= 0 || options.outWidth <= 0) {
                return false;
            }
            return (options.outWidth <= Config.IMAGE_SIZE && options.outHeight <= Config.IMAGE_SIZE && options.outMimeType.contains(Config.IMAGE_FORMAT.name().toLowerCase()));
        } catch (FileNotFoundException e) {
            return false;
        }
    }

    public String getOriginalPath(Uri uri) {
        return FileUtils.getPath(mXmppConnectionService, uri);
    }

    private void copyFileToPrivateStorage(File file, Uri uri) throws FileCopyException {
        Log.d(Config.LOGTAG, "copy file (" + uri.toString() + ") to private storage " + file.getAbsolutePath());
        file.getParentFile().mkdirs();
        OutputStream os = null;
        InputStream is = null;
        try {
            file.createNewFile();
            os = new FileOutputStream(file);
            is = mXmppConnectionService.getContentResolver().openInputStream(uri);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                try {
                    os.write(buffer, 0, length);
                } catch (IOException e) {
                    throw new FileWriterException();
                }
            }
            try {
                os.flush();
            } catch (IOException e) {
                throw new FileWriterException();
            }
        } catch (FileNotFoundException e) {
            throw new FileCopyException(R.string.error_file_not_found);
        } catch (FileWriterException e) {
            throw new FileCopyException(R.string.error_unable_to_create_temporary_file);
        } catch (IOException e) {
            e.printStackTrace();
            throw new FileCopyException(R.string.error_io_exception);
        } finally {
            close(os);
            close(is);
        }
    }

    public void copyFileToPrivateStorage(Message message, Uri uri, String type) throws FileCopyException {
        String mime = MimeUtils.guessMimeTypeFromUriAndMime(mXmppConnectionService, uri, type);
        Log.d(Config.LOGTAG, "copy " + uri.toString() + " to private storage (mime=" + mime + ")");
        String extension = MimeUtils.guessExtensionFromMimeType(mime);
        if (extension == null) {
            Log.d(Config.LOGTAG, "extension from mime type was null");
            extension = getExtensionFromUri(uri);
        }
        if ("ogg".equals(extension) && type != null && type.startsWith("audio/")) {
            extension = "oga";
        }
        message.setRelativeFilePath(message.getUuid() + "." + extension);
        copyFileToPrivateStorage(mXmppConnectionService.getFileBackend().getFile(message), uri);
    }

    private String getExtensionFromUri(Uri uri) {
        String[] projection = {MediaStore.MediaColumns.DATA};
        String filename = null;
        Cursor cursor;
        try {
            cursor = mXmppConnectionService.getContentResolver().query(uri, projection, null, null, null);
        } catch (IllegalArgumentException e) {
            cursor = null;
        }
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    filename = cursor.getString(0);
                }
            } catch (Exception e) {
                filename = null;
            } finally {
                cursor.close();
            }
        }
        if (filename == null) {
            final List<String> segments = uri.getPathSegments();
            if (segments.size() > 0) {
                filename = segments.get(segments.size() - 1);
            }
        }
        int pos = filename == null ? -1 : filename.lastIndexOf('.');
        return pos > 0 ? filename.substring(pos + 1) : null;
    }

    private void copyImageToPrivateStorage(File file, Uri image, int sampleSize) throws FileCopyException {
        file.getParentFile().mkdirs();
        InputStream is = null;
        OutputStream os = null;
        try {
            if (!file.exists() && !file.createNewFile()) {
                throw new FileCopyException(R.string.error_unable_to_create_temporary_file);
            }
            is = mXmppConnectionService.getContentResolver().openInputStream(image);
            if (is == null) {
                throw new FileCopyException(R.string.error_not_an_image_file);
            }
            Bitmap originalBitmap;
            BitmapFactory.Options options = new BitmapFactory.Options();
            int inSampleSize = (int) Math.pow(2, sampleSize);
            Log.d(Config.LOGTAG, "reading bitmap with sample size " + inSampleSize);
            options.inSampleSize = inSampleSize;
            originalBitmap = BitmapFactory.decodeStream(is, null, options);
            is.close();
            if (originalBitmap == null) {
                throw new FileCopyException(R.string.error_not_an_image_file);
            }
            Bitmap scaledBitmap = resize(originalBitmap, Config.IMAGE_SIZE);
            int rotation = getRotation(image);
            scaledBitmap = rotate(scaledBitmap, rotation);
            boolean targetSizeReached = false;
            int quality = Config.IMAGE_QUALITY;
            final int imageMaxSize = mXmppConnectionService.getResources().getInteger(R.integer.auto_accept_filesize);
            while (!targetSizeReached) {
                os = new FileOutputStream(file);
                boolean success = scaledBitmap.compress(Config.IMAGE_FORMAT, quality, os);
                if (!success) {
                    throw new FileCopyException(R.string.error_compressing_image);
                }
                os.flush();
                targetSizeReached = file.length() <= imageMaxSize || quality <= 50;
                quality -= 5;
            }
            scaledBitmap.recycle();
        } catch (FileNotFoundException e) {
            throw new FileCopyException(R.string.error_file_not_found);
        } catch (IOException e) {
            e.printStackTrace();
            throw new FileCopyException(R.string.error_io_exception);
        } catch (SecurityException e) {
            throw new FileCopyException(R.string.error_security_exception_during_image_copy);
        } catch (OutOfMemoryError e) {
            ++sampleSize;
            if (sampleSize <= 3) {
                copyImageToPrivateStorage(file, image, sampleSize);
            } else {
                throw new FileCopyException(R.string.error_out_of_memory);
            }
        } finally {
            close(os);
            close(is);
        }
    }

    public void copyImageToPrivateStorage(File file, Uri image) throws FileCopyException {
        Log.d(Config.LOGTAG, "copy image (" + image.toString() + ") to private storage " + file.getAbsolutePath());
        copyImageToPrivateStorage(file, image, 0);
    }

    public void copyImageToPrivateStorage(Message message, Uri image) throws FileCopyException {
        switch (Config.IMAGE_FORMAT) {
            case JPEG:
                message.setRelativeFilePath(message.getUuid() + ".jpg");
                break;
            case PNG:
                message.setRelativeFilePath(message.getUuid() + ".png");
                break;
            case WEBP:
                message.setRelativeFilePath(message.getUuid() + ".webp");
                break;
        }
        copyImageToPrivateStorage(getFile(message), image);
        updateFileParams(message);
    }

    public boolean unusualBounds(Uri image) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(mXmppConnectionService.getContentResolver().openInputStream(image), null, options);
            float ratio = (float) options.outHeight / options.outWidth;
            return ratio > (21.0f / 9.0f) || ratio < (9.0f / 21.0f);
        } catch (Exception e) {
            return false;
        }
    }

    private int getRotation(File file) {
        return getRotation(Uri.parse("file://" + file.getAbsolutePath()));
    }

    private int getRotation(Uri image) {
        InputStream is = null;
        try {
            is = mXmppConnectionService.getContentResolver().openInputStream(image);
            return ExifHelper.getOrientation(is);
        } catch (FileNotFoundException e) {
            return 0;
        } finally {
            close(is);
        }
    }

    public Bitmap getThumbnail(Message message, int size, boolean cacheOnly) throws IOException {
        final String uuid = message.getUuid();
        final LruCache<String, Bitmap> cache = mXmppConnectionService.getBitmapCache();
        Bitmap thumbnail = cache.get(uuid);
        if ((thumbnail == null) && (!cacheOnly)) {
            synchronized (THUMBNAIL_LOCK) {
                thumbnail = cache.get(uuid);
                if (thumbnail != null) {
                    return thumbnail;
                }
                DownloadableFile file = getFile(message);
                final String mime = file.getMimeType();
                if (mime.startsWith("video/")) {
                    thumbnail = getVideoPreview(file, size);
                } else {
                    Bitmap fullsize = getFullsizeImagePreview(file, size);
                    if (fullsize == null) {
                        throw new FileNotFoundException();
                    }
                    thumbnail = resize(fullsize, size);
                    thumbnail = rotate(thumbnail, getRotation(file));
                    if (mime.equals("image/gif")) {
                        Bitmap withGifOverlay = thumbnail.copy(Bitmap.Config.ARGB_8888, true);
                        drawOverlay(withGifOverlay, paintOverlayBlack(withGifOverlay) ? R.drawable.play_gif_black : R.drawable.play_gif_white, 1.0f);
                        thumbnail.recycle();
                        thumbnail = withGifOverlay;
                    }
                }
                cache.put(uuid, thumbnail);
            }
        }
        return thumbnail;
    }

    private Bitmap getFullsizeImagePreview(File file, int size) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = calcSampleSize(file, size);
        try {
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (OutOfMemoryError e) {
            options.inSampleSize *= 2;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        }
    }

    private void drawOverlay(Bitmap bitmap, int resource, float factor) {
        Bitmap overlay = BitmapFactory.decodeResource(mXmppConnectionService.getResources(), resource);
        Canvas canvas = new Canvas(bitmap);
        float targetSize = Math.min(canvas.getWidth(), canvas.getHeight()) * factor;
        Log.d(Config.LOGTAG, "target size overlay: " + targetSize + " overlay bitmap size was " + overlay.getHeight());
        float left = (canvas.getWidth() - targetSize) / 2.0f;
        float top = (canvas.getHeight() - targetSize) / 2.0f;
        RectF dst = new RectF(left, top, left + targetSize - 1, top + targetSize - 1);
        canvas.drawBitmap(overlay, null, dst, createAntiAliasingPaint());
    }

    /**
     * https://stackoverflow.com/a/3943023/210897
     */
    private boolean paintOverlayBlack(final Bitmap bitmap) {
        final int h = bitmap.getHeight();
        final int w = bitmap.getWidth();
        int record = 0;
        for (int y = Math.round(h * IGNORE_PADDING); y < h - Math.round(h * IGNORE_PADDING); ++y) {
            for (int x = Math.round(w * IGNORE_PADDING); x < w - Math.round(w * IGNORE_PADDING); ++x) {
                int pixel = bitmap.getPixel(x, y);
                if ((Color.red(pixel) * 0.299 + Color.green(pixel) * 0.587 + Color.blue(pixel) * 0.114) > 186) {
                    --record;
                } else {
                    ++record;
                }
            }
        }
        return record < 0;
    }

    private Bitmap cropCenterSquareVideo(Uri uri, int size) {
        MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
        Bitmap frame;
        try {
            metadataRetriever.setDataSource(mXmppConnectionService, uri);
            frame = metadataRetriever.getFrameAtTime(0);
            metadataRetriever.release();
            return cropCenterSquare(frame, size);
        } catch (Exception e) {
            frame = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            frame.eraseColor(0xff000000);
            return frame;
        }
    }

    private Bitmap getVideoPreview(File file, int size) {
        MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
        Bitmap frame;
        try {
            metadataRetriever.setDataSource(file.getAbsolutePath());
            frame = metadataRetriever.getFrameAtTime(0);
            metadataRetriever.release();
            frame = resize(frame, size);
        } catch (IOException | RuntimeException e) {
            frame = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            frame.eraseColor(0xff000000);
        }
        drawOverlay(frame, paintOverlayBlack(frame) ? R.drawable.play_video_black : R.drawable.play_video_white, 0.75f);
        return frame;
    }

    public Uri getTakePhotoUri() {
        File file;
        if (Config.ONLY_INTERNAL_STORAGE) {
            file = new File(mXmppConnectionService.getCacheDir().getAbsolutePath(), "Camera/IMG_" + this.IMAGE_DATE_FORMAT.format(new Date()) + ".jpg");
        } else {
            file = new File(getTakePhotoPath() + "IMG_" + this.IMAGE_DATE_FORMAT.format(new Date()) + ".jpg");
        }
        file.getParentFile().mkdirs();
        return getUriForFile(mXmppConnectionService, file);
    }

    public Avatar getPepAvatar(Uri image, int size, Bitmap.CompressFormat format) {

        final Avatar uncompressAvatar = getUncompressedAvatar(image);
        if (uncompressAvatar != null && uncompressAvatar.image.length() <= Config.AVATAR_CHAR_LIMIT) {
            return uncompressAvatar;
        }
        if (uncompressAvatar != null) {
            Log.d(Config.LOGTAG, "uncompressed avatar exceeded char limit by " + (uncompressAvatar.image.length() - Config.AVATAR_CHAR_LIMIT));
        }

        Bitmap bm = cropCenterSquare(image, size);
        if (bm == null) {
            return null;
        }
        if (hasAlpha(bm)) {
            Log.d(Config.LOGTAG, "alpha in avatar detected; uploading as PNG");
            bm.recycle();
            bm = cropCenterSquare(image, 96);
            return getPepAvatar(bm, Bitmap.CompressFormat.PNG, 100);
        }
        return getPepAvatar(bm, format, 100);
    }

    private Avatar getUncompressedAvatar(Uri uri) {
        Bitmap bitmap = null;
        try {
            bitmap = BitmapFactory.decodeStream(mXmppConnectionService.getContentResolver().openInputStream(uri));
            return getPepAvatar(bitmap, Bitmap.CompressFormat.PNG, 100);
        } catch (Exception e) {
            return null;
        } finally {
            if (bitmap != null) {
                bitmap.recycle();
            }
        }
    }

    private Avatar getPepAvatar(Bitmap bitmap, Bitmap.CompressFormat format, int quality) {
        try {
            ByteArrayOutputStream mByteArrayOutputStream = new ByteArrayOutputStream();
            Base64OutputStream mBase64OutputStream = new Base64OutputStream(mByteArrayOutputStream, Base64.DEFAULT);
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            DigestOutputStream mDigestOutputStream = new DigestOutputStream(mBase64OutputStream, digest);
            if (!bitmap.compress(format, quality, mDigestOutputStream)) {
                return null;
            }
            mDigestOutputStream.flush();
            mDigestOutputStream.close();
            long chars = mByteArrayOutputStream.size();
            if (format != Bitmap.CompressFormat.PNG && quality >= 50 && chars >= Config.AVATAR_CHAR_LIMIT) {
                int q = quality - 2;
                Log.d(Config.LOGTAG, "avatar char length was " + chars + " reducing quality to " + q);
                return getPepAvatar(bitmap, format, q);
            }
            Log.d(Config.LOGTAG, "settled on char length " + chars + " with quality=" + quality);
            final Avatar avatar = new Avatar();
            avatar.sha1sum = CryptoHelper.bytesToHex(digest.digest());
            avatar.image = new String(mByteArrayOutputStream.toByteArray());
            if (format.equals(Bitmap.CompressFormat.WEBP)) {
                avatar.type = "image/webp";
            } else if (format.equals(Bitmap.CompressFormat.JPEG)) {
                avatar.type = "image/jpeg";
            } else if (format.equals(Bitmap.CompressFormat.PNG)) {
                avatar.type = "image/png";
            }
            avatar.width = bitmap.getWidth();
            avatar.height = bitmap.getHeight();
            return avatar;
        } catch (OutOfMemoryError e) {
            Log.d(Config.LOGTAG,"unable to convert avatar to base64 due to low memory");
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public Avatar getStoredPepAvatar(String hash) {
        if (hash == null) {
            return null;
        }
        Avatar avatar = new Avatar();
        File file = new File(getAvatarPath(hash));
        FileInputStream is = null;
        try {
            avatar.size = file.length();
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            is = new FileInputStream(file);
            ByteArrayOutputStream mByteArrayOutputStream = new ByteArrayOutputStream();
            Base64OutputStream mBase64OutputStream = new Base64OutputStream(mByteArrayOutputStream, Base64.DEFAULT);
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            DigestOutputStream os = new DigestOutputStream(mBase64OutputStream, digest);
            byte[] buffer = new byte[4096];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
            os.flush();
            os.close();
            avatar.sha1sum = CryptoHelper.bytesToHex(digest.digest());
            avatar.image = new String(mByteArrayOutputStream.toByteArray());
            avatar.height = options.outHeight;
            avatar.width = options.outWidth;
            avatar.type = options.outMimeType;
            return avatar;
        } catch (NoSuchAlgorithmException | IOException e) {
            return null;
        } finally {
            close(is);
        }
    }

    public boolean isAvatarCached(Avatar avatar) {
        File file = new File(getAvatarPath(avatar.getFilename()));
        return file.exists();
    }

    public boolean save(final Avatar avatar) {
        File file;
        if (isAvatarCached(avatar)) {
            file = new File(getAvatarPath(avatar.getFilename()));
            avatar.size = file.length();
        } else {
            file = new File(mXmppConnectionService.getCacheDir().getAbsolutePath() + "/" + UUID.randomUUID().toString());
            if (file.getParentFile().mkdirs()) {
                Log.d(Config.LOGTAG, "created cache directory");
            }
            OutputStream os = null;
            try {
                if (!file.createNewFile()) {
                    Log.d(Config.LOGTAG, "unable to create temporary file " + file.getAbsolutePath());
                }
                os = new FileOutputStream(file);
                MessageDigest digest = MessageDigest.getInstance("SHA-1");
                digest.reset();
                DigestOutputStream mDigestOutputStream = new DigestOutputStream(os, digest);
                final byte[] bytes = avatar.getImageAsBytes();
                mDigestOutputStream.write(bytes);
                mDigestOutputStream.flush();
                mDigestOutputStream.close();
                String sha1sum = CryptoHelper.bytesToHex(digest.digest());
                if (sha1sum.equals(avatar.sha1sum)) {
                    File outputFile = new File(getAvatarPath(avatar.getFilename()));
                    if (outputFile.getParentFile().mkdirs()) {
                        Log.d(Config.LOGTAG, "created avatar directory");
                    }
                    String filename = getAvatarPath(avatar.getFilename());
                    if (!file.renameTo(new File(filename))) {
                        Log.d(Config.LOGTAG, "unable to rename " + file.getAbsolutePath() + " to " + outputFile);
                        return false;
                    }
                } else {
                    Log.d(Config.LOGTAG, "sha1sum mismatch for " + avatar.owner);
                    if (!file.delete()) {
                        Log.d(Config.LOGTAG, "unable to delete temporary file");
                    }
                    return false;
                }
                avatar.size = bytes.length;
            } catch (IllegalArgumentException | IOException | NoSuchAlgorithmException e) {
                return false;
            } finally {
                close(os);
            }
        }
        return true;
    }

    private String getAvatarPath(String avatar) {
        return mXmppConnectionService.getFilesDir().getAbsolutePath() + "/avatars/" + avatar;
    }

    public Uri getAvatarUri(String avatar) {
        return Uri.parse("file:" + getAvatarPath(avatar));
    }

    public Bitmap cropCenterSquare(Uri image, int size) {
        if (image == null) {
            return null;
        }
        InputStream is = null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = calcSampleSize(image, size);
            is = mXmppConnectionService.getContentResolver().openInputStream(image);
            if (is == null) {
                return null;
            }
            Bitmap input = BitmapFactory.decodeStream(is, null, options);
            if (input == null) {
                return null;
            } else {
                input = rotate(input, getRotation(image));
                return cropCenterSquare(input, size);
            }
        } catch (FileNotFoundException | SecurityException e) {
            Log.d(Config.LOGTAG,"unable to open file "+image.toString(), e);
            return null;
        } finally {
            close(is);
        }
    }

    public Bitmap cropCenter(Uri image, int newHeight, int newWidth) {
        if (image == null) {
            return null;
        }
        InputStream is = null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = calcSampleSize(image, Math.max(newHeight, newWidth));
            is = mXmppConnectionService.getContentResolver().openInputStream(image);
            if (is == null) {
                return null;
            }
            Bitmap source = BitmapFactory.decodeStream(is, null, options);
            if (source == null) {
                return null;
            }
            int sourceWidth = source.getWidth();
            int sourceHeight = source.getHeight();
            float xScale = (float) newWidth / sourceWidth;
            float yScale = (float) newHeight / sourceHeight;
            float scale = Math.max(xScale, yScale);
            float scaledWidth = scale * sourceWidth;
            float scaledHeight = scale * sourceHeight;
            float left = (newWidth - scaledWidth) / 2;
            float top = (newHeight - scaledHeight) / 2;

            RectF targetRect = new RectF(left, top, left + scaledWidth, top + scaledHeight);
            Bitmap dest = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(dest);
            canvas.drawBitmap(source, null, targetRect, createAntiAliasingPaint());
            if (source.isRecycled()) {
                source.recycle();
            }
            return dest;
        } catch (SecurityException e) {
            return null; //android 6.0 with revoked permissions for example
        } catch (FileNotFoundException e) {
            return null;
        } finally {
            close(is);
        }
    }

    public Bitmap cropCenterSquare(Bitmap input, int size) {
        int w = input.getWidth();
        int h = input.getHeight();

        float scale = Math.max((float) size / h, (float) size / w);

        float outWidth = scale * w;
        float outHeight = scale * h;
        float left = (size - outWidth) / 2;
        float top = (size - outHeight) / 2;
        RectF target = new RectF(left, top, left + outWidth, top + outHeight);

        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        canvas.drawBitmap(input, null, target, createAntiAliasingPaint());
        if (!input.isRecycled()) {
            input.recycle();
        }
        return output;
    }

    private int calcSampleSize(Uri image, int size) throws FileNotFoundException, SecurityException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(mXmppConnectionService.getContentResolver().openInputStream(image), null, options);
        return calcSampleSize(options, size);
    }

    public void updateFileParams(Message message) {
        updateFileParams(message, null);
    }

    public void updateFileParams(Message message, URL url) {
        DownloadableFile file = getFile(message);
        final String mime = file.getMimeType();
        boolean image = message.getType() == Message.TYPE_IMAGE || (mime != null && mime.startsWith("image/"));
        boolean video = mime != null && mime.startsWith("video/");
        boolean audio = mime != null && mime.startsWith("audio/");
        final StringBuilder body = new StringBuilder();
        if (url != null) {
            body.append(url.toString());
        }
        body.append('|').append(file.getSize());
        if (image || video) {
            try {
                Dimensions dimensions = image ? getImageDimensions(file) : getVideoDimensions(file);
                if (dimensions.valid()) {
                    body.append('|').append(dimensions.width).append('|').append(dimensions.height);
                }
            } catch (NotAVideoFile notAVideoFile) {
                Log.d(Config.LOGTAG, "file with mime type " + file.getMimeType() + " was not a video file");
                //fall threw
            }
        } else if (audio) {
            body.append("|0|0|").append(getMediaRuntime(file));
        }
        message.setBody(body.toString());
        message.setDeleted(false);
    }

    public int getMediaRuntime(Uri uri) {
        try {
            MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
            mediaMetadataRetriever.setDataSource(mXmppConnectionService, uri);
            return Integer.parseInt(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private int getMediaRuntime(File file) {
        try {
            MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
            mediaMetadataRetriever.setDataSource(file.toString());
            return Integer.parseInt(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private Dimensions getImageDimensions(File file) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        int rotation = getRotation(file);
        boolean rotated = rotation == 90 || rotation == 270;
        int imageHeight = rotated ? options.outWidth : options.outHeight;
        int imageWidth = rotated ? options.outHeight : options.outWidth;
        return new Dimensions(imageHeight, imageWidth);
    }

    private Dimensions getVideoDimensions(File file) throws NotAVideoFile {
        MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
        try {
            metadataRetriever.setDataSource(file.getAbsolutePath());
        } catch (RuntimeException e) {
            throw new NotAVideoFile(e);
        }
        return getVideoDimensions(metadataRetriever);
    }

    public Bitmap getAvatar(String avatar, int size) {
        if (avatar == null) {
            return null;
        }
        Bitmap bm = cropCenter(getAvatarUri(avatar), size, size);
        if (bm == null) {
            return null;
        }
        return bm;
    }

    public boolean isFileAvailable(Message message) {
        return getFile(message).exists();
    }

    private static class Dimensions {
        public final int width;
        public final int height;

        Dimensions(int height, int width) {
            this.width = width;
            this.height = height;
        }

        public int getMin() {
            return Math.min(width, height);
        }

        public boolean valid() {
            return width > 0 && height > 0;
        }
    }

    private static class NotAVideoFile extends Exception {
        public NotAVideoFile(Throwable t) {
            super(t);
        }

        public NotAVideoFile() {
            super();
        }
    }

    public class FileCopyException extends Exception {
        private static final long serialVersionUID = -1010013599132881427L;
        private int resId;

        public FileCopyException(int resId) {
            this.resId = resId;
        }

        public int getResId() {
            return resId;
        }
    }
}
