package local.pokewilds.standalone;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Path;
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
    private final int[] dpadKeys = {KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF[] dpad = {new RectF(), new RectF(), new RectF(), new RectF()};
    private final RectF aButton = new RectF(), bButton = new RectF(), startButton = new RectF();
    private final RectF l1Button = new RectF(), r1Button = new RectF(), toggleButton = new RectF();
    private final RectF l2Button = new RectF(), r2Button = new RectF(), selectButton = new RectF();
    private final TouchInputState inputState = new TouchInputState(
        (key, down) -> sendKey(key, down ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP));
    private final KeySink keySink;
    private final Runnable cancelInput;
    private final InputManager inputManager;
    private final Handler inputHandler = new Handler(Looper.getMainLooper());
    private final float density;
    private int touchMode;
    private boolean controllerConnected;
    private boolean controlsHidden;
    private ControlPatchBridge controlPatches;
    private int radialPointer = -1;

    public TouchControls(Context context, KeySink sink, Runnable cancelInput) {
        super(context);
        keySink = sink;
        this.cancelInput = cancelInput;
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

    void setControlPatches(ControlPatchBridge bridge) { controlPatches = bridge; invalidate(); }

    /** Sends ACTION_UP for every key currently held by a finger. */
    public void releaseAll() {
        if (cancelInput != null) cancelInput.run();
        radialPointer = -1;
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
        releaseAll();
        layoutButtons(width, height);
    }

    private float dp(float value) { return value * density; }

    private void layoutButtons(int width, int height) {
        float button = Math.max(dp(38), Math.min(dp(56), width * .14f));
        float half = button / 2f;
        float dpadX = dp(78), dpadY = height - dp(100);
        dpad[0].set(dpadX - half, dpadY - button * 1.02f, dpadX + half, dpadY - .02f * button);
        dpad[1].set(dpadX - half, dpadY + .02f * button, dpadX + half, dpadY + button * 1.02f);
        dpad[2].set(dpadX - button * 1.02f, dpadY - half, dpadX - .02f * button, dpadY + half);
        dpad[3].set(dpadX + .02f * button, dpadY - half, dpadX + button * 1.02f, dpadY + half);
        float actionX = width - dp(68), actionY = height - dp(100);
        aButton.set(actionX - half, actionY - half, actionX + half, actionY + half);
        bButton.set(actionX - dp(63) - half, actionY + dp(28) - half,
            actionX - dp(63) + half, actionY + dp(28) + half);
        selectButton.set(width / 2f - dp(68), height - dp(40), width / 2f - dp(4), height - dp(8));
        startButton.set(width / 2f + dp(4), height - dp(40), width / 2f + dp(68), height - dp(8));
        l2Button.set(dp(12), dp(8), dp(68), dp(44));
        l1Button.set(dp(12), dp(52), dp(68), dp(88));
        r2Button.set(width - dp(68), dp(8), width - dp(12), dp(44));
        r1Button.set(width - dp(68), dp(52), width - dp(12), dp(88));
        toggleButton.set(width / 2f - dp(40), dp(8), width / 2f + dp(40), dp(38));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (touchMode == TOUCH_OFF) return;
        if (shouldDrawControls()) {
            for (int i = 0; i < dpad.length; i++) drawButton(canvas, dpad[i], i == 0 ? "▲" : i == 1 ? "▼" : i == 2 ? "◀" : "▶");
            drawButton(canvas, aButton, "A"); drawButton(canvas, bButton, "B"); drawButton(canvas, startButton, "Start");
            drawButton(canvas, selectButton, "Select");
            drawButton(canvas, l1Button, "L1"); drawButton(canvas, r1Button, "R1");
            drawButton(canvas, l2Button, "L2"); drawButton(canvas, r2Button, "R2");
            drawButton(canvas, toggleButton, "×");
        } else if (isVisibleByMode()) drawButton(canvas, toggleButton, "Controls");
        if (controlPatches != null && controlPatches.wheelShown()) drawRadialWheel(canvas);
    }

    private void drawRadialWheel(Canvas canvas) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float radius = Math.min(getWidth(), getHeight()) * .37f;
        paint.setColor(Color.argb(195, 8, 11, 18));
        canvas.drawCircle(cx, cy, radius * 1.12f, paint);
        for (int i = 0; i < ControlPatchBridge.MOVES.length; i++) {
            double middle = -Math.PI / 2 + i * Math.PI * 2 / ControlPatchBridge.MOVES.length;
            double start = middle - Math.PI / ControlPatchBridge.MOVES.length;
            Path sector = new Path(); sector.moveTo(cx, cy);
            for (int point = 0; point <= 8; point++) {
                double angle = start + 2 * Math.PI / ControlPatchBridge.MOVES.length * point / 8;
                sector.lineTo(cx + (float) Math.cos(angle) * radius, cy + (float) Math.sin(angle) * radius);
            }
            sector.close();
            paint.setColor(i == controlPatches.selected() ? Color.rgb(96, 132, 174)
                : i % 2 == 0 ? Color.rgb(38, 43, 53) : Color.rgb(30, 35, 44));
            canvas.drawPath(sector, paint);
            textPaint.setColor(controlPatches.available(i) ? Color.WHITE : Color.GRAY);
            textPaint.setTextSize(dp(10)); textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(ControlPatchBridge.MOVES[i], cx + (float) Math.cos(middle) * radius * .70f,
                cy + (float) Math.sin(middle) * radius * .70f, textPaint);
        }
        paint.setColor(Color.rgb(12, 15, 21)); canvas.drawCircle(cx, cy, radius * .28f, paint);
        textPaint.setColor(Color.WHITE); textPaint.setTextSize(dp(12));
        int selected = controlPatches.selected();
        canvas.drawText(selected < 0 ? "SELECT" : ControlPatchBridge.MOVES[selected], cx, cy, textPaint);
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
        if (controlPatches != null && controlPatches.radialEnabled()) {
            int pointer = event.getPointerId(event.getActionIndex());
            if ((action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN)
                && shouldDrawControls() && l2Button.contains(event.getX(event.getActionIndex()), event.getY(event.getActionIndex()))) {
                inputState.releaseAll(); radialPointer = pointer;
                inputState.down(pointer, KeyEvent.KEYCODE_BUTTON_L2); invalidate(); return true;
            }
            if (radialPointer == pointer && (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP)
                && !controlPatches.wheelShown()) {
                inputState.up(pointer); radialPointer = -1; return true;
            }
            if (controlPatches.wheelShown()) {
                if (action == MotionEvent.ACTION_MOVE) {
                    for (int i = 0; i < event.getPointerCount(); i++)
                        if (event.getPointerId(i) != radialPointer
                            || !l2Button.contains(event.getX(i), event.getY(i)))
                            controlPatches.selectTouch(event.getX(i), event.getY(i), getWidth(), getHeight());
                } else if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                    controlPatches.selectTouch(event.getX(event.getActionIndex()), event.getY(event.getActionIndex()), getWidth(), getHeight());
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
                    if (pointer == radialPointer) { radialPointer = -1; inputState.up(pointer); }
                } else if (action == MotionEvent.ACTION_CANCEL) releaseAll();
                invalidate(); return true;
            }
        }
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            int index = event.getActionIndex(), pointer = event.getPointerId(index);
            int key = hitKey(event.getX(index), event.getY(index));
            if (key == TOGGLE) { controlsHidden = !controlsHidden; if (controlsHidden) releaseAll(); invalidate(); return true; }
            if (key == NONE || !shouldDrawControls()) return false;
            inputState.down(pointer, key); invalidate(); return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            for (int i = 0; i < event.getPointerCount(); i++) {
                int pointer = event.getPointerId(i);
                int nextKey = shouldDrawControls() ? hitKey(event.getX(i), event.getY(i)) : NONE;
                if (nextKey == TOGGLE) nextKey = NONE;
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
        for (int i = 0; i < dpad.length; i++) if (dpad[i].contains(x, y)) return dpadKeys[i];
        if (aButton.contains(x, y)) return KeyEvent.KEYCODE_BUTTON_A;
        if (bButton.contains(x, y)) return KeyEvent.KEYCODE_BUTTON_B;
        if (startButton.contains(x, y)) return KeyEvent.KEYCODE_BUTTON_START;
        if (selectButton.contains(x, y)) return KeyEvent.KEYCODE_BUTTON_SELECT;
        if (l1Button.contains(x, y)) return KeyEvent.KEYCODE_BUTTON_L1;
        if (r1Button.contains(x, y)) return KeyEvent.KEYCODE_BUTTON_R1;
        if (l2Button.contains(x, y)) return KeyEvent.KEYCODE_BUTTON_L2;
        if (r2Button.contains(x, y)) return KeyEvent.KEYCODE_BUTTON_R2;
        return NONE;
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
