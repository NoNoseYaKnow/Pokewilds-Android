package local.pokewilds.standalone;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

public final class LauncherActivity extends Activity {
    public static final String MANAGE_SAVES_EXTRA = "local.pokewilds.standalone.MANAGE_SAVES";
    private final Handler handler = new Handler();
    private TextView statusView;
    private boolean openedGame;
    private boolean managementMode;
    private boolean manuallyStarted;
    private Spinner graphicsControl;
    private Spinner viewportControl;
    private final java.util.concurrent.ExecutorService files = java.util.concurrent.Executors.newSingleThreadExecutor();
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            statusView.setText(RuntimeService.status);
            if (openedGame && !RuntimeService.active && "Game closed".equals(RuntimeService.status)) {
                openedGame = false;
                manuallyStarted = false;
                if (!managementMode) { finishAndRemoveTask(); return; }
            }
            if (RuntimeService.displayReady && !openedGame && (!managementMode || manuallyStarted)) {
                openedGame = true;
                manuallyStarted = false;
                startActivity(new Intent(LauncherActivity.this, GameActivity.class));
            }
            updateRuntimeControls();
            handler.postDelayed(this, 500);
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        managementMode = isManagementIntent(getIntent());
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 48, 32, 32);
        TextView title = new TextView(this); title.setText("PokeWilds"); title.setTextSize(30); layout.addView(title);
        statusView = new TextView(this); statusView.setTextSize(18); statusView.setText(RuntimeService.status); layout.addView(statusView);
        Button start = new Button(this); start.setText("Start / resume");
        start.setOnClickListener(v -> { openedGame = false; manuallyStarted = true; startGame(); }); layout.addView(start);
        Button forceStop = new Button(this); forceStop.setText("Force-stop recovery");
        forceStop.setOnClickListener(v -> confirmForceStop()); layout.addView(forceStop);
        Button quit = new Button(this); quit.setText("Quit game");
        quit.setOnClickListener(v -> openGameForQuit()); layout.addView(quit);
        if (managementMode) addRuntimeControls(layout);
        Button logs = new Button(this); logs.setText("View startup log");
        logs.setOnClickListener(v -> {
            String text;
            try {
                try (java.io.RandomAccessFile file = new java.io.RandomAccessFile(new java.io.File(getFilesDir(), "session.log"), "r")) {
                    long length = file.length();
                    long from = Math.max(0, length - 12000);
                    file.seek(from);
                    byte[] bytes = new byte[(int) (length - from)];
                    file.readFully(bytes);
                    text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                }
            } catch (Exception e) { text = "No startup log yet."; }
            new android.app.AlertDialog.Builder(this).setTitle("Startup log").setMessage(text).setPositiveButton("Close", null).show();
        }); layout.addView(logs);
        Button export = new Button(this); export.setText("Export saves");
        export.setOnClickListener(v -> {
            if (RuntimeService.active) { RuntimeService.status = "Save and quit the game before exporting."; return; }
            startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"pokewilds-saves.zip"), 10);
        }); layout.addView(export);
        Button importButton = new Button(this); importButton.setText("Import saves");
        importButton.setOnClickListener(v -> {
            if (RuntimeService.active) { RuntimeService.status = "Quit the game before importing."; return; }
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/zip").addCategory(Intent.CATEGORY_OPENABLE), 11);
        }); layout.addView(importButton);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(layout); setContentView(scroll);
        if (!managementMode && new java.io.File(getFilesDir(), "game/pokewilds.jar").isFile()) startGame();
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        if (RuntimeService.active) { RuntimeService.status = "Quit the game first."; return; }
        RuntimeService.status = request == 10 ? "Exporting saves…" : "Importing saves…";
        files.submit(() -> {
            if (!RuntimeService.DATA_LOCK.tryAcquire()) { RuntimeService.status = "Quit the game before transferring saves."; return; }
            try {
                java.nio.file.Path game = new java.io.File(getFilesDir(), "game").toPath();
                if (request == 10) SaveArchive.exportTo(game,getContentResolver().openOutputStream(data.getData()));
                else SaveArchive.importFrom(getContentResolver().openInputStream(data.getData()), game);
                RuntimeService.status = request == 10 ? "Saves exported" : "Saves imported";
            } catch (Exception e) { RuntimeService.status = "Save transfer failed: " + e.getMessage(); }
            finally { RuntimeService.DATA_LOCK.release(); }
        });
    }
    @Override protected void onDestroy() { files.shutdown(); super.onDestroy(); }
    private void startGame() { startForegroundService(new Intent(this, RuntimeService.class)); }

    private void addRuntimeControls(LinearLayout layout) {
        RuntimeOptions options = RuntimeOptions.read(this);
        TextView heading = new TextView(this); heading.setText("Runtime settings"); heading.setTextSize(20); layout.addView(heading);
        TextView graphicsLabel = new TextView(this); graphicsLabel.setText("Graphics profile"); layout.addView(graphicsLabel);
        graphicsControl = new Spinner(this);
        graphicsControl.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, RuntimeOptions.GRAPHICS_LABELS));
        graphicsControl.setSelection(RuntimeOptions.graphicsIndex(options.graphics));
        graphicsControl.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(AdapterView<?> parent) { }
            @Override public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                if (RuntimeService.active) {
                    graphicsControl.setSelection(RuntimeOptions.graphicsIndex(RuntimeOptions.read(LauncherActivity.this).graphics));
                    RuntimeService.status = "Quit the game before changing runtime settings.";
                    return;
                }
                RuntimeOptions current = RuntimeOptions.read(LauncherActivity.this);
                RuntimeOptions.save(LauncherActivity.this, RuntimeOptions.GRAPHICS_VALUES[position], current.width, current.height);
            }
        });
        layout.addView(graphicsControl);
        TextView viewportLabel = new TextView(this); viewportLabel.setText("Viewport"); layout.addView(viewportLabel);
        viewportControl = new Spinner(this);
        String[] viewports = new String[RuntimeOptions.VIEWPORT_WIDTHS.length];
        for (int i = 0; i < viewports.length; i++) viewports[i] = RuntimeOptions.viewportLabel(i);
        viewportControl.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, viewports));
        viewportControl.setSelection(RuntimeOptions.viewportIndex(options.width, options.height));
        viewportControl.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(AdapterView<?> parent) { }
            @Override public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                if (RuntimeService.active) {
                    RuntimeOptions current = RuntimeOptions.read(LauncherActivity.this);
                    viewportControl.setSelection(RuntimeOptions.viewportIndex(current.width, current.height));
                    RuntimeService.status = "Quit the game before changing runtime settings.";
                    return;
                }
                RuntimeOptions current = RuntimeOptions.read(LauncherActivity.this);
                RuntimeOptions.save(LauncherActivity.this, current.graphics,
                    RuntimeOptions.VIEWPORT_WIDTHS[position], RuntimeOptions.VIEWPORT_HEIGHTS[position]);
            }
        });
        layout.addView(viewportControl);
        updateRuntimeControls();
    }

    private void updateRuntimeControls() {
        if (graphicsControl == null) return;
        boolean enabled = !RuntimeService.active;
        graphicsControl.setEnabled(enabled);
        viewportControl.setEnabled(enabled);
    }
    private void openGameForQuit() {
        if (!RuntimeService.active) { RuntimeService.status = "No game is running."; return; }
        if (!RuntimeService.displayReady) { RuntimeService.status = "Game display is not ready yet."; return; }
        openedGame = true;
        startService(new Intent(this, RuntimeService.class).setAction(RuntimeService.QUIT));
        startActivity(new Intent(this, GameActivity.class));
    }
    private void confirmForceStop() {
        if (!RuntimeService.active) { RuntimeService.status = "No game is running."; return; }
        new android.app.AlertDialog.Builder(this).setTitle("Force-stop recovery?")
            .setMessage("Use this only when normal Quit cannot close the game. Unsaved progress may be lost.")
            .setPositiveButton("Force-stop", (d, w) -> {
                RuntimeService.status = "Force-stopping game…";
                startService(new Intent(this, RuntimeService.class).setAction(RuntimeService.STOP));
            })
            .setNegativeButton("Keep playing", null).show();
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        boolean nextManagementMode = isManagementIntent(intent);
        if (managementMode != nextManagementMode) {
            recreate();
            return;
        }
        managementMode = nextManagementMode;
        manuallyStarted = false;
        openedGame = false;
        if (!managementMode) startGame();
    }
    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }

    private static boolean isManagementIntent(Intent intent) {
        return intent != null && (intent.getBooleanExtra(MANAGE_SAVES_EXTRA, false)
            || "true".equalsIgnoreCase(intent.getStringExtra(MANAGE_SAVES_EXTRA)));
    }
}
