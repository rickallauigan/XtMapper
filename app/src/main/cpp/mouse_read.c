#include <stdbool.h>
#include <stdlib.h>
#include <unistd.h>
#include <linux/input.h>
#include <fcntl.h>
#include <jni.h>
#include <pthread.h>
#include <errno.h>
#include <poll.h>
#include <stdio.h>
#include <sys/ioctl.h>
#include <sys/stat.h>

/* One joined reader owns one fd and one pair of JNI references at a time. */
typedef struct input_service_context {
    JavaVM *javaVM;
    jclass inputServiceClz;
    jobject inputServiceObj;
    pthread_mutex_t lock;
    pthread_mutex_t lifecycle;
    pthread_t thread;
    bool started;
    bool done;
    bool finished;
    int mouse_lock;
    int mouse_fd;
} serviceContext;
static serviceContext g_ctx;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void) reserved;
    g_ctx.javaVM = vm;
    g_ctx.mouse_fd = -1;
    pthread_mutex_init(&g_ctx.lock, NULL);
    pthread_mutex_init(&g_ctx.lifecycle, NULL);
    return JNI_VERSION_1_6;
}

static bool is_mouse_event(const struct input_event *event) {
    if (event->type == EV_REL)
        return event->code == REL_X || event->code == REL_Y || event->code == REL_WHEEL;
    if (event->type != EV_KEY || event->value < 0 || event->value > 1) return false;
    return event->code == BTN_MOUSE || event->code == BTN_RIGHT || event->code == BTN_MIDDLE
        || event->code == BTN_EXTRA || event->code == BTN_SIDE;
}

static void *send_mouse_events(void *context) {
    serviceContext *ctx = context;
    JNIEnv *env = NULL;
    if ((*ctx->javaVM)->AttachCurrentThread(ctx->javaVM, (void **) &env, NULL) != JNI_OK) {
        close(ctx->mouse_fd);
        pthread_mutex_lock(&ctx->lock);
        ctx->finished = true;
        pthread_mutex_unlock(&ctx->lock);
        return NULL;
    }
    jmethodID callback = (*env)->GetMethodID(env, ctx->inputServiceClz, "sendMouseEvent", "(II)V");
    int fd = ctx->mouse_fd;
    int applied_lock = -1;
    struct pollfd poll_fd = {.fd = fd, .events = POLLIN};
    while (callback != NULL) {
        pthread_mutex_lock(&ctx->lock);
        bool done = ctx->done;
        int requested_lock = ctx->mouse_lock;
        pthread_mutex_unlock(&ctx->lock);
        if (done) break;
        if (requested_lock != applied_lock) {
            if (ioctl(fd, EVIOCGRAB, requested_lock) < 0) break;
            applied_lock = requested_lock;
        }
        // Bounded poll makes stop independent of the next physical mouse event.
        int ready = poll(&poll_fd, 1, 50);
        if (ready < 0) { if (errno == EINTR) continue; break; }
        if (ready == 0) continue;
        if (poll_fd.revents & (POLLERR | POLLHUP | POLLNVAL)) break;
        struct input_event event;
        ssize_t count = read(fd, &event, sizeof(event));
        if (count < 0 && (errno == EINTR || errno == EAGAIN)) continue;
        if (count != sizeof(event)) break; // disconnect/EOF never replays stale data
        if (requested_lock && is_mouse_event(&event)) {
            (*env)->CallVoidMethod(env, ctx->inputServiceObj, callback, event.code, event.value);
            if ((*env)->ExceptionCheck(env)) {
                (*env)->ExceptionDescribe(env);
                (*env)->ExceptionClear(env);
                break;
            }
        }
    }
    pthread_mutex_lock(&ctx->lock);
    bool disconnected = !ctx->done;
    pthread_mutex_unlock(&ctx->lock);
    // Internal cancellation signal: a lost mouse must release its mapped touches.
    if (disconnected && callback != NULL) {
        (*env)->CallVoidMethod(env, ctx->inputServiceObj, callback, -1, 0);
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    }
    if (applied_lock == 1) ioctl(fd, EVIOCGRAB, 0);
    close(fd);
    pthread_mutex_lock(&ctx->lock);
    ctx->finished = true;
    pthread_mutex_unlock(&ctx->lock);
    (*ctx->javaVM)->DetachCurrentThread(ctx->javaVM);
    return NULL;
}

