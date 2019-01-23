package eu.siacs.conversations.utils;

import android.Manifest;
import android.content.pm.PackageManager;

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
}
