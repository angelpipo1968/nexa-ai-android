const std = @import("std");

/// Bloque Q4_0 que almacena 32 pesos usando un factor de escala de 32-bits (d)
/// y 16 bytes de datos (cada byte guarda dos pesos de 4-bits / nibbles)
pub const BlockQ4_0 = struct {
    d: f32,          // Factor de escala (scale)
    qs: [16]u8,     // 32 pesos cuantizados en 4 bits
    
    /// Descomprime (de-cuantiza) el bloque a 32 flotantes en el array de salida
    pub fn dequantize(self: BlockQ4_0, out: *[32]f32) void {
        const d = self.d;
        var i: usize = 0;
        while (i < 16) : (i += 1) {
            const byte = self.qs[i];
            // Nibble inferior
            const q0 = @as(f32, @floatFromInt(@as(i8, @intCast(byte & 0x0F)) - 8));
            // Nibble superior
            const q1 = @as(f32, @floatFromInt(@as(i8, @intCast(byte >> 4)) - 8));
            out[i * 2] = q0 * d;
            out[i * 2 + 1] = q1 * d;
        }
    }
};

/// Bloque Q8_0 que almacena 32 pesos usando un factor de escala flotante de 32-bits (d)
/// y 32 bytes de datos (un entero con signo de 8-bits por peso)
pub const BlockQ8_0 = struct {
    d: f32,          // Factor de escala (scale)
    qs: [32]i8,     // 32 pesos cuantizados en 8 bits
    
    /// Descomprime (de-cuantiza) el bloque a 32 flotantes en el array de salida
    pub fn dequantize(self: BlockQ8_0, out: *[32]f32) void {
        const d = self.d;
        var i: usize = 0;
        while (i < 32) : (i += 1) {
            out[i] = @as(f32, @floatFromInt(self.qs[i])) * d;
        }
    }
};

/// Realiza el producto punto entre 32 floats de X y un BlockQ8_0 usando SIMD
pub fn dotProductBlockQ8(x: []const f32, y: *const BlockQ8_0) f32 {
    const vec_size = 8;
    const steps = 32 / vec_size; // Exactamente 4 iteraciones
    var sum: f32 = 0.0;
    
    var idx: usize = 0;
    while (idx < steps * vec_size) : (idx += vec_size) {
        // Cargar 8 elementos de X en un Vector
        const vx: @Vector(vec_size, f32) = x[idx..][0..vec_size].*;
        
        // Cargar 8 elementos de y.qs como Vector de i8
        const vy_i: @Vector(vec_size, i8) = y.qs[idx..][0..vec_size].*;
        // Convertir vector completo de enteros a vector de floats por hardware (SIMD)
        const vy: @Vector(vec_size, f32) = @floatFromInt(vy_i);
        
        sum += @reduce(.Add, vx * vy);
    }
    
    return sum * y.d;
}

/// Realiza el producto punto entre 32 floats de X y un BlockQ4_0 usando SIMD
pub fn dotProductBlockQ4(x: []const f32, y: *const BlockQ4_0) f32 {
    var sum: f32 = 0.0;
    const vec_size = 8;
    const steps = 32 / vec_size; // Exactamente 4 iteraciones de 8 elementos
    
    var idx: usize = 0;
    while (idx < steps * vec_size) : (idx += vec_size) {
        const vx: @Vector(vec_size, f32) = x[idx..][0..vec_size].*;
        
        var vy_f: [vec_size]f32 = undefined;
        inline for (0..vec_size) |i| {
            const global_idx = idx + i;
            const byte_idx = global_idx / 2;
            const is_upper = (global_idx % 2) != 0;
            const byte = y.qs[byte_idx];
            const q = if (is_upper) (byte >> 4) else (byte & 0x0F);
            vy_f[i] = @as(f32, @floatFromInt(@as(i8, @intCast(q)) - 8));
        }
        const vy: @Vector(vec_size, f32) = vy_f;
        sum += @reduce(.Add, vx * vy);
    }
    
    return sum * y.d;
}

