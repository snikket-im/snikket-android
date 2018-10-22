package eu.siacs.conversations.ui.util;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.support.annotation.StringRes;

import eu.siacs.conversations.R;
import eu.siacs.conversations.services.QuickConversationsService;

public class ApiErrorDialogHelper {

    public static Dialog create(Context context, int code) {
        @StringRes final int res;
        switch (code) {
            case QuickConversationsService.API_ERROR_AIRPLANE_MODE:
                res = R.string.no_network_connection;
                break;
            case QuickConversationsService.API_ERROR_OTHER:
                res = R.string.unknown_api_error_network;
                break;
            case QuickConversationsService.API_ERROR_CONNECT:
                res = R.string.unable_to_connect_to_server;
                break;
            case QuickConversationsService.API_ERROR_SSL_HANDSHAKE:
                res = R.string.unable_to_establish_secure_connection;
                break;
            case QuickConversationsService.API_ERROR_UNKNOWN_HOST:
                res = R.string.unable_to_find_server;
                break;
            case 400:
                res = R.string.invalid_user_input;
                break;
            case 502:
            case 503:
            case 504:
                res = R.string.temporarily_unavailable;
                break;
            default:
                res = R.string.unknown_api_error_response;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(res);
        builder.setPositiveButton(R.string.ok, null);
        return builder.create();
    }
}
