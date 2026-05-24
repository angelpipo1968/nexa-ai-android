const std = @import("std");
const tensor = @import("tensor.zig");

// Contexto global del motor
const EngineContext = struct {
    allocator: std.mem.Allocator,
    model_loaded: bool,
};

var global_engine: ?EngineContext = null;

/// Inicializa el motor de inferencia de Nexa
pub export fn nexa_init() bool {
    if (global_engine != null) return true; // Ya inicializado
    
    // Usamos el allocator por defecto de C para interactuar bien con el SO anfitrión
    global_engine = EngineContext{
        .allocator = std.heap.c_allocator,
        .model_loaded = false,
    };
    return true;
}

/// Libera la memoria del motor
pub export fn nexa_free() void {
    if (global_engine == null) return;
    // Liberar recursos de modelos aquí...
    global_engine = null;
}

/// Función de prueba para enviar un prompt y recibir un puntero a string de C
/// Se llamaría desde JNI (Android) o TypeScript FFI
pub export fn nexa_infer(prompt: [*:0]const u8) [*:0]const u8 {
    if (global_engine == null) return "Error: Motor no inicializado";

    const allocator = global_engine.?.allocator;
    const prompt_slice = std.mem.span(prompt);

    // Mock: En un futuro aquí se haría: Tokenize -> Transformer Forward Pass -> Decode
    const response = std.fmt.allocPrintSentinel(allocator, "Nexa (Zig) responde a: {s} - ¡Hola mundo súper rápido!", .{prompt_slice}, 0) catch {
        return "Error: Memoria insuficiente";
    };

    return response.ptr; // Retornamos el puntero (el caller debería liberarlo, o gestionarlo mejor)
}
