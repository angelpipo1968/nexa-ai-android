const std = @import("std");

// Importamos nuestros módulos y los exponemos
pub const tensor = @import("tensor.zig");
pub const c_api = @import("c_api.zig");

// Bloque principal requerido por Zig para saber qué compilar en la librería
comptime {
    // Forzamos la inclusión de las funciones de c_api para que se exporten
    _ = c_api.nexa_init;
    _ = c_api.nexa_free;
    _ = c_api.nexa_infer;
}

test "Inclusión de tests" {
    std.testing.refAllDecls(@This());
}
