package eu.siacs.conversations.http;

import android.content.Context;
import androidx.annotation.NonNull;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import eu.siacs.conversations.AppSettings;
import eu.siacs.conversations.entities.Account;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class ServiceOutageStatus {

    private static final Collection<Account.State> SERVICE_OUTAGE_STATE =
            Arrays.asList(
                    Account.State.CONNECTION_TIMEOUT,
                    Account.State.SERVER_NOT_FOUND,
                    Account.State.STREAM_OPENING_ERROR);

    private final boolean planned;
    private final Instant beginning;

    @SerializedName("expected_end")
    private final Instant expectedEnd;

    private final Map<String, String> message;

    public ServiceOutageStatus(
            final boolean planned,
            final Instant beginning,
            final Instant expectedEnd,
            final Map<String, String> message) {
        this.planned = planned;
        this.beginning = beginning;
        this.expectedEnd = expectedEnd;
        this.message = message;
    }

    public boolean isNow() {
        final var now = Instant.now();
        final var hasDefault = this.message != null && this.message.containsKey("default");
        return hasDefault
                && this.beginning != null
                && this.expectedEnd != null
                && this.beginning.isBefore(now)
                && this.expectedEnd.isAfter(now);
    }

    public static ListenableFuture<ServiceOutageStatus> fetch(
            final Context context, final HttpUrl url) {
        final var appSettings = new AppSettings(context);
        final var builder = HttpConnectionManager.okHttpClient(context).newBuilder();
        if (appSettings.isUseTor()) {
            builder.proxy(HttpConnectionManager.getProxy());
        }

        var client = builder.build();

        final SettableFuture<ServiceOutageStatus> future = SettableFuture.create();

        var request = new Request.Builder().url(url).build();

        client.newCall(request)
                .enqueue(
                        new Callback() {
                            @Override
                            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                                future.setException(e);
                            }

                            @Override
                            public void onResponse(@NonNull Call call, @NonNull Response response) {
                                try (final ResponseBody body = response.body()) {
                                    if (!response.isSuccessful() || body == null) {
                                        future.setException(
                                                new IOException(
                                                        "unexpected server response ("
                                                                + response.code()
                                                                + ")"));
                                        return;
                                    }
                                    var gson =
                                            new GsonBuilder()
                                                    .registerTypeAdapter(
                                                            Instant.class,
                                                            new InstantDeserializer())
                                                    .create();
                                    future.set(
                                            gson.fromJson(
                                                    body.string(), ServiceOutageStatus.class));
                                } catch (final IOException | JsonSyntaxException e) {
                                    future.setException(e);
                                }
                            }
                        });

        return future;
    }

    public static boolean isPossibleOutage(final Account.State state) {
        return SERVICE_OUTAGE_STATE.contains(state);
    }

    @NonNull
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("planned", planned)
                .add("beginning", beginning)
                .add("expectedEnd", expectedEnd)
                .add("message", message)
                .toString();
    }

    public boolean isPlanned() {
        return this.planned;
    }

    public long getExpectedEnd() {
        if (this.expectedEnd == null) {
            return 0L;
        }
        return this.expectedEnd.toEpochMilli();
    }

    public String getMessage() {
        final var translated = this.message.get(Locale.getDefault().getLanguage());
        if (Strings.isNullOrEmpty(translated)) {
            return this.message.get("default");
        }
        return translated;
    }

    private static class InstantDeserializer implements JsonDeserializer<Instant> {
        @Override
        public Instant deserialize(
                JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            return Instant.parse(json.getAsString());
        }
    }
}
