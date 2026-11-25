#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <elf.h>
#include <link.h>
#include <sys/stat.h>
#include <unistd.h>

#include <algorithm>
#include <cstdint>
#include <cstring>
#include <fstream>
#include <mutex>
#include <string>
#include <string_view>
#include <vector>

#include <dobby.h>
#include <lsplant.hpp>

namespace {
constexpr const char *kTag = "XAutoDaily-Zygisk";
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, kTag, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, kTag, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, kTag, __VA_ARGS__)

struct ArtImage {
    std::string path;
    uintptr_t load_bias = 0;
    std::vector<uint8_t> bytes;
};

ArtImage g_art;
void *g_art_handle = nullptr;
bool g_initialized = false;
std::mutex g_hook_mutex;

int find_art_image(dl_phdr_info *info, size_t, void *opaque) {
    if (info == nullptr || info->dlpi_name == nullptr) return 0;
    std::string path(info->dlpi_name);
    if (path.find("libart.so") == std::string::npos) return 0;
    auto *image = static_cast<ArtImage *>(opaque);
    image->path = std::move(path);
    image->load_bias = static_cast<uintptr_t>(info->dlpi_addr);
    return 1;
}

bool load_art_image() {
    dl_iterate_phdr(find_art_image, &g_art);
    if (g_art.path.empty()) {
        LOGE("libart.so was not found in loaded program headers");
        return false;
    }
    std::ifstream input(g_art.path, std::ios::binary | std::ios::ate);
    if (!input) {
        LOGW("Cannot open %s for ELF symbol fallback", g_art.path.c_str());
        return true; // dlsym may still resolve the required exported symbols.
    }
    const auto end = input.tellg();
    if (end <= 0 || static_cast<uint64_t>(end) > 128ULL * 1024ULL * 1024ULL) {
        LOGW("Unexpected libart image size: %lld", static_cast<long long>(end));
        return true;
    }
    g_art.bytes.resize(static_cast<size_t>(end));
    input.seekg(0);
    input.read(reinterpret_cast<char *>(g_art.bytes.data()), static_cast<std::streamsize>(g_art.bytes.size()));
    if (!input) {
        g_art.bytes.clear();
        LOGW("Could not read complete libart image");
    }
    LOGI("ART image: %s (base=%p, file=%zu bytes)", g_art.path.c_str(),
         reinterpret_cast<void *>(g_art.load_bias), g_art.bytes.size());
    return true;
}