/// Un tensor básico para la inferencia con optimizaciones nativas SIMD
pub const Tensor = struct {
    data: []f32,
    rows: usize,
    cols: usize,
    allocator: std.mem.Allocator,

    /// Inicializa un tensor lleno de ceros
    pub fn init(allocator: std.mem.Allocator, rows: usize, cols: usize) !Tensor {
        const data = try allocator.alloc(f32, rows * cols);
        @memset(data, 0.0);
        return Tensor{
            .data = data,
            .rows = rows,
            .cols = cols,
            .allocator = allocator,
        };
    }

    /// Libera la memoria del tensor
    pub fn deinit(self: *Tensor) void {
        self.allocator.free(self.data);
    }

    /// Obtener elemento en (fila, columna)
    pub fn get(self: Tensor, row: usize, col: usize) f32 {
        return self.data[row * self.cols + col];
    }

    /// Asignar elemento en (fila, columna)
    pub fn set(self: *Tensor, row: usize, col: usize, val: f32) void {
        self.data[row * self.cols + col] = val;
    }

    /// Transponer tensor: genera una nueva copia transpuesta de memoria
    pub fn transpose(self: Tensor, allocator: std.mem.Allocator) !Tensor {
        var result = try Tensor.init(allocator, self.cols, self.rows);
        var r: usize = 0;
        while (r < self.rows) : (r += 1) {
            var c: usize = 0;
            while (c < self.cols) : (c += 1) {
                result.set(c, r, self.get(r, c));
            }
        }
        return result;
    }

    /// Suma elemento a elemento in-place: self = self + other (ideal para aplicar bias)
    pub fn add(self: *Tensor, other: Tensor) !void {
        if (self.rows != other.rows or self.cols != other.cols) return error.DimensionMismatch;

        var idx: usize = 0;
        const len = self.data.len;
        const vec_size = 4;
        const steps = len / vec_size;

        // Suma acelerada por hardware (SIMD)
        while (idx < steps * vec_size) : (idx += vec_size) {
            const self_vec: @Vector(vec_size, f32) = self.data[idx..][0..vec_size].*;
            const other_vec: @Vector(vec_size, f32) = other.data[idx..][0..vec_size].*;
            const res_vec = self_vec + other_vec;
            self.data[idx..][0..vec_size].* = res_vec;
        }

        // Elementos remanentes
        while (idx < len) : (idx += 1) {
            self.data[idx] += other.data[idx];
        }
    }

    /// Escalado in-place por una constante flotante: self = self * scalar
    pub fn scale(self: *Tensor, scalar: f32) void {
        var idx: usize = 0;
        const len = self.data.len;
        const vec_size = 4;
        const steps = len / vec_size;
        const scalar_vec: @Vector(vec_size, f32) = @splat(scalar);

        // Escalado acelerado por hardware (SIMD)
        while (idx < steps * vec_size) : (idx += vec_size) {
            const self_vec: @Vector(vec_size, f32) = self.data[idx..][0..vec_size].*;
            const res_vec = self_vec * scalar_vec;
            self.data[idx..][0..vec_size].* = res_vec;
        }

        // Elementos remanentes
        while (idx < len) : (idx += 1) {
            self.data[idx] *= scalar;
        }
    }

    /// Función de activación ReLU in-place: self = max(0, self)
    pub fn relu(self: *Tensor) void {
        var idx: usize = 0;
        const len = self.data.len;
        const vec_size = 4;
        const steps = len / vec_size;
        const zero_vec: @Vector(vec_size, f32) = @splat(0.0);

        // Activación acelerada por hardware (SIMD) usando selección selectiva
        while (idx < steps * vec_size) : (idx += vec_size) {
            const self_vec: @Vector(vec_size, f32) = self.data[idx..][0..vec_size].*;
            const cond = self_vec > zero_vec;
            const res_vec = @select(f32, cond, self_vec, zero_vec);
            self.data[idx..][0..vec_size].* = res_vec;
        }

        // Elementos remanentes
        while (idx < len) : (idx += 1) {
            if (self.data[idx] < 0.0) {
                self.data[idx] = 0.0;
            }
        }
    }

    /// Función de activación Sigmoid in-place: self = 1 / (1 + exp(-self))
    pub fn sigmoid(self: *Tensor) void {
        var idx: usize = 0;
        const len = self.data.len;
        while (idx < len) : (idx += 1) {
            const x = self.data[idx];
            self.data[idx] = 1.0 / (1.0 + std.math.exp(-x));
        }
    }

    /// Función de activación SiLU (Swish) in-place: self = self * sigmoid(self)
    pub fn silu(self: *Tensor) void {
        var idx: usize = 0;
        const len = self.data.len;
        while (idx < len) : (idx += 1) {
            const x = self.data[idx];
            const sig = 1.0 / (1.0 + std.math.exp(-x));
            self.data[idx] = x * sig;
        }
    }

    /// Aplicación de SwiGLU in-place: self = silu(self) * other
    pub fn swiglu(self: *Tensor, other: Tensor) !void {
        if (self.rows != other.rows or self.cols != other.cols) return error.DimensionMismatch;
        var idx: usize = 0;
        const len = self.data.len;
        while (idx < len) : (idx += 1) {
            const x = self.data[idx];
            const sig = 1.0 / (1.0 + std.math.exp(-x));
            self.data[idx] = (x * sig) * other.data[idx];
        }
    }
};

