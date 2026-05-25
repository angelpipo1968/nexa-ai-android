const std = @import("std");
const c_api = @import("c_api.zig");
const tensor = @import("tensor.zig");

extern "kernel32" fn QueryPerformanceCounter(lpPerformanceCount: *i64) callconv(.winapi) i32;
extern "kernel32" fn QueryPerformanceFrequency(lpFrequency: *i64) callconv(.winapi) i32;


pub fn main() !void {
    std.debug.print("====================================================\n", .{});
    std.debug.print("   🚀 NEXA INFERENCE ENGINE (ZIG NATIVE BENCHMARK)   \n", .{});
    std.debug.print("====================================================\n\n", .{});

    // 1. Probar la API de C
    std.debug.print("[1/3] Inicializando el motor FFI C API...\n", .{});
    if (!c_api.nexa_init()) {
        std.debug.print("❌ Error al inicializar el motor\n", .{});
        return;
    }
    defer {
        std.debug.print("\n[3/3] Liberando recursos del motor...\n", .{});
        c_api.nexa_free();
        std.debug.print("✅ Motor liberado exitosamente.\n", .{});
    }
    std.debug.print("✅ ¡Motor FFI C API inicializado con éxito!\n\n", .{});

    const prompt = "Hola Nexa, cuéntame una historia corta sobre un programador.";
    std.debug.print("[2/3] Probando Inferencia FFI Mock...\n", .{});
    std.debug.print("   > Prompt: \"{s}\"\n", .{prompt});
    const response = c_api.nexa_infer(prompt);
    std.debug.print("\n💬 Respuesta de Inferencia del Motor (FFI):\n", .{});
    std.debug.print("   ====================================================\n", .{});
    std.debug.print("   {s}\n", .{response});
    std.debug.print("   ====================================================\n\n", .{});

    // 2. Ejecutar Benchmark de Tensores Acelerados por SIMD
    std.debug.print("[2.5] Iniciando Benchmark de Tensores de Alto Rendimiento...\n", .{});
    
    const allocator = std.heap.c_allocator;

    const size = 256;
    std.debug.print("   * Asignando matrices A ({d}x{d}) y B ({d}x{d})...\n", .{size, size, size, size});
    var A = try tensor.Tensor.init(allocator, size, size);
    defer A.deinit();
    var B = try tensor.Tensor.init(allocator, size, size);
    defer B.deinit();

    // Rellenar con valores de prueba secuenciales y alternados
    var r: usize = 0;
    while (r < size) : (r += 1) {
        var c: usize = 0;
        while (c < size) : (c += 1) {
            const rf = @as(f32, @floatFromInt(r));
            const cf = @as(f32, @floatFromInt(c));
            A.set(r, c, (rf + cf) * 0.0001);
            B.set(r, c, (rf - cf) * 0.0001);
        }
    }

    std.debug.print("   * Ejecutando multiplicación de matrices A * B usando optimización SIMD ({d} FLOPs)... \n", .{2 * size * size * size});
    
    var freq: i64 = 0;
    var start_time: i64 = 0;
    var end_time: i64 = 0;
    _ = QueryPerformanceFrequency(&freq);
    _ = QueryPerformanceCounter(&start_time);
    
    var C = try tensor.matmul(allocator, A, B);
    defer C.deinit();
    
    _ = QueryPerformanceCounter(&end_time);

    const elapsed_ticks = end_time - start_time;
    const ms = (@as(f64, @floatFromInt(elapsed_ticks)) * 1000.0) / @as(f64, @floatFromInt(freq));
    std.debug.print("   ✅ ¡Multiplicación completada en {d:.3} ms!\n", .{ms});
    std.debug.print("   * Muestra de elementos en diagonal de C (antes de activación):\n", .{});
    std.debug.print("     C[0,0] = {d:.6}, C[128,128] = {d:.6}\n", .{C.get(0, 0), C.get(128, 128)});

    // Demostrar bias + activación relu y sigmoid
    std.debug.print("\n   * Ejecutando suma de bias (element-wise addition) y ReLU SIMD...\n", .{});
    var bias = try tensor.Tensor.init(allocator, size, size);
    defer bias.deinit();
    @memset(bias.data, -0.05); // Bias negativo para probar ReLU

    try C.add(bias);
    C.relu();
    std.debug.print("   ✅ ¡Bias y ReLU aplicados!\n", .{});
    std.debug.print("     C[0,0] = {d:.6} (esperado 0.0 por ReLU), C[128,128] = {d:.6}\n", .{C.get(0, 0), C.get(128, 128)});

    std.debug.print("\n   * Aplicando activación Sigmoid element-wise...\n", .{});
    C.sigmoid();
    std.debug.print("   ✅ ¡Sigmoid aplicada!\n", .{});
    std.debug.print("     C[0,0] = {d:.6} (esperado 0.5 por sigmoid(0)), C[128,128] = {d:.6}\n", .{C.get(0, 0), C.get(128, 128)});
}

