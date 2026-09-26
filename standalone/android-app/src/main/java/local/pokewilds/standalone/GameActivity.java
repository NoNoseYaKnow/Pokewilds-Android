package local.pokewilds.standalone;

import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;

/** The upstream display/input implementation, hosted inside our application. */
public final class GameActivity extends com.termux.x11.MainActivity {
    private TouchControls touchControls;
    private ControlPatchBridge controlPatches;
    private boolean pixelAlignedZoom;
    private android.app.AlertDialog gameMenu;
    private GameKeyBindings keyBindings = GameKeyBindings.defaults();
    private int keyboardShortcut;
    private boolean rightTriggerHeld;
    private boolean rightTriggerKeyHeld;
    private boolean rightTriggerAxisHeld;
    private boolean rightTriggerKeySeen;
    private final android.os.Handler sessionHandler = new android.os.Handler();
    private final Runnable checkSession = new Runnable() {
        public void run() {
            if (!RuntimeService.active) { RuntimeService.surfaceReady = false; finish(); return; }
            updateAutoViewport();
            RuntimeService.surfaceReady = getLorieView() != null
                && getLorieView().connected() && getLorieView().getWidth() > 0;
            sessionHandler.postDelayed(this, 500);
        }
    };
    private int viewportWidth, viewportHeight;
    private void updateAutoViewport() {
        if (getLorieView() == null) return;
        RuntimeOptions options = RuntimeOptions.read(this);
        android.graphics.Rect area = getLorieView().getAvailableRect();
        if (area.width() <= 0 || area.height() <= 0) return;
        int[] size;
        if (!options.autoViewport) size = new int[]{options.width, options.height};
        else if (pixelAlignedZoom) size = ViewportSize.autoZoom(area.width(), area.height());
        else size = ViewportSize.auto(area.width(), area.height());
        if (size[0] == viewportWidth && size[1] == viewportHeight) return;
        viewportWidth = size[0]; viewportHeight = size[1];
        RuntimeOptions.applyDisplaySize(this, viewportWidth, viewportHeight);
        getLorieView().triggerCallback();
        RuntimeService.requestedViewport = size;
    }
    @Override public void onResume() {
        super.onResume();
        keyBindings = GameKeyBindings.read(this);
        keyboardShortcut = KeyboardShortcut.read(this);
        directionKeys[0] = keyBindings.left; directionKeys[1] = keyBindings.right;
        directionKeys[2] = keyBindings.up; directionKeys[3] = keyBindings.down;
        if (touchControls != null) {
            touchControls.setKeyBindings(keyBindings);
            touchControls.setTouchMode(TouchControls.readTouchMode(this));
        }
        if (controlPatches != null) controlPatches.resume();
        sessionHandler.post(checkSession);
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        RuntimeService.displayFocused = focused;
        if (controlPatches != null) {
            if (focused) controlPatches.resume(); else controlPatches.suspend();
        }
    }
    @Override public void onPause() {
        sessionHandler.removeCallbacks(checkSession);
        RuntimeService.displayFocused = false;
        RuntimeService.surfaceReady = false;
        if (controlPatches != null) controlPatches.suspend();
        if (touchControls != null) touchControls.releaseAll();
        rightTriggerHeld = false;
        rightTriggerKeyHeld = false;
        rightTriggerAxisHeld = false;
        rightTriggerKeySeen = false;
        releaseGamepadKeys();
        super.onPause();
    }
    private void releaseDirections() {
        for (int i=0;i<directions.length;i++) if(directions[i]) {
            directions[i]=false;
            super.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP,directionKeys[i]));
        }
    }
    private void releaseGamepadKeys() {
        releaseDirections();
        // Physical D-pad keys can have reached X11 without passing through the
        // analog direction tracker. The dialog receives their eventual key-up.
        for (int key : directionKeys) sendXKey(key, false);
        for (int key : new int[]{keyBindings.a, keyBindings.b, keyBindings.start,
            keyBindings.shoulderLeft, keyBindings.shoulderRight}) sendXKey(key, false);
    }
    @Override protected void onCreate(Bundle state) {
        // Taps must click at the touched screen position so the unchanged
        // Swing save/quit dialog remains usable without a visible X cursor.
        ((com.termux.x11.LorieApp) getApplication()).getPrefs(this).touchMode.put("2");
        super.onCreate(state);
        if (getLorieView() != null)
            getLorieView().setOnKeyListener((view, code, event) -> handleKey(event));
        android.view.View exit = findViewById(com.termux.x11.R.id.exit_button);
        if (exit != null) exit.setOnClickListener(v -> onBackPressed());
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        android.view.View content = findViewById(android.R.id.content);
        PatchOptions patches = PatchOptions.read(this);
        pixelAlignedZoom = patches.zoom;
        if (content instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) content).setMotionEventSplittingEnabled(true);
            touchControls = new TouchControls(this, this::dispatchTouchKey, this::toggleKeyboard);
            if (patches.controlsEnabled()) {
                controlPatches = new ControlPatchBridge(this, patches.radial, patches.zoom,
                    () -> { if (touchControls != null) touchControls.invalidate(); });
                touchControls.setControlPatches(controlPatches);
            }
            ((android.view.ViewGroup) content).addView(touchControls, new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }
    private void toggleKeyboard() {
        if (getLorieView() != null) getLorieView().toggleKeyboardVisible();
    }
    private boolean dispatchTouchKey(int keyCode, int action) {
        if (gameMenu != null) return true;
        if (handleSaveDialogKey(keyCode, action, 0)) return true;
        if (controlPatches != null && (keyCode == android.view.KeyEvent.KEYCODE_BUTTON_L1
            || keyCode == android.view.KeyEvent.KEYCODE_BUTTON_R1))
            return controlPatches.touchZoom(keyCode == android.view.KeyEvent.KEYCODE_BUTTON_R1,
                action == android.view.KeyEvent.ACTION_DOWN);
        return sendXKey(keyCode, action == android.view.KeyEvent.ACTION_DOWN);
    }
    private boolean sendXKey(int keyCode, boolean down) {
        return getLorieView() != null && getLorieView().connected()
            && getLorieView().sendKeyEvent(0, keyCode, down);
    }
    private boolean handleSaveDialogKey(int keyCode, int action, int repeatCount) {
        if (!RuntimeService.saveDialogVisible) return false;
        boolean left = keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT || keyCode == keyBindings.left;
        boolean right = keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == keyBindings.right;
        if (left || right) {
            if (action == android.view.KeyEvent.ACTION_DOWN && repeatCount == 0) {
                if (left) sendXKey(android.view.KeyEvent.KEYCODE_SHIFT_LEFT, true);
                sendXKey(android.view.KeyEvent.KEYCODE_TAB, true);
                sendXKey(android.view.KeyEvent.KEYCODE_TAB, false);
                if (left) sendXKey(android.view.KeyEvent.KEYCODE_SHIFT_LEFT, false);
            }
            return true;
        }
        if (keyCode == android.view.KeyEvent.KEYCODE_BUTTON_A || keyCode == keyBindings.a) {
            if (repeatCount == 0) sendXKey(android.view.KeyEvent.KEYCODE_SPACE,
                action == android.view.KeyEvent.ACTION_DOWN);
            return true;
        }
        return false;
    }
    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (gameMenu != null) {
            if (event.getKeyCode() == android.view.KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == android.view.KeyEvent.ACTION_UP) gameMenu.dismiss();
                return true;
            }
            return gameMenu.dispatchKeyEvent(event);
        }
        // Lorie normally consumes Back to toggle its soft keyboard. Own it here
        // so the handheld's Back button always reaches the game's quit flow.
        if (event.getKeyCode() == android.view.KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == android.view.KeyEvent.ACTION_UP) onBackPressed();
            return true;
        }
        if (handleKeyboardShortcut(event)) return true;
        if (handleSaveDialogKey(event.getKeyCode(), event.getAction(), event.getRepeatCount())) return true;
        if (controlPatches != null && controlPatches.routeKey(event)) return true;
        if (sendGamepadButton(event)) return true;
        return super.dispatchKeyEvent(event);
    }
    @Override public boolean handleKey(android.view.KeyEvent event) {
        // LorieView forwards some physical buttons before Activity.dispatchKeyEvent.
        if (gameMenu != null) return true;
        if (handleKeyboardShortcut(event)) return true;
        if (handleSaveDialogKey(event.getKeyCode(), event.getAction(), event.getRepeatCount())) return true;
        if (controlPatches != null && controlPatches.routeKey(event)) return true;
        if (sendGamepadButton(event)) return true;
        return super.handleKey(event);
    }
    private boolean handleKeyboardShortcut(android.view.KeyEvent event) {
        if (KeyboardShortcut.matchesKey(keyboardShortcut, event.getKeyCode())) {
            if (keyboardShortcut == KeyboardShortcut.RIGHT_TRIGGER) {
                rightTriggerKeySeen = true;
                rightTriggerAxisHeld = false;
                if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) rightTriggerKeyHeld = true;
                else if (event.getAction() == android.view.KeyEvent.ACTION_UP) rightTriggerKeyHeld = false;
                updateRightTrigger();
            } else if (event.getAction() == android.view.KeyEvent.ACTION_UP && !event.isCanceled()) toggleKeyboard();
            return true;
        }
        return false;
    }
    private boolean sendGamepadButton(android.view.KeyEvent event) {
        int mapped = mapButton(event.getKeyCode());
        if (mapped == event.getKeyCode()) return false;
        if (event.getAction() == android.view.KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0)
            sendXKey(mapped, true);
        else if (event.getAction() == android.view.KeyEvent.ACTION_UP)
            sendXKey(mapped, false);
        return true;
    }
    private int mapButton(int key) {
        switch (key) {
            case android.view.KeyEvent.KEYCODE_BUTTON_A: return keyBindings.a;
            case android.view.KeyEvent.KEYCODE_BUTTON_B: return keyBindings.b;
            case android.view.KeyEvent.KEYCODE_BUTTON_START: return keyBindings.start;
            case android.view.KeyEvent.KEYCODE_BUTTON_L1: return keyBindings.shoulderLeft;
            case android.view.KeyEvent.KEYCODE_BUTTON_R1: return keyBindings.shoulderRight;
            case android.view.KeyEvent.KEYCODE_BUTTON_X: return keyBindings.shoulderLeft;
            case android.view.KeyEvent.KEYCODE_BUTTON_Y: return keyBindings.shoulderRight;
            default: return key;
        }
    }
    private final boolean[] directions = new boolean[4];
    private void updateRightTrigger() {
        boolean held = rightTriggerKeyHeld || rightTriggerAxisHeld;
        if (held && !rightTriggerHeld) toggleKeyboard();
        rightTriggerHeld = held;
    }
    private final int[] directionKeys = {android.view.KeyEvent.KEYCODE_DPAD_LEFT, android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
        android.view.KeyEvent.KEYCODE_DPAD_UP, android.view.KeyEvent.KEYCODE_DPAD_DOWN};
    @Override public boolean dispatchGenericMotionEvent(android.view.MotionEvent event) {
        if (gameMenu == null && controlPatches != null && controlPatches.routeMotion(event)) return true;
        return super.dispatchGenericMotionEvent(event);
    }
    @Override public boolean onGenericMotionEvent(android.view.MotionEvent event) {
        if (touchControls != null) touchControls.refreshControllerState();
        if (gameMenu != null) return true;
        if (controlPatches != null && controlPatches.routeMotion(event)) return true;
        if ((event.getSource() & android.view.InputDevice.SOURCE_JOYSTICK) != 0 && event.getAction() == android.view.MotionEvent.ACTION_MOVE) {
            if (keyboardShortcut == KeyboardShortcut.RIGHT_TRIGGER && !rightTriggerKeySeen) {
                float trigger = Math.max(event.getAxisValue(android.view.MotionEvent.AXIS_RTRIGGER),
                    event.getAxisValue(android.view.MotionEvent.AXIS_GAS));
                if (!rightTriggerAxisHeld && trigger > .6f) rightTriggerAxisHeld = true;
                else if (rightTriggerAxisHeld && trigger < .3f) rightTriggerAxisHeld = false;
                updateRightTrigger();
            }
            float x=event.getAxisValue(android.view.MotionEvent.AXIS_HAT_X), y=event.getAxisValue(android.view.MotionEvent.AXIS_HAT_Y);
            if (x == 0) x=event.getAxisValue(android.view.MotionEvent.AXIS_X);
            if (y == 0) y=event.getAxisValue(android.view.MotionEvent.AXIS_Y);
            boolean[] next={x < -.3f, x > .3f, y < -.3f, y > .3f};
            for(int i=0;i<4;i++) if(next[i]!=directions[i]) {
                directions[i]=next[i]; dispatchKeyEvent(new android.view.KeyEvent(next[i] ? android.view.KeyEvent.ACTION_DOWN : android.view.KeyEvent.ACTION_UP,directionKeys[i]));
            }
            return true;
        }
        return super.onGenericMotionEvent(event);
    }
    // The upstream super implementation toggles its keyboard; this host owns Quit.
    @android.annotation.SuppressLint("MissingSuperCall")
    @Override public void onBackPressed() {
        if (gameMenu != null) { gameMenu.dismiss(); return; }
        if (getLorieView() != null) getLorieView().setKeyboardVisible(false);
        if (touchControls != null) touchControls.releaseAll();
        releaseGamepadKeys();
        gameMenu = new android.app.AlertDialog.Builder(this).setTitle("Game menu")
            .setItems(new String[]{"Keep playing", "Show keyboard", "App settings", "Quit PokeWilds"}, (d, which) -> {
                if (which == 1 && getLorieView() != null) getLorieView().post(this::toggleKeyboard);
                else if (which == 2) startActivity(new Intent(this, LauncherActivity.class)
                    .putExtra(LauncherActivity.MANAGE_SAVES_EXTRA, true));
                else if (which == 3) startService(new Intent(this, RuntimeService.class).setAction(RuntimeService.QUIT));
            }).create();
        gameMenu.setOnDismissListener(d -> gameMenu = null);
        gameMenu.show();
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
