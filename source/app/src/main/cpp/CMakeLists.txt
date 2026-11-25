cmake_minimum_required(VERSION 3.4.1)
project(xa_native)

enable_language(CXX C)
set(CMAKE_CXX_STANDARD 23)
set(CMAKE_C_STANDARD 23)

add_subdirectory(../../../../mmkv mmkv)
add_subdirectory(../../../../dexkit/DexKit/Core dexkit_static)

# Dobby supplies the inline-hook backend used by LSPlant. The LSPlant runtime itself is
# provided as a pinned Prefab AAR (org.lsposed.lsplant:lsplant-standalone:6.4).
set(DOBBY_BUILD_EXAMPLE OFF CACHE BOOL "" FORCE)
set(DOBBY_BUILD_TEST OFF CACHE BOOL "" FORCE)
add_subdirectory(../../../../zygisk/third_party/Dobby dobby_build)
# Dobby sets a directory-wide C++17 default; restore the upstream app standard for xa_native.
set(CMAKE_CXX_STANDARD 23)
find_package(lsplant REQUIRED CONFIG)

add_library(crypto STATIC IMPORTED)
set_target_properties(
        crypto
        PROPERTIES IMPORTED_LOCATION
        ${CMAKE_CURRENT_SOURCE_DIR}/../../../../openssl/lib/${ANDROID_ABI}/libcrypto.a
)

add_library(openssl STATIC IMPORTED)
set_target_properties(
        openssl
        PROPERTIES IMPORTED_LOCATION
        ${CMAKE_CURRENT_SOURCE_DIR}/../../../../openssl/lib/${ANDROID_ABI}/libssl.a
)

add_library(
        ${PROJECT_NAME} SHARED
        md5.cpp
        base64.cpp
        native-lib.cpp
        ../../../../dexkit/DexKit/dexkit/src/main/cpp/native-bridge.cpp
)

include_directories(${CMAKE_CURRENT_SOURCE_DIR}/../../../../openssl/include)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/include)

find_library(log-lib log)

target_link_libraries(xa_native
        crypto
        openssl
        mmkv
        dexkit_static
        z
        ${log-lib})


add_library(xautodaily_hook SHARED zygisk_hook.cpp)
target_compile_features(xautodaily_hook PRIVATE cxx_std_17)
target_compile_options(xautodaily_hook PRIVATE -Wall -Wextra -fvisibility=hidden -fvisibility-inlines-hidden)
target_link_libraries(xautodaily_hook PRIVATE lsplant::lsplant dobby ${log-lib} dl)
set_target_properties(xautodaily_hook PROPERTIES OUTPUT_NAME "xautodaily_hook" CXX_STANDARD 20 CXX_STANDARD_REQUIRED ON)
