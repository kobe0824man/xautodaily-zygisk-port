#include <sys/types.h>
#include "zygisk.hpp"
#include <android/log.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>
#include <array>
#include <cerrno>
#include <cstring>
#include <string>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "XAutoDaily-Zygisk", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "XAutoDaily-Zygisk", __VA_ARGS__)

namespace {
constexpr char kQQ[] = "com.tencent.mobileqq";
constexpr char kTIM[] = "com.tencent.tim";
constexpr char kBootstrap[] = "me.teble.xposed.autodaily.zygisk.Bootstrap";
#if defined(__aarch64__)
constexpr char kAbi[] = "arm64-v8a";
#elif defined(__arm__)
constexpr char kAbi[] = "armeabi-v7a";
#else
#error Unsupported Zygisk ABI
#endif
constexpr const char *kNativeLibraries[] = {
    "liblsplant.so", "libdobby.so", "libxautodaily_hook.so"
};

std::string from_java(JNIEnv *env, jstring s) {
    if (!s) return {};
    const char *p = env->GetStringUTFChars(s, nullptr);
    if (!p) return {};
    std::string v(p);
    env->ReleaseStringUTFChars(s, p);
    return v;
}
bool has_process_prefix(const std::string &process, const char *package_name) {
    const size_t length = std::strlen(package_name);
    return process == package_name || (process.size() > length
        && process.compare(0, length, package_name) == 0
        && (process[length] == ':' || process[length] == '.'));
}
bool target(const std::string &p) {
    return has_process_prefix(p, kQQ) || has_process_prefix(p, kTIM);
}
bool copy_fd_to_path(int fd, const std::string &dst, mode_t mode) {
    if (lseek(fd, 0, SEEK_SET) < 0) return false;
    int out = open(dst.c_str(), O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC | O_NOFOLLOW, mode);
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
    if (fchmod(out, mode) != 0) ok = false;
    close(out);
    return ok;
}
jobject system_loader(JNIEnv *env) {
    jclass loader_class = env->FindClass("java/lang/ClassLoader");
    if (!loader_class) return nullptr;
    jmethodID get_system = env->GetStaticMethodID(loader_class, "getSystemClassLoader",
        "()Ljava/lang/ClassLoader;");
    return get_system ? env->CallStaticObjectMethod(loader_class, get_system) : nullptr;
}

class ScopedLocalFrame {
public:
    ScopedLocalFrame(JNIEnv *env, jint capacity) : env_(env), valid_(env_ != nullptr
            && env_->PushLocalFrame(capacity) == JNI_OK) {}
    ~ScopedLocalFrame() { if (valid_) env_->PopLocalFrame(nullptr); }
    bool valid() const { return valid_; }
private:
    JNIEnv *env_;
    bool valid_;
};

class XAutoDaily final : public zygisk::ModuleBase {
public:
    void onLoad(zygisk::Api *api, JNIEnv *env) override { api_ = api; env_ = env; }

