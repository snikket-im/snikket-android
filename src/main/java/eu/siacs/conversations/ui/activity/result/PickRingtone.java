package eu.siacs.conversations.ui.activity.result;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class PickRingtone extends ActivityResultContract<Uri, Uri> {

    private static final Uri NONE = Uri.parse("about:blank");

    private final int ringToneType;

    public PickRingtone(final int ringToneType) {
        this.ringToneType = ringToneType;
    }

    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context, final Uri existing) {
        final Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, ringToneType);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
        if (existing != null) {
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing);
        }
        return intent;
    }

    @Override
    public Uri parseResult(int resultCode, @Nullable Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null) {
            return null;
        }
        final Uri pickedUri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
        return pickedUri == null ? NONE : pickedUri;
    }

    public static Uri noneToNull(final Uri uri) {
        return uri == null || NONE.equals(uri) ? null : uri;
    }
}
