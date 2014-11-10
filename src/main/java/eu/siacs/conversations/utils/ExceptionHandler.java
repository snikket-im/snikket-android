package eu.siacs.conversations.utils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.Thread.UncaughtExceptionHandler;

import android.content.Context;

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
		try {
			OutputStream os = context.openFileOutput("stacktrace.txt",
					Context.MODE_PRIVATE);
			os.write(stacktrace.getBytes());
			os.flush();
			os.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		this.defaultHandler.uncaughtException(thread, ex);
	}

}
