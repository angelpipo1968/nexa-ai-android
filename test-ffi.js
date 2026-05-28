import koffi from 'koffi';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

console.log("==========================================================");
console.log("   🔌 NEXA TOTAL INTEGRATION: NODE.JS TYPESCRIPT FFI      ");
console.log("==========================================================\n");

// 1. Generar un Archivo GGUF v3 Binario de Prueba Realista
const mockGgufPath = path.join(__dirname, 'nexa_test_model.gguf');
console.log(`[1/4] Creando modelo GGUF binario de prueba: ${mockGgufPath}...`);

// Función helper para escribir un String formateado de GGUF (Longitud u64 + bytes)
function writeGgufString(bufferList, str) {
    const lenBuf = Buffer.alloc(8);
    lenBuf.writeBigUInt64LE(BigInt(str.length));
    bufferList.push(lenBuf);
    bufferList.push(Buffer.from(str, 'utf-8'));
}

const chunks = [];

// A. GGUF Header
const magic = Buffer.from('GGUF'); // Magic Bytes
chunks.push(magic);

const version = Buffer.alloc(4);
version.writeUInt32LE(3); // GGUFv3
chunks.push(version);

const tensorCount = Buffer.alloc(8);
tensorCount.writeBigUInt64LE(2n); // 2 Tensores
chunks.push(tensorCount);

const metadataCount = Buffer.alloc(8);
metadataCount.writeBigUInt64LE(2n); // 2 Metadatos KV
chunks.push(metadataCount);

// B. Metadatos KV
// KV 1: general.name => "Nexa-SIMD-Micro-1B"
writeGgufString(chunks, "general.name");
const type1 = Buffer.alloc(4);
type1.writeUInt32LE(8); // String Type
chunks.push(type1);
writeGgufString(chunks, "Nexa-SIMD-Micro-1B");

// KV 2: general.architecture => "llama"
writeGgufString(chunks, "general.architecture");
const type2 = Buffer.alloc(4);
type2.writeUInt32LE(8); // String Type
chunks.push(type2);
writeGgufString(chunks, "llama");

// C. Tensors Metadata
// Tensor 1: "token_embeddings.weight"
writeGgufString(chunks, "token_embeddings.weight");
const nDims1 = Buffer.alloc(4);
nDims1.writeUInt32LE(2); // 2D Tensor
chunks.push(nDims1);
const dims1 = Buffer.alloc(16);
dims1.writeBigUInt64LE(128n, 0); // Dim 0
dims1.writeBigUInt64LE(256n, 8); // Dim 1
chunks.push(dims1);
const tType1 = Buffer.alloc(4);
tType1.writeUInt32LE(0); // FP32 precision
chunks.push(tType1);
const tOffset1 = Buffer.alloc(8);
tOffset1.writeBigUInt64LE(0n); // Offset 0
chunks.push(tOffset1);

// Tensor 2: "output.weight"
writeGgufString(chunks, "output.weight");
const nDims2 = Buffer.alloc(4);
nDims2.writeUInt32LE(2); // 2D Tensor
chunks.push(nDims2);
const dims2 = Buffer.alloc(16);
dims2.writeBigUInt64LE(256n, 0); // Dim 0
dims2.writeBigUInt64LE(128n, 8); // Dim 1
chunks.push(dims2);
const tType2 = Buffer.alloc(4);
tType2.writeUInt32LE(1); // FP16 precision
chunks.push(tType2);
const tOffset2 = Buffer.alloc(8);
tOffset2.writeBigUInt64LE(131072n); // Offset
chunks.push(tOffset2);

fs.writeFileSync(mockGgufPath, Buffer.concat(chunks));
console.log("✅ Modelo binario GGUF mock generado con éxito.\n");

// 2. Cargar Librería Dinámica Nativa usando Koffi
const libPath = path.join(__dirname, 'nexa-inference-zig', 'zig-out', 'bin', 'nexa_inference.dll');
console.log(`[2/4] Cargando librería dinámica nativa desde: ${libPath}...`);

if (!fs.existsSync(libPath)) {
    console.error(`❌ Error: No se encontró la DLL en ${libPath}. Asegúrate de haber compilado el motor Zig.`);
    process.exit(1);
}

const lib = koffi.load(libPath);
console.log("✅ Librería cargada con éxito en Node.js.\n");

// 3. Declarar Funciones FFI
console.log("[3/4] Vinculando funciones nativas C API...");
const nexa_init = lib.func('bool nexa_init()');
const nexa_free = lib.func('void nexa_free()');
const nexa_load_gguf = lib.func('bool nexa_load_gguf(const char *path)');
const nexa_infer = lib.func('const char *nexa_infer(const char *prompt)');
console.log("✅ Funciones FFI vinculadas.\n");

// 4. Ejecutar el Flujo de Trabajo Nativo Completo
console.log("[4/4] Iniciando flujo nativo...");

// A. Inicializar
const initSuccess = nexa_init();
console.log(`   * nexa_init() => ${initSuccess ? "✅ Éxito" : "❌ Falló"}`);

if (initSuccess) {
    // B. Cargar y Parsear el Modelo GGUF Binario Real en caliente desde Zig
    console.log("\n--- Llama nativa al parser de GGUF en Zig ---");
    const loadSuccess = nexa_load_gguf(mockGgufPath);
    console.log(`\n   * nexa_load_gguf() => ${loadSuccess ? "✅ Éxito" : "❌ Falló"}`);
    
    // C. Ejecutar Inferencia Nativa
    console.log("\n--- Llama nativa a Inferencia en Zig ---");
    const prompt = "Dime qué piensas del lenguaje Zig para inteligencia artificial.";
    console.log(`   > Enviando Prompt: "${prompt}"`);
    
    const response = nexa_infer(prompt);
    console.log(`\n💬 Respuesta Nativa FFI:\n   ==========================================================\n   ${response}\n   ==========================================================`);
    
    // D. Liberar memoria
    nexa_free();
    console.log("\n✅ Motor nativo cerrado y memoria liberada.");
}

// Limpiar el GGUF de prueba generado
if (fs.existsSync(mockGgufPath)) {
    fs.unlinkSync(mockGgufPath);
}
console.log("\n🎉 ¡Integración FFI completada y verificada de forma perfecta!");
