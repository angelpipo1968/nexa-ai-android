const std = @import("std");

// Declaraciones de funciones estándar de la librería C (LibC) para I/O
// Esto garantiza compatibilidad del 100% en Windows, Linux y Android sin depender de std.Io de Zig
const FILE = anyopaque;
extern "c" fn fopen(filename: [*:0]const u8, mode: [*:0]const u8) ?*FILE;
extern "c" fn fclose(stream: *FILE) c_int;
extern "c" fn fread(ptr: *anyopaque, size: usize, nmemb: usize, stream: *FILE) usize;
extern "c" fn fseek(stream: *FILE, offset: c_long, whence: c_int) c_int;

/// Tipos de datos soportados en metadatos de GGUF
pub const GgufType = enum(u32) {
    uint8 = 0,
    int8 = 1,
    uint16 = 2,
    int16 = 3,
    uint32 = 4,
    int32 = 5,
    float32 = 6,
    boolean = 7,
    string = 8,
    array = 9,
    uint64 = 10,
    int64 = 11,
    float64 = 12,
};

/// Tipos de precisión para tensores
pub const TensorType = enum(u32) {
    f32 = 0,
    f16 = 1,
    q4_0 = 2,
    q4_1 = 3,
    q5_0 = 6,
    q5_1 = 7,
    q8_0 = 8,
    q8_1 = 9,
    q2_k = 10,
    q3_k = 11,
    q4_k = 12,
    q5_k = 13,
    q6_k = 14,
    q8_k = 15,
};

fn readIntU32(file: *FILE) !u32 {
    var val: u32 = 0;
    const read_bytes = fread(&val, 1, 4, file);
    if (read_bytes < 4) return error.UnexpectedEOF;
    return val;
}

fn readIntU64(file: *FILE) !u64 {
    var val: u64 = 0;
    const read_bytes = fread(&val, 1, 8, file);
    if (read_bytes < 8) return error.UnexpectedEOF;
    return val;
}

/// Lee una cadena GGUF (u64 longitud + bytes raw)
fn readGgufString(allocator: std.mem.Allocator, file: *FILE) ![]const u8 {
    const len = try readIntU64(file);
    const buf = try allocator.alloc(u8, len);
    errdefer allocator.free(buf);
    const bytes_read = fread(buf.ptr, 1, len, file);
    if (bytes_read < len) return error.UnexpectedEOF;
    return buf;
}

/// Lee y descarta el valor de metadatos del reader según su tipo,
/// devolviendo una representación formateada del valor en string si es legible
fn readGgufValue(allocator: std.mem.Allocator, val_type_val: u32, file: *FILE) ![]const u8 {
    if (val_type_val > 12) return error.UnknownGgufType;
    const val_type: GgufType = @enumFromInt(val_type_val);

    switch (val_type) {
        .uint8 => {
            var val: u8 = 0;
            const read = fread(&val, 1, 1, file);
            if (read < 1) return error.UnexpectedEOF;
            return std.fmt.allocPrint(allocator, "{d} (u8)", .{val});
        },
        .int8 => {
            var val: i8 = 0;
            const read = fread(&val, 1, 1, file);
            if (read < 1) return error.UnexpectedEOF;
            return std.fmt.allocPrint(allocator, "{d} (i8)", .{val});
        },
        .uint16 => {
            var val: u16 = 0;
            const read = fread(&val, 2, 1, file);
            if (read < 1) return error.UnexpectedEOF;
            return std.fmt.allocPrint(allocator, "{d} (u16)", .{val});
        },
        .int16 => {
            var val: i16 = 0;
            const read = fread(&val, 2, 1, file);
            if (read < 1) return error.UnexpectedEOF;
            return std.fmt.allocPrint(allocator, "{d} (i16)", .{val});
        },
        .uint32 => {
            const val = try readIntU32(file);
            return std.fmt.allocPrint(allocator, "{d} (u32)", .{val});
        },
        .int32 => {
            const val = try readIntU32(file);
            const s_val = @as(i32, @bitCast(val));
            return std.fmt.allocPrint(allocator, "{d} (i32)", .{s_val});
        },
        .float32 => {
            const bits = try readIntU32(file);
            const val = @as(f32, @bitCast(bits));
            return std.fmt.allocPrint(allocator, "{d:.6} (f32)", .{val});
        },
        .boolean => {
            var val: u8 = 0;
            const read = fread(&val, 1, 1, file);
            if (read < 1) return error.UnexpectedEOF;
            return std.fmt.allocPrint(allocator, "{}", .{val != 0});
        },
        .string => {
            const str = try readGgufString(allocator, file);
            return str;
        },
        .array => {
            const array_type_val = try readIntU32(file);
            const array_len = try readIntU64(file);
            
            var list = std.ArrayList([]const u8).empty;
            defer {
                for (list.items) |item| allocator.free(item);
                list.deinit(allocator);
            }

            var i: usize = 0;
            while (i < array_len) : (i += 1) {
                const item = try readGgufValue(allocator, array_type_val, file);
                if (i < 5) {
                    try list.append(allocator, item);
                } else {
                    allocator.free(item);
                }
            }

            if (array_len > 5) {
                return std.fmt.allocPrint(allocator, "[ {s}, ... +{d} más ]", .{
                    try std.mem.join(allocator, ", ", list.items),
                    array_len - 5,
                });
            } else {
                return std.fmt.allocPrint(allocator, "[ {s} ]", .{
                    try std.mem.join(allocator, ", ", list.items),
                });
            }
        },
        .uint64 => {
            const val = try readIntU64(file);
            return std.fmt.allocPrint(allocator, "{d} (u64)", .{val});
        },
        .int64 => {
            const val = try readIntU64(file);
            const s_val = @as(i64, @bitCast(val));
            return std.fmt.allocPrint(allocator, "{d} (i64)", .{s_val});
        },
        .float64 => {
            const bits = try readIntU64(file);
            const val = @as(f64, @bitCast(bits));
            return std.fmt.allocPrint(allocator, "{d:.6} (f64)", .{val});
        },
    }
}

