package eu.siacs.conversations.entities;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class Edited {

    private final String editedId;
    private final String serverMsgId;

    public Edited(String editedId, String serverMsgId) {
        this.editedId = editedId;
        this.serverMsgId = serverMsgId;
    }

    public static String toJson(List<Edited> edits) throws JSONException {
        JSONArray jsonArray = new JSONArray();
        for (Edited edited : edits) {
            jsonArray.put(edited.toJson());
        }
        return jsonArray.toString();
    }

    public static boolean wasPreviouslyEditedRemoteMsgId(List<Edited> editeds, String remoteMsgId) {
        for (Edited edited : editeds) {
            if (edited.editedId != null && edited.editedId.equals(remoteMsgId)) {
                return true;
            }
        }
        return false;
    }

    public static boolean wasPreviouslyEditedServerMsgId(List<Edited> editeds, String serverMsgId) {
        for (Edited edited : editeds) {
            if (edited.serverMsgId != null && edited.serverMsgId.equals(serverMsgId)) {
                return true;
            }
        }
        return false;
    }

    public static Edited fromJson(JSONObject jsonObject) throws JSONException {
        String edited = jsonObject.getString("edited_id");
        String serverMsgId = jsonObject.getString("server_msg_id");
        return new Edited(edited, serverMsgId);
    }

    public static List<Edited> fromJson(String input) {
        ArrayList<Edited> list = new ArrayList<>();
        if (input == null) {
            return list;
        }
        try {
            JSONArray jsonArray = new JSONArray(input);
            for (int i = 0; i < jsonArray.length(); ++i) {
                list.add(fromJson(jsonArray.getJSONObject(i)));
            }

        } catch (JSONException e) {
            list = new ArrayList<>();
            list.add(new Edited(input, null));
        }
        return list;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("edited_id", editedId);
        jsonObject.put("server_msg_id", serverMsgId);
        return jsonObject;
    }

    public String getEditedId() {
        return editedId;
    }
}
