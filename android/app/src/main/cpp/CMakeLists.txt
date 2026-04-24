# CMakeLists.txt for Pure BASS Audio Engine (No Oboe)
cmake_minimum_required(VERSION 3.22.1)
project(native-lib)

# Set C++ standard and optimization flags
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Optimization flags for release builds
set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -O3 -DNDEBUG -flto")
set(CMAKE_CXX_FLAGS_DEBUG "${CMAKE_CXX_FLAGS_DEBUG} -DDEBUG -g")

# Project paths
set(PROJECT_ROOT ${CMAKE_SOURCE_DIR}/../../../../..)
set(BASS_INCLUDE_DIR ${PROJECT_ROOT}/bass24-android/c)
set(BASSFLAC_INCLUDE_DIR ${PROJECT_ROOT}/bassflac24-android/c)
set(BASS_SSL_INCLUDE_DIR ${PROJECT_ROOT}/bass_ssl-android/c)
set(BASS_FX_INCLUDE_DIR ${PROJECT_ROOT}/bass_fx24-android/C)

# BASS library paths for current ABI
set(JNILIBS_DIR ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})

# Verify libraries exist
if(NOT EXISTS ${JNILIBS_DIR}/libbass.so)
    message(FATAL_ERROR "BASS library not found for ABI ${ANDROID_ABI}: ${JNILIBS_DIR}/libbass.so")
endif()

if(NOT EXISTS ${JNILIBS_DIR}/libbassflac.so)
    message(FATAL_ERROR "BASSFLAC library not found for ABI ${ANDROID_ABI}: ${JNILIBS_DIR}/libbassflac.so")
endif()

if(NOT EXISTS ${JNILIBS_DIR}/libbass_ssl.so)
    message(FATAL_ERROR "BASS_SSL library not found for ABI ${ANDROID_ABI}: ${JNILIBS_DIR}/libbass_ssl.so")
endif()

if(NOT EXISTS ${JNILIBS_DIR}/libbass_fx.so)
    message(FATAL_ERROR "BASS_FX library not found for ABI ${ANDROID_ABI}: ${JNILIBS_DIR}/libbass_fx.so")
endif()

message(STATUS "Building BASS-only audio engine for ABI: ${ANDROID_ABI}")
message(STATUS "BASS libraries found in: ${JNILIBS_DIR}")

# Pure BASS audio engine sources (no Oboe)
set(BASS_AUDIO_SOURCES
    BassAudioEngine.cpp
    bass_audio_bridge.cpp
    native_audio_bridge.cpp
    bass_audio_engine_test.cpp
)

# Create main shared library
add_library(native-lib SHARED ${BASS_AUDIO_SOURCES})

# Include directories for BASS components only
target_include_directories(native-lib PRIVATE
    ${BASS_INCLUDE_DIR}
    ${BASSFLAC_INCLUDE_DIR}
    ${BASS_SSL_INCLUDE_DIR}
    ${BASS_FX_INCLUDE_DIR}
)

# Link BASS libraries
target_link_libraries(native-lib
    ${JNILIBS_DIR}/libbass.so
    ${JNILIBS_DIR}/libbassflac.so
    ${JNILIBS_DIR}/libbass_ssl.so
    ${JNILIBS_DIR}/libbass_fx.so
    android
    log
)

# Compiler flags for BASS-only build
target_compile_definitions(native-lib PRIVATE
    -DBASS_ONLY_ENGINE=1
    -DANDROID_PLATFORM=${ANDROID_PLATFORM}
    -DANDROID_ABI=${ANDROID_ABI}
)

# Optimization flags for audio performance (Android-compatible)
target_compile_options(native-lib PRIVATE
    -ffast-math
    -funroll-loops
    -fomit-frame-pointer
)

# Link time optimization for release builds
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    target_link_options(native-lib PRIVATE
        -flto
        -fuse-linker-plugin
        -fwhole-program
    )
endif()

# Strip debug symbols in release
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    add_custom_command(TARGET native-lib POST_BUILD
        COMMAND ${CMAKE_STRIP} "$<TARGET_FILE:native-lib>"
        COMMENT "Stripping debug symbols from native-lib"
    )
endif()

# Copy BASS libraries to output directory
add_custom_command(TARGET native-lib POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy_if_different
        ${JNILIBS_DIR}/libbass.so
        ${JNILIBS_DIR}/libbassflac.so
        ${JNILIBS_DIR}/libbass_ssl.so
        $<TARGET_FILE_DIR:native-lib>
    COMMENT "Copying BASS libraries to output directory"
)

# Print build configuration
message(STATUS "========================================")
message(STATUS "BASS-Only Audio Engine Build Configuration")
message(STATUS "========================================")
message(STATUS "ABI: ${ANDROID_ABI}")
message(STATUS "Platform: ${ANDROID_PLATFORM}")
message(STATUS "Build Type: ${CMAKE_BUILD_TYPE}")
message(STATUS "C++ Standard: ${CMAKE_CXX_STANDARD}")
message(STATUS "Bass Include: ${BASS_INCLUDE_DIR}")
message(STATUS "Bass Libraries: ${JNILIBS_DIR}")
message(STATUS "========================================")