template <typename Ehdr, typename Shdr, typename Sym, typename TypeFn>
void *scan_elf_symbols(const std::vector<uint8_t> &bytes, uintptr_t load_bias,
                       std::string_view query, bool prefix, TypeFn symbol_type) {
    if (bytes.size() < sizeof(Ehdr)) return nullptr;
    const auto *header = reinterpret_cast<const Ehdr *>(bytes.data());
    if (header->e_shoff == 0 || header->e_shnum == 0 || header->e_shentsize != sizeof(Shdr)) return nullptr;
    const uint64_t section_bytes = static_cast<uint64_t>(header->e_shnum) * sizeof(Shdr);
    if (header->e_shoff > bytes.size() || section_bytes > bytes.size() - header->e_shoff) return nullptr;
    const auto *sections = reinterpret_cast<const Shdr *>(bytes.data() + header->e_shoff);

    // Dynamic symbols are searched first; a retained full .symtab is the fallback for hidden ART symbols.
    for (int pass = 0; pass < 2; ++pass) {
        const uint32_t wanted_type = pass == 0 ? SHT_DYNSYM : SHT_SYMTAB;
        for (uint16_t section_index = 0; section_index < header->e_shnum; ++section_index) {
            const auto &section = sections[section_index];
            if (section.sh_type != wanted_type || section.sh_entsize != sizeof(Sym)
                    || section.sh_link >= header->e_shnum || section.sh_size == 0) continue;
            if (section.sh_offset > bytes.size() || section.sh_size > bytes.size() - section.sh_offset) continue;
            const auto &strings_section = sections[section.sh_link];
            if (strings_section.sh_offset > bytes.size()
                    || strings_section.sh_size > bytes.size() - strings_section.sh_offset) continue;
            const char *strings = reinterpret_cast<const char *>(bytes.data() + strings_section.sh_offset);
            const auto *symbols = reinterpret_cast<const Sym *>(bytes.data() + section.sh_offset);
            const size_t count = static_cast<size_t>(section.sh_size / sizeof(Sym));
            for (size_t i = 0; i < count; ++i) {
                const auto &symbol = symbols[i];
                if (symbol.st_name >= strings_section.sh_size || symbol.st_shndx == SHN_UNDEF || symbol.st_value == 0) continue;
                const uint8_t kind = symbol_type(symbol.st_info);
                if (kind != STT_FUNC && kind != STT_NOTYPE) continue;
                const char *name = strings + symbol.st_name;
                const size_t remaining = static_cast<size_t>(strings_section.sh_size - symbol.st_name);
                const void *terminator = std::memchr(name, '\0', remaining);
                if (terminator == nullptr) continue;
                const size_t name_length = static_cast<const char *>(terminator) - name;
                const std::string_view candidate(name, name_length);
                const bool matches = prefix
                        ? candidate.size() >= query.size() && candidate.compare(0, query.size(), query) == 0
                        : candidate == query;
                if (!matches) continue;
                const uintptr_t address = symbol.st_shndx == SHN_ABS
                        ? static_cast<uintptr_t>(symbol.st_value)
                        : load_bias + static_cast<uintptr_t>(symbol.st_value);
                if (address != 0) return reinterpret_cast<void *>(address);
            }
        }
    }
    return nullptr;
}

void *scan_art_file(std::string_view query, bool prefix) {
    if (g_art.bytes.size() < EI_NIDENT || std::memcmp(g_art.bytes.data(), ELFMAG, SELFMAG) != 0) return nullptr;
    if (g_art.bytes[EI_CLASS] == ELFCLASS64) {
        return scan_elf_symbols<Elf64_Ehdr, Elf64_Shdr, Elf64_Sym>(
                g_art.bytes, g_art.load_bias, query, prefix,
                [](unsigned char info) { return ELF64_ST_TYPE(info); });
    }
    if (g_art.bytes[EI_CLASS] == ELFCLASS32) {
        return scan_elf_symbols<Elf32_Ehdr, Elf32_Shdr, Elf32_Sym>(
                g_art.bytes, g_art.load_bias, query, prefix,
                [](unsigned char info) { return ELF32_ST_TYPE(info); });
    }
    return nullptr;
}

void *resolve_art_symbol(std::string_view name) {
    const std::string symbol(name);
    if (g_art_handle != nullptr) {
        if (void *address = dlsym(g_art_handle, symbol.c_str())) return address;
    }
    if (void *address = dlsym(RTLD_DEFAULT, symbol.c_str())) return address;
    if (void *address = DobbySymbolResolver("libart.so", symbol.c_str())) return address;
    return scan_art_file(name, false);
}

void *resolve_art_prefix(std::string_view prefix) {
    return scan_art_file(prefix, true);
}

void *inline_hook(void *target, void *replacement) {
    void *backup = nullptr;
    const int status = DobbyHook(target, replacement, &backup);
    if (status != 0 || backup == nullptr) {
        LOGE("DobbyHook failed: target=%p replacement=%p status=%d", target, replacement, status);
        return nullptr;
    }
    return backup;
}

bool inline_unhook(void *target) {
    const int status = DobbyDestroy(target);
    if (status != 0) LOGE("DobbyDestroy failed: target=%p status=%d", target, status);
    return status == 0;
}

void throw_java(JNIEnv *env, const char *class_name, const std::string &message) {
    if (env->ExceptionCheck()) return;
    jclass exception = env->FindClass(class_name);
    if (exception != nullptr) env->ThrowNew(exception, message.c_str());
}

