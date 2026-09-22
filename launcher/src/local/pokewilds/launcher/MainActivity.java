package local.pokewilds.launcher;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.os.Build;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

public final class MainActivity extends Activity {
    private static final String PERMISSION = "com.termux.permission.RUN_COMMAND";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { PERMISSION }, 1);
        } else {
            prepareLaunch();
        }
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == 2) {
            launchGame();
            return;
        }
        if (code == 1 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            prepareLaunch();
        } else {
            showError("Allow PokeWilds to run commands in Termux, then open it again.");
        }
    }

    private void prepareLaunch() {
        if (!"local.pokewilds.QUIT".equals(getIntent().getAction()) && Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { "android.permission.POST_NOTIFICATIONS" }, 2);
        } else {
            launchGame();
        }
    }

    private void showControls() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("controls", "PokeWilds controls", NotificationManager.IMPORTANCE_LOW));
        Intent quit = new Intent(this, MainActivity.class).setAction("local.pokewilds.QUIT");
        PendingIntent quitAction = PendingIntent.getActivity(this, 1, quit, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent open = new Intent(this, MainActivity.class).setAction(Intent.ACTION_MAIN);
        PendingIntent openAction = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, "controls")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("PokeWilds")
            .setContentText("Tap to open. Use Quit for the game's save-and-exit prompt.")
            .setContentIntent(openAction)
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "Quit", quitAction).build())
            .setOnlyAlertOnce(true).build();
        manager.notify(1, notification);
    }

    private void launchGame() {
        try {
            boolean quitting = "local.pokewilds.QUIT".equals(getIntent().getAction());
            Intent command = new Intent("com.termux.RUN_COMMAND");
            command.setClassName("com.termux", "com.termux.app.RunCommandService");
            command.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
            command.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[] { quitting ? "/data/data/com.termux/files/home/pokewilds-quit.sh" : "/data/data/com.termux/files/home/pokewilds.sh" });
            command.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home");
            command.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
            command.putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", "PokeWilds");
            if (startForegroundService(command) == null) {
                showError("Install and open Termux first, then try again.");
                return;
            }
            if (quitting) {
                getSystemService(NotificationManager.class).cancel(1);
            } else {
                showControls();
            }
            Intent display = new Intent(Intent.ACTION_MAIN);
            display.setClassName("com.termux.x11", "com.termux.x11.MainActivity");
            display.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(display);
            finish();
        } catch (Exception e) {
            showError("PokeWilds could not start. Check that Termux and Termux:X11 are installed and the PokeWilds setup is present.\n\n" + e.getMessage());
        }
    }

    private void showError(String message) {
        new AlertDialog.Builder(this).setTitle("PokeWilds").setMessage(message)
            .setPositiveButton("Close", (dialog, which) -> finish())
            .setOnCancelListener(dialog -> finish()).show();
    }
}
