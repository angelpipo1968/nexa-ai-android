#ifndef LLAMA_H
#define LLAMA_H

#include <string>

typedef int llama_token;

struct llama_model {
    std::string model_path;
};

struct llama_context {
    llama_model* model;
    int n_ctx;
};

struct llama_model_params {
    int n_gpu_layers;
};

struct llama_context_params {
    int n_ctx;
};

llama_model_params llama_model_default_params();
llama_context_params llama_context_default_params();

llama_model* llama_load_model_from_file(const char* path, llama_model_params params);
llama_context* llama_new_context_with_model(llama_model* model, llama_context_params params);

void llama_free(llama_context* ctx);
void llama_free_model(llama_model* model);

int llama_tokenize(llama_model* model, const char* text, llama_token* tokens, int max_tokens, bool add_bos);
int llama_eval(llama_context* ctx, llama_token* tokens, int n_tokens, int n_past, int n_threads);
llama_token llama_sample_token(llama_context* ctx);
const char* llama_token_to_str(llama_model* model, llama_token token);

#endif // LLAMA_H
