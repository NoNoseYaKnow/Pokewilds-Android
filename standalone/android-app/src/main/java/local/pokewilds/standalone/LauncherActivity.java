package local.pokewilds.standalone;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import java.io.IOException;

public final class LauncherActivity extends Activity {
    public static final String MANAGE_SAVES_EXTRA = "local.pokewilds.standalone.MANAGE_SAVES";
    private static boolean autoDownloadAttempted;
    private final Handler handler = new Handler();
    private TextView statusView;
    private LinearLayout acquisitionPanel;
    private LinearLayout gamePanel;
    private Button downloadButton;
    private Button localZipButton;
    private Button localFolderButton;
    private Button cancelAcquisitionButton;
    private boolean openedGame;
    private boolean managementMode;
    private boolean manuallyStarted;
    private boolean gameStartRequested;
    private Spinner graphicsControl;
    private Spinner viewportControl;
    private Spinner touchControl;
    private final java.util.concurrent.ExecutorService files = java.util.concurrent.Executors.newSingleThreadExecutor();
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            boolean gameInstalled = GameInstaller.isInstalled(LauncherActivity.this);
            if (!gameInstalled) {
                showAcquisitionPanel();
                statusView.setText(GameAcquisitionService.status);
                boolean acquiring = GameAcquisitionService.active;
                downloadButton.setEnabled(!acquiring);
                localZipButton.setEnabled(true);
                localFolderButton.setEnabled(true);
                cancelAcquisitionButton.setEnabled(acquiring);
                if (!managementMode && "Game files ready".equals(GameAcquisitionService.status)) startGame();
            } else {
                showGamePanel();
                statusView.setText(RuntimeService.status);
                if (!managementMode && !gameStartRequested && !GameAcquisitionService.active) startGame();
            }
            if (openedGame && !RuntimeService.active && "Game closed".equals(RuntimeService.status)) {
                openedGame = false;
                manuallyStarted = false;
                gameStartRequested = false;
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
        autoDownloadAttempted = getPreferences(MODE_PRIVATE).getBoolean("auto-download-attempted", false);
        managementMode = isManagementIntent(getIntent());
        if (RuntimeService.DATA_LOCK.tryAcquire()) {
            try { GameInstaller.discardInterruptedFiles(getFilesDir().toPath()); }
            catch (IOException error) { GameAcquisitionService.status = "Could not clean up interrupted game setup: " + error.getMessage(); }
            finally { RuntimeService.DATA_LOCK.release(); }
        }
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 48, 32, 32);
        TextView title = new TextView(this); title.setText("PokeWilds"); title.setTextSize(30); layout.addView(title);
        statusView = new TextView(this); statusView.setTextSize(18); statusView.setText(GameAcquisitionService.status); layout.addView(statusView);
        acquisitionPanel = new LinearLayout(this); acquisitionPanel.setOrientation(LinearLayout.VERTICAL);
        TextView acquisitionHelp = new TextView(this);
        acquisitionHelp.setText("Game files are needed before you can play. Download them or choose a local game ZIP or extracted game folder.");
        acquisitionPanel.addView(acquisitionHelp);
        downloadButton = new Button(this); downloadButton.setText("Download game files");
        downloadButton.setOnClickListener(v -> startAcquisition(GameAcquisitionService.DOWNLOAD, null));
        acquisitionPanel.addView(downloadButton);
        localZipButton = new Button(this); localZipButton.setText("Choose game ZIP");
        localZipButton.setOnClickListener(v -> chooseLocalZip()); acquisitionPanel.addView(localZipButton);
        localFolderButton = new Button(this); localFolderButton.setText("Choose game folder");
        localFolderButton.setOnClickListener(v -> chooseLocalFolder()); acquisitionPanel.addView(localFolderButton);
        cancelAcquisitionButton = new Button(this); cancelAcquisitionButton.setText("Cancel setup");
        cancelAcquisitionButton.setOnClickListener(v -> startAcquisition(GameAcquisitionService.CANCEL, null));
        acquisitionPanel.addView(cancelAcquisitionButton);
        Button earlyImport = new Button(this); earlyImport.setText("Import saves");
        earlyImport.setOnClickListener(v -> chooseSaveImport());
        acquisitionPanel.addView(earlyImport);
        layout.addView(acquisitionPanel);

