const std = @import("std");

pub fn build(b: *std.Build) void {
    const target = b.standardTargetOptions(.{});
    const optimize = b.standardOptimizeOption(.{});

    // We build a shared library (.so, .dll, .dylib) so it can be loaded by Android JNI or Node FFI
    const lib = b.addSharedLibrary(.{
        .name = "nexa_inference",
        .root_source_file = b.path("src/main.zig"),
        .target = target,
        .optimize = optimize,
    });
    
    // Link libc if we plan to be used from C/C++ or standard environments
    lib.linkLibC();
    
    b.installArtifact(lib);

    // Also build a static library for other use cases
    const static_lib = b.addStaticLibrary(.{
        .name = "nexa_inference_static",
        .root_source_file = b.path("src/main.zig"),
        .target = target,
        .optimize = optimize,
    });
    b.installArtifact(static_lib);

    // Add unit tests
    const main_tests = b.addTest(.{
        .root_source_file = b.path("src/main.zig"),
        .target = target,
        .optimize = optimize,
    });
    const run_main_tests = b.addRunArtifact(main_tests);

    const test_step = b.step("test", "Run library tests");
    test_step.dependOn(&run_main_tests.step);
}
