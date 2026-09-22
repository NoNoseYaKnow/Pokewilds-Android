package local.pokewilds.standalone;

import android.preference.PreferenceManager;
import com.termux.x11.LorieApp;

public final class GameApplication extends LorieApp {
    @Override public void onCreate() {
        // Initialize before LorieApp attaches its preference readers.
        RuntimeOptions options = RuntimeOptions.read(this);
        PreferenceManager.getDefaultSharedPreferences(this).edit()
            .putString("displayResolutionMode", "custom")
            .putString("displayResolutionCustom", options.width + "x" + options.height)
            .putBoolean("showAdditionalKbd", false)
            .putBoolean("fullscreen", true).apply();
        super.onCreate();
    }
}
