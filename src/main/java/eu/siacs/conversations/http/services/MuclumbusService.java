package eu.siacs.conversations.http.services;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import eu.siacs.conversations.services.AvatarService;
import eu.siacs.conversations.utils.UIHelper;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;
import rocks.xmpp.addr.Jid;

public interface MuclumbusService {

    @GET("/api/1.0/rooms/unsafe")
    Call<Rooms> getRooms(@Query("p") int page);

    @POST("/api/1.0/search")
    Call<SearchResult> search(@Body SearchRequest searchRequest);

    class Rooms {
        int page;
        int total;
        int pages;
        public List<Room> items;
    }

    class Room implements AvatarService.Avatarable {

        public String address;
        public int nusers;
        public boolean is_open;
        public String anonymity_mode;
        public String name;
        public String description;
        public String language;

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public Jid getRoom() {
            try {
                return Jid.of(address);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        @Override
        public int getAvatarBackgroundColor() {
            Jid room = getRoom();
            return UIHelper.getColorForName(room != null ? room.asBareJid().toEscapedString() : name);
        }
    }

    class SearchRequest {

        public Set<String> keywords;

        public SearchRequest(String keyword) {
            this.keywords = Collections.singleton(keyword);
        }
    }

    class SearchResult {

        public Result result;

    }

    class Result {

        public List<Room> items;

    }

}