/// Realiza el producto punto (dot product) entre dos vectores contiguos usando SIMD
fn dotProductSIMD(a: []const f32, b: []const f32) f32 {
    const len = a.len;
    const vec_size = 8;
    const steps = len / vec_size;
    var sum: f32 = 0.0;

    if (steps > 0) {
        var vec_sum: @Vector(vec_size, f32) = @splat(0.0);
        var idx: usize = 0;
        while (idx < steps * vec_size) : (idx += vec_size) {
            const va: @Vector(vec_size, f32) = a[idx..][0..vec_size].*;
            const vb: @Vector(vec_size, f32) = b[idx..][0..vec_size].*;
            vec_sum += va * vb;
        }
        sum += @reduce(.Add, vec_sum);
    }

    // Elementos remanentes
    var idx = steps * vec_size;
    while (idx < len) : (idx += 1) {
        sum += a[idx] * b[idx];
    }
    return sum;
}

/// Multiplicación de matrices de alto rendimiento con SIMD: C = A * B
/// Transpone internamente la matriz B para mantener accesos a memoria secuenciales
pub fn matmul(allocator: std.mem.Allocator, a: Tensor, b: Tensor) !Tensor {
    if (a.cols != b.rows) return error.DimensionMismatch;

    var c = try Tensor.init(allocator, a.rows, b.cols);
    errdefer c.deinit();

    // Transponer B para lograr accesos a memoria contiguos y vectorizar con SIMD
    var b_t = try b.transpose(allocator);
    defer b_t.deinit();

    var i: usize = 0;
    while (i < a.rows) : (i += 1) {
        const a_row_start = i * a.cols;
        const a_row = a.data[a_row_start .. a_row_start + a.cols];

        var j: usize = 0;
        while (j < b.cols) : (j += 1) {
            const b_t_row_start = j * b_t.cols;
            const b_t_row = b_t.data[b_t_row_start .. b_t_row_start + b_t.cols];

            const sum = dotProductSIMD(a_row, b_t_row);
            c.set(i, j, sum);
        }
    }

    return c;
}

test "Tensor MatMul Vectorizado" {
    const allocator = std.testing.allocator;

    var a = try Tensor.init(allocator, 2, 3);
    defer a.deinit();
    a.set(0, 0, 1.0); a.set(0, 1, 2.0); a.set(0, 2, 3.0);
    a.set(1, 0, 4.0); a.set(1, 1, 5.0); a.set(1, 2, 6.0);

    var b = try Tensor.init(allocator, 3, 2);
    defer b.deinit();
    b.set(0, 0, 7.0); b.set(0, 1, 8.0);
    b.set(1, 0, 9.0); b.set(1, 1, 10.0);
    b.set(2, 0, 11.0); b.set(2, 1, 12.0);

    var c = try matmul(allocator, a, b);
    defer c.deinit();

    // 1*7 + 2*9 + 3*11 = 7 + 18 + 33 = 58
    try std.testing.expectEqual(@as(f32, 58.0), c.get(0, 0));
    // 1*8 + 2*10 + 3*12 = 8 + 20 + 36 = 64
    try std.testing.expectEqual(@as(f32, 64.0), c.get(0, 1));
    // 4*7 + 5*9 + 6*11 = 28 + 45 + 66 = 139
    try std.testing.expectEqual(@as(f32, 139.0), c.get(1, 0));
    // 4*8 + 5*10 + 6*12 = 32 + 50 + 72 = 154
    try std.testing.expectEqual(@as(f32, 154.0), c.get(1, 1));
}

