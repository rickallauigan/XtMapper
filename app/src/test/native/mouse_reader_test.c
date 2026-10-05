/* Host regression tests for the JNI reader's event filtering and thread lifecycle.
 * Compile with the JDK JNI headers; the mocked JVM/ioctl never access Android or hardware. */
#define _GNU_SOURCE
#define ioctl test_ioctl
#include "../../main/cpp/mouse_read.c"
#include <assert.h>
#include <stdarg.h>
#include <stdatomic.h>
#include <string.h>
#include <time.h>

int test_ioctl(int fd, unsigned long request, ...) { (void)fd; (void)request; return 0; }
static atomic_int callbacks, cancellations;
static int last_code, last_value;
static JNIEnv fake_env;
static bool fail_attach;
static jint attach(JavaVM *vm, void **env, void *args) { (void)vm; (void)args; if(fail_attach)return JNI_ERR; *env=&fake_env; return JNI_OK; }
static jint detach(JavaVM *vm) { (void)vm; return JNI_OK; }
static jmethodID get_method(JNIEnv *env,jclass clz,const char *name,const char *signature) {
    (void)env;(void)clz;(void)name;(void)signature;return (jmethodID)1;
}
static void call(JNIEnv *env,jobject obj,jmethodID method,...) {
    (void)env;(void)obj;va_list args;va_start(args,method);
    int code=va_arg(args,int),value=va_arg(args,int);va_end(args);
    if(code==-1){atomic_fetch_add(&cancellations,1);return;}
    last_code=code;last_value=value;atomic_fetch_add(&callbacks,1);
}
static jboolean no_exception(JNIEnv *env) { (void)env;return JNI_FALSE; }
static const char *string_chars(JNIEnv *env,jstring string,jboolean *copy) { (void)env;(void)copy;return (const char*)string; }
static void release_string(JNIEnv *env,jstring string,const char *chars) { (void)env;(void)string;(void)chars; }
static const struct JNINativeInterface_ native_table={.GetMethodID=get_method,.CallVoidMethod=call,
    .ExceptionCheck=no_exception,.GetStringUTFChars=string_chars,.ReleaseStringUTFChars=release_string};
static const struct JNIInvokeInterface_ vm_table={.AttachCurrentThread=attach,.DetachCurrentThread=detach};
static JavaVM fake_vm=&vm_table;
static void wait_ms(int ms) { struct timespec delay={.tv_sec=ms/1000,.tv_nsec=(ms%1000)*1000000L};nanosleep(&delay,NULL); }
static void start(serviceContext *ctx,int fd,pthread_t *thread) {
    memset(ctx,0,sizeof(*ctx));ctx->javaVM=&fake_vm;ctx->mouse_fd=fd;ctx->mouse_lock=1;
    pthread_mutex_init(&ctx->lock,NULL);atomic_store(&callbacks,0);atomic_store(&cancellations,0);
    assert(pthread_create(thread,NULL,send_mouse_events,ctx)==0);
}
int main(void) {
    fake_env=&native_table;
    struct input_event event={.type=EV_SYN,.code=REL_X,.value=0};
    assert(!is_mouse_event(&event));event.type=EV_MSC;assert(!is_mouse_event(&event));
    event.type=EV_REL;event.value=9;assert(is_mouse_event(&event));
    event.type=EV_KEY;event.code=BTN_RIGHT;event.value=1;assert(is_mouse_event(&event));
    event.value=0;assert(is_mouse_event(&event));event.value=2;assert(!is_mouse_event(&event));

    int descriptors[2];serviceContext ctx;pthread_t thread;
    assert(pipe(descriptors)==0);start(&ctx,descriptors[0],&thread);
    event=(struct input_event){.type=EV_REL,.code=REL_X,.value=9};
    assert(write(descriptors[1],&event,sizeof(event))==sizeof(event));
    for(int i=0;i<100 && atomic_load(&callbacks)==0;i++)wait_ms(1);
    assert(atomic_load(&callbacks)==1);assert(last_code==REL_X && last_value==9);
    close(descriptors[1]);pthread_join(thread,NULL);
    assert(ctx.finished);assert(atomic_load(&cancellations)==1);assert(atomic_load(&callbacks)==1); // EOF never replays previous packet
    pthread_mutex_destroy(&ctx.lock);

    assert(pipe(descriptors)==0);start(&ctx,descriptors[0],&thread);
    close(descriptors[1]);pthread_join(thread,NULL);
    assert(ctx.finished);assert(atomic_load(&callbacks)==0);
    pthread_mutex_destroy(&ctx.lock);

    assert(pipe(descriptors)==0);start(&ctx,descriptors[0],&thread);wait_ms(5);
    pthread_mutex_lock(&ctx.lock);ctx.done=true;pthread_mutex_unlock(&ctx.lock);
    pthread_join(thread,NULL);close(descriptors[1]);
    assert(ctx.finished);assert(atomic_load(&cancellations)==0);assert(atomic_load(&callbacks)==0); // stop needs no physical event
    pthread_mutex_destroy(&ctx.lock);

    // Short/incomplete read terminates without interpreting uninitialized packet bytes.
    assert(pipe(descriptors)==0);start(&ctx,descriptors[0],&thread);
    assert(write(descriptors[1],"x",1)==1);wait_ms(5);close(descriptors[1]);pthread_join(thread,NULL);
    assert(ctx.finished);assert(atomic_load(&callbacks)==0);
    pthread_mutex_destroy(&ctx.lock);

    // JVM attachment failure must also close the fd and mark the reader finished.
    assert(pipe(descriptors)==0);fail_attach=true;start(&ctx,descriptors[0],&thread);
    pthread_join(thread,NULL);assert(ctx.finished);assert(fcntl(descriptors[0],F_GETFD)==-1);
    close(descriptors[1]);pthread_mutex_destroy(&ctx.lock);fail_attach=false;

    pthread_mutex_init(&g_ctx.lock,NULL);
    Java_xtr_keymapper_server_InputService_setMouseLock(&fake_env,NULL,JNI_TRUE);
    // Same event path after replacement must differ from the fd opened before reconnect.
    pthread_mutex_init(&g_ctx.lifecycle,NULL);
    char path[]="/tmp/xtmapper-mouse-reader-XXXXXX";int old_fd=mkstemp(path);assert(old_fd>=0);
    g_ctx.started=true;g_ctx.finished=false;g_ctx.mouse_fd=old_fd;
    assert(Java_xtr_keymapper_server_InputService_isMouseDeviceCurrent(&fake_env,NULL,(jstring)path));
    assert(unlink(path)==0);int new_fd=open(path,O_CREAT|O_RDWR,0600);assert(new_fd>=0);
    assert(!Java_xtr_keymapper_server_InputService_isMouseDeviceCurrent(&fake_env,NULL,(jstring)path));
    g_ctx.finished=true;assert(!Java_xtr_keymapper_server_InputService_isMouseDeviceCurrent(&fake_env,NULL,(jstring)path));
    // Failed candidates (including REL-only sensors) must not stop the live mouse.
    assert(Java_xtr_keymapper_server_InputService_openDevice(&fake_env,NULL,(jstring)path)==-1);
    assert(g_ctx.started);assert(g_ctx.mouse_fd==old_fd);
    close(old_fd);close(new_fd);unlink(path);
    puts("mouse reader regression tests passed");return 0;
}
