# Lyron-Music-Player
A evolução do Tolete Music Mobile

aviso: isso aqui é puro AI Slop então se tu é contra vibe coding só passa longe blz

## Áudio DSF

O player reconhece arquivos `.dsf`, lê suas tags e decodifica o fluxo DSD para PCM usando o decoder FFmpeg já fornecido no projeto. Uma ponte JNI incluída no app configura a taxa e o layout de canais necessários para iniciar o decoder DSD. Para o DSF, o renderer seleciona PCM16 porque o Media3 não executa processadores personalizados no caminho float; essa etapa permite que o downmix e o filtro FIR ocorram antes da saída Android. DSD64 (2,8224 MHz) é convertido internamente para PCM a 352,8 kHz e reduzido antes da reprodução. Em dispositivos que aceitam a combinação, uma faixa estéreo DSD64 pode sair a 176,4 kHz; se o Android não aceitar a taxa, o app tenta taxas menores. Conteúdo acima de dois canais que exceda 192 kHz é downmixado para estéreo, pois muitos dispositivos não aceitam saída PCM multicanal nessa taxa. PCM convencional a 192 kHz permanece a 192 kHz. Isso não é reprodução DSD nativa/DoP.

No modo de acesso a todos os arquivos, o scanner também procura `.dsf` diretamente nos volumes compartilhados, pois o Android pode omiti-los do índice `MediaStore.Audio`.

O wrapper Java FFmpeg acompanha o Media3 1.11.1 em `app/src/main/java/androidx/media3/decoder/ffmpeg`, e as bibliotecas JNI do AAR local estão em `app/src/main/jniLibs`.