test "Tensor Transpose" {
    const allocator = std.testing.allocator;

    var a = try Tensor.init(allocator, 2, 3);
    defer a.deinit();
    a.set(0, 0, 1.0); a.set(0, 1, 2.0); a.set(0, 2, 3.0);
    a.set(1, 0, 4.0); a.set(1, 1, 5.0); a.set(1, 2, 6.0);

    var a_t = try a.transpose(allocator);
    defer a_t.deinit();

    try std.testing.expectEqual(@as(usize, 3), a_t.rows);
    try std.testing.expectEqual(@as(usize, 2), a_t.cols);
    try std.testing.expectEqual(@as(f32, 1.0), a_t.get(0, 0));
    try std.testing.expectEqual(@as(f32, 4.0), a_t.get(0, 1));
    try std.testing.expectEqual(@as(f32, 2.0), a_t.get(1, 0));
    try std.testing.expectEqual(@as(f32, 5.0), a_t.get(1, 1));
    try std.testing.expectEqual(@as(f32, 3.0), a_t.get(2, 0));
    try std.testing.expectEqual(@as(f32, 6.0), a_t.get(2, 1));
}

test "Tensor Element-wise Ops y Activaciones" {
    const allocator = std.testing.allocator;

    var a = try Tensor.init(allocator, 2, 2);
    defer a.deinit();
    a.set(0, 0, 1.0); a.set(0, 1, -2.0);
    a.set(1, 0, 3.0); a.set(1, 1, -4.0);

    var b = try Tensor.init(allocator, 2, 2);
    defer b.deinit();
    b.set(0, 0, 0.5); b.set(0, 1, 1.0);
    b.set(1, 0, 1.5); b.set(1, 1, 2.0);

    // Test add
    try a.add(b);
    try std.testing.expectEqual(@as(f32, 1.5), a.get(0, 0));
    try std.testing.expectEqual(@as(f32, -1.0), a.get(0, 1));
    try std.testing.expectEqual(@as(f32, 4.5), a.get(1, 0));
    try std.testing.expectEqual(@as(f32, -2.0), a.get(1, 1));

    // Test scale
    a.scale(2.0);
    try std.testing.expectEqual(@as(f32, 3.0), a.get(0, 0));
    try std.testing.expectEqual(@as(f32, -2.0), a.get(0, 1));
    try std.testing.expectEqual(@as(f32, 9.0), a.get(1, 0));
    try std.testing.expectEqual(@as(f32, -4.0), a.get(1, 1));

    // Test relu
    a.relu();
    try std.testing.expectEqual(@as(f32, 3.0), a.get(0, 0));
    try std.testing.expectEqual(@as(f32, 0.0), a.get(0, 1));
    try std.testing.expectEqual(@as(f32, 9.0), a.get(1, 0));
    try std.testing.expectEqual(@as(f32, 0.0), a.get(1, 1));

    // Test sigmoid
    var s = try Tensor.init(allocator, 1, 1);
    defer s.deinit();
    s.set(0, 0, 0.0);
    s.sigmoid();
    // sigmoid(0) = 0.5
    try std.testing.expectEqual(@as(f32, 0.5), s.get(0, 0));
}

