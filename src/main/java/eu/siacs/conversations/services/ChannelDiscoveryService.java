package eu.siacs.conversations.services;

import android.util.Log;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.http.HttpConnectionManager;
import eu.siacs.conversations.http.services.MuclumbusService;
import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ChannelDiscoveryService {

    private final XmppConnectionService service;


    private MuclumbusService muclumbusService;

    private final Cache<String, List<MuclumbusService.Room>> cache;

    public ChannelDiscoveryService(XmppConnectionService service) {
        this.service = service;
        this.cache = CacheBuilder.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).build();
    }

    public void initializeMuclumbusService() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        if (service.useTorToConnect()) {
            try {
                builder.proxy(HttpConnectionManager.getProxy());
            } catch (IOException e) {
                throw new RuntimeException("Unable to use Tor proxy", e);
            }
        }
        Retrofit retrofit = new Retrofit.Builder()
                .client(builder.build())
                .baseUrl(Config.CHANNEL_DISCOVERY)
                .addConverterFactory(GsonConverterFactory.create())
                .callbackExecutor(Executors.newSingleThreadExecutor())
                .build();
        this.muclumbusService = retrofit.create(MuclumbusService.class);
    }

    public void discover(String query, OnChannelSearchResultsFound onChannelSearchResultsFound) {
        final boolean all = query == null || query.trim().isEmpty();
        Log.d(Config.LOGTAG, "discover channels. query=" + query);
        List<MuclumbusService.Room> result = cache.getIfPresent(all ? "" : query);
        if (result != null) {
            onChannelSearchResultsFound.onChannelSearchResultsFound(result);
            return;
        }
        if (all) {
            discoverChannels(onChannelSearchResultsFound);
        } else {
            discoverChannels(query, onChannelSearchResultsFound);
        }
    }

    private void discoverChannels(OnChannelSearchResultsFound listener) {
        Call<MuclumbusService.Rooms> call = muclumbusService.getRooms(1);
        try {
            call.enqueue(new Callback<MuclumbusService.Rooms>() {
                @Override
                public void onResponse(Call<MuclumbusService.Rooms> call, Response<MuclumbusService.Rooms> response) {
                    final MuclumbusService.Rooms body = response.body();
                    if (body == null) {
                        return;
                    }
                    cache.put("", body.items);
                    listener.onChannelSearchResultsFound(body.items);
                }

                @Override
                public void onFailure(Call<MuclumbusService.Rooms> call, Throwable throwable) {
                    Log.d(Config.LOGTAG, "Unable to query muclumbus on "+Config.CHANNEL_DISCOVERY, throwable);
                    listener.onChannelSearchResultsFound(Collections.emptyList());
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void discoverChannels(final String query, OnChannelSearchResultsFound listener) {
        Call<MuclumbusService.SearchResult> searchResultCall = muclumbusService.search(new MuclumbusService.SearchRequest(query));

        searchResultCall.enqueue(new Callback<MuclumbusService.SearchResult>() {
            @Override
            public void onResponse(Call<MuclumbusService.SearchResult> call, Response<MuclumbusService.SearchResult> response) {
                System.out.println(response.message());
                MuclumbusService.SearchResult body = response.body();
                if (body == null) {
                    return;
                }
                cache.put(query, body.result.items);
                listener.onChannelSearchResultsFound(body.result.items);
            }

            @Override
            public void onFailure(Call<MuclumbusService.SearchResult> call, Throwable throwable) {
                Log.d(Config.LOGTAG, "Unable to query muclumbus on "+Config.CHANNEL_DISCOVERY, throwable);
                listener.onChannelSearchResultsFound(Collections.emptyList());
            }
        });
    }

    public interface OnChannelSearchResultsFound {
        void onChannelSearchResultsFound(List<MuclumbusService.Room> results);
    }
}
