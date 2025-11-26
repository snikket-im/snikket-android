package eu.siacs.conversations.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.view.View;
import com.google.android.material.elevation.SurfaceColors;

public final class Activities {

    private Activities() {}

    public static void setStatusAndNavigationBarColors(final Activity activity, final View view) {
        setStatusAndNavigationBarColors(activity, view, false);
    }

    public static void setStatusAndNavigationBarColors(
            final Activity activity, final View view, final boolean raisedStatusBar) {
        final var isLightMode = isLightMode(activity);
        final var window = activity.getWindow();
        final var flags = view.getSystemUiVisibility();
        // an elevation of 4 matches the MaterialToolbar elevation
        if (raisedStatusBar) {
            window.setStatusBarColor(SurfaceColors.SURFACE_5.getColor(activity));
        } else {
            window.setStatusBarColor(SurfaceColors.SURFACE_0.getColor(activity));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.setNavigationBarColor(SurfaceColors.SURFACE_1.getColor(activity));
            if (isLightMode) {
                view.setSystemUiVisibility(
                        flags
                                | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
            }
        } else if (isLightMode) {
            view.setSystemUiVisibility(flags | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    private static boolean isLightMode(final Context context) {
        final int nightModeFlags =
                context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags != Configuration.UI_MODE_NIGHT_YES;
    }

    public static boolean isNightMode(final Context context) {
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }
}