test "Tensor SiLU y SwiGLU" {
    const allocator = std.testing.allocator;

    var a = try Tensor.init(allocator, 1, 1);
    defer a.deinit();
    a.set(0, 0, 0.0);
    
    // silu(0) = 0 * sigmoid(0) = 0
    a.silu();
    try std.testing.expectEqual(@as(f32, 0.0), a.get(0, 0));

    // silu(1.0) = 1.0 * (1.0 / (1.0 + exp(-1.0))) = 0.7310586
    var b = try Tensor.init(allocator, 1, 1);
    defer b.deinit();
    b.set(0, 0, 1.0);
    b.silu();
    try std.testing.expectApproxEqAbs(@as(f32, 0.7310586), b.get(0, 0), 0.00001);

    // swiglu(b, c): b.set(0, 0, 1.0), c.set(0, 0, 2.0)
    // b.swiglu(c) => silu(1.0) * 2.0 = 0.7310586 * 2.0 = 1.4621172
    var gate = try Tensor.init(allocator, 1, 1);
    defer gate.deinit();
    gate.set(0, 0, 1.0);

    var up = try Tensor.init(allocator, 1, 1);
    defer up.deinit();
    up.set(0, 0, 2.0);

    try gate.swiglu(up);
    try std.testing.expectApproxEqAbs(@as(f32, 1.4621172), gate.get(0, 0), 0.00001);
}

test "Cuantización Q4 y Q8 SIMD" {
    // 1. Probar BlockQ8_0
    var q8_block = BlockQ8_0{
        .d = 0.5,
        .qs = [_]i8{0} ** 32,
    };
    // Asignar algunos valores cuantizados
    q8_block.qs[0] = 10;
    q8_block.qs[16] = -20;
    
    var q8_out: [32]f32 = undefined;
    q8_block.dequantize(&q8_out);
    try std.testing.expectEqual(@as(f32, 5.0), q8_out[0]); // 10 * 0.5 = 5.0
    try std.testing.expectEqual(@as(f32, -10.0), q8_out[16]); // -20 * 0.5 = -10.0

    // Probar producto punto Q8
    var x = [_]f32{0.0} ** 32;
    x[0] = 2.0;
    x[16] = 4.0;
    // dotProductBlockQ8 = sum(x_i * qs_i) * d = (2.0 * 10 + 4.0 * -20) * 0.5 = (20 - 80) * 0.5 = -60 * 0.5 = -30.0
    const dot_q8 = dotProductBlockQ8(&x, &q8_block);
    try std.testing.expectEqual(@as(f32, -30.0), dot_q8);

    // 2. Probar BlockQ4_0
    var q4_block = BlockQ4_0{
        .d = 0.25,
        .qs = [_]u8{0} ** 16,
    };
    // qs[0] tiene 2 nibbles: inferior y superior
    // byte 0 = upper_nibble << 4 | lower_nibble
    // Asignar: lower_nibble = 10 (q = 10 - 8 = 2), upper_nibble = 4 (q = 4 - 8 = -4)
    q4_block.qs[0] = (4 << 4) | 10;
    
    var q4_out: [32]f32 = undefined;
    q4_block.dequantize(&q4_out);
    try std.testing.expectEqual(@as(f32, 0.5), q4_out[0]); // q=2 * 0.25 = 0.5
    try std.testing.expectEqual(@as(f32, -1.0), q4_out[1]); // q=-4 * 0.25 = -1.0

    // Probar producto punto Q4
    var x4 = [_]f32{0.0} ** 32;
    x4[0] = 4.0;
    x4[1] = 8.0;
    // dotProductBlockQ4 = sum(x_i * q_i) * d = (4.0 * 2 + 8.0 * -4) * 0.25 = (8 - 32) * 0.25 = -24 * 0.25 = -6.0
    const dot_q4 = dotProductBlockQ4(&x4, &q4_block);
    try std.testing.expectEqual(@as(f32, -6.0), dot_q4);
}

extern "kernel32" fn QueryPerformanceCounter(lpPerformanceCount: *i64) callconv(.winapi) i32;
extern "kernel32" fn QueryPerformanceFrequency(lpFrequency: *i64) callconv(.winapi) i32;

