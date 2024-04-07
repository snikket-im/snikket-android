package eu.siacs.conversations.ui;

import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import eu.siacs.conversations.Conversations;
import eu.siacs.conversations.ui.util.SettingsUtils;

public abstract class BaseActivity extends AppCompatActivity {
    private Boolean isDynamicColors;

    @Override
    public void onStart() {
        super.onStart();
        final int desiredNightMode = Conversations.getDesiredNightMode(this);
        if (setDesiredNightMode(desiredNightMode)) {
            return;
        }
        final boolean isDynamicColors = Conversations.isDynamicColorsDesired(this);
        setDynamicColors(isDynamicColors);
    }

    @Override
    protected void onResume(){
        super.onResume();
        SettingsUtils.applyScreenshotSetting(this);
    }

    public void setDynamicColors(final boolean isDynamicColors) {
        if (this.isDynamicColors == null) {
            this.isDynamicColors = isDynamicColors;
        } else {
            if (this.isDynamicColors != isDynamicColors) {
                Log.i(
                        "Recreating {} because dynamic color setting has changed",
                        getClass().getSimpleName());
                recreate();
            }
        }
    }

    public boolean setDesiredNightMode(final int desiredNightMode) {
        if (desiredNightMode == AppCompatDelegate.getDefaultNightMode()) {
            return false;
        }
        AppCompatDelegate.setDefaultNightMode(desiredNightMode);
        Log.i("Recreating {} because desired night mode has changed", getClass().getSimpleName());
        recreate();
        return true;
    }
}
