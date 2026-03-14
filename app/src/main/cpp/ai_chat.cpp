#include <android/log.h>
#include <jni.h>
#include <string>
#include <unistd.h>

#include "whisper.h"
#include "whisper_version.h"

#include "llama.h"
#include "ggml.h"
#include <android/log.h>
#define NLOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "AI_NATIVE", __VA_ARGS__)


const char * LLAMA_COMMIT = "local_build";
int LLAMA_BUILD_NUMBER = 0;
// グローバルでモデルとコンテキストを保持
static llama_model*   g_model = nullptr;
static llama_context* g_ctx   = nullptr;
static llama_sampler* g_smpl  = nullptr;

extern "C" void ai_chat_load_model(const char* modelPath) {

    if (g_ctx != nullptr) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model != nullptr) { llama_model_free(g_model); g_model = nullptr; }
    if (g_smpl != nullptr) { llama_sampler_free(g_smpl); g_smpl = nullptr; }

    llama_model_params model_params = llama_model_default_params();
    g_model = llama_model_load_from_file(modelPath, model_params);

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 4096;
    ctx_params.n_batch = 2048;
    ctx_params.n_ubatch = 1024;

    int n_threads = sysconf(_SC_NPROCESSORS_ONLN);

    ctx_params.n_threads = n_threads;
    ctx_params.n_threads_batch = n_threads;

    g_ctx = llama_new_context_with_model(g_model, ctx_params);

    // ★ 正しいサンプラー初期化（simple-chat と同じ）
    g_smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());

    llama_sampler_chain_add(g_smpl, llama_sampler_init_temp(0.3f));
    llama_sampler_chain_add(g_smpl, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(g_smpl, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(g_smpl, llama_sampler_init_greedy());
}

#include <sstream>
#include <vector>


extern "C"
std::string ai_chat_summarize(const char* input) {

    llama_sampler_reset(g_smpl);
    NLOGD("enter, input=%s", input ? input : "(null)");
    NLOGD("g_model=%p g_ctx=%p g_smpl=%p", g_model, g_ctx, g_smpl);

    if (!g_model || !g_ctx || !g_smpl) {
        NLOGD("model/context/sampler not ready");
        return "モデルがロードされていません。";
    }

    const llama_vocab* vocab = llama_model_get_vocab(g_model);

    // ★ プロンプト生成（ここだけ置き換える）
    std::string prompt;

    prompt =
            "次の文章を日本語で箇条書きで要約してください。\n\n";
    prompt += input;
    prompt += "\n\n要約:\n";

    const bool is_first = true;
    int n_tokens = -llama_tokenize(
            vocab,
            prompt.c_str(),
            prompt.size(),
            nullptr,
            0,
            is_first,
            true
    );
    NLOGD("n_tokens=%d", n_tokens);

    if (n_tokens <= 0) {
        return "トークン化に失敗しました。";
    }

    std::vector<llama_token> tokens(n_tokens);
    if (llama_tokenize(
            vocab,
            prompt.c_str(),
            prompt.size(),
            tokens.data(),
            tokens.size(),
            is_first,
            true) < 0) {
        return "トークン化に失敗しました。";
    }

    int n_ctx = llama_n_ctx(g_ctx);
    NLOGD("n_ctx=%d, tokens.size=%zu", n_ctx, tokens.size());
    if ((int)tokens.size() > n_ctx) {
        return "コンテキストサイズを超えました。";
    }

    // ① プロンプト decode
    NLOGD("before prompt decode");
    {
        llama_batch prompt_batch = llama_batch_get_one(tokens.data(), tokens.size());
        int ret = llama_decode(g_ctx, prompt_batch);
        NLOGD("after prompt decode, ret=%d", ret);
        if (ret != 0) {
            return "プロンプトの推論に失敗しました。";
        }
    }

    // ② 生成ループ
    std::string output;
    const int MAX_STEPS = 24;  // 好きな値でOK
    int step = 0;
    while (true) {
        if (step >= MAX_STEPS) {
            NLOGD("max steps reached");
            break;
        }

        NLOGD("step=%d before sample", step);

        llama_token new_token = llama_sampler_sample(g_smpl, g_ctx, -1);

        if (llama_vocab_is_eog(vocab, new_token) ||
            new_token == llama_vocab_eos(vocab)) {
            NLOGD("EOS/EOG reached at step=%d", step);
            break;
        }

        char buf[256];
        int n = llama_token_to_piece(vocab, new_token, buf, sizeof(buf), 0, true);
        if (n < 0) {
            return "トークン変換に失敗しました。";
        }
        output.append(buf, n);

        NLOGD("step=%d before gen decode", step);
        llama_batch gen_batch = llama_batch_get_one(&new_token, 1);
        int ret = llama_decode(g_ctx, gen_batch);
        NLOGD("step=%d after gen decode, ret=%d", step, ret);
        if (ret != 0) {
            return "生成中の推論に失敗しました。";
        }

        if (output.size() > 2000) {
            NLOGD("output limit reached");
            break;
        }

        step++;
    }

    NLOGD("done, output.size=%zu", output.size());
    return output;
}

/**
 * LLama resources: context, model, batch and sampler
 */
constexpr int   N_THREADS_MIN           = 2;
constexpr int   N_THREADS_MAX           = 4;
constexpr int   N_THREADS_HEADROOM      = 2;

constexpr int   DEFAULT_CONTEXT_SIZE    = 8192;
constexpr int   OVERFLOW_HEADROOM       = 4;
//constexpr int   BATCH_SIZE              = 512;
constexpr float DEFAULT_SAMPLER_TEMP    = 0.3f;


/*
static llama_context *init_context(llama_model *model, const int n_ctx = DEFAULT_CONTEXT_SIZE) {
    if (!model) {

        return nullptr;
    }

    // Multi-threading setup
    const int n_threads = std::max(N_THREADS_MIN, std::min(N_THREADS_MAX,
                                                     (int) sysconf(_SC_NPROCESSORS_ONLN) -
                                                     N_THREADS_HEADROOM));
    fprintf(stderr, "%s: Using %d threads\n", __func__, n_threads);

    // Context parameters setup
    llama_context_params ctx_params = llama_context_default_params();
    const int trained_context_size = llama_model_n_ctx_train(model);
    if (n_ctx > trained_context_size) {
    }
    ctx_params.n_ctx = n_ctx;
    //ctx_params.n_batch = BATCH_SIZE;
    //ctx_params.n_ubatch = BATCH_SIZE;
    ctx_params.n_threads = n_threads;
    ctx_params.n_threads_batch = n_threads;
    auto *context = llama_init_from_model(g_model, ctx_params);
    if (context == nullptr) {
        fprintf(stderr, "%s: Enforcing context size: %d\n", __func__, n_ctx);
    }
    return context;
}
 */


static std::string get_backend() {
    std::vector<std::string> backends;
    for (size_t i = 0; i < ggml_backend_reg_count(); i++) {
        auto *reg = ggml_backend_reg_get(i);
        std::string name = ggml_backend_reg_name(reg);
        if (name != "CPU") {
            backends.push_back(ggml_backend_reg_name(reg));
        }
    }
    return backends.empty() ? "CPU" : "UNKNOWN";
}




/**
 * Completion loop's long-term states:
 * - chat management
 * - position tracking
 */
constexpr const char *ROLE_SYSTEM       = "system";
constexpr const char *ROLE_USER         = "user";
constexpr const char *ROLE_ASSISTANT    = "assistant";

static llama_pos system_prompt_position;
static llama_pos current_position;

static void reset_long_term_states(const bool clear_kv_cache = true) {
    system_prompt_position = 0;
    current_position = 0;

    if (clear_kv_cache)
        llama_memory_clear(llama_get_memory(g_ctx), false);
}

/**
 * TODO-hyin: implement sliding-window version as a better alternative
 *
 * Context shifting by discarding the older half of the tokens appended after system prompt:
 * - take the [system_prompt_position] first tokens from the original prompt
 * - take half of the last (system_prompt_position - system_prompt_position) tokens
 * - recompute the logits in batches
 */

/**
 * Completion loop's short-term states:
 * - stop generation position
 * - token chars caching
 * - current assistant message being generated
 */
static llama_pos stop_generation_position;
static std::string cached_token_chars;
static std::ostringstream assistant_ss;

static void reset_short_term_states() {
    stop_generation_position = 0;
    cached_token_chars.clear();
    assistant_ss.str("");
}


static bool is_valid_utf8(const char *string) {
    if (!string) { return true; }

    const auto *bytes = (const unsigned char *) string;
    int num;

    while (*bytes != 0x00) {
        if ((*bytes & 0x80) == 0x00) {
            // U+0000 to U+007F
            num = 1;
        } else if ((*bytes & 0xE0) == 0xC0) {
            // U+0080 to U+07FF
            num = 2;
        } else if ((*bytes & 0xF0) == 0xE0) {
            // U+0800 to U+FFFF
            num = 3;
        } else if ((*bytes & 0xF8) == 0xF0) {
            // U+10000 to U+10FFFF
            num = 4;
        } else {
            return false;
        }

        bytes += 1;
        for (int i = 1; i < num; ++i) {
            if ((*bytes & 0xC0) != 0x80) {
                return false;
            }
            bytes += 1;
        }
    }
    return true;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_yamanobora_offlinerecorder_summarizer_AiSummarizer_loadModel(
        JNIEnv* env,
        jobject /* this */,
        jstring jpath) {

    const char* path = env->GetStringUTFChars(jpath, nullptr);
    ai_chat_load_model(path);
    env->ReleaseStringUTFChars(jpath, path);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_yamanobora_offlinerecorder_summarizer_AiSummarizer_summarizeWithLlama(
        JNIEnv* env,
        jobject /* this */,
        jstring jtext) {

    const char* text = env->GetStringUTFChars(jtext, nullptr);
    std::string result = ai_chat_summarize(text);
    env->ReleaseStringUTFChars(jtext, text);

    return env->NewStringUTF(result.c_str());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_yamanobora_offlinerecorder_summarizer_AiSummarizer_nativeTest(
        JNIEnv* env,
        jobject /* this */,
        jstring input) {

    const char* str = env->GetStringUTFChars(input, nullptr);
    std::string out = std::string("JNI通った: ") + str;
    env->ReleaseStringUTFChars(input, str);

    return env->NewStringUTF(out.c_str());
}