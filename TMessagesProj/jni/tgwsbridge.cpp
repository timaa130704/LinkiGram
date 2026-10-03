#include <jni.h>
#include <android/log.h>
#include <stdlib.h>
#include <string.h>

// tg-ws-linki: the Rust core from tg-ws-proxy-android (GPL-3.0,
// https://github.com/amurcanov/tg-ws-proxy-android), embedded in the app.
// Exposed as a JNI API so the client can run the proxy in-process instead of shipping a
// separate helper app.
extern "C" {
int StartProxy(const char *host, int port, const char *dcIps, const char *secret, int verbose);
int StopProxy();
void SetPoolSize(int size);
void SetSecret(const char *secret);
void SetCfProxyConfig(int enabled, int priority, const char *userDomain);
char *GetStats();
char *GetSecretWithPrefix();
void FreeString(char *p);
}

#define LOG_TAG "tgwsbridge"

static char *jstringToC(JNIEnv *env, jstring s) {
    if (s == nullptr) return nullptr;
    const char *chars = env->GetStringUTFChars(s, nullptr);
    if (chars == nullptr) return nullptr;
    char *out = strdup(chars);
    env->ReleaseStringUTFChars(s, chars);
    return out;
}

extern "C" JNIEXPORT jint JNICALL
Java_app_nimarkogram_messenger_wsbypass_TgWsBridge_nativeStart(JNIEnv *env, jclass, jstring host, jint port,
                                                              jstring dcIps, jstring secret, jboolean verbose) {
    char *cHost = jstringToC(env, host);
    char *cDcIps = jstringToC(env, dcIps);
    char *cSecret = jstringToC(env, secret);
    int rc = StartProxy(cHost ? cHost : "127.0.0.1", (int) port, cDcIps ? cDcIps : "", cSecret ? cSecret : "",
                        verbose ? 1 : 0);
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "StartProxy host=%s port=%d rc=%d",
                        cHost ? cHost : "127.0.0.1", (int) port, rc);
    free(cHost);
    free(cDcIps);
    free(cSecret);
    return (jint) rc;
}

extern "C" JNIEXPORT jint JNICALL
Java_app_nimarkogram_messenger_wsbypass_TgWsBridge_nativeStop(JNIEnv *, jclass) {
    int rc = StopProxy();
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "StopProxy rc=%d", rc);
    return (jint) rc;
}

extern "C" JNIEXPORT void JNICALL
Java_app_nimarkogram_messenger_wsbypass_TgWsBridge_nativeSetPoolSize(JNIEnv *, jclass, jint size) {
    SetPoolSize((int) size);
}

extern "C" JNIEXPORT void JNICALL
Java_app_nimarkogram_messenger_wsbypass_TgWsBridge_nativeSetSecret(JNIEnv *env, jclass, jstring secret) {
    char *cSecret = jstringToC(env, secret);
    if (cSecret != nullptr) {
        SetSecret(cSecret);
        free(cSecret);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_app_nimarkogram_messenger_wsbypass_TgWsBridge_nativeSetCfProxy(JNIEnv *env, jclass, jboolean enabled,
                                                                      jstring domain) {
    char *cDomain = jstringToC(env, domain);
    SetCfProxyConfig(enabled ? 1 : 0, 0, cDomain ? cDomain : "");
    free(cDomain);
}

extern "C" JNIEXPORT jstring JNICALL
Java_app_nimarkogram_messenger_wsbypass_TgWsBridge_nativeStats(JNIEnv *env, jclass) {
    char *s = GetStats();
    if (s == nullptr) return env->NewStringUTF("");
    jstring out = env->NewStringUTF(s);
    FreeString(s);
    return out;
}

extern "C" JNIEXPORT jstring JNICALL
Java_app_nimarkogram_messenger_wsbypass_TgWsBridge_nativeSecretWithPrefix(JNIEnv *env, jclass) {
    char *s = GetSecretWithPrefix();
    if (s == nullptr) return env->NewStringUTF("");
    jstring out = env->NewStringUTF(s);
    FreeString(s);
    return out;
}
