const std = @import("std");

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

