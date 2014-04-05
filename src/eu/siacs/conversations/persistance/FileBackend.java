package eu.siacs.conversations.persistance;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import eu.siacs.conversations.entities.Conversation;


public class FileBackend {
	
	private static int IMAGE_SIZE = 1920;
	
	private Context context;
	
	public FileBackend(Context context) {
		this.context = context;
	}
	
	
	public File copyImageToPrivateStorage(Conversation conversation, Uri image) {
		try {
			InputStream is = context.getContentResolver().openInputStream(image);
			String prefix =  context.getFilesDir().getAbsolutePath();
			String path = prefix+"/"+conversation.getAccount().getJid()+"/"+conversation.getContactJid();
			String filename =new BigInteger(""+System.currentTimeMillis()).toString(32) + ".webp";
			File file = new File(path+"/"+filename);
			file.getParentFile().mkdirs();
			file.createNewFile();
			OutputStream os = new FileOutputStream(file);
			Bitmap originalBitmap = BitmapFactory.decodeStream(is);
			is.close();
			int w = originalBitmap.getWidth();
			int h = originalBitmap.getHeight();
			boolean success;
			if (Math.max(w, h) > IMAGE_SIZE) {
				int scalledW;
				int scalledH;
				if (w<=h) {
					scalledW = (int) (w / ((double) h/IMAGE_SIZE));
					scalledH = IMAGE_SIZE;
				} else {
					scalledW = IMAGE_SIZE;
					scalledH = (int) (h / ((double) w/IMAGE_SIZE));
				}
				Bitmap scalledBitmap = Bitmap.createScaledBitmap(originalBitmap, scalledW,scalledH, true);
				success = scalledBitmap.compress(Bitmap.CompressFormat.WEBP, 75, os);
			} else {
				success = originalBitmap.compress(Bitmap.CompressFormat.WEBP, 75, os);
			}
			if (!success) {
				Log.d("xmppService","couldnt compress");
			}
			os.close();
			return file;
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return null;
	}
}
