const std = @import("std");
const c_api = @import("c_api.zig");

pub fn main() !void {
    std.debug.print("=== NEXA INFERENCE ENGINE (ZIG NATIVE TEST) ===\n", .{});
    std.debug.print("[1/3] Inicializando el motor...\n", .{});
    
    // Inicializar el motor
    if (!c_api.nexa_init()) {
        std.debug.print("❌ Error al inicializar el motor\n", .{});
        return;
    }
    defer {
        std.debug.print("\n[3/3] Liberando recursos del motor...\n", .{});
        c_api.nexa_free();
        std.debug.print("✅ Motor liberado exitosamente.\n", .{});
    }
    
    std.debug.print("✅ ¡Motor inicializado con éxito!\n\n", .{});
    
    // Ejecutar inferencia
    const prompt = "Hola Nexa, cuéntame una historia corta sobre un programador.";
    std.debug.print("[2/3] Enviando Prompt de Inferencia...\n", .{});
    std.debug.print("   > Prompt: \"{s}\"\n", .{prompt});
    
    // El motor acepta un puntero de C null-terminated
    const response = c_api.nexa_infer(prompt);
    
    std.debug.print("\n💬 Respuesta de Inferencia del Motor (FFI):\n", .{});
    std.debug.print("   ========================================\n", .{});
    std.debug.print("   {s}\n", .{response});
    std.debug.print("   ========================================\n", .{});
}
