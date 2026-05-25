const std = @import("std");
const tensor = @import("tensor.zig");
const gguf = @import("gguf.zig");

// Contexto global del motor
const EngineContext = struct {
    allocator: std.mem.Allocator,
    model_loaded: bool,
};

var global_engine: ?EngineContext = null;

// ==========================================
//   FFI C API - ESTÁNDAR Y TYPESCRIPT FFI
// ==========================================

/// Inicializa el motor de inferencia de Nexa
pub export fn nexa_init() bool {
    if (global_engine != null) return true; // Ya inicializado
    
    global_engine = EngineContext{
        .allocator = std.heap.c_allocator,
        .model_loaded = false,
    };
    return true;
}

/// Libera la memoria del motor
pub export fn nexa_free() void {
    if (global_engine == null) return;
    global_engine = null;
}

/// Carga un modelo GGUF y muestra su estructura binaria
pub export fn nexa_load_gguf(path: [*:0]const u8) bool {
    if (global_engine == null) return false;
    const allocator = global_engine.?.allocator;
    const path_slice = std.mem.span(path);

    gguf.parseGguf(allocator, path_slice) catch |err| {
        std.debug.print("❌ [C API] Error cargando GGUF: {}\n", .{err});
        return false;
    };

    global_engine.?.model_loaded = true;
    return true;
}

/// Función de prueba para enviar un prompt y recibir una respuesta
pub export fn nexa_infer(prompt: [*:0]const u8) [*:0]const u8 {
    if (global_engine == null) return "Error: Motor no inicializado";

    const allocator = global_engine.?.allocator;
    const prompt_slice = std.mem.span(prompt);

    // Respuesta dinámica simulada de alto rendimiento
    const response = std.fmt.allocPrintSentinel(allocator, "Nexa (Zig SIMD Engine) procesó el prompt: \"{s}\" - Inferencia nativa ultra-veloz completada.", .{prompt_slice}, 0) catch {
        return "Error: Memoria insuficiente";
    };

    return response.ptr;
}

// ==========================================
//   JNI BINDINGS - INTEGRACIÓN NATIVA ANDROID
// ==========================================

// Reconstrucción matemática de la tabla de funciones del JNIEnv
const JNIEnv = *const *const JNINativeInterface;

const JNINativeInterface = struct {
    padding: [167]*anyopaque,
    NewStringUTF: *const fn (env: ?*anyopaque, bytes: [*:0]const u8) callconv(.c) ?*anyopaque,
    padding2: [1]*anyopaque,
    GetStringUTFChars: *const fn (env: ?*anyopaque, str: ?*anyopaque, isCopy: ?*u8) callconv(.c) ?[*:0]const u8,
    ReleaseStringUTFChars: *const fn (env: ?*anyopaque, str: ?*anyopaque, chars: ?[*:0]const u8) callconv(.c) void,
};

/// JNI para inicializar el motor en Android
pub export fn Java_com_nexa_ai_NexaInference_nexaInit(env: ?*anyopaque, clazz: ?*anyopaque) callconv(.c) u8 {
    _ = env; _ = clazz;
    return if (nexa_init()) 1 else 0;
}

/// JNI para liberar el motor en Android
pub export fn Java_com_nexa_ai_NexaInference_nexaFree(env: ?*anyopaque, clazz: ?*anyopaque) callconv(.c) void {
    _ = env; _ = clazz;
    nexa_free();
}

/// JNI para cargar un modelo GGUF en Android
pub export fn Java_com_nexa_ai_NexaInference_nexaLoadGguf(env: ?*anyopaque, clazz: ?*anyopaque, j_path: ?*anyopaque) callconv(.c) u8 {
    _ = clazz;
    if (env == null or j_path == null) return 0;
    const jenv: JNIEnv = @ptrCast(@alignCast(env.?));
    const vtable = jenv.*;

    const chars = vtable.GetStringUTFChars(env, j_path, null);
    if (chars == null) return 0;
    defer vtable.ReleaseStringUTFChars(env, j_path, chars);

    return if (nexa_load_gguf(chars.?)) 1 else 0;
}

/// JNI para ejecutar inferencia en Android
pub export fn Java_com_nexa_ai_NexaInference_nexaInfer(env: ?*anyopaque, clazz: ?*anyopaque, j_prompt: ?*anyopaque) callconv(.c) ?*anyopaque {
    _ = clazz;
    if (env == null or j_prompt == null) return null;
    const jenv: JNIEnv = @ptrCast(@alignCast(env.?));
    const vtable = jenv.*;

    const chars = vtable.GetStringUTFChars(env, j_prompt, null);
    if (chars == null) return null;
    defer vtable.ReleaseStringUTFChars(env, j_prompt, chars);

    const response = nexa_infer(chars.?);
    return vtable.NewStringUTF(env, response);
}


