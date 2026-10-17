cmake_minimum_required(VERSION 3.22.1)
project(lyron_projectm LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# ---- opções do projectM (v4.x) ----
set(ENABLE_GLES      ON  CACHE BOOL "" FORCE)  # OpenGL ES (Android)
set(ENABLE_PLAYLIST  OFF CACHE BOOL "" FORCE)  # não usamos a playlist deles
set(ENABLE_SDL_UI    OFF CACHE BOOL "" FORCE)
set(BUILD_TESTING    OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE) # projectM entra estático dentro da nossa .so

# O código-fonte do projectM fica em  app/src/main/cpp/projectm
add_subdirectory(projectm EXCLUDE_FROM_ALL)

add_library(lyron_projectm SHARED projectm_jni.cpp)

target_link_libraries(lyron_projectm PRIVATE
    libprojectM::projectM
    GLESv3
    EGL
    android
    log)

# Android 15+ exige libs nativas alinhadas em 16 KB (targetSdk 35+ / Google Play)
target_link_options(lyron_projectm PRIVATE -Wl,-z,max-page-size=16384)
