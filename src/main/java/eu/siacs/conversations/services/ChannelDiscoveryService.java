package eu.siacs.conversations.services;

import android.util.Log;
import androidx.annotation.NonNull;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Ordering;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Room;
import eu.siacs.conversations.http.HttpConnectionManager;
import eu.siacs.conversations.http.services.MuclumbusService;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.manager.MultiUserChatManager;
import im.conversations.android.xmpp.model.disco.info.InfoQuery;
import im.conversations.android.xmpp.model.disco.items.Item;
import im.conversations.android.xmpp.model.disco.items.ItemsQuery;
import im.conversations.android.xmpp.model.stanza.Iq;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ChannelDiscoveryService {

    private final XmppConnectionService service;

    private MuclumbusService muclumbusService;

    private final Cache<String, List<Room>> cache;

    ChannelDiscoveryService(XmppConnectionService service) {
        this.service = service;
        this.cache = CacheBuilder.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).build();
    }

    void initializeMuclumbusService() {
        if (Strings.isNullOrEmpty(Config.CHANNEL_DISCOVERY)) {
            this.muclumbusService = null;
            return;
        }
        final OkHttpClient.Builder builder =
                HttpConnectionManager.okHttpClient(service).newBuilder();
        if (service.useTorToConnect()) {
            builder.proxy(HttpConnectionManager.getProxy());
        }
        final Retrofit retrofit =
                new Retrofit.Builder()
                        .client(builder.build())
                        .baseUrl(Config.CHANNEL_DISCOVERY)
                        .addConverterFactory(GsonConverterFactory.create())
                        .callbackExecutor(Executors.newSingleThreadExecutor())
                        .build();
        this.muclumbusService = retrofit.create(MuclumbusService.class);
    }

    void cleanCache() {
        cache.invalidateAll();
    }

    void discover(
            @NonNull final String query,
            Method method,
            OnChannelSearchResultsFound onChannelSearchResultsFound) {
        final List<Room> result = cache.getIfPresent(key(method, query));
        if (result != null) {
            onChannelSearchResultsFound.onChannelSearchResultsFound(result);
            return;
        }
        if (method == Method.LOCAL_SERVER) {
            discoverChannelsLocalServers(query, onChannelSearchResultsFound);
        } else {
            if (query.isEmpty()) {
                discoverChannelsJabberNetwork(onChannelSearchResultsFound);
            } else {
                discoverChannelsJabberNetwork(query, onChannelSearchResultsFound);
            }
        }
    }

    private void discoverChannelsJabberNetwork(final OnChannelSearchResultsFound listener) {
        if (muclumbusService == null) {
            listener.onChannelSearchResultsFound(Collections.emptyList());
            return;
        }
        final Call<MuclumbusService.Rooms> call = muclumbusService.getRooms(1);
        call.enqueue(
                new Callback<MuclumbusService.Rooms>() {
                    @Override
                    public void onResponse(
                            @NonNull Call<MuclumbusService.Rooms> call,
                            @NonNull Response<MuclumbusService.Rooms> response) {
                        final MuclumbusService.Rooms body = response.body();
                        if (body == null) {
                            listener.onChannelSearchResultsFound(Collections.emptyList());
                            logError(response);
                            return;
                        }
                        cache.put(key(Method.JABBER_NETWORK, ""), body.items);
                        listener.onChannelSearchResultsFound(body.items);
                    }

                    @Override
                    public void onFailure(
                            @NonNull Call<MuclumbusService.Rooms> call,
                            @NonNull Throwable throwable) {
                        Log.d(
                                Config.LOGTAG,
                                "Unable to query muclumbus on " + Config.CHANNEL_DISCOVERY,
                                throwable);
                        listener.onChannelSearchResultsFound(Collections.emptyList());
                    }
                });
    }

    private void discoverChannelsJabberNetwork(
            final String query, final OnChannelSearchResultsFound listener) {
        if (muclumbusService == null) {
            listener.onChannelSearchResultsFound(Collections.emptyList());
            return;
        }
        final MuclumbusService.SearchRequest searchRequest =
                new MuclumbusService.SearchRequest(query);
        final Call<MuclumbusService.SearchResult> searchResultCall =
                muclumbusService.search(searchRequest);
        searchResultCall.enqueue(
                new Callback<MuclumbusService.SearchResult>() {
                    @Override
                    public void onResponse(
                            @NonNull Call<MuclumbusService.SearchResult> call,
                            @NonNull Response<MuclumbusService.SearchResult> response) {
                        final MuclumbusService.SearchResult body = response.body();
                        if (body == null) {
                            listener.onChannelSearchResultsFound(Collections.emptyList());
                            logError(response);
                            return;
                        }
                        cache.put(key(Method.JABBER_NETWORK, query), body.result.items);
                        listener.onChannelSearchResultsFound(body.result.items);
                    }

                    @Override
                    public void onFailure(
                            @NonNull Call<MuclumbusService.SearchResult> call,
                            @NonNull Throwable throwable) {
                        Log.d(
                                Config.LOGTAG,
                                "Unable to query muclumbus on " + Config.CHANNEL_DISCOVERY,
                                throwable);
                        listener.onChannelSearchResultsFound(Collections.emptyList());
                    }
                });
    }

    private void discoverChannelsLocalServers(
            final String query, final OnChannelSearchResultsFound listener) {
        final var localMucService = getLocalMucServices();
        Log.d(Config.LOGTAG, "checking with " + localMucService.size() + " muc services");
        if (localMucService.isEmpty()) {
            listener.onChannelSearchResultsFound(Collections.emptyList());
            return;
        }
        if (!query.isEmpty()) {
            final List<Room> cached = cache.getIfPresent(key(Method.LOCAL_SERVER, ""));
            if (cached != null) {
                final List<Room> results = copyMatching(cached, query);
                cache.put(key(Method.LOCAL_SERVER, query), results);
                listener.onChannelSearchResultsFound(results);
            }
        }
        final var roomsRoomsFuture =
                Futures.successfulAsList(
                        Collections2.transform(
                                localMucService.entrySet(),
                                e -> discoverRooms(e.getValue(), e.getKey())));
        final var roomsFuture =
                Futures.transform(
                        roomsRoomsFuture,
                        rooms -> {
                            final var builder = new ImmutableList.Builder<Room>();
                            for (final var inner : rooms) {
                                if (inner == null) {
                                    continue;
                                }
                                builder.addAll(inner);
                            }
                            return builder.build();
                        },
                        MoreExecutors.directExecutor());
        Futures.addCallback(
                roomsFuture,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(ImmutableList<Room> rooms) {
                        finishDiscoSearch(rooms, query, listener);
                    }

                    @Override
                    public void onFailure(@NonNull Throwable throwable) {
                        Log.d(Config.LOGTAG, "could not perform room search", throwable);
                    }
                },
                MoreExecutors.directExecutor());
    }

    private ListenableFuture<Collection<Room>> discoverRooms(
            final XmppConnection connection, final Jid server) {
        final var request = new Iq(Iq.Type.GET);
        request.addExtension(new ItemsQuery());
        request.setTo(server);
        final ListenableFuture<Collection<Item>> itemsFuture =
                Futures.transform(
                        connection.sendIqPacket(request),
                        iq -> {
                            final var itemsQuery = iq.getExtension(ItemsQuery.class);
                            if (itemsQuery == null) {
                                return Collections.emptyList();
                            }
                            final var items = itemsQuery.getExtensions(Item.class);
                            return Collections2.filter(items, i -> Objects.nonNull(i.getJid()));
                        },
                        MoreExecutors.directExecutor());
        final var roomsFutures =
                Futures.transformAsync(
                        itemsFuture,
                        items -> {
                            final var infoFutures =
                                    Collections2.transform(
                                            items, i -> discoverRoom(connection, i.getJid()));
                            return Futures.successfulAsList(infoFutures);
                        },
                        MoreExecutors.directExecutor());
        return Futures.transform(
                roomsFutures,
                rooms -> Collections2.filter(rooms, Objects::nonNull),
                MoreExecutors.directExecutor());
    }

    private ListenableFuture<Room> discoverRoom(final XmppConnection connection, final Jid room) {
        final var request = new Iq(Iq.Type.GET);
        request.addExtension(new InfoQuery());
        request.setTo(room);
        final var infoQueryResponseFuture = connection.sendIqPacket(request);
        return Futures.transform(
                infoQueryResponseFuture,
                result -> {
                    final var infoQuery = result.getExtension(InfoQuery.class);
                    if (infoQuery == null) {
                        return null;
                    }
                    return Room.of(room, infoQuery);
                },
                MoreExecutors.directExecutor());
    }

    private void finishDiscoSearch(
            final List<Room> rooms,
            final String query,
            final OnChannelSearchResultsFound listener) {
        Log.d(Config.LOGTAG, "finishDiscoSearch with " + rooms.size() + " rooms");
        final var sorted = Ordering.natural().sortedCopy(rooms);
        cache.put(key(Method.LOCAL_SERVER, ""), sorted);
        if (query.isEmpty()) {
            listener.onChannelSearchResultsFound(sorted);
        } else {
            List<Room> results = copyMatching(sorted, query);
            cache.put(key(Method.LOCAL_SERVER, query), results);
            listener.onChannelSearchResultsFound(sorted);
        }
    }

    private static List<Room> copyMatching(List<Room> haystack, String needle) {
        ArrayList<Room> result = new ArrayList<>();
        for (Room room : haystack) {
            if (room.contains(needle)) {
                result.add(room);
            }
        }
        return result;
    }

    private Map<Jid, XmppConnection> getLocalMucServices() {
        final ImmutableMap.Builder<Jid, XmppConnection> localMucServices =
                new ImmutableMap.Builder<>();
        for (final var account : service.getAccounts()) {
            final var connection = account.getXmppConnection();
            if (connection != null && account.isEnabled()) {
                for (final var mucService :
                        connection.getManager(MultiUserChatManager.class).getServices()) {
                    if (Jid.Invalid.isValid(mucService)) {
                        localMucServices.put(mucService, connection);
                    }
                }
            }
        }
        return localMucServices.buildKeepingLast();
    }

    private static String key(Method method, String query) {
        return String.format("%s\00%s", method, query);
    }

    private static void logError(final Response response) {
        final ResponseBody errorBody = response.errorBody();
        Log.d(Config.LOGTAG, "code from muclumbus=" + response.code());
        if (errorBody == null) {
            return;
        }
        try {
            Log.d(Config.LOGTAG, "error body=" + errorBody.string());
        } catch (IOException e) {
            // ignored
        }
    }

    public interface OnChannelSearchResultsFound {
        void onChannelSearchResultsFound(List<Room> results);
    }

    public enum Method {
        JABBER_NETWORK,
        LOCAL_SERVER
    }
}
