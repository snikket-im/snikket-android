package de.gultsch.chat.utils;

import java.text.SimpleDateFormat;
import java.util.Date;

public class Beautifier {
	public static String readableTimeDifference(long time) {
		if (time==0) {
			return "just now";
		}
		Date date = new Date(time);
		long difference = (System.currentTimeMillis() - time) / 1000;
		if (difference<60) {
			return "just now";
		} else if (difference<60*10) {
			return difference / 60 + " min ago";
		} else if (difference<60*60*24) {
			SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
			return sdf.format(date);
		} else {
			SimpleDateFormat sdf = new SimpleDateFormat("M/D");
			return sdf.format(date);
		}
	}
}
