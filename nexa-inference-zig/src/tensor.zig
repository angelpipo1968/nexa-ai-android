const std = @import("std");

/// Un tensor básico para la inferencia
pub const Tensor = struct {
    data: []f32,
    rows: usize,
    cols: usize,
    allocator: std.mem.Allocator,

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
};

/// Multiplicación de matrices básica: C = A * B
/// Esto se puede optimizar brutalmente en el futuro usando SIMD (Single Instruction Multiple Data)
pub fn matmul(allocator: std.mem.Allocator, a: Tensor, b: Tensor) !Tensor {
    if (a.cols != b.rows) return error.DimensionMismatch;

    var c = try Tensor.init(allocator, a.rows, b.cols);

    var i: usize = 0;
    while (i < a.rows) : (i += 1) {
        var j: usize = 0;
        while (j < b.cols) : (j += 1) {
            var sum: f32 = 0.0;
            var k: usize = 0;
            while (k < a.cols) : (k += 1) {
                sum += a.get(i, k) * b.get(k, j);
            }
            c.set(i, j, sum);
        }
    }

    return c;
}

test "Tensor MatMul" {
    const allocator = std.testing.allocator;

    var a = try Tensor.init(allocator, 2, 2);
    defer a.deinit();
    a.set(0, 0, 1.0); a.set(0, 1, 2.0);
    a.set(1, 0, 3.0); a.set(1, 1, 4.0);

    var b = try Tensor.init(allocator, 2, 2);
    defer b.deinit();
    b.set(0, 0, 2.0); b.set(0, 1, 0.0);
    b.set(1, 0, 1.0); b.set(1, 1, 2.0);

    var c = try matmul(allocator, a, b);
    defer c.deinit();

    try std.testing.expectEqual(@as(f32, 4.0), c.get(0, 0)); // 1*2 + 2*1 = 4
    try std.testing.expectEqual(@as(f32, 4.0), c.get(0, 1)); // 1*0 + 2*2 = 4
    try std.testing.expectEqual(@as(f32, 10.0), c.get(1, 0)); // 3*2 + 4*1 = 10
    try std.testing.expectEqual(@as(f32, 8.0), c.get(1, 1)); // 3*0 + 4*2 = 8
}
