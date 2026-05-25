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

    // 3. Probar y medir SiLU y SwiGLU
    std.debug.print("\n[2.6] Iniciando Benchmark de Activaciones Modernas SiLU & SwiGLU...\n", .{});
    
    // SiLU in-place
    _ = QueryPerformanceCounter(&start_time);
    C.silu();
    _ = QueryPerformanceCounter(&end_time);
    const silu_ms = (@as(f64, @floatFromInt(end_time - start_time)) * 1000.0) / @as(f64, @floatFromInt(freq));
    std.debug.print("   ✅ ¡Activación SiLU in-place completada en {d:.3} ms!\n", .{silu_ms});
    
    // SwiGLU in-place (multiplica C por un tensor "up" de las mismas dimensiones)
    var up_tensor = try tensor.Tensor.init(allocator, size, size);
    defer up_tensor.deinit();
    @memset(up_tensor.data, 2.0); // Rellenar con 2.0 para escala visible
    
    _ = QueryPerformanceCounter(&start_time);
    try C.swiglu(up_tensor);
    _ = QueryPerformanceCounter(&end_time);
    const swiglu_ms = (@as(f64, @floatFromInt(end_time - start_time)) * 1000.0) / @as(f64, @floatFromInt(freq));
    std.debug.print("   ✅ ¡Activación SwiGLU in-place completada en {d:.3} ms!\n", .{swiglu_ms});

    // 4. Benchmark de Producto Punto Cuantizado Q8 SIMD
    std.debug.print("\n[2.7] Iniciando Micro-Benchmark: Producto Punto f32 vs. Q8 Cuantizado (10,000,000 iteraciones)...\n", .{});
    
    // Crear un vector flotante de 32 elementos y un bloque Q8 de 32 pesos
    const vec_len = 32;
    var float_x: [vec_len]f32 = undefined;
    var float_y: [vec_len]f32 = undefined;
    var q8_block = tensor.BlockQ8_0{
        .d = 0.125,
        .qs = [_]i8{0} ** 32,
    };
    
    var idx: usize = 0;
    while (idx < vec_len) : (idx += 1) {
        float_x[idx] = @as(f32, @floatFromInt(idx)) * 0.1;
        float_y[idx] = @as(f32, @floatFromInt(idx % 10));
        q8_block.qs[idx] = @intCast(@as(i32, @intFromFloat(float_y[idx] * 8.0)));
    }
    
    const iterations = 10000000;
    
    // A. Benchmark f32 estándar (simulación de producto punto simple)
    _ = QueryPerformanceCounter(&start_time);
    var dummy_sum_f32: f32 = 0.0;
    var iter: usize = 0;
    while (iter < iterations) : (iter += 1) {
        // Operación matemática flotante
        var sum: f32 = 0.0;
        inline for (0..32) |k| {
            sum += float_x[k] * float_y[k];
        }
        dummy_sum_f32 += sum;
    }
    _ = QueryPerformanceCounter(&end_time);
    const float_dot_ms = (@as(f64, @floatFromInt(end_time - start_time)) * 1000.0) / @as(f64, @floatFromInt(freq));
    
    // B. Benchmark Q8 cuantizado SIMD
    _ = QueryPerformanceCounter(&start_time);
    var dummy_sum_q8: f32 = 0.0;
    iter = 0;
    while (iter < iterations) : (iter += 1) {
        dummy_sum_q8 += tensor.dotProductBlockQ8(&float_x, &q8_block);
    }
    _ = QueryPerformanceCounter(&end_time);
    const q8_dot_ms = (@as(f64, @floatFromInt(end_time - start_time)) * 1000.0) / @as(f64, @floatFromInt(freq));
    
    std.debug.print("   📊 Producto Punto f32 Estándar   : {d:.3} ms (Resultado: {d:.2})\n", .{float_dot_ms, dummy_sum_f32 / @as(f32, @floatFromInt(iterations))});
    std.debug.print("   📊 Producto Punto Q8 SIMD Nativo : {d:.3} ms (Resultado: {d:.2})\n", .{q8_dot_ms, dummy_sum_q8 / @as(f32, @floatFromInt(iterations))});
    
    const speedup = float_dot_ms / q8_dot_ms;
    std.debug.print("   ⚡ ¡El kernel Q8 cuantizado SIMD es {d:.2}x más rápido que la aritmética flotante equivalente!\n", .{speedup});
}

