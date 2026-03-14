package com.yamanobora.offlinerecorder;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.yamanobora.offlinerecorder.audio.SegmentedRecorder;
//import com.yamanobora.offlinerecorder.audio.WhisperTranscriber;
import com.yamanobora.offlinerecorder.summarizer.AiSummarizer;
import com.yamanobora.offlinerecorder.summarizer.MeetingSummarizer;
import com.yamanobora.offlinerecorder.summarizer.Summarizer;
import com.yamanobora.offlinerecorder.summarizer.RuleBasedSummarizer;

import com.yamanobora.offlinerecorder.utils.AssetUtils;


public class MainActivity extends AppCompatActivity {

    String whisperModelPath;

    static {
        System.loadLibrary("llama");
        System.loadLibrary("ai-chat");
    }

    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private long startTime = 0;
    private TextView recordTime;


    // 追加
    private final ExecutorService summarizerExecutor =
            Executors.newSingleThreadExecutor();

    private WaveformView waveformView;

    // もし共有したいなら（任意だけどオススメ）
    private AiSummarizer aiSummarizer;

    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;

    private TextView statusText;
    private TextView resultText;
    private TextView summaryText;  // ← 要約結果表示用
    private Button recordButton;

    private boolean isRecording = false;
    private boolean keepListening = false;

    private RecordingSession session;
    private final StringBuilder confirmedText = new StringBuilder();
    private String currentPartial = "";
    private String lastRealtimeText = "";

    private Switch aiModeSwitch;

    private SegmentedRecorder recorder;





    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {}
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recordTime = findViewById(R.id.recordTime);

        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{android.Manifest.permission.RECORD_AUDIO},
                    1
            );
        }

        whisperModelPath = null;

        try {
            whisperModelPath = AssetUtils.copyAssetToCache(
                    this,
                    "models/ggml-base.bin"
            );
        } catch (IOException e) {
            e.printStackTrace();
        }

// コピー成功したら初期化
        if (whisperModelPath != null) {
            boolean ok = WhisperBridge.INSTANCE.initModel(whisperModelPath);
            Log.d("WHISPER_INIT", "model load = " + ok + " path=" + whisperModelPath);
        }

        waveformView = findViewById(R.id.waveformView);
        statusText = findViewById(R.id.statusText);
        resultText = findViewById(R.id.resultText);
        summaryText = findViewById(R.id.summaryText); // ← activity_main.xml に TextView を追加しておく
        recordButton = findViewById(R.id.recordButton);

        checkPermission();

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(listener);

        recorder = new SegmentedRecorder(this);

        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        );
        recognizerIntent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                Locale.JAPAN.toLanguageTag()
        );
        recognizerIntent.putExtra(
                RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                true
        );
        recognizerIntent.putExtra(
                RecognizerIntent.EXTRA_PREFER_OFFLINE,
                true
        );

        recordButton.setOnClickListener(v -> {
            if (!isRecording) {
                startRecording();
            } else {
                stopRecording();
            }
        });

        Button listButton = findViewById(R.id.listButton);

        listButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, MeetingListActivity.class);
            startActivity(intent);
        });

        aiModeSwitch = findViewById(R.id.aiModeSwitch);

        // ★★★ ここから AiSummarizer の初期化 ★★★

        aiSummarizer = new AiSummarizer(this);

        String modelPath = null;
        try {
            modelPath = AssetUtils.copyAssetToCache(
                    this,
                    "models/HACHI-Summary.gguf"
            );
            Log.d("ModelPath", "Model copied to: " + modelPath);
        } catch (IOException e) {
            Log.e("ModelPath", "Failed to copy model", e);
        }

        if (modelPath != null) {
            aiSummarizer.loadModel(modelPath);
        }

// ★★★ ここまで AiSummarizer の初期化 ★★★


// 保存された状態を復元
        SharedPreferences prefs =
                getSharedPreferences("settings", MODE_PRIVATE);

        boolean aiEnabled = prefs.getBoolean("ai_mode", false);
        aiModeSwitch.setChecked(aiEnabled);