void JNICALL native_hook_method(JNIEnv *env, jclass, jobject member, jclass, jint, jobject additional_info) {
    std::lock_guard<std::mutex> guard(g_hook_mutex);
    if (!g_initialized) {
        throw_java(env, "java/lang/IllegalStateException", "LSPlant is not initialized");
        return;
    }
    jclass hooker_class = env->FindClass("me/teble/xposed/autodaily/zygisk/XposedHooker");
    if (hooker_class == nullptr) return;
    jmethodID constructor = env->GetMethodID(hooker_class, "<init>",
            "(Ljava/lang/reflect/Member;Ljava/lang/Object;)V");
    jmethodID callback = env->GetMethodID(hooker_class, "dispatch", "([Ljava/lang/Object;)Ljava/lang/Object;");
    if (constructor == nullptr || callback == nullptr) return;
    jobject hooker = env->NewObject(hooker_class, constructor, member, additional_info);
    if (hooker == nullptr) return;

    jobject backup = lsplant::Hook(env, member, hooker, callback);
    if (backup == nullptr) {
        throw_java(env, "java/lang/IllegalStateException", "LSPlant could not hook "
                "the requested method/constructor");
        return;
    }

    jclass runtime_class = env->FindClass("me/teble/xposed/autodaily/zygisk/HookRuntime");
    if (runtime_class == nullptr) return;
    jmethodID register_backup = env->GetStaticMethodID(runtime_class, "registerBackup",
            "(Ljava/lang/reflect/Member;Ljava/lang/reflect/Method;)V");
    if (register_backup == nullptr) return;
    env->CallStaticVoidMethod(runtime_class, register_backup, member, backup);
}

jobject JNICALL native_invoke_original(JNIEnv *env, jclass, jobject member, jint,
                                        jobjectArray, jclass, jobject this_object, jobjectArray arguments) {
    jclass runtime_class = env->FindClass("me/teble/xposed/autodaily/zygisk/HookRuntime");
    if (runtime_class == nullptr) return nullptr;
    jmethodID invoke = env->GetStaticMethodID(runtime_class, "invokeOriginal",
            "(Ljava/lang/reflect/Member;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");
    if (invoke == nullptr) return nullptr;
    return env->CallStaticObjectMethod(runtime_class, invoke, member, this_object, arguments);
}

jstring JNICALL native_get_start_class_name(JNIEnv *, jclass) { return nullptr; }
jint JNICALL native_get_runtime(JNIEnv *, jclass) { return 2; } // Xposed API 82: RUNTIME_ART
jboolean JNICALL native_starts_system_server(JNIEnv *, jclass) { return JNI_FALSE; }
jint JNICALL native_get_xposed_version(JNIEnv *, jclass) { return 82; }
jboolean JNICALL native_init(JNIEnv *, jclass) { return JNI_TRUE; }

void JNICALL native_set_object_class(JNIEnv *env, jclass, jobject, jclass) {
    throw_java(env, "java/lang/UnsupportedOperationException",
            "Xposed object-class substitution is not provided by the Zygisk compatibility layer");
}

void JNICALL native_dump_object(JNIEnv *env, jclass, jobject object) {
    if (object == nullptr) {
        LOGI("dumpObject(null)");
        return;
    }
    jclass object_class = env->GetObjectClass(object);
    jclass class_class = env->FindClass("java/lang/Class");
    jmethodID get_name = class_class == nullptr ? nullptr
            : env->GetMethodID(class_class, "getName", "()Ljava/lang/String;");
    if (get_name != nullptr) {
        auto name = static_cast<jstring>(env->CallObjectMethod(object_class, get_name));
        if (name != nullptr) {
            const char *chars = env->GetStringUTFChars(name, nullptr);
            if (chars != nullptr) {
                LOGI("dumpObject(%s)", chars);
                env->ReleaseStringUTFChars(name, chars);
            }
        }
    }
}

