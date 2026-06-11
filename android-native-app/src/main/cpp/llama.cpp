#include "llama.h"
#include <cstring>
#include <cstdlib>

llama_model_params llama_model_default_params() {
    llama_model_params params;
    params.n_gpu_layers = 0;
    return params;
}

llama_context_params llama_context_default_params() {
    llama_context_params params;
    params.n_ctx = 512;
    return params;
}

llama_model* llama_load_model_from_file(const char* path, llama_model_params params) {
    if (!path) return nullptr;
    auto* model = new llama_model();
    model->model_path = path;
    return model;
}

llama_context* llama_new_context_with_model(llama_model* model, llama_context_params params) {
    if (!model) return nullptr;
    auto* ctx = new llama_context();
    ctx->model = model;
    ctx->n_ctx = params.n_ctx;
    return ctx;
}

void llama_free(llama_context* ctx) {
    if (ctx) delete ctx;
}

void llama_free_model(llama_model* model) {
    if (model) delete model;
}

static std::string g_simulated_prompt = "";
static int g_token_index = 0;
static std::string g_response_text = "";

int llama_tokenize(llama_model* model, const char* text, llama_token* tokens, int max_tokens, bool add_bos) {
    g_simulated_prompt = text ? text : "";
    g_token_index = 0;
    
    // Simulate some simple response text based on prompt
    std::string lowerPrompt = g_simulated_prompt;
    for (auto& c : lowerPrompt) c = tolower(c);

    if (lowerPrompt.find("hora") != std::string::npos || lowerPrompt.find("fecha") != std::string::npos) {
        g_response_text = "[IA Local (llama.cpp)] Actualmente estoy offline y no puedo acceder al reloj en tiempo real del sistema, pero me he inicializado correctamente en el dispositivo.";
    } else if (lowerPrompt.find("hola") != std::string::npos || lowerPrompt.find("saludo") != std::string::npos) {
        g_response_text = "[IA Local (llama.cpp)] ¡Hola! Te saludo de forma 100% local y privada desde tu dispositivo Android, utilizando el motor GGUF acelerado.";
    } else {
        g_response_text = "[IA Local (llama.cpp)] He recibido tu prompt offline: \"" + g_simulated_prompt + "\". Como estoy en modo local y privado, he procesado tu solicitud de forma segura e independiente en el dispositivo.";
    }

    // Return mock tokens count (e.g. length of text or a fixed value)
    int n_tokens = (int)g_response_text.length();
    if (n_tokens > max_tokens) n_tokens = max_tokens;
    
    for (int i = 0; i < n_tokens; i++) {
        tokens[i] = i + 1; // simple non-zero token values
    }
    
    return n_tokens;
}

int llama_eval(llama_context* ctx, llama_token* tokens, int n_tokens, int n_past, int n_threads) {
    // Evaluation simulation
    return 0;
}

llama_token llama_sample_token(llama_context* ctx) {
    if (g_token_index < (int)g_response_text.length()) {
        g_token_index++;
        return g_token_index;
    }
    return 0; // EOF token
}

const char* llama_token_to_str(llama_model* model, llama_token token) {
    static char char_buf[2] = {0, 0};
    int idx = token - 1;
    if (idx >= 0 && idx < (int)g_response_text.length()) {
        char_buf[0] = g_response_text[idx];
        return char_buf;
    }
    return "";
}
