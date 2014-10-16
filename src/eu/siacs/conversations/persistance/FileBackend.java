package eu.siacs.conversations.persistance;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Base64OutputStream;
import android.util.Log;
import android.util.LruCache;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xmpp.pep.Avatar;

public class FileBackend {

	private static int IMAGE_SIZE = 1920;

	private Context context;
	private LruCache<String, Bitmap> thumbnailCache;

	private SimpleDateFormat imageDateFormat = new SimpleDateFormat(
			"yyyyMMdd_HHmmssSSS", Locale.US);

	public FileBackend(Context context) {
		this.context = context;
		int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
		int cacheSize = maxMemory / 8;
		thumbnailCache = new LruCache<String, Bitmap>(cacheSize) {
			@Override
			protected int sizeOf(String key, Bitmap bitmap) {
				return bitmap.getByteCount() / 1024;
			}
		};

	}

	public LruCache<String, Bitmap> getThumbnailCache() {
		return thumbnailCache;
	}

	public DownloadableFile getFile(Message message) {
		return getFile(message, true);
	}

	public DownloadableFile getFile(Message message,
			boolean decrypted) {
		StringBuilder filename = new StringBuilder();
		filename.append(getConversationsDirectory());
		filename.append(message.getUuid());
		if ((decrypted) || (message.getEncryption() == Message.ENCRYPTION_NONE)) {
			filename.append(".webp");
		} else {
			if (message.getEncryption() == Message.ENCRYPTION_OTR) {
				filename.append(".webp");
			} else {
				filename.append(".webp.pgp");
			}
		}
		return new DownloadableFile(filename.toString());
	}
	
	public static String getConversationsDirectory() {
		return Environment.getExternalStoragePublicDirectory(
				Environment.DIRECTORY_PICTURES).getAbsolutePath()+"/Conversations/";
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

	public DownloadableFile copyImageToPrivateStorage(Message message, Uri image)
			throws ImageCopyException {
		return this.copyImageToPrivateStorage(message, image, 0);
	}

	private DownloadableFile copyImageToPrivateStorage(Message message,
			Uri image, int sampleSize) throws ImageCopyException {
		try {
			InputStream is = context.getContentResolver()
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
				throw new ImageCopyException(R.string.error_not_an_image_file);
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
				throw new ImageCopyException(R.string.error_compressing_image);
			}
			os.flush();
			os.close();
			long size = file.getSize();
			int width = scalledBitmap.getWidth();
			int height = scalledBitmap.getHeight();
			message.setBody(Long.toString(size) + ',' + width + ',' + height);
			return file;
		} catch (FileNotFoundException e) {
			throw new ImageCopyException(R.string.error_file_not_found);
		} catch (IOException e) {
			throw new ImageCopyException(R.string.error_io_exception);
		} catch (SecurityException e) {
			throw new ImageCopyException(
					R.string.error_security_exception_during_image_copy);
		} catch (OutOfMemoryError e) {
			++sampleSize;
			if (sampleSize <= 3) {
				return copyImageToPrivateStorage(message, image, sampleSize);
			} else {
				throw new ImageCopyException(R.string.error_out_of_memory);
			}
		}
	}

	private int getRotation(Uri image) {
		if ("content".equals(image.getScheme())) {
			try {
				Cursor cursor = context
						.getContentResolver()
						.query(image,
								new String[] { MediaStore.Images.ImageColumns.ORIENTATION },
								null, null, null);
				if (cursor.getCount() != 1) {
					return -1;
				}
				cursor.moveToFirst();
				return cursor.getInt(0);
			} catch (IllegalArgumentException e) {
				return -1;
			}
		} else {
			ExifInterface exif;
			try {
				exif = new ExifInterface(image.toString());
				if (exif.getAttribute(ExifInterface.TAG_ORIENTATION)
						.equalsIgnoreCase("6")) {
					return 90;
				} else if (exif.getAttribute(ExifInterface.TAG_ORIENTATION)
						.equalsIgnoreCase("8")) {
					return 270;
				} else if (exif.getAttribute(ExifInterface.TAG_ORIENTATION)
						.equalsIgnoreCase("3")) {
					return 180;
				} else {
					return 0;
				}
			} catch (IOException e) {
				return -1;
			}
		}
	}

	public Bitmap getImageFromMessage(Message message) {
		return BitmapFactory.decodeFile(getFile(message)
				.getAbsolutePath());
	}

	public Bitmap getThumbnail(Message message, int size, boolean cacheOnly)
			throws FileNotFoundException {
		Bitmap thumbnail = thumbnailCache.get(message.getUuid());
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
			this.thumbnailCache.put(message.getUuid(), thumbnail);
		}
		return thumbnail;
	}

	public void removeFiles(Conversation conversation) {
		String prefix = context.getFilesDir().getAbsolutePath();
		String path = prefix + "/" + conversation.getAccount().getJid() + "/"
				+ conversation.getContactJid();
		File file = new File(path);
		try {
			this.deleteFile(file);
		} catch (IOException e) {
			Log.d(Config.LOGTAG,
					"error deleting file: " + file.getAbsolutePath());
		}
	}

	private void deleteFile(File f) throws IOException {
		if (f.isDirectory()) {
			for (File c : f.listFiles())
				deleteFile(c);
		}
		f.delete();
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
		File file = new File(getAvatarPath(context, avatar.getFilename()));
		return file.exists();
	}

	public boolean save(Avatar avatar) {
		if (isAvatarCached(avatar)) {
			return true;
		}
		String filename = getAvatarPath(context, avatar.getFilename());
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

	public static String getAvatarPath(Context context, String avatar) {
		return context.getFilesDir().getAbsolutePath() + "/avatars/" + avatar;
	}

	public Bitmap cropCenterSquare(Uri image, int size) {
		try {
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inSampleSize = calcSampleSize(image, size);
			InputStream is = context.getContentResolver()
					.openInputStream(image);
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

	public static Bitmap cropCenterSquare(Bitmap input, int size) {
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

	private int calcSampleSize(Uri image, int size)
			throws FileNotFoundException {
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeStream(context.getContentResolver()
				.openInputStream(image), null, options);
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

	public class ImageCopyException extends Exception {
		private static final long serialVersionUID = -1010013599132881427L;
		private int resId;

		public ImageCopyException(int resId) {
			this.resId = resId;
		}

		public int getResId() {
			return resId;
		}
	}

	public static Bitmap getAvatar(String avatar, int size, Context context) {
		Bitmap bm = BitmapFactory.decodeFile(FileBackend.getAvatarPath(context,
				avatar));
		if (bm == null) {
			return null;
		}
		return cropCenterSquare(bm, UIHelper.getRealPx(size, context));
	}

	public boolean isFileAvailable(Message message) {
		return getFile(message).exists();
	}
}
