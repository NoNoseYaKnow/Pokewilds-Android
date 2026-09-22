package local.pokewilds.standalone;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class LauncherActivity extends Activity {
    public static final String MANAGE_SAVES_EXTRA = "local.pokewilds.standalone.MANAGE_SAVES";
    private final Handler handler = new Handler();
    private TextView statusView;
    private boolean openedGame;
    private boolean managementMode;
    private boolean manuallyStarted;
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
        managementMode = isManagementIntent(intent);
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
