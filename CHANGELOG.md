# Changelog — Lyron Music Player

## 0.41 (191) — 2026-10-21

### Novidades
- **Equalizador** com visual *liquid glass* (inspirado no EQ do vídeo): 10 bandas (31 Hz–16 kHz), presets embutidos, controles de Grave / Agudo e botão de acesso ao lado da fila de reprodução no Now Playing.
- **Timer de sono** configurável (5 / 10 / 15 / 30 / 45 / 60 min + personalizado + desligar), acessível pelo menu de 3 pontos do Now Playing.
- **Compartilhar trecho**: compartilha título, artista, posição atual e linha de letra ativa (quando houver). Acessível pelo menu de 3 pontos do Now Playing.
- **Modo Carro / Bluetooth**: interface simplificada com controles maiores, tela permanece ligada e ativação automática ao conectar fone Bluetooth A2DP (também toggle manual em Configurações).
- Mais fontes de pesquisa de letras (além do LRCLIB).
- Pesquisa de tags online aprimorada + **trocar / adicionar foto da capa** no editor de tags.

### Técnico
- Equalizer nativo (`android.media.audiofx.Equalizer`) ligado à sessão de áudio do ExoPlayer.
- Persistência de bandas do EQ e do timer de sono em SharedPreferences.
