package eu.siacs.conversations.persistance;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.system.Os;
import android.system.StructStat;
import android.util.Base64;
import android.util.Base64OutputStream;
import android.util.Log;
import android.util.LruCache;
import android.webkit.MimeTypeMap;

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
import java.util.Date;
import java.util.List;
import java.util.Locale;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.ExifHelper;
import eu.siacs.conversations.utils.FileUtils;
import eu.siacs.conversations.xmpp.pep.Avatar;

public class FileBackend {
	private final SimpleDateFormat imageDateFormat = new SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US);

	private XmppConnectionService mXmppConnectionService;

	public FileBackend(XmppConnectionService service) {
		this.mXmppConnectionService = service;
	}

	private void createNoMedia() {
		final File nomedia = new File(getConversationsFileDirectory()+".nomedia");
		if (!nomedia.exists()) {
			try {
				nomedia.createNewFile();
			} catch (Exception e) {
				Log.d(Config.LOGTAG, "could not create nomedia file");
			}
		}
	}

	public void updateMediaScanner(File file) {
		if (file.getAbsolutePath().startsWith(getConversationsImageDirectory())) {
			Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
			intent.setData(Uri.fromFile(file));
			mXmppConnectionService.sendBroadcast(intent);
		} else {
			createNoMedia();
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

	public DownloadableFile getFile(Message message, boolean decrypted) {
		final boolean encrypted = !decrypted
				&& (message.getEncryption() == Message.ENCRYPTION_PGP
				|| message.getEncryption() == Message.ENCRYPTION_DECRYPTED);
		final DownloadableFile file;
		String path = message.getRelativeFilePath();
		if (path == null) {
			path = message.getUuid();
		}
		if (path.startsWith("/")) {
			file = new DownloadableFile(path);
		} else {
			String mime = message.getMimeType();
			if (mime != null && mime.startsWith("image")) {
				file = new DownloadableFile(getConversationsImageDirectory() + path);
			} else {
				file = new DownloadableFile(getConversationsFileDirectory() + path);
			}
		}
		if (encrypted) {
			return new DownloadableFile(getConversationsFileDirectory() + file.getName() + ".pgp");
		} else {
			return file;
		}
	}

	private static long getFileSize(Context context, Uri uri) {
		Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
		if (cursor != null && cursor.moveToFirst()) {
			return cursor.getLong(cursor.getColumnIndex(OpenableColumns.SIZE));
		} else {
			return -1;
		}
	}

	public static boolean allFilesUnderSize(Context context, List<Uri> uris, long max) {
		if (max <= 0) {
			Log.d(Config.LOGTAG,"server did not report max file size for http upload");
			return true; //exception to be compatible with HTTP Upload < v0.2
		}
		for(Uri uri : uris) {
			if (FileBackend.getFileSize(context, uri) > max) {
				Log.d(Config.LOGTAG,"not all files are under "+max+" bytes. suggesting falling back to jingle");
				return false;
			}
		}
		return true;
	}

	public static String getConversationsFileDirectory() {
		return  Environment.getExternalStorageDirectory().getAbsolutePath()+"/Conversations/";
	}

	public static String getConversationsImageDirectory() {
		return Environment.getExternalStoragePublicDirectory(
				Environment.DIRECTORY_PICTURES).getAbsolutePath()
			+ "/Conversations/";
	}

	public Bitmap resize(Bitmap originalBitmap, int size) {
		int w = originalBitmap.getWidth();
		int h = originalBitmap.getHeight();
		if (Math.max(w, h) > size) {
			int scalledW;
			int scalledH;
			if (w <= h) {
				scalledW = (int) (w / ((double) h / size));
				scalledH = size;
			} else {
				scalledW = size;
				scalledH = (int) (h / ((double) w / size));
			}
			Bitmap result = Bitmap.createScaledBitmap(originalBitmap, scalledW, scalledH, true);
			if (originalBitmap != null && !originalBitmap.isRecycled()) {
				originalBitmap.recycle();
			}
			return result;
		} else {
			return originalBitmap;
		}
	}

	public static Bitmap rotate(Bitmap bitmap, int degree) {
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

	public boolean useImageAsIs(Uri uri) {
		String path = getOriginalPath(uri);
		if (path == null) {
			return false;
		}
		File file = new File(path);
		long size = file.length();
		if (size == 0 || size >= Config.IMAGE_MAX_SIZE ) {
			return false;
		}
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		try {
			BitmapFactory.decodeStream(mXmppConnectionService.getContentResolver().openInputStream(uri), null, options);
			if (options == null || options.outMimeType == null || options.outHeight <= 0 || options.outWidth <= 0) {
				return false;
			}
			return (options.outWidth <= Config.IMAGE_SIZE && options.outHeight <= Config.IMAGE_SIZE && options.outMimeType.contains(Config.IMAGE_FORMAT.name().toLowerCase()));
		} catch (FileNotFoundException e) {
			return false;
		}
	}

	public String getOriginalPath(Uri uri) {
		return FileUtils.getPath(mXmppConnectionService,uri);
	}

	public void copyFileToPrivateStorage(File file, Uri uri) throws FileCopyException {
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
				os.write(buffer, 0, length);
			}
			os.flush();
		} catch(FileNotFoundException e) {
			throw new FileCopyException(R.string.error_file_not_found);
		} catch (IOException e) {
			e.printStackTrace();
			throw new FileCopyException(R.string.error_io_exception);
		} finally {
			close(os);
			close(is);
		}
		Log.d(Config.LOGTAG, "output file name " + file.getAbsolutePath());
	}

	public void copyFileToPrivateStorage(Message message, Uri uri) throws FileCopyException {
		Log.d(Config.LOGTAG, "copy " + uri.toString() + " to private storage");
		String mime = mXmppConnectionService.getContentResolver().getType(uri);
		String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
		message.setRelativeFilePath(message.getUuid() + "." + extension);
		copyFileToPrivateStorage(mXmppConnectionService.getFileBackend().getFile(message), uri);
	}

	private void copyImageToPrivateStorage(File file, Uri image, int sampleSize) throws FileCopyException {
		file.getParentFile().mkdirs();
		InputStream is = null;
		OutputStream os = null;
		try {
			file.createNewFile();
			is = mXmppConnectionService.getContentResolver().openInputStream(image);
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
			while(!targetSizeReached) {
				os = new FileOutputStream(file);
				boolean success = scaledBitmap.compress(Config.IMAGE_FORMAT, quality, os);
				if (!success) {
					throw new FileCopyException(R.string.error_compressing_image);
				}
				os.flush();
				targetSizeReached = file.length() <= Config.IMAGE_MAX_SIZE || quality <= 50;
				quality -= 5;
			}
			scaledBitmap.recycle();
			return;
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
		} catch (NullPointerException e) {
			throw new FileCopyException(R.string.error_io_exception);
		} finally {
			close(os);
			close(is);
		}
	}

	public void copyImageToPrivateStorage(File file, Uri image) throws FileCopyException {
		copyImageToPrivateStorage(file, image, 0);
	}

	public void copyImageToPrivateStorage(Message message, Uri image) throws FileCopyException {
		switch(Config.IMAGE_FORMAT) {
			case JPEG:
				message.setRelativeFilePath(message.getUuid()+".jpg");
				break;
			case PNG:
				message.setRelativeFilePath(message.getUuid()+".png");
				break;
			case WEBP:
				message.setRelativeFilePath(message.getUuid()+".webp");
				break;
		}
		copyImageToPrivateStorage(getFile(message), image);
		updateFileParams(message);
	}

	private int getRotation(File file) {
		return getRotation(Uri.parse("file://"+file.getAbsolutePath()));
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

	public Bitmap getThumbnail(Message message, int size, boolean cacheOnly) throws FileNotFoundException {
		final String uuid = message.getUuid();
		final LruCache<String,Bitmap> cache = mXmppConnectionService.getBitmapCache();
		Bitmap thumbnail = cache.get(uuid);
		if ((thumbnail == null) && (!cacheOnly)) {
			synchronized (cache) {
				thumbnail = cache.get(uuid);
				if (thumbnail != null) {
					return thumbnail;
				}
				File file = getFile(message);
				BitmapFactory.Options options = new BitmapFactory.Options();
				options.inSampleSize = calcSampleSize(file, size);
				Bitmap fullsize = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
				if (fullsize == null) {
					throw new FileNotFoundException();
				}
				thumbnail = resize(fullsize, size);
				thumbnail = rotate(thumbnail, getRotation(file));
				this.mXmppConnectionService.getBitmapCache().put(uuid, thumbnail);
			}
		}
		return thumbnail;
	}

	public Uri getTakePhotoUri() {
		StringBuilder pathBuilder = new StringBuilder();
		pathBuilder.append(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM));
		pathBuilder.append('/');
		pathBuilder.append("Camera");
		pathBuilder.append('/');
		pathBuilder.append("IMG_" + this.imageDateFormat.format(new Date()) + ".jpg");
		Uri uri = Uri.parse("file://" + pathBuilder.toString());
		File file = new File(uri.toString());
		file.getParentFile().mkdirs();
		return uri;
	}

	public Avatar getPepAvatar(Uri image, int size, Bitmap.CompressFormat format) {
		try {
			Avatar avatar = new Avatar();
			Bitmap bm = cropCenterSquare(image, size);
			if (bm == null) {
				return null;
			}
			ByteArrayOutputStream mByteArrayOutputStream = new ByteArrayOutputStream();
			Base64OutputStream mBase64OutputSttream = new Base64OutputStream(
					mByteArrayOutputStream, Base64.DEFAULT);
			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			DigestOutputStream mDigestOutputStream = new DigestOutputStream(
					mBase64OutputSttream, digest);
			if (!bm.compress(format, 75, mDigestOutputStream)) {
				return null;
			}
			mDigestOutputStream.flush();
			mDigestOutputStream.close();
			avatar.sha1sum = CryptoHelper.bytesToHex(digest.digest());
			avatar.image = new String(mByteArrayOutputStream.toByteArray());
			return avatar;
		} catch (NoSuchAlgorithmException e) {
			return null;
		} catch (IOException e) {
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
			return avatar;
		} catch (IOException e) {
			return null;
		} catch (NoSuchAlgorithmException e) {
			return null;
		} finally {
			close(is);
		}
	}

	public boolean isAvatarCached(Avatar avatar) {
		File file = new File(getAvatarPath(avatar.getFilename()));
		return file.exists();
	}

	public boolean save(Avatar avatar) {
		File file;
		if (isAvatarCached(avatar)) {
			file = new File(getAvatarPath(avatar.getFilename()));
		} else {
			String filename = getAvatarPath(avatar.getFilename());
			file = new File(filename + ".tmp");
			file.getParentFile().mkdirs();
			OutputStream os = null;
			try {
				file.createNewFile();
				os = new FileOutputStream(file);
				MessageDigest digest = MessageDigest.getInstance("SHA-1");
				digest.reset();
				DigestOutputStream mDigestOutputStream = new DigestOutputStream(os, digest);
				mDigestOutputStream.write(avatar.getImageAsBytes());
				mDigestOutputStream.flush();
				mDigestOutputStream.close();
				String sha1sum = CryptoHelper.bytesToHex(digest.digest());
				if (sha1sum.equals(avatar.sha1sum)) {
					file.renameTo(new File(filename));
				} else {
					Log.d(Config.LOGTAG, "sha1sum mismatch for " + avatar.owner);
					file.delete();
					return false;
				}
			} catch (IllegalArgumentException | IOException | NoSuchAlgorithmException e) {
				return false;
			} finally {
				close(os);
			}
		}
		avatar.size = file.length();
		return true;
	}

	public String getAvatarPath(String avatar) {
		return mXmppConnectionService.getFilesDir().getAbsolutePath()+ "/avatars/" + avatar;
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
		} catch (SecurityException e) {
			return null; // happens for example on Android 6.0 if contacts permissions get revoked
		} catch (FileNotFoundException e) {
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
			canvas.drawBitmap(source, null, targetRect, null);
			if (source != null && !source.isRecycled()) {
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
		canvas.drawBitmap(input, null, target, null);
		if (input != null && !input.isRecycled()) {
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

	private static int calcSampleSize(File image, int size) {
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(image.getAbsolutePath(), options);
		return calcSampleSize(options, size);
	}

	public static int calcSampleSize(BitmapFactory.Options options, int size) {
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

	public Uri getJingleFileUri(Message message) {
		File file = getFile(message);
		return Uri.parse("file://" + file.getAbsolutePath());
	}

	public void updateFileParams(Message message) {
		updateFileParams(message,null);
	}

	public void updateFileParams(Message message, URL url) {
		DownloadableFile file = getFile(message);
		if (message.getType() == Message.TYPE_IMAGE || file.getMimeType().startsWith("image/")) {
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inJustDecodeBounds = true;
			BitmapFactory.decodeFile(file.getAbsolutePath(), options);
			int rotation = getRotation(file);
			boolean rotated = rotation == 90 || rotation == 270;
			int imageHeight = rotated ? options.outWidth : options.outHeight;
			int imageWidth = rotated ? options.outHeight : options.outWidth;
			if (url == null) {
				message.setBody(Long.toString(file.getSize()) + '|' + imageWidth + '|' + imageHeight);
			} else {
				message.setBody(url.toString()+"|"+Long.toString(file.getSize()) + '|' + imageWidth + '|' + imageHeight);
			}
		} else {
			if (url != null) {
				message.setBody(url.toString()+"|"+Long.toString(file.getSize()));
			} else {
				message.setBody(Long.toString(file.getSize()));
			}
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
}
