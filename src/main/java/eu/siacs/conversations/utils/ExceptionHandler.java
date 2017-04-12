package eu.siacs.conversations.utils;

import android.content.Context;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.Thread.UncaughtExceptionHandler;

public class ExceptionHandler implements UncaughtExceptionHandler {

	private UncaughtExceptionHandler defaultHandler;
	private Context context;

	public ExceptionHandler(Context context) {
		this.context = context;
		this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
	}

	@Override
	public void uncaughtException(Thread thread, Throwable ex) {
		Writer result = new StringWriter();
		PrintWriter printWriter = new PrintWriter(result);
		ex.printStackTrace(printWriter);
		String stacktrace = result.toString();
		printWriter.close();
		ExceptionHelper.writeToStacktraceFile(context, stacktrace);
		this.defaultHandler.uncaughtException(thread, ex);
	}

}
