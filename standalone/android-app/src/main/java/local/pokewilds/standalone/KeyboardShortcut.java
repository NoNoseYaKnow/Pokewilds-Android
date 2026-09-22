package local.pokewilds.standalone;

import android.content.Context;
import android.view.KeyEvent;

/** Physical gamepad button used to show or hide the Android keyboard. */
final class KeyboardShortcut {
    static final int RIGHT_TRIGGER = 0;
    static final int RIGHT_STICK = 6;
    static final String[] LABELS = {"Right trigger (R2)", "Select", "X", "Y", "Left trigger (L2)", "Left stick click", "Right stick click", "Off"};
    private static final String PREFS = "keyboard_shortcut";
    private static final String KEY = "button";

    static int read(Context context) {
        int choice = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, RIGHT_STICK);
        return valid(choice) ? choice : RIGHT_STICK;
    }

    static void save(Context context, int choice) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY, valid(choice) ? choice : RIGHT_STICK).apply();
    }

    static boolean matchesKey(int choice, int keyCode) {
        switch (choice) {
            case RIGHT_TRIGGER: return keyCode == KeyEvent.KEYCODE_BUTTON_R2;
            case 1: return keyCode == KeyEvent.KEYCODE_BUTTON_SELECT;
            case 2: return keyCode == KeyEvent.KEYCODE_BUTTON_X;
            case 3: return keyCode == KeyEvent.KEYCODE_BUTTON_Y;
            case 4: return keyCode == KeyEvent.KEYCODE_BUTTON_L2;
            case 5: return keyCode == KeyEvent.KEYCODE_BUTTON_THUMBL;
            case RIGHT_STICK: return keyCode == KeyEvent.KEYCODE_BUTTON_THUMBR;
            default: return false;
        }
    }

    private static boolean valid(int choice) { return choice >= 0 && choice < LABELS.length; }
}
