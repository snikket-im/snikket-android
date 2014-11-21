package eu.siacs.conversations.persistance;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Base64OutputStream;
import android.util.Log;
import android.webkit.MimeTypeMap;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.ExifHelper;
import eu.siacs.conversations.xmpp.pep.Avatar;

public class FileBackend {

	private static int IMAGE_SIZE = 1920;

	private SimpleDateFormat imageDateFormat = new SimpleDateFormat(
			"yyyyMMdd_HHmmssSSS", Locale.US);

	private XmppConnectionService mXmppConnectionService;

	public FileBackend(XmppConnectionService service) {
		this.mXmppConnectionService = service;
	}

	public DownloadableFile getFile(Message message) {
		return getFile(message, true);
	}

	public DownloadableFile getFile(Message message, boolean decrypted) {
		String path = message.getRelativeFilePath();
		if (!decrypted && (message.getEncryption() == Message.ENCRYPTION_PGP || message.getEncryption() == Message.ENCRYPTION_DECRYPTED)) {
			String extension;
			if (path != null && !path.isEmpty()) {
				String[] parts = path.split("\\.");
				extension = "."+parts[parts.length - 1];
			} else if (message.getType() == Message.TYPE_IMAGE || message.getType() == Message.TYPE_TEXT) {
				extension = ".webp";
			} else {
				extension = "";
			}
			return new DownloadableFile(getConversationsFileDirectory()+message.getUuid()+extension+".pgp");
		} else if (path != null && !path.isEmpty()) {
			if (path.startsWith("/")) {
				return new DownloadableFile(path);
			} else {
				return new DownloadableFile(getConversationsFileDirectory()+path);
			}
		} else {
			StringBuilder filename = new StringBuilder();
			filename.append(getConversationsImageDirectory());
			filename.append(message.getUuid()+".webp");
			return new DownloadableFile(filename.toString());
		}
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
			Bitmap scalledBitmap = Bitmap.createScaledBitmap(originalBitmap,
					scalledW, scalledH, true);
			return scalledBitmap;
		} else {
			return originalBitmap;
		}
	}

	public Bitmap rotate(Bitmap bitmap, int degree) {
		int w = bitmap.getWidth();
		int h = bitmap.getHeight();
		Matrix mtx = new Matrix();
		mtx.postRotate(degree);
		return Bitmap.createBitmap(bitmap, 0, 0, w, h, mtx, true);
	}

	public String getOriginalPath(Uri uri) {
		String path = null;
		if (uri.getScheme().equals("file")) {
			return uri.getPath();
		} else if (uri.toString().startsWith("content://media/")) {
			String[] projection = {MediaStore.MediaColumns.DATA};
			Cursor metaCursor = mXmppConnectionService.getContentResolver().query(uri,
					projection, null, null, null);
			if (metaCursor != null) {
				try {
					if (metaCursor.moveToFirst()) {
						path = metaCursor.getString(0);
					}
				} finally {
					metaCursor.close();
				}
			}
		}
		return path;
	}

	public DownloadableFile copyFileToPrivateStorage(Message message, Uri uri) throws FileCopyException {
		try {
			Log.d(Config.LOGTAG, "copy " + uri.toString() + " to private storage");
			String mime = mXmppConnectionService.getContentResolver().getType(uri);
			String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
			message.setRelativeFilePath(message.getUuid() + "." + extension);
			DownloadableFile file = mXmppConnectionService.getFileBackend().getFile(message);
			file.getParentFile().mkdirs();
			file.createNewFile();
			OutputStream os = new FileOutputStream(file);
			InputStream is = mXmppConnectionService.getContentResolver().openInputStream(uri);
			byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
				os.write(buffer, 0, length);
            }
			os.flush();
			os.close();
			is.close();
			Log.d(Config.LOGTAG, "output file name " + mXmppConnectionService.getFileBackend().getFile(message));
			return file;
		} catch (FileNotFoundException e) {
			throw new FileCopyException(R.string.error_file_not_found);
		} catch (IOException e) {
			throw new FileCopyException(R.string.error_io_exception);
		}
	}

	public DownloadableFile copyImageToPrivateStorage(Message message, Uri image)
			throws FileCopyException {
		return this.copyImageToPrivateStorage(message, image, 0);
	}

	private DownloadableFile copyImageToPrivateStorage(Message message,
			Uri image, int sampleSize) throws FileCopyException {
		try {
			InputStream is = mXmppConnectionService.getContentResolver()
					.openInputStream(image);
			DownloadableFile file = getFile(message);
			file.getParentFile().mkdirs();
			file.createNewFile();
			Bitmap originalBitmap;
			BitmapFactory.Options options = new BitmapFactory.Options();
			int inSampleSize = (int) Math.pow(2, sampleSize);
			Log.d(Config.LOGTAG, "reading bitmap with sample size "
					+ inSampleSize);
			options.inSampleSize = inSampleSize;
			originalBitmap = BitmapFactory.decodeStream(is, null, options);
			is.close();
			if (originalBitmap == null) {
				throw new FileCopyException(R.string.error_not_an_image_file);
			}
			Bitmap scalledBitmap = resize(originalBitmap, IMAGE_SIZE);
			originalBitmap = null;
			int rotation = getRotation(image);
			if (rotation > 0) {
				scalledBitmap = rotate(scalledBitmap, rotation);
			}
			OutputStream os = new FileOutputStream(file);
			boolean success = scalledBitmap.compress(
					Bitmap.CompressFormat.WEBP, 75, os);
			if (!success) {
				throw new FileCopyException(R.string.error_compressing_image);
			}
			os.flush();
			os.close();
			long size = file.getSize();
			int width = scalledBitmap.getWidth();
			int height = scalledBitmap.getHeight();
			message.setBody(Long.toString(size) + ',' + width + ',' + height);
			return file;
		} catch (FileNotFoundException e) {
			throw new FileCopyException(R.string.error_file_not_found);
		} catch (IOException e) {
			throw new FileCopyException(R.string.error_io_exception);
		} catch (SecurityException e) {
			throw new FileCopyException(
					R.string.error_security_exception_during_image_copy);
		} catch (OutOfMemoryError e) {
			++sampleSize;
			if (sampleSize <= 3) {
				return copyImageToPrivateStorage(message, image, sampleSize);
			} else {
				throw new FileCopyException(R.string.error_out_of_memory);
			}
		}
	}

	private int getRotation(Uri image) {
		try {
			InputStream is = mXmppConnectionService.getContentResolver()
					.openInputStream(image);
			return ExifHelper.getOrientation(is);
		} catch (FileNotFoundException e) {
			return 0;
		}
	}

	public Bitmap getImageFromMessage(Message message) {
		return BitmapFactory.decodeFile(getFile(message).getAbsolutePath());
	}

	public Bitmap getThumbnail(Message message, int size, boolean cacheOnly)
			throws FileNotFoundException {
		Bitmap thumbnail = mXmppConnectionService.getBitmapCache().get(
				message.getUuid());
		if ((thumbnail == null) && (!cacheOnly)) {
			File file = getFile(message);
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inSampleSize = calcSampleSize(file, size);
			Bitmap fullsize = BitmapFactory.decodeFile(file.getAbsolutePath(),
					options);
			if (fullsize == null) {
				throw new FileNotFoundException();
			}
			thumbnail = resize(fullsize, size);
			this.mXmppConnectionService.getBitmapCache().put(message.getUuid(),
					thumbnail);
		}
		return thumbnail;
	}

	public Uri getTakePhotoUri() {
		StringBuilder pathBuilder = new StringBuilder();
		pathBuilder.append(Environment
				.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM));
		pathBuilder.append('/');
		pathBuilder.append("Camera");
		pathBuilder.append('/');
		pathBuilder.append("IMG_" + this.imageDateFormat.format(new Date())
				+ ".jpg");
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

	public boolean isAvatarCached(Avatar avatar) {
		File file = new File(getAvatarPath(avatar.getFilename()));
		return file.exists();
	}

	public boolean save(Avatar avatar) {
		if (isAvatarCached(avatar)) {
			return true;
		}
		String filename = getAvatarPath(avatar.getFilename());
		File file = new File(filename + ".tmp");
		file.getParentFile().mkdirs();
		try {
			file.createNewFile();
			FileOutputStream mFileOutputStream = new FileOutputStream(file);
			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			digest.reset();
			DigestOutputStream mDigestOutputStream = new DigestOutputStream(
					mFileOutputStream, digest);
			mDigestOutputStream.write(avatar.getImageAsBytes());
			mDigestOutputStream.flush();
			mDigestOutputStream.close();
			avatar.size = file.length();
			String sha1sum = CryptoHelper.bytesToHex(digest.digest());
			if (sha1sum.equals(avatar.sha1sum)) {
				file.renameTo(new File(filename));
				return true;
			} else {
				Log.d(Config.LOGTAG, "sha1sum mismatch for " + avatar.owner);
				file.delete();
				return false;
			}
		} catch (FileNotFoundException e) {
			return false;
		} catch (IOException e) {
			return false;
		} catch (NoSuchAlgorithmException e) {
			return false;
		}
	}

	public String getAvatarPath(String avatar) {
		return mXmppConnectionService.getFilesDir().getAbsolutePath()
				+ "/avatars/" + avatar;
	}

	public Uri getAvatarUri(String avatar) {
		return Uri.parse("file:" + getAvatarPath(avatar));
	}

	public Bitmap cropCenterSquare(Uri image, int size) {
		if (image == null) {
			return null;
		}
		try {
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inSampleSize = calcSampleSize(image, size);
			InputStream is = mXmppConnectionService.getContentResolver().openInputStream(image);
			Bitmap input = BitmapFactory.decodeStream(is, null, options);
			if (input == null) {
				return null;
			} else {
				int rotation = getRotation(image);
				if (rotation > 0) {
					input = rotate(input, rotation);
				}
				return cropCenterSquare(input, size);
			}
		} catch (FileNotFoundException e) {
			return null;
		}
	}

	public Bitmap cropCenter(Uri image, int newHeight, int newWidth) {
		if (image == null) {
			return null;
		}
		try {
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inSampleSize = calcSampleSize(image,Math.max(newHeight, newWidth));
			InputStream is = mXmppConnectionService.getContentResolver().openInputStream(image);
			Bitmap source = BitmapFactory.decodeStream(is, null, options);

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
			Bitmap dest = Bitmap.createBitmap(newWidth, newHeight, source.getConfig());
			Canvas canvas = new Canvas(dest);
			canvas.drawBitmap(source, null, targetRect, null);
			return dest;
		} catch (FileNotFoundException e) {
			return null;
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

		Bitmap output = Bitmap.createBitmap(size, size, input.getConfig());
		Canvas canvas = new Canvas(output);
		canvas.drawBitmap(input, null, target, null);
		return output;
	}

	private int calcSampleSize(Uri image, int size) throws FileNotFoundException {
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeStream(mXmppConnectionService.getContentResolver().openInputStream(image), null, options);
		return calcSampleSize(options, size);
	}

	private int calcSampleSize(File image, int size) {
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(image.getAbsolutePath(), options);
		return calcSampleSize(options, size);
	}

	private int calcSampleSize(BitmapFactory.Options options, int size) {
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
			int imageHeight = options.outHeight;
			int imageWidth = options.outWidth;
			if (url == null) {
				message.setBody(Long.toString(file.getSize()) + '|' + imageWidth + '|' + imageHeight);
			} else {
				message.setBody(url.toString()+"|"+Long.toString(file.getSize()) + '|' + imageWidth + '|' + imageHeight);
			}
		} else {
			message.setBody(Long.toString(file.getSize()));
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
}
