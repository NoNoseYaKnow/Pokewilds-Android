package local.pokewilds.standalone;

import android.app.*;
import android.content.Intent;
import android.os.IBinder;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Owns all processes for one game session; no commands are accepted from intents. */
public final class RuntimeService extends Service {
    public static final String QUIT = "local.pokewilds.standalone.QUIT";
    public static final String STOP = "local.pokewilds.standalone.STOP";
    static volatile String status = "Ready";
    static volatile boolean running;
    static volatile boolean active;
    static volatile boolean displayReady;
    static volatile boolean surfaceReady;
    static final Semaphore DATA_LOCK = new Semaphore(1);
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Process> processes = Collections.synchronizedList(new ArrayList<>());
    private Future<?> session;
    private volatile File runtime;
    private File tmp, log, nativeDir;
    private volatile boolean stopping;
    private volatile boolean forcedStop;

    @Override public void onCreate() {
        super.onCreate();
        tmp = new File(getFilesDir(), "shared-tmp"); tmp.mkdirs();
        log = new File(getFilesDir(), "session.log");
        nativeDir = new File(getApplicationInfo().nativeLibraryDir);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("game", "PokeWilds session", NotificationManager.IMPORTANCE_LOW));
    }
    @Override public int onStartCommand(Intent intent, int flags, int id) {
        Intent quit = new Intent(this, RuntimeService.class).setAction(QUIT);
        Notification note = new Notification.Builder(this, "game").setContentTitle("PokeWilds")
            .setContentText("Game session active").setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, LauncherActivity.class)
                .putExtra(LauncherActivity.MANAGE_SAVES_EXTRA, true), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT))
            .addAction(new Notification.Action.Builder(null, "Quit", PendingIntent.getService(this, 1, quit, PendingIntent.FLAG_IMMUTABLE)).build()).build();
        startForeground(100, note);
        if (intent != null && STOP.equals(intent.getAction())) {
            forcedStop = true; stopping = true; status = "Stopped without saving"; cleanup();
            if (!active) stopSelf();
        } else if (intent != null && QUIT.equals(intent.getAction())) {
            if (running) new Thread(this::requestQuit, "game-quit").start();
            else if (active) { stopping = true; status = "Cancelling startup…"; cleanup(); }
            else stopSelf();
        } else if (session == null || session.isDone()) {
            if (!DATA_LOCK.tryAcquire()) { status = "Save transfer in progress; try again when it finishes."; stopForeground(true); stopSelf(); return START_NOT_STICKY; }
            stopping = false; forcedStop = false; active = true;
            session = worker.submit(this::runSession);
        }
        return START_NOT_STICKY;
    }
    private void runSession() {
        try {
            Files.write(log.toPath(), new byte[0]);
            status = "Preparing bundled game files…";
            runtime = PayloadInstaller.install(this, () -> stopping || Thread.currentThread().isInterrupted());
            if (stopping) throw new InterruptedException("Startup cancelled");
            File game = new File(getFilesDir(), "game");
            if (!new File(game, ".distribution-ready").isFile()) {
                copyGame(new File(runtime, "game").toPath(), game.toPath());
                Files.write(new File(game, ".distribution-ready").toPath(), new byte[]{1});
            }
            for (String name : new String[]{"libproot.so", "libproot-loader.so", "libvirgl_test_server_android.so", "libpulseaudio.so"}) {
                if (!new File(nativeDir, name).isFile()) throw new IOException("Build is missing native runtime component: " + name);
            }
            SafeTar.deleteTree(tmp.toPath());
            if (!tmp.mkdirs()) throw new IOException("Cannot prepare session sockets");
            status = "Starting display…";
            Process x11 = start(Arrays.asList("/system/bin/app_process", "/", "com.termux.x11.CmdEntryPoint", ":0", "-ac", "-nolisten", "tcp"));
            waitForSocket(new File(tmp, ".X11-unix/X0"), x11);
            displayReady = true;
            status = "Starting GPU and audio…";
            if (!BuildConfig.RUNTIME_GRAPHICS.equals("software")) {
                List<String> gpuArgs = new ArrayList<>(Arrays.asList(new File(nativeDir, "libvirgl_test_server_android.so").toString(), "--no-fork", "--socket-path", new File(tmp, ".virgl_test").toString()));
                if (!BuildConfig.RUNTIME_GRAPHICS.equals("native")) gpuArgs.add("--" + BuildConfig.RUNTIME_GRAPHICS);
                Process gpu = start(gpuArgs);
                waitForSocket(new File(tmp, ".virgl_test"), gpu);
            }
            // Use a private UNIX socket shared with the guest, never a public audio port.
            // PRoot's guest/host credentials prevent Pulse's shared-memory
            // negotiation from agreeing. Keep audio on the private socket.
            Process pulse = start(Arrays.asList(new File(nativeDir, "libpulseaudio.so").toString(), "--daemonize=no", "--exit-idle-time=-1", "--disable-shm=yes", "--enable-memfd=no", "--use-pid-file=no", "--dl-search-path=" + new File(runtime, "host/lib/pulseaudio/modules"), "-n", "--load=module-native-protocol-unix socket=" + new File(tmp, "pulse-native") + " auth-anonymous=1", "--load=module-sles-sink"));
            waitForSocket(new File(tmp, "pulse-native"), pulse);
            // Attach the Android surface before GLFW chooses its initial size.
            // Opening it after the game starts clips the desktop menu.
            for (int i=0; i<120 && !surfaceReady && !stopping; i++) Thread.sleep(250);
            if (!surfaceReady) throw new IOException("Display surface did not connect");
            status = "Starting PokeWilds…";
            Process gameProcess = start(guest(Arrays.asList("/usr/bin/java", "-Dorg.lwjgl.system.allocator=system", "-Dorg.lwjgl.glfw.window.fullscreen=true", "-jar", "/game/pokewilds.jar")));
            // Do not report ready just because Java exists: require the game's X11 window.
            boolean window = false;
            for (int i = 0; i < 60 && gameProcess.isAlive() && !stopping; i++) {
                Process probe = start(guest(Arrays.asList("/usr/bin/xdotool", "search", "--name", "^PokeWilds$")));
                if (probe.waitFor(2, TimeUnit.SECONDS) && probe.exitValue() == 0) { window = true; break; }
                probe.destroy(); Thread.sleep(500);
            }
            if (!window) throw new IOException("Game did not open a window; see startup log");
            start(guest(Arrays.asList("/bin/sh", "-c", "w=$(xdotool search --name '^PokeWilds$' | head -n1); xdotool windowsize --sync \"$w\" 480 432 windowmove --sync \"$w\" 0 0"))).waitFor();
            running = true; status = "Running";
            int code = gameProcess.waitFor();
            status = forcedStop ? "Stopped without saving" : code == 0 ? "Game closed" : "Game exited with code " + code + ". See startup log.";
        } catch (Exception e) {
            status = forcedStop ? "Stopped without saving" : stopping ? "Startup cancelled" : "Unable to start: " + e.getMessage();
            try (PrintWriter out = new PrintWriter(new FileOutputStream(log, true))) { e.printStackTrace(out); } catch (IOException ignored) { }
        } finally {
            running = false; displayReady = false; surfaceReady = false; active = false;
            cleanup(); DATA_LOCK.release(); stopForeground(true); stopSelf();
        }
    }
    private Process start(List<String> args) throws IOException {
        if (stopping) throw new IOException("Session stopped");
        ProcessBuilder b = new ProcessBuilder(args).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(log));
        Map<String,String> env = b.environment();
        env.put("HOME", getFilesDir().toString()); env.put("TMPDIR", tmp.toString()); env.put("XDG_RUNTIME_DIR", tmp.toString());
        env.put("CLASSPATH", getApplicationInfo().sourceDir);
        env.put("POKEWILDS_NATIVE_DIR", nativeDir.toString());
        env.put("XKB_CONFIG_ROOT", new File(runtime, "rootfs/usr/share/X11/xkb").toString());
        String host = new File(runtime, "host").toString();
        if (args.get(0).equals(new File(nativeDir, "libvirgl_test_server_android.so").toString())) b.directory(new File(host));
        // app_process must use Android's framework libraries, not Termux's
        // namespace shims (notably libbinder_ndk/libandroid).
        env.put("LD_LIBRARY_PATH", args.get(0).equals("/system/bin/app_process") ? nativeDir.toString() :
            nativeDir + ":" + host + "/lib:" + host + "/lib/pulseaudio:" + host + "/opt/virglrenderer-android/lib:" + host + "/opt/angle-android/" + (BuildConfig.RUNTIME_GRAPHICS.equals("angle-vulkan") ? "vulkan" : "gl"));
        env.put("PREFIX", host);
        env.put("PULSE_CONFIG_PATH", host + "/etc/pulse");
        env.put("PULSE_RUNTIME_PATH", new File(getFilesDir(), "pulse").toString());
        env.put("PULSE_STATE_PATH", new File(getFilesDir(), "pulse-state").toString());
        env.put("PULSE_DLPATH", host + "/lib/pulseaudio/modules");
        env.put("PROOT_TMP_DIR", tmp.toString()); env.put("PROOT_LOADER", new File(nativeDir, "libproot-loader.so").toString());
        Process p = b.start(); processes.add(p); return p;
    }
    private List<String> guest(List<String> command) {
        List<String> args = new ArrayList<>(Arrays.asList(new File(nativeDir, "libproot.so").toString(), "--kill-on-exit", "-0", "-r", new File(runtime, "rootfs").toString(), "-b", "/dev", "-b", "/proc", "-b", "/sys", "-b", tmp + ":/tmp", "-b", new File(getFilesDir(), "game") + ":/game", "-w", "/game", "/usr/bin/env", "-i", "HOME=/root", "PATH=/usr/local/bin:/usr/bin:/bin", "TMPDIR=/tmp", "DISPLAY=:0", "XDG_RUNTIME_DIR=/tmp", "PULSE_SERVER=unix:/tmp/pulse-native", "ALSOFT_DRIVERS=pulse", "ALSOFT_LOGLEVEL=" + (BuildConfig.DEBUG ? "3" : "1"), "GALLIUM_DRIVER=" + (BuildConfig.RUNTIME_GRAPHICS.equals("software") ? "llvmpipe" : "virpipe"), "__GLX_VENDOR_LIBRARY_NAME=mesa", "MESA_GL_VERSION_OVERRIDE=3.3", "LANG=C.UTF-8"));
        args.addAll(command); return args;
    }
    private void waitForSocket(File socket, Process owner) throws Exception {
        for (int i=0; i<100; i++) {
            if (!owner.isAlive()) throw new IOException("Runtime server exited before readiness: " + socket.getName());
            try { if (android.system.OsConstants.S_ISSOCK(android.system.Os.stat(socket.toString()).st_mode)) return; }
            catch (android.system.ErrnoException notReady) { /* wait for the server to bind */ }
            Thread.sleep(100);
        }
        throw new IOException("Runtime server timed out: " + socket.getName());
    }
    private void requestQuit() {
        try {
            status = "Waiting for the game's save / quit dialog…";
            // WM_DELETE_WINDOW preserves the desktop game's own save/cancel handling.
            start(guest(Arrays.asList("/bin/sh", "-c", "w=$(xdotool search --name '^PokeWilds$' | head -n1); test -n \"$w\" && /usr/local/bin/pokewilds-close \"$w\""))).waitFor();
        } catch (Exception e) { status = "Quit request failed: " + e.getMessage(); }
    }
    private static void copyGame(Path source, Path target) throws IOException {
        Path stage = target.resolveSibling("game.preparing"); SafeTar.deleteTree(stage);
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            public FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes a) throws IOException { Files.createDirectories(stage.resolve(source.relativize(dir))); return FileVisitResult.CONTINUE; }
            public FileVisitResult visitFile(Path f, java.nio.file.attribute.BasicFileAttributes a) throws IOException { Files.copy(f, stage.resolve(source.relativize(f))); return FileVisitResult.CONTINUE; }
        });
        if (Files.exists(target)) {
            // Preserve pre-launch imported worlds/settings; only add missing distribution files.
            Files.walkFileTree(stage, new SimpleFileVisitor<Path>() {
                public FileVisitResult preVisitDirectory(Path p, java.nio.file.attribute.BasicFileAttributes a) throws IOException { Files.createDirectories(target.resolve(stage.relativize(p))); return FileVisitResult.CONTINUE; }
                public FileVisitResult visitFile(Path p, java.nio.file.attribute.BasicFileAttributes a) throws IOException {
                    Path relative=stage.relativize(p); Path dest=target.resolve(relative);
                    String top=relative.getName(0).toString();
                    boolean userData=top.equals("settings.txt") || top.equals("mods") || top.endsWith(".sav") || top.endsWith(".sav.zip");
                    if (!userData || !Files.exists(dest)) {
                        Path temp=dest.resolveSibling(dest.getFileName()+".preparing");
                        Files.copy(p,temp,StandardCopyOption.REPLACE_EXISTING);
                        Files.move(temp,dest,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            SafeTar.deleteTree(stage);
        } else Files.move(stage, target);
    }
    private void cleanup() {
        synchronized (processes) {
            for (int i=processes.size()-1; i>=0; i--) {
                Process p=processes.get(i);
                if (p.isAlive()) {
                    p.destroy();
                    try { if (!p.waitFor(500, TimeUnit.MILLISECONDS)) p.destroyForcibly(); }
                    catch (InterruptedException interrupted) { p.destroyForcibly(); Thread.currentThread().interrupt(); }
                }
            }
            processes.clear();
        }
    }
    @Override public void onDestroy() { stopping = true; cleanup(); worker.shutdownNow(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