        gamePanel = new LinearLayout(this); gamePanel.setOrientation(LinearLayout.VERTICAL);
        Button start = new Button(this); start.setText("Start / resume");
        start.setOnClickListener(v -> { openedGame = false; manuallyStarted = true; startGame(); }); gamePanel.addView(start);
        Button forceStop = new Button(this); forceStop.setText("Force-stop recovery");
        forceStop.setOnClickListener(v -> confirmForceStop()); gamePanel.addView(forceStop);
        Button quit = new Button(this); quit.setText("Quit game");
        quit.setOnClickListener(v -> openGameForQuit()); gamePanel.addView(quit);
        if (managementMode) {
            addRuntimeControls(gamePanel);
            Button gameSettings = new Button(this); gameSettings.setText("PokeWilds game settings");
            gameSettings.setOnClickListener(v -> startActivity(new Intent(this, GameSettingsActivity.class)));
            gamePanel.addView(gameSettings);
        }
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
        }); gamePanel.addView(logs);
        Button export = new Button(this); export.setText("Export saves");
        export.setOnClickListener(v -> {
            if (RuntimeService.active) { RuntimeService.status = "Save and quit the game before exporting."; return; }
            startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"pokewilds-saves.zip"), 10);
        }); gamePanel.addView(export);
        Button importButton = new Button(this); importButton.setText("Import saves");
        importButton.setOnClickListener(v -> chooseSaveImport()); gamePanel.addView(importButton);
        layout.addView(gamePanel);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(layout); setContentView(scroll);
        if (GameInstaller.isInstalled(this)) {
            showGamePanel();
            if (!managementMode && !GameAcquisitionService.active) startGame();
        } else {
            showAcquisitionPanel();
            maybeStartAutoDownload();
        }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        if (request == 12 || request == 13) {
            android.net.Uri source = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(source,
                    data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
            } catch (SecurityException ignored) { /* This provider did not offer persistent access. */ }
            startAcquisition(request == 12 ? GameAcquisitionService.LOCAL_ZIP : GameAcquisitionService.LOCAL_FOLDER, source);
            return;
        }
        if (RuntimeService.active) { setSaveStatus("Quit the game first."); return; }
        setSaveStatus(request == 10 ? "Exporting saves…" : "Importing saves…");
        files.submit(() -> {
            if (!RuntimeService.DATA_LOCK.tryAcquire()) { setSaveStatus("Wait for game setup or quit the game before transferring saves."); return; }
            try {
                java.nio.file.Path game = new java.io.File(getFilesDir(), "game").toPath();
                if (request == 10) SaveArchive.exportTo(game,getContentResolver().openOutputStream(data.getData()));
                else SaveArchive.importFrom(getContentResolver().openInputStream(data.getData()), game);
                setSaveStatus(request == 10 ? "Saves exported" : "Saves imported");
            } catch (Exception e) { setSaveStatus("Save transfer failed: " + e.getMessage()); }
            finally { RuntimeService.DATA_LOCK.release(); }
        });
    }
    @Override protected void onDestroy() { files.shutdown(); super.onDestroy(); }
    private void startGame() {
        if (!GameInstaller.isInstalled(this)) return;
        gameStartRequested = true;
        startForegroundService(new Intent(this, RuntimeService.class));
    }

    private void chooseLocalZip() {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/zip")
            .addCategory(Intent.CATEGORY_OPENABLE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(picker, 12);
    }

    private void chooseSaveImport() {
        if (RuntimeService.active) { setSaveStatus("Quit the game before importing."); return; }
        if (GameAcquisitionService.active) { setSaveStatus("Cancel or finish game setup before importing saves."); return; }
        startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/zip")
            .addCategory(Intent.CATEGORY_OPENABLE), 11);
    }

    private void setSaveStatus(String value) {
        RuntimeService.status = value;
        GameAcquisitionService.status = value;
    }

    private void chooseLocalFolder() {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(picker, 13);
    }

    private void startAcquisition(String action, android.net.Uri source) {
        if (GameAcquisitionService.DOWNLOAD.equals(action)) markDownloadAttempted();
        Intent intent = new Intent(this, GameAcquisitionService.class).setAction(action);
        if (source != null) intent.setData(source).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (GameAcquisitionService.CANCEL.equals(action)) startService(intent);
        else startForegroundService(intent);
        showAcquisitionPanel();
        if (GameAcquisitionService.CANCEL.equals(action)) statusView.setText("Cancelling game file setup…");
        else statusView.setText(source == null ? "Starting game file setup…" : "Importing local game files…");
    }

    private void showAcquisitionPanel() {
        if (acquisitionPanel != null) acquisitionPanel.setVisibility(android.view.View.VISIBLE);
        if (gamePanel != null) gamePanel.setVisibility(android.view.View.GONE);
    }

    private void markDownloadAttempted() {
        autoDownloadAttempted = true;
        getPreferences(MODE_PRIVATE).edit().putBoolean("auto-download-attempted", true).apply();
    }

    private void maybeStartAutoDownload() {
        if (GameAcquisitionService.active) { markDownloadAttempted(); return; }
        if (autoDownloadAttempted) return;
        if (new java.io.File(getFilesDir(), "game/pokewilds.jar").isFile()) {
            markDownloadAttempted();
            GameAcquisitionService.status = "Existing game files differ from the official release. Choose Download game files or local files to replace them; saves and settings will be kept.";
            return;
        }
        startAcquisition(GameAcquisitionService.DOWNLOAD, null);
    }

    private void showGamePanel() {
        if (acquisitionPanel != null) acquisitionPanel.setVisibility(android.view.View.GONE);
        if (gamePanel != null) gamePanel.setVisibility(android.view.View.VISIBLE);
    }

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
                RuntimeOptions.save(LauncherActivity.this, RuntimeOptions.GRAPHICS_VALUES[position], current.autoViewport ? 0 : current.width, current.autoViewport ? 0 : current.height);
            }
        });
        layout.addView(graphicsControl);
        TextView viewportLabel = new TextView(this); viewportLabel.setText("Viewport"); layout.addView(viewportLabel);
        viewportControl = new Spinner(this);
        String[] viewports = new String[RuntimeOptions.VIEWPORT_WIDTHS.length];
        for (int i = 0; i < viewports.length; i++) viewports[i] = RuntimeOptions.viewportLabel(i);
        viewportControl.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, viewports));
        viewportControl.setSelection(RuntimeOptions.viewportIndex(options));
        viewportControl.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(AdapterView<?> parent) { }
            @Override public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                if (RuntimeService.active) {
                    RuntimeOptions current = RuntimeOptions.read(LauncherActivity.this);
                    viewportControl.setSelection(RuntimeOptions.viewportIndex(current));
                    RuntimeService.status = "Quit the game before changing runtime settings.";
                    return;
                }
                RuntimeOptions current = RuntimeOptions.read(LauncherActivity.this);
                RuntimeOptions.save(LauncherActivity.this, current.graphics,
                    RuntimeOptions.VIEWPORT_WIDTHS[position], RuntimeOptions.VIEWPORT_HEIGHTS[position]);
            }
        });
        layout.addView(viewportControl);
        TextView touchLabel = new TextView(this); touchLabel.setText("On-screen controls"); layout.addView(touchLabel);
        touchControl = new Spinner(this);
        touchControl.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item,
            TouchControls.TOUCH_MODE_LABELS));
        touchControl.setSelection(TouchControls.readTouchMode(this));
        touchControl.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(AdapterView<?> parent) { }
            @Override public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                if (RuntimeService.active) {
                    touchControl.setSelection(TouchControls.readTouchMode(LauncherActivity.this));
                    RuntimeService.status = "Quit the game before changing runtime settings.";
                    return;
                }
                TouchControls.saveTouchMode(LauncherActivity.this, position);
            }
        });
        layout.addView(touchControl);
        TextView keyboardLabel = new TextView(this);
        keyboardLabel.setText("Show keyboard with gamepad"); layout.addView(keyboardLabel);
        Spinner keyboardControl = new Spinner(this);
        keyboardControl.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item,
            KeyboardShortcut.LABELS));
        keyboardControl.setSelection(KeyboardShortcut.read(this));
        keyboardControl.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(AdapterView<?> parent) { }
            @Override public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                KeyboardShortcut.save(LauncherActivity.this, position);
            }
        });
        layout.addView(keyboardControl);
        updateRuntimeControls();
    }

    private void updateRuntimeControls() {
        if (graphicsControl == null) return;
        boolean enabled = !RuntimeService.active;
        graphicsControl.setEnabled(enabled);
        viewportControl.setEnabled(enabled);
        touchControl.setEnabled(enabled);
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
        gameStartRequested = false;
        if (!managementMode && GameInstaller.isInstalled(this) && !GameAcquisitionService.active) startGame();
        else if (!GameInstaller.isInstalled(this)) {
            maybeStartAutoDownload();
        }
    }
    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }

    private static boolean isManagementIntent(Intent intent) {
        return intent != null && (intent.getBooleanExtra(MANAGE_SAVES_EXTRA, false)
            || "true".equalsIgnoreCase(intent.getStringExtra(MANAGE_SAVES_EXTRA)));
    }
}