/// Parsea un archivo GGUF e imprime todo su contenido estructural y metadatos
pub fn parseGguf(allocator: std.mem.Allocator, file_path: []const u8) !void {
    std.debug.print("\n📂 [GGUF Parser] Abriendo: {s}...\n", .{file_path});
    
    // Convertir a string con sentinel u8 para fopen
    const file_path_z = try allocator.dupeZ(u8, file_path);
    defer allocator.free(file_path_z);

    const file = fopen(file_path_z, "rb") orelse {
        std.debug.print("❌ [GGUF Parser] Error al abrir el archivo con LibC fopen\n", .{});
        return error.FileNotFound;
    };
    defer _ = fclose(file);

    // 1. Validar Magic Bytes ("GGUF")
    var magic_buf: [4]u8 = undefined;
    const bytes_read = fread(&magic_buf, 1, 4, file);
    if (bytes_read < 4 or !std.mem.eql(u8, &magic_buf, "GGUF")) {
        std.debug.print("❌ [GGUF Parser] Archivo no válido. Magic bytes incorrectos.\n", .{});
        return error.InvalidMagicBytes;
    }
    
    // 2. Leer versión
    const version = try readIntU32(file);
    std.debug.print("✅ [GGUF Parser] ¡Magic Bytes correcto! GGUF Versión: {d}\n", .{version});
    if (version != 3) {
        std.debug.print("⚠️ [GGUF Parser] Advertencia: Versión del archivo es {d}, optimizado para versión 3.\n", .{version});
    }

    // 3. Leer recuentos de tensores y metadatos
    const tensor_count = try readIntU64(file);
    const metadata_kv_count = try readIntU64(file);
    std.debug.print("📊 [GGUF Parser] Tensores: {d} | Metadatos KV: {d}\n\n", .{ tensor_count, metadata_kv_count });

    // 4. Parsear Metadatos KV
    std.debug.print("--- METADATOS CLAVE-VALOR DEL ARCHIVO ---\n", .{});
    var kv_i: usize = 0;
    while (kv_i < metadata_kv_count) : (kv_i += 1) {
        const key = try readGgufString(allocator, file);
        defer allocator.free(key);

        const val_type_val = try readIntU32(file);
        const val_str = try readGgufValue(allocator, val_type_val, file);
        defer allocator.free(val_str);

        std.debug.print("  🔑 {s} => {s}\n", .{ key, val_str });
    }
    std.debug.print("----------------------------------------\n\n", .{});

    // 5. Parsear Información de Tensores
    std.debug.print("--- REGISTRO DE TENSORES DEL MODELO ---\n", .{});
    var t_i: usize = 0;
    while (t_i < tensor_count) : (t_i += 1) {
        const t_name = try readGgufString(allocator, file);
        defer allocator.free(t_name);

        const n_dims = try readIntU32(file);
        
        // Leer las dimensiones (shapes)
        var dims_list = std.ArrayList(u64).empty;
        defer dims_list.deinit(allocator);
        
        var d: u32 = 0;
        while (d < n_dims) : (d += 1) {
            const dim_val = try readIntU64(file);
            try dims_list.append(allocator, dim_val);
        }

        const t_type_val = try readIntU32(file);
        const t_offset = try readIntU64(file);

        // Formatear shape descriptiva: [128, 128] etc
        var shape_list = std.ArrayList([]const u8).empty;
        defer {
            for (shape_list.items) |item| allocator.free(item);
            shape_list.deinit(allocator);
        }
        for (dims_list.items) |dim| {
            const dim_str = try std.fmt.allocPrint(allocator, "{d}", .{dim});
            try shape_list.append(allocator, dim_str);
        }
        const shape_str = try std.mem.join(allocator, "x", shape_list.items);
        defer allocator.free(shape_str);

        var t_type = TensorType.f32;
        if (t_type_val <= 15) {
            t_type = @enumFromInt(t_type_val);
        }

        std.debug.print("  🧠 Tensor [{d}]: \"{s}\" | Shape: [{s}] | Formato: {s} | Offset: {d} bytes\n", .{
            t_i,
            t_name,
            shape_str,
            @tagName(t_type),
            t_offset,
        });
    }
    std.debug.print("---------------------------------------\n", .{});
    std.debug.print("✅ [GGUF Parser] Estructura binaria completada con éxito.\n", .{});
}
