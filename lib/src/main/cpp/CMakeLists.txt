cmake_minimum_required(VERSION 3.22.1)
project("ai-chat" VERSION 1.0.0 LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED TRUE)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED TRUE)

# --------------------------------------------------------------------------
# Android ABI 判定
# --------------------------------------------------------------------------
if(DEFINED ANDROID_ABI)
    message(STATUS "Detected Android ABI: ${ANDROID_ABI}")
    if(ANDROID_ABI STREQUAL "arm64-v8a")
        set(GGML_SYSTEM_ARCH "ARM")
        set(GGML_CPU_KLEIDIAI OFF)
        set(GGML_OPENMP OFF)
    elseif(ANDROID_ABI STREQUAL "x86_64")
        set(GGML_SYSTEM_ARCH "x86")
        set(GGML_CPU_KLEIDIAI OFF)
        set(GGML_OPENMP OFF)
    else()
        message(FATAL_ERROR "Unsupported ABI: ${ANDROID_ABI}")
    endif()
endif()

# --------------------------------------------------------------------------
# llama.cpp ソースディレクトリ
# --------------------------------------------------------------------------
get_filename_component(PROJECT_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../.." ABSOLUTE)
set(LLAMA_SRC "${PROJECT_ROOT}/llama.cpp-master")
message(STATUS "LLAMA_SRC = ${LLAMA_SRC}")

# llama 側で common/*.cpp をビルドさせる
set(LLAMA_BUILD_COMMON ON CACHE BOOL "Enable building common utils in llama" FORCE)

# llama ライブラリをビルド（common/*.cpp も llama 側に含まれる）
add_subdirectory(${LLAMA_SRC} ${CMAKE_BINARY_DIR}/llama)

# --------------------------------------------------------------------------
# ai-chat ライブラリ
# --------------------------------------------------------------------------
# ai_chat.cpp と common 系ファイルを直接組み込む
add_library(${PROJECT_NAME} SHARED
        ../../../../app/src/main/cpp/ai_chat.cpp
        ../../../../app/src/main/cpp/native_bridge.cpp
)


target_compile_definitions(${PROJECT_NAME} PRIVATE
        GGML_SYSTEM_ARCH=${GGML_SYSTEM_ARCH}
        GGML_CPU_KLEIDIAI=$<BOOL:${GGML_CPU_KLEIDIAI}>
        GGML_OPENMP=$<BOOL:${GGML_OPENMP}>
        LLAMA_COMMIT_STR="local_build"
)

target_include_directories(${PROJECT_NAME} PRIVATE
        ${LLAMA_SRC}
        ${LLAMA_SRC}/common
        ${LLAMA_SRC}/include
        ${LLAMA_SRC}/ggml/include
        ${LLAMA_SRC}/ggml/src
        ${LLAMA_SRC}/vendor
)

# --------------------------------------------------------------------------
# llama ライブラリをリンク（common.cpp はすでに組み込まれている）
# --------------------------------------------------------------------------
target_link_libraries(${PROJECT_NAME}
        llama        # common.cpp/chat.cpp/sampling.cpp は含まれない

        android
        log
)