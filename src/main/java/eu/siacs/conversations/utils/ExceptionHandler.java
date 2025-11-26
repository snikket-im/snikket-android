package eu.siacs.conversations.utils;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

import eu.siacs.conversations.BuildConfig;
import eu.siacs.conversations.services.NotificationService;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.Thread.UncaughtExceptionHandler;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ExceptionHandler implements UncaughtExceptionHandler {

    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ENGLISH);

    private final UncaughtExceptionHandler defaultHandler;
    private final Context context;

    ExceptionHandler(final Context context) {
        this.context = context;
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    @Override
    public void uncaughtException(@NonNull Thread thread, final Throwable throwable) {
        NotificationService.cancelIncomingCallNotification(context);
        final String stacktrace;
        try (final StringWriter stringWriter = new StringWriter();
                final PrintWriter printWriter = new PrintWriter(stringWriter)) {
            throwable.printStackTrace(printWriter);
            stacktrace = stringWriter.toString();
        } catch (final IOException e) {
            return;
        }
        final List<String> report =
                ImmutableList.of(
                        String.format(
                                "Version: %s %s", BuildConfig.APP_NAME, BuildConfig.VERSION_NAME),
                        String.format("Manufacturer: %s", Strings.nullToEmpty(Build.MANUFACTURER)),
                        String.format("Device: %s", Strings.nullToEmpty(Build.DEVICE)),
                        String.format("Timestamp: %s", DATE_FORMAT.format(new Date())),
                        stacktrace);
        ExceptionHelper.writeToStacktraceFile(context, Joiner.on("\n").join(report));
        this.defaultHandler.uncaughtException(thread, throwable);
    }
}
