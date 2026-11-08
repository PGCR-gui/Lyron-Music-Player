# Ponte JNI DSD do FFmpeg

O app inclui `libffmpegDsdBridge.so` pré-compilada para `arm64-v8a`, `armeabi-v7a`, `x86` e `x86_64` em `app/src/main/jniLibs`. O build normal do app usa essas bibliotecas e não compila este arquivo C.

A ponte é necessária porque o FFmpeg JNI original não configura `AVCodecContext.ch_layout` para o decoder DSD. Ela inicializa o decoder com a taxa PCM e o número de canais do arquivo DSF e devolve o `AVCodecContext` ao wrapper Media3 existente. O bridge se vincula dinamicamente a `libffmpegJNI.so`, que deve exportar as funções FFmpeg usadas.

As bibliotecas fornecidas foram compiladas com NDK r27c, API 26 e headers FFmpeg n6.0, compatíveis com `Lavc60.3.100` do JNI FFmpeg incluído. Se a versão da biblioteca FFmpeg for substituída, confira a compatibilidade ABI e recompile a ponte.

Para gerar uma ponte novamente em Linux, primeiro baixe/descompacte a fonte FFmpeg n6.0 e gere `libavutil/avconfig.h` com:

```sh
./configure --disable-programs --disable-doc --disable-everything --disable-autodetect --disable-x86asm
```

Depois, a partir do diretório `app/src/main/cpp`, compile cada ABI (ajuste `NDK`, `FFMPEG_SRC` e ABI conforme necessário):

```sh
"$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android26-clang" \
  -shared -fPIC -O2 -I"$FFMPEG_SRC" dsd_ffmpeg_bridge.c \
  -L../jniLibs/arm64-v8a -Wl,--no-as-needed -lffmpegJNI -llog \
  -Wl,-soname,libffmpegDsdBridge.so -o ../jniLibs/arm64-v8a/libffmpegDsdBridge.so
```

Use `armv7a-linux-androideabi26-clang`, `i686-linux-android26-clang` e `x86_64-linux-android26-clang` para os outros três ABIs, apontando `-L` e `-o` para a pasta correspondente.
