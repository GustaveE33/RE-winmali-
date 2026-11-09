cmake_minimum_required(VERSION 3.22.1)

project(Winlator)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O2 -Wno-unused-function -Wimplicit-function-declaration")

add_subdirectory(patchelf)
add_subdirectory(adrenotools)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Add Winlator shared library
include_directories(winlator/include)

add_library(winlator SHARED
        winlator/drawable.c
        winlator/gpu_image.c
        winlator/gpu_helper.c
        winlator/sysvshared_memory.c
        winlator/xconnector_epoll.c
        winlator/alsa_client.c
        winlator/vulkan.c
        winlator/arrays.c
        winlator/ring_buffer.c
        winlator/xinput_stream.c
        winlator/xoutput_stream.c
        winlator/renderer/egl.cpp
        winlator/renderer/renderer_jni.cpp
        winlator/mali_counters.cpp
        winlator/renderer/shader.cpp
        winlator/renderer/window.cpp
        winlator/renderer/cursor.cpp
        winlator/renderer/xform.cpp
        winlator/renderer/view_transformation.cpp
        winlator/renderer/postfx.cpp
        winlator/renderer/readback.cpp
        winlator/renderer/frame_gen.cpp
        winlator/rambooster.cpp
)

target_compile_options(winlator PRIVATE -Wall -Wextra)

target_link_libraries(winlator
        log
        android
        jnigraphics
        aaudio
        EGL
        GLESv2
        GLESv3
        vulkan
        adrenotools
        zerofg
)

# ZeroFG (Apache-2.0): the frame generation engine, with its shaders prebuilt.
set(ZEROFG_PREBUILT_SHADERS ON CACHE STRING "" FORCE)
set(ZEROFG_VULKAN_HEADERS_DIR "${CMAKE_SYSROOT}/usr/include" CACHE PATH "" FORCE)
add_subdirectory(zerofg)

# Add Fake Input library for controller emulation (loaded via LD_PRELOAD)
add_library(fakeinput SHARED
        winlator/fakeinput.cpp
)

target_compile_options(fakeinput PRIVATE -Wall -Wextra -fvisibility=hidden)

target_link_libraries(fakeinput
        log
        dl
)

add_subdirectory(gladiorenderer)

# Bannerlator Vulkan host compositor dependencies and backend.
add_subdirectory(thirdparty/dxbc)

add_library(vulkan_renderer SHARED
        winlator/vulkan_jni.cpp
        winlator/stream_jni.cpp
        winlator/VulkanRendererContext.cpp
        winlator/VulkanRendererScanout.cpp
        winlator/lsfg/lsfg_probe.cpp
        winlator/lsfg/lsfg_dll.cpp
        winlator/lsfg/lsfg_dxbc.cpp
        winlator/lsfg/lsfg_vkd.cpp
        winlator/lsfg/lsfg_common.cpp
        winlator/lsfg/lsfg_shaders.cpp
        winlator/lsfg/lsfg_mipmaps.cpp
        winlator/lsfg/lsfg_alpha.cpp
        winlator/lsfg/lsfg_beta.cpp
        winlator/lsfg/lsfg_gamma.cpp
        winlator/lsfg/lsfg_delta.cpp
        winlator/lsfg/lsfg_generate.cpp
        winlator/lsfg/lsfg_chain.cpp
        winlator/lsfg/lsfg_pacer.cpp
        winlator/lsfg/lsfg_engine.cpp
        winlator/lsfg/lsfg_governor.cpp
        winlator/lsfg/lsfg_jni.cpp
        winlator/winfg/framegen.cpp
        winlator/winfg/winfg_vkd.cpp
        winlator/winfg/winfg_engine.cpp
        scanout/ScanoutContext.cpp
)

target_include_directories(vulkan_renderer PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}/winlator
)

target_compile_options(vulkan_renderer PRIVATE
        -Wall -Wextra -fvisibility=hidden -std=c++17
)

target_link_libraries(vulkan_renderer
        log
        android
        nativewindow
        vulkan
        adrenotools
        dxbc
        dl
        atomic
)

# SurfaceFlinger compositor (API 29+; runtime-gated in ASurfaceRenderer).
add_library(asurface_renderer SHARED
        asurfacerenderer/asurface_jni.cpp
        asurfacerenderer/ASurfaceRendererContext.cpp
        asurfacerenderer/blit_converter.cpp
)

target_compile_options(asurface_renderer PRIVATE
        -Wall -Wextra -fvisibility=hidden -std=c++17
)

target_link_libraries(asurface_renderer
        log
        android
        dl
        atomic
        EGL
        GLESv3
)

add_library(ahbimage SHARED
        asurfacerenderer/ahbimage.c
)

target_compile_definitions(ahbimage PRIVATE
        EGL_EGLEXT_PROTOTYPES
        GL_GLEXT_PROTOTYPES
)

target_compile_options(ahbimage PRIVATE -Wall -Wextra)

target_link_libraries(ahbimage
        log
        android
        EGL
        GLESv2
)

