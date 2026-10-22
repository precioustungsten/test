cmake_minimum_required(VERSION 3.22.1)
project(openear_native LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_C_STANDARD 11)

# whisper.cpp is a git submodule pinned to a release tag (see docs/UPDATING_WHISPER.md).
set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp)
if(NOT EXISTS ${WHISPER_DIR}/src/whisper.cpp)
    message(FATAL_ERROR "whisper.cpp sources missing. Run: git submodule update --init --recursive")
endif()

# AGP builds debug variants with CMAKE_BUILD_TYPE=Debug. Inference at -O0 is unusably slow,
# so optimise every variant (debug symbols are still available via the NDK's -g in RelWithDebInfo).
set(CMAKE_C_FLAGS_DEBUG "-O3 -DNDEBUG")
set(CMAKE_CXX_FLAGS_DEBUG "-O3 -DNDEBUG")

find_library(LOG_LIB log)
include(FetchContent)

# Mirrors the structure of whisper.cpp's own examples/whisper.android build
# (the upstream-tested way to build the engine for Android with CPU-only ggml).
# A single generic library is built for every ABI. Per-CPU variants (for example
# armv8.2 fp16) are a Milestone 4 optimisation: they need runtime CPU detection
# and must be benchmarked before being enabled.
add_library(openear_whisper SHARED
    ${WHISPER_DIR}/src/whisper.cpp
    ${WHISPER_DIR}/src/aneforge/whisper-aneforge.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/openear_jni.cpp
)
target_compile_definitions(openear_whisper PUBLIC GGML_USE_CPU)
target_compile_definitions(openear_whisper PRIVATE WHISPER_VERSION="1.9.5")

if(NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    target_compile_options(openear_whisper PRIVATE -O3 -fvisibility=hidden -fvisibility-inlines-hidden
                                                  -ffunction-sections -fdata-sections)
    target_link_options(openear_whisper PRIVATE -Wl,--gc-sections -Wl,--exclude-libs,ALL -flto)
endif()

FetchContent_Declare(ggml SOURCE_DIR ${WHISPER_DIR}/ggml)
FetchContent_MakeAvailable(ggml)

target_include_directories(openear_whisper PRIVATE
    ${WHISPER_DIR}
    ${WHISPER_DIR}/src
    ${WHISPER_DIR}/include
    ${WHISPER_DIR}/ggml/include
    ${WHISPER_DIR}/ggml/src
    ${WHISPER_DIR}/ggml/src/ggml-cpu
)
target_link_libraries(openear_whisper ${LOG_LIB} android ggml)
