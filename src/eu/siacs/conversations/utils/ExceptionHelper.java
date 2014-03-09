package eu.siacs.conversations.utils;

import android.content.Context;

public class ExceptionHelper {
	public static void init(Context context) {
		if(!(Thread.getDefaultUncaughtExceptionHandler() instanceof ExceptionHandler)) {
		    Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler(context));
		}
	}
}
