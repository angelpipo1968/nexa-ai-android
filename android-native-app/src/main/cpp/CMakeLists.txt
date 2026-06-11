cmake_minimum_required(VERSION 3.22.1)

project("llama-android")

add_library(
    # Sets the name of the library.
    llama-android

    # Sets the library as a shared library.
    SHARED

    # Provides a relative path to your source file(s).
    llama-android.cpp
)

# Specifies libraries that CMake should link to your target library.
# You can link multiple libraries, such as libraries in the Android NDK,
# or dynamic libraries compiled separately.
find_library(
    log-lib
    log
)

target_link_libraries(
    llama-android
    ${log-lib}
)