    void preAppSpecialize(zygisk::AppSpecializeArgs *args) override {
        process_ = from_java(env_, args->nice_name);
        if (!target(process_)) return;
        app_data_ = from_java(env_, args->app_data_dir);
        int dir = api_->getModuleDir();
        if (dir < 0) { LOGE("getModuleDir failed"); return; }
        payload_fd_ = openat(dir, "payload.apk", O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
        if (payload_fd_ < 0 || !api_->exemptFd(payload_fd_)) {
            LOGE("payload APK unavailable or FD exemption failed");
            if (payload_fd_ >= 0) close(payload_fd_);
            payload_fd_ = -1;
            close(dir);
            return;
        }
        for (size_t i = 0; i < native_lib_fds_.size(); ++i) {
            const std::string relative = std::string("native-libs/") + kAbi + "/" + kNativeLibraries[i];
            native_lib_fds_[i] = openat(dir, relative.c_str(), O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
            struct stat st {};
            if (native_lib_fds_[i] < 0 || fstat(native_lib_fds_[i], &st) != 0
                    || !S_ISREG(st.st_mode) || st.st_size <= 0
                    || !api_->exemptFd(native_lib_fds_[i])) {
                LOGE("required native support library unavailable: %s", relative.c_str());
                close(dir);
                close_staged_fds();
                return;
            }
        }
        close(dir);
        enabled_ = true;
        LOGI("prepared for %s", process_.c_str());
    }

    void postAppSpecialize(const zygisk::AppSpecializeArgs *) override {
        if (!enabled_ || payload_fd_ < 0) return;
        ScopedLocalFrame local_frame(env_, 64);
        if (!local_frame.valid()) {
            if (env_->ExceptionCheck()) env_->ExceptionClear();
            LOGE("could not allocate JNI local-reference frame");
            close(payload_fd_); payload_fd_ = -1; return;
        }
        std::string cache = app_data_ + "/code_cache";
        if (mkdir(cache.c_str(), 0700) != 0 && errno != EEXIST) {
            LOGE("cannot create code_cache: %s", strerror(errno));
            close(payload_fd_); payload_fd_ = -1; return;
        }
        const std::string initial_lib_dir = cache + "/lib";
        if (mkdir(initial_lib_dir.c_str(), 0700) != 0 && errno != EEXIST) {
            LOGE("cannot create bootstrap lib dir: %s", strerror(errno));
            close(payload_fd_); payload_fd_ = -1; return;
        }
        std::string apk = cache + "/xautodaily-payload.apk";
        if (!copy_fd_to_path(payload_fd_, apk, 0600)) {
            LOGE("payload staging failed: %s", strerror(errno));
            close_staged_fds(); return;
        }
        close(payload_fd_); payload_fd_ = -1;

        const std::string native_root = cache + "/xautodaily-native-libs";
        if (mkdir(native_root.c_str(), 0700) != 0 && errno != EEXIST) {
            LOGE("cannot create native support directory: %s", strerror(errno));
            close_staged_fds(); return;
        }
        const std::string native_dir = native_root + "/" + kAbi;
        if (mkdir(native_dir.c_str(), 0700) != 0 && errno != EEXIST) {
            LOGE("cannot create ABI support directory: %s", strerror(errno));
            close_staged_fds(); return;
        }
        for (size_t i = 0; i < native_lib_fds_.size(); ++i) {
            const std::string destination = native_dir + "/" + kNativeLibraries[i];
            if (!copy_fd_to_path(native_lib_fds_[i], destination, 0700)) {
                LOGE("native support library staging failed: %s", kNativeLibraries[i]);
                close_staged_fds(); return;
            }
            close(native_lib_fds_[i]);
            native_lib_fds_[i] = -1;
        }

        // ActivityThread.currentActivityThread() is still null in postAppSpecialize. Load only
        // the bootstrap with the system loader; it arms a one-shot handleBindApplication hook
        // and resolves LoadedApk.getClassLoader() immediately before Application creation.
        jobject parent = system_loader(env_);
        if (!parent || env_->ExceptionCheck()) {
            env_->ExceptionClear(); LOGE("system ClassLoader unavailable"); return;
        }
        jclass dex = env_->FindClass("dalvik/system/DexClassLoader");
        if (!dex) { env_->ExceptionClear(); LOGE("DexClassLoader unavailable"); return; }
        jmethodID ctor = env_->GetMethodID(dex, "<init>",
            "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/ClassLoader;)V");
        if (!ctor) { env_->ExceptionClear(); return; }
        jstring dex_path = env_->NewStringUTF(apk.c_str());
        jstring opt = env_->NewStringUTF(cache.c_str());
        jstring libs = env_->NewStringUTF(initial_lib_dir.c_str());
        if (!dex_path || !opt || !libs || env_->ExceptionCheck()) {
            env_->ExceptionClear(); LOGE("could not allocate bootstrap path strings"); return;
        }
        jobject loader = env_->NewObject(dex, ctor, dex_path, opt, libs, parent);
        if (!loader || env_->ExceptionCheck()) {
            env_->ExceptionClear(); LOGE("bootstrap DexClassLoader creation failed"); return;
        }
        jmethodID load = env_->GetMethodID(dex, "loadClass",
            "(Ljava/lang/String;)Ljava/lang/Class;");
        if (!load) { env_->ExceptionClear(); LOGE("DexClassLoader.loadClass unavailable"); return; }
        jstring name = env_->NewStringUTF(kBootstrap);
        if (!name) { env_->ExceptionClear(); LOGE("could not allocate bootstrap class name"); return; }
        jobject klass = env_->CallObjectMethod(loader, load, name);
        if (!klass || env_->ExceptionCheck()) {
            env_->ExceptionClear(); LOGE("Bootstrap class not found in payload"); return;
        }
        jmethodID start = env_->GetStaticMethodID(static_cast<jclass>(klass), "start",
            "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
        if (!start) { env_->ExceptionClear(); LOGE("Bootstrap.start signature missing"); return; }
        jstring pkg = env_->NewStringUTF(has_process_prefix(process_, kQQ) ? kQQ : kTIM);
        jstring proc = env_->NewStringUTF(process_.c_str());
        if (!pkg || !proc || env_->ExceptionCheck()) {
            env_->ExceptionClear(); LOGE("could not allocate process identity strings"); return;
        }
        env_->CallStaticVoidMethod(static_cast<jclass>(klass), start, dex_path, pkg, proc);
        if (env_->ExceptionCheck()) {
            env_->ExceptionDescribe(); env_->ExceptionClear(); LOGE("Bootstrap.start failed");
        } else LOGI("deferred bootstrap armed: %s", process_.c_str());
    }

private:
    void close_staged_fds() {
        if (payload_fd_ >= 0) { close(payload_fd_); payload_fd_ = -1; }
        for (int &fd : native_lib_fds_) {
            if (fd >= 0) { close(fd); fd = -1; }
        }
    }

    zygisk::Api *api_ = nullptr;
    JNIEnv *env_ = nullptr;
    int payload_fd_ = -1;
    std::array<int, 3> native_lib_fds_{{-1, -1, -1}};
    bool enabled_ = false;
    std::string process_, app_data_;
};
}
REGISTER_ZYGISK_MODULE(XAutoDaily)