/* Called with lifecycle locked. Thread alone closes its fd; join precedes reference deletion. */
static void stop_reader(JNIEnv *env) {
    if (!g_ctx.started) return;
    pthread_mutex_lock(&g_ctx.lock);
    g_ctx.done = true;
    pthread_mutex_unlock(&g_ctx.lock);
    pthread_join(g_ctx.thread, NULL);
    (*env)->DeleteGlobalRef(env, g_ctx.inputServiceClz);
    (*env)->DeleteGlobalRef(env, g_ctx.inputServiceObj);
    g_ctx.inputServiceClz = NULL;
    g_ctx.inputServiceObj = NULL;
    g_ctx.mouse_fd = -1;
    g_ctx.started = false;
}

JNIEXPORT jint JNICALL Java_xtr_keymapper_server_InputService_openDevice(JNIEnv *env, jobject thiz, jstring device) {
    pthread_mutex_lock(&g_ctx.lifecycle);
    const char *path = (*env)->GetStringUTFChars(env, device, NULL);
    int fd = open(path, O_RDONLY | O_NONBLOCK | O_CLOEXEC);
    (*env)->ReleaseStringUTFChars(env, device, path);
    if (fd >= 0) {
        unsigned long key_bits[(KEY_MAX + 8 * sizeof(unsigned long)) / (8 * sizeof(unsigned long))] = {0};
        unsigned long rel_bits = 0;
        if (ioctl(fd, EVIOCGBIT(EV_KEY, sizeof(key_bits)), key_bits) < 0
                || ioctl(fd, EVIOCGBIT(EV_REL, sizeof(rel_bits)), &rel_bits) < 0
                || !(key_bits[BTN_MOUSE / (8 * sizeof(unsigned long))] & (1UL << (BTN_MOUSE % (8 * sizeof(unsigned long)))))
                || !(rel_bits & (1UL << REL_X)) || !(rel_bits & (1UL << REL_Y))) {
            close(fd); fd = -1;
        }
    }
    if (fd >= 0) {
        // Reject non-mouse probes before replacing the working reader.
        stop_reader(env);
        jclass clz = (*env)->GetObjectClass(env, thiz);
        g_ctx.inputServiceClz = (*env)->NewGlobalRef(env, clz);
        (*env)->DeleteLocalRef(env, clz);
        g_ctx.inputServiceObj = (*env)->NewGlobalRef(env, thiz);
        g_ctx.mouse_fd = fd;
        pthread_mutex_lock(&g_ctx.lock);
        g_ctx.done = false;
        g_ctx.finished = false;
        pthread_mutex_unlock(&g_ctx.lock);
        int error = pthread_create(&g_ctx.thread, NULL, send_mouse_events, &g_ctx);
        if (error == 0) g_ctx.started = true;
        else {
            close(fd);
            (*env)->DeleteGlobalRef(env, g_ctx.inputServiceClz);
            (*env)->DeleteGlobalRef(env, g_ctx.inputServiceObj);
            g_ctx.inputServiceClz = NULL; g_ctx.inputServiceObj = NULL; g_ctx.mouse_fd = -1;
            fd = -1;
        }
    }
    pthread_mutex_unlock(&g_ctx.lifecycle);
    return fd;
}

JNIEXPORT void JNICALL Java_xtr_keymapper_server_InputService_stopMouse(JNIEnv *env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_ctx.lifecycle);
    stop_reader(env);
    pthread_mutex_unlock(&g_ctx.lifecycle);
}

JNIEXPORT void JNICALL Java_xtr_keymapper_server_InputService_setMouseLock(JNIEnv *env, jobject thiz, jboolean lock) {
    (void) env; (void) thiz;
    pthread_mutex_lock(&g_ctx.lock);
    g_ctx.mouse_lock = lock == JNI_TRUE;
    pthread_mutex_unlock(&g_ctx.lock);
}

JNIEXPORT jboolean JNICALL Java_xtr_keymapper_server_InputService_isMouseDeviceCurrent(JNIEnv *env, jobject thiz, jstring device) {
    (void) thiz;
    pthread_mutex_lock(&g_ctx.lifecycle);
    pthread_mutex_lock(&g_ctx.lock);
    bool alive = g_ctx.started && !g_ctx.finished;
    pthread_mutex_unlock(&g_ctx.lock);
    const char *path = (*env)->GetStringUTFChars(env, device, NULL);
    struct stat opened, current;
    bool matches = alive && fstat(g_ctx.mouse_fd, &opened) == 0 && stat(path, &current) == 0
            && opened.st_dev == current.st_dev && opened.st_ino == current.st_ino;
    (*env)->ReleaseStringUTFChars(env, device, path);
    pthread_mutex_unlock(&g_ctx.lifecycle);
    return matches ? JNI_TRUE : JNI_FALSE;
}
