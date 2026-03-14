package com.yamanobora.offlinerecorder.audio

// 追加 import

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.yamanobora.offlinerecorder.RecordingSession
import com.yamanobora.offlinerecorder.WhisperBridge
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class SegmentedRecorder(private val context: Context) {
    interface AmplitudeListener {
        fun onAmplitude(amp: Int)
    }

    private var amplitudeListener: AmplitudeListener? = null

    fun setAmplitudeListener(listener: AmplitudeListener?) {
        this.amplitudeListener = listener
    }

    var session: RecordingSession? = null

    private var lastText = ""

    private val TAG = "SegmentedRecorder"

    @JvmField
    var realtimeListener: ((String) -> Unit)? = null

    fun setRealtimeListener(listener: (String) -> Unit) {
        realtimeListener = listener
    }

    private val audioDir: File by lazy {
        File(context.cacheDir, "audio").apply {
            if (!exists()) mkdirs()
        }
    }

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private val isRecording = AtomicBoolean(false)

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private val bufferSize: Int by lazy {
        AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
    }

    // ★ 追加：現在のファイル
    private var currentOutput: FileOutputStream? = null
    private var currentFile: File? = null
    private var segmentStartTime: Long = 0L

    // ★ 10分（ミリ秒）
    private val segmentDurationMs = 10 * 60 * 1000L

    fun start() {
        if (isRecording.get()) return

        clearFiles()

        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED) {

            Log.e(TAG, "RECORD_AUDIO permission not granted")
            return
        }

        Log.d(TAG, "start() called")

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        audioRecord?.startRecording()
        isRecording.set(true)

        // ★ 最初のファイルを作成
        openNewSegment()

        recordingThread = Thread({

            val buffer = ByteArray(bufferSize)

            while (isRecording.get()) {

                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0

                if (read > 0) {

                    // ★ 音量取得
                    var max = 0
                    var i = 0

                    while (i < read - 1) {

                        val lo = buffer[i].toInt() and 0xff
                        val hi = buffer[i + 1].toInt()

                        val sample = (hi shl 8) or lo
                        val absSample = kotlin.math.abs(sample)

                        if (absSample > max) {
                            max = absSample
                        }

                        i += 2
                    }

                    amplitudeListener?.onAmplitude(max)

                    // ★ PCM保存
                    currentOutput?.write(buffer, 0, read)


                }
            }

        }, "AudioRecord Thread")

        recordingThread?.start()
    }

    fun stop() {
        Log.d(TAG, "stop() called")

        isRecording.set(false)

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        recordingThread?.join()
        recordingThread = null

        // ★ 最後のファイルを閉じる
        closeCurrentSegment()

        // ★ PCM → WAV 変換（←ここに入れる）
        val pcmFiles = getRecordedFiles().filter { it.extension == "pcm" }
        pcmFiles.forEach { pcm ->
            val wav = convertPcmToWav(pcm)
            pcm.delete() // PCM は削除
            Log.d(TAG, "Converted to WAV: ${wav.absolutePath}")
        }
    }

    // ★ 新しいファイルを作る
    private fun openNewSegment() {
        segmentStartTime = System.currentTimeMillis()

        val fileName = "segment_${segmentStartTime}.pcm"
        currentFile = File(audioDir, fileName)

        currentOutput = FileOutputStream(currentFile)
        Log.d(TAG, "Opened new segment: ${currentFile?.absolutePath}")
    }

    // ★ ファイルを切り替える
    private fun rotateSegment() {
        closeCurrentSegment()
        openNewSegment()
    }

    // ★ ファイルを閉じる
    private fun closeCurrentSegment() {
        try {
            currentOutput?.flush()
            currentOutput?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        currentOutput = null
    }

    fun getRecordedFiles(): List<File> {
        return audioDir.listFiles()?.toList() ?: emptyList()
    }

    fun clearFiles() {
        audioDir.listFiles()?.forEach { it.delete() }
    }

    private fun writeWavHeader(
        out: FileOutputStream,
        totalAudioLen: Long,
        sampleRate: Int = 16000,
        channels: Int = 1,
        bitsPerSample: Int = 16
    ) {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val totalDataLen = totalAudioLen + 36

        val header = ByteArray(44)

        // RIFF/WAVE header
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()

        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()

        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()

        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0

        header[20] = 1
        header[21] = 0

        header[22] = channels.toByte()
        header[23] = 0

        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()

        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()

        header[32] = ((channels * bitsPerSample) / 8).toByte()
        header[33] = 0

        header[34] = bitsPerSample.toByte()
        header[35] = 0

        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()

        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

        out.write(header, 0, 44)
    }

    fun convertPcmToWav(pcmFile: File): File {
        val wavFile = File(pcmFile.parent, pcmFile.nameWithoutExtension + ".wav")

        val pcmData = pcmFile.readBytes()
        val totalAudioLen = pcmData.size.toLong()

        FileOutputStream(wavFile).use { out ->
            writeWavHeader(out, totalAudioLen)
            out.write(pcmData)
        }
        return wavFile
    }

    private fun pcm16ToFloat(buffer: ByteArray): FloatArray {

        val samples = buffer.size / 2
        val floats = FloatArray(samples)

        var i = 0
        var j = 0

        while (i < buffer.size) {

            val lo = buffer[i].toInt() and 0xff
            val hi = buffer[i + 1].toInt()

            val sample = (hi shl 8) or lo

            floats[j] = sample / 32768.0f

            i += 2
            j++
        }

        return floats
    }

}