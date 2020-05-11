package eu.siacs.conversations.utils;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v4.app.ActivityCompat;

import com.google.common.collect.ImmutableList;

import java.util.List;

public class PermissionUtils {

    public static boolean allGranted(int[] grantResults) {
        for (int grantResult : grantResults) {
            if (grantResult != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public static boolean writeGranted(int[] grantResults, String[] permission) {
        for (int i = 0; i < grantResults.length; ++i) {
            if (Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permission[i])) {
                return grantResults[i] == PackageManager.PERMISSION_GRANTED;
            }
        }
        return false;
    }

    public static String getFirstDenied(int[] grantResults, String[] permissions) {
        for (int i = 0; i < grantResults.length; ++i) {
            if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                return permissions[i];
            }
        }
        return null;
    }

    public static boolean hasPermission(final Activity activity, final List<String> permissions, final int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            final ImmutableList.Builder<String> missingPermissions = new ImmutableList.Builder<>();
            for (final String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                    missingPermissions.add(permission);
                }
            }
            final ImmutableList<String> missing = missingPermissions.build();
            if (missing.size() == 0) {
                return true;
            }
            ActivityCompat.requestPermissions(activity, missing.toArray(new String[0]), requestCode);
            return false;
        } else {
            return true;
        }
    }
}