// 切り替え時に保存
        aiModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit()
                    .putBoolean("ai_mode", isChecked)
                    .apply();
        });
        String test = aiSummarizer.nativeTest("hello");
        Log.d("JNI_TEST", "nativeTest returned = " + test);

        Log.d("AI_MODE", "Loaded ai_mode = " + aiEnabled);


    }

    private void checkPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    private void startRecording() {

        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{android.Manifest.permission.RECORD_AUDIO},
                    1
            );
            return;
        }

        isRecording = true;
        keepListening = true;

        session = new RecordingSession();
        confirmedText.setLength(0);
        currentPartial = "";

        resultText.setText("");
        summaryText.setText("");
        statusText.setText("録音中…");
        recordButton.setText("停止");

        recorder.setAmplitudeListener(amp -> {

            runOnUiThread(() -> {

                waveformView.addAmplitude(amp);

                statusText.setText("録音中…");

            });

        });

        recorder.start();
        startTime = System.currentTimeMillis();
        timerHandler.post(timerRunnable);
    }

    /** ⏹ 録音停止 → 保存 */
    private void stopRecording() {
        isRecording = false;
        keepListening = false;

        speechRecognizer.stopListening();
        recorder.stop();
        timerHandler.removeCallbacks(timerRunnable);

        statusText.setText("停止");
        recordButton.setText("録音開始");

        if (session != null) {
            session.finish();
            saveSession(session);
            session = null;
        }

        List<File> wavFiles = recorder.getRecordedFiles();
        for (File f : wavFiles) {
            Log.d("WAV_FILE", "Found WAV: " + f.getAbsolutePath());
        }

       // WhisperTranscriber wt = new WhisperTranscriber(this);

        if (!wavFiles.isEmpty()) {

            File lastWavFile = wavFiles.get(wavFiles.size() - 1);

            Log.d("WAV_FILE", "path=" + lastWavFile.getAbsolutePath());
            Log.d("WAV_FILE", "size=" + lastWavFile.length());

            String finalModelPath = whisperModelPath;

            new Thread(() -> {

                try {

                    float[] audio = loadWavAsFloatArray(lastWavFile);

                    String text = WhisperBridge.INSTANCE.runWhisper(audio);

                    Log.d("WHISPER_RESULT", text);

                    String summary = aiSummarizer.summarizeWithLlama(text);

                    Log.d("HACHI_SUMMARY", summary);
                    runOnUiThread(() -> summaryText.setText(summary));

                } catch (Exception e) {
                    e.printStackTrace();
                }

            }).start();
        }
    }

    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {

            long sec = (System.currentTimeMillis() - startTime) / 1000;

            long min = sec / 60;
            long s = sec % 60;

            String text = String.format("%02d:%02d", min, s);

            recordTime.setText(text);

            timerHandler.postDelayed(this, 500);
        }
    };

    /** 💾 JSON 保存＋要約 */
    private void saveSession(RecordingSession session) {

        String id = String.valueOf(session.startTime);
        String title = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN)
                .format(new Date(session.startTime));

        String fullText = session.getFullText();

        // ★ここ追加
        if (fullText == null || fullText.trim().isEmpty()) {
            Log.d("AI_JNI", "skip summarize (empty text)");
            return;
        }

        summarizerExecutor.execute(() -> {

            MeetingSummarizer summarizer = new MeetingSummarizer(this, aiSummarizer);

            Log.d("AI_JNI", "call ai_chat_summarize, text.length=" + fullText.length());

            String summary = summarizer.summarize(fullText);

            Log.d("AI_JNI", "ai_chat_summarize returned");

            runOnUiThread(() -> summaryText.setText(summary));

            MeetingRecord record = new MeetingRecord(
                    id,
                    title,
                    session.startTime,
                    session.endTime,
                    session.getBlocks(),
                    fullText,
                    summary
            );
            MeetingStorage.save(this, record);
        });
    }

    /** RecognitionListener */
    private final RecognitionListener listener = new RecognitionListener() {

        @Override public void onReadyForSpeech(Bundle params) {}
        @Override public void onBeginningOfSpeech() {}
        @Override public void onRmsChanged(float rmsdB) {}
        @Override public void onBufferReceived(byte[] buffer) {}

        @Override
        public void onEndOfSpeech() {
            if (keepListening) {
                speechRecognizer.startListening(recognizerIntent);
            }
        }

        @Override
        public void onError(int error) {
            if (keepListening) {
                speechRecognizer.startListening(recognizerIntent);
            }
        }

        @Override
        public void onResults(Bundle results) {
            ArrayList<String> list =
                    results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

            if (list != null && !list.isEmpty()) {
                String finalText = list.get(0);

                confirmedText.append(finalText).append("\n");
                currentPartial = "";

                if (session != null) {
                    session.addLine(finalText);
                }

                resultText.setText(confirmedText.toString());
            }

            if (keepListening) {
                speechRecognizer.startListening(recognizerIntent);
            }
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            ArrayList<String> list =
                    partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

            if (list != null && !list.isEmpty()) {
                currentPartial = list.get(0);
                resultText.setText(
                        confirmedText.toString() + currentPartial
                );
            }
        }

        @Override public void onEvent(int eventType, Bundle params) {}
    };

    private float[] loadWavAsFloatArray(File file) throws IOException {

        FileInputStream fis = new FileInputStream(file);
        byte[] data = fis.readAllBytes();
        fis.close();

        int header = 44;

        // 念のためヘッダサイズチェック
        if (data.length <= header) {
            throw new IOException("Invalid WAV file");
        }

        int samples = (data.length - header) / 2;

        float[] audio = new float[samples];

        for (int i = 0; i < samples; i++) {

            int low = data[header + i * 2] & 0xff;
            int high = data[header + i * 2 + 1];

            short val = (short)((high << 8) | low);

            audio[i] = val / 32768f;
        }

        return audio;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
    }
}
