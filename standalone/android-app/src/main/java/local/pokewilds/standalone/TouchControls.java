package local.pokewilds.standalone;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

/** Edge anchored controls that only consume touches landing on a button. */
public final class TouchControls extends View implements InputManager.InputDeviceListener {
    public interface KeySink { boolean send(int keyCode, int action); }

    public static final int TOUCH_AUTO = 0;
    public static final int TOUCH_ON = 1;
    public static final int TOUCH_OFF = 2;
    public static final String[] TOUCH_MODE_LABELS = {"Auto", "On", "Off"};
    private static final String PREFS = "touch_controls";
    private static final String MODE_KEY = "mode";
    private static final int NONE = -1;
    private static final int TOGGLE = -2;
    private static final int KEYBOARD = -3;
    private final int[] dpadKeys = {KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT};
    private int aKey = KeyEvent.KEYCODE_Z, bKey = KeyEvent.KEYCODE_X, startKey = KeyEvent.KEYCODE_ENTER;
    private int cKey = KeyEvent.KEYCODE_C, vKey = KeyEvent.KEYCODE_V;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF[] dpad = {new RectF(), new RectF(), new RectF(), new RectF()};
    private final RectF aButton = new RectF(), bButton = new RectF(), startButton = new RectF();
    private final RectF cButton = new RectF(), vButton = new RectF(), toggleButton = new RectF(), keyboardButton = new RectF();
    private final TouchInputState inputState = new TouchInputState(
        (key, down) -> sendKey(key, down ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP));
    private final KeySink keySink;
    private final Runnable keyboardToggle;
    private final InputManager inputManager;
    private final Handler inputHandler = new Handler(Looper.getMainLooper());
    private final float density;
    private int touchMode;
    private boolean controllerConnected;
    private boolean controlsHidden;

