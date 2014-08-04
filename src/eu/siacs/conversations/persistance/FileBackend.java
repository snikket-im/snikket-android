package eu.siacs.conversations.persistance;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.Base64;
import android.util.Base64OutputStream;
import android.util.Log;
import android.util.LruCache;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.xmpp.jingle.JingleFile;
import eu.siacs.conversations.xmpp.pep.Avatar;

public class FileBackend {

	private static int IMAGE_SIZE = 1920;

	private Context context;
	private LruCache<String, Bitmap> thumbnailCache;

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

	public JingleFile getJingleFile(Message message) {
		return getJingleFile(message, true);
	}

	public JingleFile getJingleFile(Message message, boolean decrypted) {
		Conversation conversation = message.getConversation();
		String prefix = context.getFilesDir().getAbsolutePath();
		String path = prefix + "/" + conversation.getAccount().getJid() + "/"
				+ conversation.getContactJid();
		String filename;
		if ((decrypted) || (message.getEncryption() == Message.ENCRYPTION_NONE)) {
			filename = message.getUuid() + ".webp";
		} else {
			if (message.getEncryption() == Message.ENCRYPTION_OTR) {
				filename = message.getUuid() + ".webp";
			} else {
				filename = message.getUuid() + ".webp.pgp";
			}
		}
		return new JingleFile(path + "/" + filename);
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

	public JingleFile copyImageToPrivateStorage(Message message, Uri image)
			throws ImageCopyException {
		return this.copyImageToPrivateStorage(message, image, 0);
	}

	private JingleFile copyImageToPrivateStorage(Message message, Uri image,
			int sampleSize) throws ImageCopyException {
		try {
			InputStream is;
			if (image != null) {
				is = context.getContentResolver().openInputStream(image);
			} else {
				is = new FileInputStream(getIncomingFile());
				image = getIncomingUri();
			}
			JingleFile file = getJingleFile(message);
			file.getParentFile().mkdirs();
			file.createNewFile();
			Bitmap originalBitmap;
			BitmapFactory.Options options = new BitmapFactory.Options();
			int inSampleSize = (int) Math.pow(2, sampleSize);
			Log.d("xmppService", "reading bitmap with sample size "
					+ inSampleSize);
			options.inSampleSize = inSampleSize;
			originalBitmap = BitmapFactory.decodeStream(is, null, options);
			is.close();
			if (originalBitmap == null) {
				throw new ImageCopyException(R.string.error_not_an_image_file);
			}
			if (image == null) {
				getIncomingFile().delete();
			}
			Bitmap scalledBitmap = resize(originalBitmap, IMAGE_SIZE);
			originalBitmap = null;
			ExifInterface exif = new ExifInterface(image.toString());
			if (exif.getAttribute(ExifInterface.TAG_ORIENTATION)
					.equalsIgnoreCase("6")) {
				scalledBitmap = rotate(scalledBitmap, 90);
			} else if (exif.getAttribute(ExifInterface.TAG_ORIENTATION)
					.equalsIgnoreCase("8")) {
				scalledBitmap = rotate(scalledBitmap, 270);
			} else if (exif.getAttribute(ExifInterface.TAG_ORIENTATION)
					.equalsIgnoreCase("3")) {
				scalledBitmap = rotate(scalledBitmap, 180);
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
			message.setBody("" + size + "," + width + "," + height);
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

	public Bitmap getImageFromMessage(Message message) {
		return BitmapFactory.decodeFile(getJingleFile(message)
				.getAbsolutePath());
	}

	public Bitmap getThumbnail(Message message, int size, boolean cacheOnly)
			throws FileNotFoundException {
		Bitmap thumbnail = thumbnailCache.get(message.getUuid());
		if ((thumbnail == null) && (!cacheOnly)) {
			Bitmap fullsize = BitmapFactory.decodeFile(getJingleFile(message)
					.getAbsolutePath());
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
			Log.d("xmppService",
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

	public File getIncomingFile() {
		return new File(context.getFilesDir().getAbsolutePath() + "/incoming");
	}

	public Uri getIncomingUri() {
		return Uri.parse(context.getFilesDir().getAbsolutePath() + "/incoming");
	}
	
	public Avatar getPepAvatar(Uri image, int size, Bitmap.CompressFormat format) {
		try {
			Avatar avatar = new Avatar();
			Bitmap bm = cropCenterSquare(image, size);
			ByteArrayOutputStream mByteArrayOutputStream = new ByteArrayOutputStream();
			Base64OutputStream mBase64OutputSttream = new Base64OutputStream(mByteArrayOutputStream, Base64.DEFAULT);
			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			DigestOutputStream mDigestOutputStream = new DigestOutputStream(mBase64OutputSttream, digest);
			bm.compress(format, 75, mDigestOutputStream);
			avatar.sha1sum = CryptoHelper.bytesToHex(digest.digest());
			avatar.image = new String(mByteArrayOutputStream.toByteArray());
			return avatar;
		} catch (NoSuchAlgorithmException e) {
			return null;
		}
	}
	
	public void save(Avatar avatar) {
		String path = context.getFilesDir().getAbsolutePath() + "/avatars/";
		File file = new File(path+"/"+avatar.getFilename());
		file.getParentFile().mkdirs();
		Log.d("xmppService",file.getAbsolutePath());
		try {
			file.createNewFile();
			FileOutputStream mFileOutputStream = new FileOutputStream(file);
			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			DigestOutputStream mDigestOutputStream = new DigestOutputStream(mFileOutputStream, digest);
			mDigestOutputStream.write(avatar.getImageAsBytes());
			mDigestOutputStream.flush();
			mDigestOutputStream.close();
			avatar.size = file.length();
		} catch (FileNotFoundException e) {
			
		} catch (IOException e) {
			Log.d("xmppService",e.getMessage());
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public Bitmap cropCenterSquare(Uri image, int size) {
		try {
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inSampleSize = calcSampleSize(image, size);
			InputStream is = context.getContentResolver()
					.openInputStream(image);
			Bitmap input = BitmapFactory.decodeStream(is, null, options);
			int w = input.getWidth();
			int h = input.getHeight();

			float scale = Math.max((float) size / h, (float) size / w);

			float outWidth = scale * w;
			float outHeight = scale * h;
			float left = (size - outWidth) / 2;
			float top = (size - outHeight) / 2;
			RectF target = new RectF(left, top, left + outWidth, top
					+ outHeight);

			Bitmap output = Bitmap.createBitmap(size, size, input.getConfig());
			Canvas canvas = new Canvas(output);
			canvas.drawBitmap(input, null, target, null);
			return output;
		} catch (FileNotFoundException e) {
			return null;
		}
	}

	private int calcSampleSize(Uri image, int size)
			throws FileNotFoundException {
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeStream(context.getContentResolver()
				.openInputStream(image), null, options);
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
}
