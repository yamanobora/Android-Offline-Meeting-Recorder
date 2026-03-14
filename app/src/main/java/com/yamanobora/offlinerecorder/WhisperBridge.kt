package com.yamanobora.offlinerecorder

object WhisperBridge {

    external fun initModel(modelPath: String): Boolean

    external fun runWhisper(
        audio: FloatArray
    ): String

    external fun freeModel()

    init {
        System.loadLibrary("ai-chat")
    }
}