package local.pokewilds.standalone;

import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;

/** The upstream display/input implementation, hosted inside our application. */
public final class GameActivity extends com.termux.x11.MainActivity {
    private final android.os.Handler sessionHandler = new android.os.Handler();
    private final Runnable checkSession = new Runnable() {
        public void run() {
            if (!RuntimeService.active) { RuntimeService.surfaceReady = false; finish(); return; }
            RuntimeService.surfaceReady = getLorieView() != null
                && getLorieView().connected() && getLorieView().getWidth() > 0;
            sessionHandler.postDelayed(this, 500);
        }
    };
    @Override public void onResume() { super.onResume(); sessionHandler.post(checkSession); }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        RuntimeService.displayFocused = focused;
    }
    @Override public void onPause() {
        sessionHandler.removeCallbacks(checkSession);
        RuntimeService.displayFocused = false;
        RuntimeService.surfaceReady = false;
        for (int i=0;i<directions.length;i++) if(directions[i]) { directions[i]=false; super.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP,DIRECTION_KEYS[i])); }
        super.onPause();
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        android.view.View exit = findViewById(com.termux.x11.R.id.exit_button);
        if (exit != null) exit.setOnClickListener(v -> onBackPressed());
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        // Lorie normally consumes Back to toggle its soft keyboard. Own it here
        // so the handheld's Back button always reaches the game's quit flow.
        if (event.getKeyCode() == android.view.KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == android.view.KeyEvent.ACTION_UP) onBackPressed();
            return true;
        }
        int mapped = mapButton(event.getKeyCode());
        if (mapped != event.getKeyCode()) {
            android.view.KeyEvent key = new android.view.KeyEvent(event.getDownTime(), event.getEventTime(), event.getAction(), mapped,
                event.getRepeatCount(), 0, event.getDeviceId(), 0, event.getFlags(), android.view.InputDevice.SOURCE_KEYBOARD);
            return super.dispatchKeyEvent(key);
        }
        return super.dispatchKeyEvent(event);
    }
    private static int mapButton(int key) {
        switch (key) {
            case android.view.KeyEvent.KEYCODE_BUTTON_A: return android.view.KeyEvent.KEYCODE_Z;
            case android.view.KeyEvent.KEYCODE_BUTTON_B: return android.view.KeyEvent.KEYCODE_X;
            case android.view.KeyEvent.KEYCODE_BUTTON_START: return android.view.KeyEvent.KEYCODE_ENTER;
            case android.view.KeyEvent.KEYCODE_BUTTON_L1: return android.view.KeyEvent.KEYCODE_C;
            case android.view.KeyEvent.KEYCODE_BUTTON_R1: return android.view.KeyEvent.KEYCODE_V;
            default: return key;
        }
    }
    private final boolean[] directions = new boolean[4];
    private static final int[] DIRECTION_KEYS = {android.view.KeyEvent.KEYCODE_DPAD_LEFT, android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
        android.view.KeyEvent.KEYCODE_DPAD_UP, android.view.KeyEvent.KEYCODE_DPAD_DOWN};
    @Override public boolean onGenericMotionEvent(android.view.MotionEvent event) {
        if ((event.getSource() & android.view.InputDevice.SOURCE_JOYSTICK) != 0 && event.getAction() == android.view.MotionEvent.ACTION_MOVE) {
            float x=event.getAxisValue(android.view.MotionEvent.AXIS_HAT_X), y=event.getAxisValue(android.view.MotionEvent.AXIS_HAT_Y);
            if (x == 0) x=event.getAxisValue(android.view.MotionEvent.AXIS_X);
            if (y == 0) y=event.getAxisValue(android.view.MotionEvent.AXIS_Y);
            boolean[] next={x < -.3f, x > .3f, y < -.3f, y > .3f};
            for(int i=0;i<4;i++) if(next[i]!=directions[i]) { directions[i]=next[i]; super.dispatchKeyEvent(new android.view.KeyEvent(next[i] ? android.view.KeyEvent.ACTION_DOWN : android.view.KeyEvent.ACTION_UP,DIRECTION_KEYS[i])); }
            return true;
        }
        return super.onGenericMotionEvent(event);
    }
    // The upstream super implementation toggles its keyboard; this host owns Quit.
    @android.annotation.SuppressLint("MissingSuperCall")
    @Override public void onBackPressed() {
        if (getLorieView() != null) getLorieView().setKeyboardVisible(false);
        new android.app.AlertDialog.Builder(this).setTitle("Quit PokeWilds?")
            .setMessage("The game will ask whether to save if needed.")
            .setPositiveButton("Quit", (d, w) -> startService(new Intent(this, RuntimeService.class).setAction(RuntimeService.QUIT)))
            .setNeutralButton("Force-stop recovery", (d, w) -> confirmForceStop())
            .setNegativeButton("Keep playing", null).show();
    }
    private void confirmForceStop() {
        new android.app.AlertDialog.Builder(this).setTitle("Force-stop recovery?")
            .setMessage("Use this only when normal Quit cannot close the game. Unsaved progress may be lost.")
            .setPositiveButton("Force-stop", (d, w) -> {
                RuntimeService.status = "Force-stopping game…";
                startService(new Intent(this, RuntimeService.class).setAction(RuntimeService.STOP));
            })
            .setNegativeButton("Keep playing", null).show();
    }
}
