cmake_minimum_required(VERSION 3.22.1)
project(ai-chat)

set(LLAMA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp)

add_subdirectory(${LLAMA_DIR})

add_library(ai-chat SHARED
        ai_chat.cpp
        native_bridge.cpp
        whisper_jni.cpp
        cpp-master/src/whisper.cpp
)

target_include_directories(ai-chat PRIVATE
        ${LLAMA_DIR}
        ${LLAMA_DIR}/include
        ${LLAMA_DIR}/common
        ${CMAKE_CURRENT_SOURCE_DIR}/cpp-master/include
)

target_link_libraries(ai-chat
        llama
        log
)