test "Benchmark de Rendimiento y Cuantización" {
    std.debug.print("\n\n====================================================\n", .{});
    std.debug.print("   🚀 NEXA INFERENCE ENGINE (ZIG TEST BENCHMARK)   \n", .{});
    std.debug.print("====================================================\n\n", .{});

    const allocator = std.testing.allocator;
    const size = 256;
    
    var A = try Tensor.init(allocator, size, size);
    defer A.deinit();
    var B = try Tensor.init(allocator, size, size);
    defer B.deinit();

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

    std.debug.print("   * Ejecutando matmul A * B SIMD (33.5 MILLONES FLOPs)...\n", .{});
    
    var freq: i64 = 0;
    var start: i64 = 0;
    var end: i64 = 0;
    _ = QueryPerformanceFrequency(&freq);
    
    _ = QueryPerformanceCounter(&start);
    var C = try matmul(allocator, A, B);
    defer C.deinit();
    _ = QueryPerformanceCounter(&end);
    const matmul_ms = (@as(f64, @floatFromInt(end - start)) * 1000.0) / @as(f64, @floatFromInt(freq));
    
    std.debug.print("   ✅ ¡Multiplicación completada en {d:.3} ms!\n", .{matmul_ms});

    // Probar y medir SiLU y SwiGLU
    std.debug.print("\n   * Ejecutando activaciones modernas SiLU y SwiGLU...\n", .{});
    _ = QueryPerformanceCounter(&start);
    C.silu();
    _ = QueryPerformanceCounter(&end);
    const silu_ms = (@as(f64, @floatFromInt(end - start)) * 1000.0) / @as(f64, @floatFromInt(freq));
    std.debug.print("   ✅ ¡Activación SiLU in-place completada en {d:.3} ms!\n", .{silu_ms});

    var up_tensor = try Tensor.init(allocator, size, size);
    defer up_tensor.deinit();
    @memset(up_tensor.data, 2.0);

    _ = QueryPerformanceCounter(&start);
    try C.swiglu(up_tensor);
    _ = QueryPerformanceCounter(&end);
    const swiglu_ms = (@as(f64, @floatFromInt(end - start)) * 1000.0) / @as(f64, @floatFromInt(freq));
    std.debug.print("   ✅ ¡Activación SwiGLU in-place completada en {d:.3} ms!\n", .{swiglu_ms});

    // Benchmark de producto punto Q8 SIMD Vectorizado
    std.debug.print("\n   * Ejecutando Micro-Benchmark: Producto Punto (10,000,000 iteraciones)...\n", .{});
    const vec_len = 32;
    var float_x: [vec_len]f32 = undefined;
    var float_y: [vec_len]f32 = undefined;
    var q8_block = BlockQ8_0{
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
    
    // A. f32 estándar
    _ = QueryPerformanceCounter(&start);
    var dummy_sum_f32: f32 = 0.0;
    var iter: usize = 0;
    while (iter < iterations) : (iter += 1) {
        var sum: f32 = 0.0;
        inline for (0..32) |k| {
            sum += float_x[k] * float_y[k];
        }
        dummy_sum_f32 += sum;
    }
    _ = QueryPerformanceCounter(&end);
    const float_dot_ms = (@as(f64, @floatFromInt(end - start)) * 1000.0) / @as(f64, @floatFromInt(freq));

    // B. Q8 cuantizado SIMD Vectorizado
    _ = QueryPerformanceCounter(&start);
    var dummy_sum_q8: f32 = 0.0;
    iter = 0;
    while (iter < iterations) : (iter += 1) {
        dummy_sum_q8 += dotProductBlockQ8(&float_x, &q8_block);
    }
    _ = QueryPerformanceCounter(&end);
    const q8_dot_ms = (@as(f64, @floatFromInt(end - start)) * 1000.0) / @as(f64, @floatFromInt(freq));

    std.debug.print("   📊 Producto Punto f32 Estándar   : {d:.3} ms (Resultado: {d:.2})\n", .{float_dot_ms, dummy_sum_f32 / @as(f32, @floatFromInt(iterations))});
    std.debug.print("   📊 Producto Punto Q8 SIMD Nativo : {d:.3} ms (Resultado: {d:.2})\n", .{q8_dot_ms, dummy_sum_q8 / @as(f32, @floatFromInt(iterations))});
    
    const speedup = float_dot_ms / q8_dot_ms;
    std.debug.print("   ⚡ ¡El kernel Q8 cuantizado SIMD es {d:.2}x más rápido que la aritmética flotante equivalente!\n", .{speedup});
}


