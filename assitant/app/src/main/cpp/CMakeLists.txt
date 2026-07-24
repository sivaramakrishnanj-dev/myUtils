cmake_minimum_required(VERSION 3.22)
project(whisper_android)

include(FetchContent)

# Pin whisper.cpp to a release tag; shallow-cloned at configure time and
# cached in the .cxx build directory afterwards.
FetchContent_Declare(
    whisper
    GIT_REPOSITORY https://github.com/ggml-org/whisper.cpp.git
    GIT_TAG v1.9.1
    GIT_SHALLOW TRUE
)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)

FetchContent_MakeAvailable(whisper)

add_library(whisper_android SHARED whisper_jni.cpp)

target_link_libraries(whisper_android
    whisper
    android
    log)
