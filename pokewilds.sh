#!/data/data/com.termux/files/usr/bin/bash
set -eu
export HOME=/data/data/com.termux/files/home
export PREFIX=/data/data/com.termux/files/usr
export PATH=$PREFIX/bin:/system/bin
export TMPDIR=$PREFIX/tmp
export XDG_RUNTIME_DIR=$TMPDIR
cd "$HOME"
# The APK can be tapped repeatedly while the game is starting.
exec 9> "$HOME/.pokewilds-launch.lock"
if ! flock -n 9; then
 am start -n com.termux.x11/.MainActivity || true
 exit 0
fi
exec > "$HOME/pokewilds-launch.log" 2>&1
pulseaudio --start --load='module-native-protocol-tcp listen=127.0.0.1 auth-ip-acl=127.0.0.1 auth-anonymous=1' --exit-idle-time=-1 9>&-
# An X11 abstract socket can still answer xdotool after its filesystem
# socket disappears; the guest GLFW client needs the filesystem socket too.
if [ ! -S "$TMPDIR/.X11-unix/X0" ] || ! DISPLAY=:0 xdotool getdisplaygeometry >/dev/null 2>&1; then
 pkill -f '^termux-x11 com.termux.x11 :0 -ac$' || true
 for _attempt in $(seq 1 30); do
  pgrep -f '^termux-x11 com.termux.x11 :0 -ac$' >/dev/null || break
  sleep 0.1
 done
 termux-x11 :0 -ac > "$HOME/pokewilds-x11.log" 2>&1 9>&- &
 for _attempt in $(seq 1 50); do
  if [ -S "$TMPDIR/.X11-unix/X0" ] && DISPLAY=:0 xdotool getdisplaygeometry >/dev/null 2>&1; then break; fi
  sleep 0.1
 done
 [ -S "$TMPDIR/.X11-unix/X0" ] && DISPLAY=:0 xdotool getdisplaygeometry >/dev/null 2>&1 || { echo 'X11 display failed to start.'; exit 1; }
fi
# A surviving VirGL process without its listening socket cannot serve Java.
if [ ! -S "$TMPDIR/.virgl_test" ] || ! pgrep -f '^virgl_test_server_android($| )' >/dev/null; then
 pkill -f '^virgl_test_server_android($| )' || true
 for _attempt in $(seq 1 30); do
  pgrep -f '^virgl_test_server_android($| )' >/dev/null || break
  sleep 0.1
 done
 nohup virgl_test_server_android > "$HOME/pokewilds-gpu.log" 2>&1 < /dev/null 9>&- &
 for _attempt in $(seq 1 50); do
  [ -S "$TMPDIR/.virgl_test" ] && break
  sleep 0.1
 done
 [ -S "$TMPDIR/.virgl_test" ] || { echo 'GPU server failed to start.'; exit 1; }
fi
am start -n com.termux.x11/.MainActivity || true
export DISPLAY=:0
existing_window=$(xdotool search --name '^PokeWilds$' 2>/dev/null | head -n 1 || true)
if [ -n "$existing_window" ]; then
 xdotool windowmove "$existing_window" 0 0 windowsize "$existing_window" 480 432
 exit 0
fi
(
 for _attempt in $(seq 1 60); do
  game_window=$(xdotool search --name '^PokeWilds$' 2>/dev/null | head -n 1 || true)
  if [ -n "$game_window" ]; then
   xdotool windowmove "$game_window" 0 0 windowsize "$game_window" 480 432
   break
  fi
  sleep 1
 done
) 9>&- &
proot-distro login ubuntu --shared-tmp -- bash -c '
set -eu
export DISPLAY=:0
export XDG_RUNTIME_DIR=/tmp
export PULSE_SERVER=127.0.0.1
unset LIBGL_ALWAYS_SOFTWARE
export GALLIUM_DRIVER=virpipe
export __GLX_VENDOR_LIBRARY_NAME=mesa
export MESA_GL_VERSION_OVERRIDE=3.3
if [ -f /root/pokewilds/pokewilds.jar ]; then
 cd /root/pokewilds
elif [ -f /root/pokewilds-download/pokewilds-v0.8.11-otherplatforms/pokewilds.jar ]; then
 cd /root/pokewilds-download/pokewilds-v0.8.11-otherplatforms
else
 echo "PokeWilds is missing. Run pokewilds_install.sh first." >&2
 exit 1
fi
exec java -Dorg.lwjgl.opengl.Display.allowSoftwareOpenGL=true -Dorg.lwjgl.system.allocator=system -Dorg.lwjgl.glfw.window.fullscreen=true -jar pokewilds.jar
'
