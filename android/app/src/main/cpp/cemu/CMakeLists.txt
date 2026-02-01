# CEmu Backend Build Configuration
# This file is included when USE_CEMU_BACKEND is ON

# Path to CEmu core source (from android/app/src/main/cpp -> project root)
set(CEMU_CORE_DIR "${CMAKE_SOURCE_DIR}/../../../../../cemu-ref/core")

# Collect all CEmu core source files
file(GLOB CEMU_CORE_SOURCES
    "${CEMU_CORE_DIR}/*.c"
    "${CEMU_CORE_DIR}/debug/*.c"
    "${CEMU_CORE_DIR}/usb/*.c"
)

# Use Linux OS implementation for Android (POSIX compatible)
list(APPEND CEMU_CORE_SOURCES "${CEMU_CORE_DIR}/os/os-linux.c")

# Remove other OS implementations and emu.c (we provide our own wrapper functions)
list(FILTER CEMU_CORE_SOURCES EXCLUDE REGEX "os-emscripten\\.c$")
list(FILTER CEMU_CORE_SOURCES EXCLUDE REGEX "os-win32\\.c$")
list(FILTER CEMU_CORE_SOURCES EXCLUDE REGEX "/emu\\.c$")

# Build CEmu core as static library
add_library(cemucore STATIC ${CEMU_CORE_SOURCES})

target_include_directories(cemucore PUBLIC
    ${CEMU_CORE_DIR}
)

target_compile_options(cemucore PRIVATE
    -Wall -Wextra -O3 -std=gnu11 -fPIC
    -Wno-unused-parameter
    -Wno-sign-compare
    -Wno-missing-field-initializers
)

# CEmu adapter that provides our API
add_library(cemu_adapter STATIC
    cemu_adapter.c
)

target_include_directories(cemu_adapter PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CEMU_CORE_DIR}
)

target_link_libraries(cemu_adapter
    cemucore
)
