#include <jni.h>
#include <string>
#include "llama.h"

static llama_model* g_model = nullptr;
static llama_context* g_ctx = nullptr;

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_nexa_ai_offline_LocalLLMManager_loadModel(
    JNIEnv* env,
    jobject /* thiz */,
    jstring modelPath
) {
    if (!modelPath) return 0;
    const char* path = env->GetStringUTFChars(modelPath, nullptr);

    llama_model_params model_params = llama_model_default_params();
    g_model = llama_load_model_from_file(path, model_params);

    env->ReleaseStringUTFChars(modelPath, path);

    if (!g_model) return 0;

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 2048;
    g_ctx = llama_new_context_with_model(g_model, ctx_params);

    return reinterpret_cast<jlong>(g_ctx);
}

JNIEXPORT void JNICALL
Java_com_nexa_ai_offline_LocalLLMManager_freeModel(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jlong ptr
) {
    if (ptr != 0 && g_ctx) {
        llama_free(g_ctx);
        llama_free_model(g_model);
    }
}

JNIEXPORT jstring JNICALL
Java_com_nexa_ai_offline_LocalLLMManager_generate(
    JNIEnv* env,
    jobject /* thiz */,
    jlong ptr,
    jstring prompt,
    jint maxTokens
) {
    if (!prompt) return env->NewStringUTF("");
    const char* input = env->GetStringUTFChars(prompt, nullptr);

    std::string result;
    // Limit token buffer size to maxTokens or a reasonable maximum
    int safeMax = (maxTokens > 0 && maxTokens < 4096) ? maxTokens : 256;
    
    // Allocate buffer on heap to prevent stack overflow for large token limits
    auto* tokens = new llama_token[safeMax];
    int n_tokens = llama_tokenize(g_model, input, tokens, safeMax, true);

    llama_eval(g_ctx, tokens, n_tokens, 0, 1);

    for (int i = 0; i < safeMax; i++) {
        llama_token token = llama_sample_token(g_ctx);
        result += llama_token_to_str(g_model, token);
    }

    delete[] tokens;
    env->ReleaseStringUTFChars(prompt, input);
    return env->NewStringUTF(result.c_str());
}

}
