const std = @import("std");

pub fn build(b: *std.Build) void {
    const target = b.standardTargetOptions(.{});
    const optimize = b.standardOptimizeOption(.{});

    // Create the root module that contains all our logic
    const root_module = b.createModule(.{
        .root_source_file = b.path("src/main.zig"),
        .target = target,
        .optimize = optimize,
        .link_libc = true,
    });

    // We build a dynamic/shared library (.so, .dll, .dylib) so it can be loaded by Android JNI or Node FFI
    const lib = b.addLibrary(.{
        .name = "nexa_inference",
        .root_module = root_module,
        .linkage = .dynamic,
    });
    
    b.installArtifact(lib);

    // Also build a static library for other use cases
    const static_lib = b.addLibrary(.{
        .name = "nexa_inference_static",
        .root_module = root_module,
        .linkage = .static,
    });
    b.installArtifact(static_lib);

    // Add unit tests
    const main_tests = b.addTest(.{
        .name = "nexa_inference_tests",
        .root_module = root_module,
    });
    const run_main_tests = b.addRunArtifact(main_tests);

    const test_step = b.step("test", "Run library tests");
    test_step.dependOn(&run_main_tests.step);
}
