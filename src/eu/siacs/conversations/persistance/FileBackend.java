package eu.siacs.conversations.persistance;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;
import android.util.LruCache;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.xmpp.jingle.JingleFile;

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
			filename = message.getUuid() + ".webp.pgp";
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

	public JingleFile copyImageToPrivateStorage(Message message, Uri image)
			throws ImageCopyException {
		try {
			InputStream is;
			if (image != null) {
				is = context.getContentResolver().openInputStream(image);
			} else {
				is = new FileInputStream(getIncomingFile());
			}
			JingleFile file = getJingleFile(message);
			file.getParentFile().mkdirs();
			file.createNewFile();
			OutputStream os = new FileOutputStream(file);
			Bitmap originalBitmap = BitmapFactory.decodeStream(is);
			if (originalBitmap == null) {
				os.close();
				throw new ImageCopyException(R.string.error_not_an_image_file);
			}
			is.close();
			if (image == null) {
				getIncomingFile().delete();
			}
			Bitmap scalledBitmap = resize(originalBitmap, IMAGE_SIZE);
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