jobject JNICALL native_clone_to_subclass(JNIEnv *env, jclass, jobject, jclass) {
    throw_java(env, "java/lang/UnsupportedOperationException",
            "Xposed cloneToSubclass is not provided by the Zygisk compatibility layer");
    return nullptr;
}

JNINativeMethod g_methods[] = {
    {const_cast<char *>("getStartClassName"), const_cast<char *>("()Ljava/lang/String;"), reinterpret_cast<void *>(native_get_start_class_name)},
    {const_cast<char *>("getRuntime"), const_cast<char *>("()I"), reinterpret_cast<void *>(native_get_runtime)},
    {const_cast<char *>("startsSystemServer"), const_cast<char *>("()Z"), reinterpret_cast<void *>(native_starts_system_server)},
    {const_cast<char *>("getXposedVersion"), const_cast<char *>("()I"), reinterpret_cast<void *>(native_get_xposed_version)},
    {const_cast<char *>("initNative"), const_cast<char *>("()Z"), reinterpret_cast<void *>(native_init)},
    {const_cast<char *>("hookMethodNative"), const_cast<char *>("(Ljava/lang/reflect/Member;Ljava/lang/Class;ILjava/lang/Object;)V"), reinterpret_cast<void *>(native_hook_method)},
    {const_cast<char *>("invokeOriginalMethodNative"), const_cast<char *>("(Ljava/lang/reflect/Member;I[Ljava/lang/Class;Ljava/lang/Class;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;"), reinterpret_cast<void *>(native_invoke_original)},
    {const_cast<char *>("setObjectClassNative"), const_cast<char *>("(Ljava/lang/Object;Ljava/lang/Class;)V"), reinterpret_cast<void *>(native_set_object_class)},
    {const_cast<char *>("dumpObjectNative"), const_cast<char *>("(Ljava/lang/Object;)V"), reinterpret_cast<void *>(native_dump_object)},
    {const_cast<char *>("cloneToSubclassNative"), const_cast<char *>("(Ljava/lang/Object;Ljava/lang/Class;)Ljava/lang/Object;"), reinterpret_cast<void *>(native_clone_to_subclass)},
};
} // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *) {
    JNIEnv *env = nullptr;
    if (vm == nullptr || vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK || env == nullptr) {
        return JNI_ERR;
    }

    jclass bridge = env->FindClass("de/robv/android/xposed/XposedBridge");
    if (bridge == nullptr) {
        LOGE("XposedBridge API-82 class is unavailable to the module class loader");
        if (env->ExceptionCheck()) env->ExceptionDescribe();
        return JNI_ERR;
    }
    if (env->RegisterNatives(bridge, g_methods, sizeof(g_methods) / sizeof(g_methods[0])) != JNI_OK) {
        LOGE("Could not register Xposed API-82 native entry points");
        if (env->ExceptionCheck()) env->ExceptionDescribe();
        return JNI_ERR;
    }

    if (!load_art_image()) return JNI_ERR;
    g_art_handle = dlopen("libart.so", RTLD_NOW);
    if (g_art_handle == nullptr && !g_art.path.empty()) g_art_handle = dlopen(g_art.path.c_str(), RTLD_NOW);

    lsplant::InitInfo info;
    info.inline_hooker = inline_hook;
    info.inline_unhooker = inline_unhook;
    info.art_symbol_resolver = resolve_art_symbol;
    info.art_symbol_prefix_resolver = resolve_art_prefix;
    info.generated_class_name = "XAutoDailyLSPlant_";
    info.generated_source_name = "XAutoDaily-Zygisk";

    g_initialized = lsplant::Init(env, info);
    if (!g_initialized) {
        LOGE("LSPlant initialization failed; no Java hooks will be installed");
        return JNI_ERR;
    }
    LOGI("LSPlant initialized with Dobby inline hooks and ART ELF symbol resolution");
    return JNI_VERSION_1_6;
}
