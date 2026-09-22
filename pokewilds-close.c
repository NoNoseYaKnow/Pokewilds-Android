#include <X11/Xlib.h>
#include <stdlib.h>
#include <string.h>
int main(int argc, char **argv) {
    if (argc != 2) return 2;
    Display *display = XOpenDisplay(NULL);
    if (!display) return 3;
    Window window = strtoul(argv[1], NULL, 0);
    XEvent event;
    memset(&event, 0, sizeof(event));
    event.xclient.type = ClientMessage;
    event.xclient.window = window;
    event.xclient.message_type = XInternAtom(display, "WM_PROTOCOLS", False);
    event.xclient.format = 32;
    event.xclient.data.l[0] = XInternAtom(display, "WM_DELETE_WINDOW", False);
    event.xclient.data.l[1] = CurrentTime;
    int sent = XSendEvent(display, window, False, NoEventMask, &event);
    XSync(display, False);
    XCloseDisplay(display);
    return sent ? 0 : 4;
}
