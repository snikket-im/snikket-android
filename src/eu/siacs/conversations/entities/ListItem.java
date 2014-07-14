package eu.siacs.conversations.entities;

import android.content.Context;
import android.graphics.Bitmap;

public interface ListItem extends Comparable<ListItem> {
	public String getDisplayName();
	public String getJid();
	public Bitmap getImage(int dpSize, Context context);
}
