package de.gultsch.chat.utils;

import java.text.SimpleDateFormat;
import java.util.Date;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.DisplayMetrics;

public class Beautifier {
	public static String readableTimeDifference(long time) {
		if (time == 0) {
			return "just now";
		}
		Date date = new Date(time);
		long difference = (System.currentTimeMillis() - time) / 1000;
		if (difference < 60) {
			return "just now";
		} else if (difference < 60 * 10) {
			return difference / 60 + " min ago";
		} else if (difference < 60 * 60 * 24) {
			SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
			return sdf.format(date);
		} else {
			SimpleDateFormat sdf = new SimpleDateFormat("M/D");
			return sdf.format(date);
		}
	}

	public static Bitmap getUnknownContactPicture(String name, int size) {
		String firstLetter = name.substring(0, 1).toUpperCase();
		String centerLetter = name.substring(name.length() / 2,
				(name.length() / 2) + 1);

		int holoColors[] = { 0xFF1da9da, 0xFFb368d9, 0xFF83b600, 0xFFffa713,
				0xFFe92727 };

		int color = holoColors[centerLetter.charAt(0) % holoColors.length];

		Bitmap bitmap = Bitmap
				.createBitmap(size, size, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(bitmap);

		bitmap.eraseColor(color);

		Paint paint = new Paint();
		paint.setColor(0xffe5e5e5);
		paint.setTextSize((float) (size * 0.9));
		paint.setAntiAlias(true);
		Rect rect = new Rect();
		paint.getTextBounds(firstLetter, 0, 1, rect);
		float width = paint.measureText(firstLetter);
		canvas.drawText(firstLetter, (size / 2) - (width / 2), (size / 2)
				+ (rect.height() / 2), paint);

		return bitmap;
	}
}