    public TouchControls(Context context, KeySink sink, Runnable keyboardToggle) {
        super(context);
        keySink = sink;
        this.keyboardToggle = keyboardToggle;
        density = getResources().getDisplayMetrics().density;
        inputManager = (InputManager) context.getSystemService(Context.INPUT_SERVICE);
        touchMode = readTouchMode(context);
        controllerConnected = hasController();
        setWillNotDraw(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public TouchControls(Context context, AttributeSet attrs) { this(context, null, null); }

    public static int readTouchMode(Context context) {
        int mode = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(MODE_KEY, TOUCH_AUTO);
        return isValidMode(mode) ? mode : TOUCH_AUTO;
    }

    public static void saveTouchMode(Context context, int mode) {
        if (!isValidMode(mode)) mode = TOUCH_AUTO;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(MODE_KEY, mode).apply();
    }

    public static String touchModeLabel(int mode) {
        return isValidMode(mode) ? TOUCH_MODE_LABELS[mode] : TOUCH_MODE_LABELS[TOUCH_AUTO];
    }

    private static boolean isValidMode(int mode) { return mode >= TOUCH_AUTO && mode <= TOUCH_OFF; }

    public void setTouchMode(int mode) {
        releaseAll();
        touchMode = isValidMode(mode) ? mode : TOUCH_AUTO;
        controlsHidden = false;
        refreshControllerState();
        invalidate();
    }

    public int getTouchMode() { return touchMode; }

    public void setKeyBindings(GameKeyBindings bindings) {
        releaseAll();
        dpadKeys[0] = bindings.up; dpadKeys[1] = bindings.down;
        dpadKeys[2] = bindings.left; dpadKeys[3] = bindings.right;
        aKey = bindings.a; bKey = bindings.b; startKey = bindings.start;
        cKey = bindings.shoulderLeft; vKey = bindings.shoulderRight;
    }

    /** Sends ACTION_UP for every key currently held by a finger. */
    public void releaseAll() {
        inputState.releaseAll();
    }

    public void refreshControllerState() {
        boolean connected = hasController();
        if (connected != controllerConnected) {
            controllerConnected = connected;
            releaseAll();
            invalidate();
        }
    }

    private boolean isVisibleByMode() { return touchMode == TOUCH_ON || (touchMode == TOUCH_AUTO && !controllerConnected); }
    private boolean shouldDrawControls() { return isVisibleByMode() && !controlsHidden; }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (inputManager != null) inputManager.registerInputDeviceListener(this, inputHandler);
        refreshControllerState();
    }

    @Override protected void onDetachedFromWindow() {
        releaseAll();
        if (inputManager != null) inputManager.unregisterInputDeviceListener(this);
        super.onDetachedFromWindow();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        layoutButtons(width, height);
    }

    private float dp(float value) { return value * density; }

    private void layoutButtons(int width, int height) {
        float button = Math.max(dp(38), Math.min(dp(56), width * .14f));
        float half = button / 2f;
        float dpadX = dp(78), dpadY = height - dp(86);
        dpad[0].set(dpadX - half, dpadY - button * 1.02f, dpadX + half, dpadY - .02f * button);
        dpad[1].set(dpadX - half, dpadY + .02f * button, dpadX + half, dpadY + button * 1.02f);
        dpad[2].set(dpadX - button * 1.02f, dpadY - half, dpadX - .02f * button, dpadY + half);
        dpad[3].set(dpadX + .02f * button, dpadY - half, dpadX + button * 1.02f, dpadY + half);
        float actionX = width - dp(68), actionY = height - dp(83);
        aButton.set(actionX - half, actionY - half, actionX + half, actionY + half);
        bButton.set(actionX - dp(63) - half, actionY + dp(28) - half,
            actionX - dp(63) + half, actionY + dp(28) + half);
        startButton.set(width / 2f - dp(34), height - dp(27), width / 2f + dp(34), height - dp(3));
        float shoulderY = dp(64);
        cButton.set(width - dp(100), shoulderY - dp(19), width - dp(58), shoulderY + dp(19));
        vButton.set(width - dp(52), shoulderY - dp(19), width - dp(10), shoulderY + dp(19));
        toggleButton.set(width - dp(88), dp(8), width - dp(8), dp(38));
        keyboardButton.set(width - dp(160), dp(8), width - dp(96), dp(38));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (touchMode == TOUCH_OFF) return;
        if (shouldDrawControls()) {
            for (int i = 0; i < dpad.length; i++) drawButton(canvas, dpad[i], i == 0 ? "▲" : i == 1 ? "▼" : i == 2 ? "◀" : "▶");
            drawButton(canvas, aButton, "A"); drawButton(canvas, bButton, "B"); drawButton(canvas, startButton, "Start");
            drawButton(canvas, cButton, "C"); drawButton(canvas, vButton, "V");
            drawButton(canvas, keyboardButton, "Keyboard"); drawButton(canvas, toggleButton, "×");
        } else if (isVisibleByMode()) drawButton(canvas, toggleButton, "Controls");
    }

    private void drawButton(Canvas canvas, RectF rect, String label) {
        paint.setColor(Color.argb(112, 20, 24, 32));
        canvas.drawRoundRect(rect, dp(12), dp(12), paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1));
        paint.setColor(Color.argb(175, 235, 240, 250)); canvas.drawRoundRect(rect, dp(12), dp(12), paint);
        paint.setStyle(Paint.Style.FILL); textPaint.setColor(Color.argb(235, 255, 255, 255));
        textPaint.setTextSize(label.length() > 2 ? dp(11) : dp(17)); textPaint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        canvas.drawText(label, rect.centerX(), rect.centerY() - (metrics.ascent + metrics.descent) / 2f, textPaint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            int index = event.getActionIndex(), pointer = event.getPointerId(index);
            int key = hitKey(event.getX(index), event.getY(index));
            if (key == TOGGLE) { controlsHidden = !controlsHidden; if (controlsHidden) releaseAll(); invalidate(); return true; }
            if (key == KEYBOARD) { if (keyboardToggle != null) keyboardToggle.run(); return true; }
            if (key == NONE || !shouldDrawControls()) return false;
            inputState.down(pointer, key); invalidate(); return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            for (int i = 0; i < event.getPointerCount(); i++) {
                int pointer = event.getPointerId(i);
                int nextKey = shouldDrawControls() ? hitKey(event.getX(i), event.getY(i)) : NONE;
                if (nextKey == TOGGLE || nextKey == KEYBOARD) nextKey = NONE;
                inputState.move(pointer, nextKey);
            }
            return inputState.hasCapturedPointers();
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            inputState.up(event.getPointerId(event.getActionIndex())); if (action == MotionEvent.ACTION_UP) performClick(); return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) { releaseAll(); return true; }
        return false;
    }

    private int hitKey(float x, float y) {
        if (toggleButton.contains(x, y)) return TOGGLE;
        if (shouldDrawControls() && keyboardButton.contains(x, y)) return KEYBOARD;
        for (int i = 0; i < dpad.length; i++) if (dpad[i].contains(x, y)) return dpadKeys[i];
        if (aButton.contains(x, y)) return aKey; if (bButton.contains(x, y)) return bKey;
        if (startButton.contains(x, y)) return startKey; if (cButton.contains(x, y)) return cKey;
        if (vButton.contains(x, y)) return vKey; return NONE;
    }

    private void sendKey(int key, int action) { if (keySink != null) keySink.send(key, action); }

    private boolean hasController() {
        for (int id : InputDevice.getDeviceIds()) { InputDevice device = InputDevice.getDevice(id); if (device == null) continue;
            int sources = device.getSources(); if (TouchInputState.isControllerSource(sources,
                InputDevice.SOURCE_GAMEPAD, InputDevice.SOURCE_JOYSTICK)) return true; }
        return false;
    }

    @Override public void onInputDeviceAdded(int deviceId) { refreshControllerState(); }
    @Override public void onInputDeviceRemoved(int deviceId) { refreshControllerState(); }
    @Override public void onInputDeviceChanged(int deviceId) { refreshControllerState(); }
}
