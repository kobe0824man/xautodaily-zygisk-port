#include <sys/types.h>
#include "zygisk.hpp"
#include <android/log.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>
#include <cerrno>
#include <cstring>
#include <string>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "XAutoDaily-Zygisk", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "XAutoDaily-Zygisk", __VA_ARGS__)

namespace {
constexpr char kQQ[] = "com.tencent.mobileqq";
constexpr char kTIM[] = "com.tencent.tim";
constexpr char kBootstrap[] = "me.teble.xposed.autodaily.zygisk.Bootstrap";

std::string from_java(JNIEnv *env, jstring s) {
    if (!s) return {};
    const char *p = env->GetStringUTFChars(s, nullptr);
    if (!p) return {};
    std::string v(p);
    env->ReleaseStringUTFChars(s, p);
    return v;
}
bool target(const std::string &p) {
    return p.compare(0, sizeof(kQQ)-1, kQQ) == 0 ||
           p.compare(0, sizeof(kTIM)-1, kTIM) == 0;
}
bool copy_payload(int fd, const std::string &dst) {
    if (lseek(fd, 0, SEEK_SET) < 0) return false;
    int out = open(dst.c_str(), O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0600);
    if (out < 0) return false;
    char buf[65536];
    bool ok = true;
    for (;;) {
        ssize_t n = read(fd, buf, sizeof(buf));
        if (n == 0) break;
        if (n < 0) { if (errno == EINTR) continue; ok = false; break; }
        ssize_t off = 0;
        while (off < n) {
            ssize_t w = write(out, buf + off, static_cast<size_t>(n-off));
            if (w < 0) { if (errno == EINTR) continue; ok = false; break; }
            off += w;
        }
        if (!ok) break;
    }
    if (fsync(out) != 0) ok = false;
    close(out);
    return ok;
}
jobject host_loader(JNIEnv *env) {
    jclass at = env->FindClass("android/app/ActivityThread");
    if (!at) return nullptr;
    jmethodID current = env->GetStaticMethodID(at, "currentActivityThread",
        "()Landroid/app/ActivityThread;");
    if (!current) return nullptr;
    jobject thread = env->CallStaticObjectMethod(at, current);
    if (!thread || env->ExceptionCheck()) return nullptr;
    jfieldID bound_id = env->GetFieldID(at, "mBoundApplication",
        "Landroid/app/ActivityThread$AppBindData;");
    if (!bound_id) return nullptr;
    jobject bound = env->GetObjectField(thread, bound_id);
    if (!bound) return nullptr;
    jclass bc = env->GetObjectClass(bound);
    jfieldID info_id = env->GetFieldID(bc, "info", "Landroid/app/LoadedApk;");
    if (!info_id) return nullptr;
    jobject info = env->GetObjectField(bound, info_id);
    if (!info) return nullptr;
    jclass ic = env->GetObjectClass(info);
    jmethodID get = env->GetMethodID(ic, "getClassLoader", "()Ljava/lang/ClassLoader;");
    return get ? env->CallObjectMethod(info, get) : nullptr;
}

class XAutoDaily final : public zygisk::ModuleBase {
public:
    void onLoad(zygisk::Api *api, JNIEnv *env) override { api_ = api; env_ = env; }

    void preAppSpecialize(zygisk::AppSpecializeArgs *args) override {
        process_ = from_java(env_, args->nice_name);
        if (!target(process_)) return;
        app_data_ = from_java(env_, args->app_data_dir);
        int dir = api_->getModuleDir();
        if (dir < 0) { LOGE("getModuleDir failed"); return; }
        payload_fd_ = openat(dir, "payload.apk", O_RDONLY | O_CLOEXEC);
        close(dir);
        if (payload_fd_ < 0 || !api_->exemptFd(payload_fd_)) {
            LOGE("payload APK unavailable or FD exemption failed");
            if (payload_fd_ >= 0) close(payload_fd_);
            payload_fd_ = -1;
            return;
        }
        enabled_ = true;
        LOGI("prepared for %s", process_.c_str());
    }

    void postAppSpecialize(const zygisk::AppSpecializeArgs *) override {
        if (!enabled_ || payload_fd_ < 0) return;
        std::string cache = app_data_ + "/code_cache";
        mkdir(cache.c_str(), 0700);
        std::string apk = cache + "/xautodaily-payload.apk";
        if (!copy_payload(payload_fd_, apk)) {
            LOGE("payload staging failed: %s", strerror(errno));
            close(payload_fd_); payload_fd_ = -1; return;
        }
        close(payload_fd_); payload_fd_ = -1;

        jobject parent = host_loader(env_);
        if (!parent || env_->ExceptionCheck()) {
            env_->ExceptionClear(); LOGE("host class loader unavailable"); return;
        }
        jclass dex = env_->FindClass("dalvik/system/DexClassLoader");
        if (!dex) { env_->ExceptionClear(); LOGE("DexClassLoader unavailable"); return; }
        jmethodID ctor = env_->GetMethodID(dex, "<init>",
            "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/ClassLoader;)V");
        if (!ctor) { env_->ExceptionClear(); return; }
        jstring dex_path = env_->NewStringUTF(apk.c_str());
        jstring opt = env_->NewStringUTF(cache.c_str());
        jstring libs = env_->NewStringUTF((cache + "/lib").c_str());
        jobject loader = env_->NewObject(dex, ctor, dex_path, opt, libs, parent);
        if (!loader || env_->ExceptionCheck()) {
            env_->ExceptionClear(); LOGE("payload DexClassLoader creation failed"); return;
        }
        jmethodID load = env_->GetMethodID(dex, "loadClass",
            "(Ljava/lang/String;)Ljava/lang/Class;");
        jstring name = env_->NewStringUTF(kBootstrap);
        jobject klass = env_->CallObjectMethod(loader, load, name);
        if (!klass || env_->ExceptionCheck()) {
            env_->ExceptionClear(); LOGE("Bootstrap class not found in payload"); return;
        }
        jmethodID start = env_->GetStaticMethodID(static_cast<jclass>(klass), "start",
            "(Ljava/lang/ClassLoader;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
        if (!start) { env_->ExceptionClear(); LOGE("Bootstrap.start signature missing"); return; }
        jstring pkg = env_->NewStringUTF(process_.compare(0, sizeof(kQQ)-1, kQQ) == 0 ? kQQ : kTIM);
        jstring proc = env_->NewStringUTF(process_.c_str());
        env_->CallStaticVoidMethod(static_cast<jclass>(klass), start, parent, dex_path, pkg, proc);
        if (env_->ExceptionCheck()) {
            env_->ExceptionDescribe(); env_->ExceptionClear(); LOGE("Bootstrap.start failed");
        } else LOGI("bootstrap dispatched: %s", process_.c_str());
    }
private:
    zygisk::Api *api_ = nullptr;
    JNIEnv *env_ = nullptr;
    int payload_fd_ = -1;
    bool enabled_ = false;
    std::string process_, app_data_;
};
}
REGISTER_ZYGISK_MODULE(XAutoDaily)
