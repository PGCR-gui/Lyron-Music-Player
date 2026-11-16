package com.tolete.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tolete.player.R
import kotlinx.coroutines.delay

private val HelpBg = Color(0xFF0E1014)


private val helpPageTitles = listOf(
    "Bem-vindo",
    "Agradecimentos",
    "Aviso importante",
    "Guia rápido",
    "Tela tocando agora",
    "Menu de três pontos",
    "O Visualizador",
    "Letras em tela cheia",
    "Fila de Reprodução",
    "Editor de Tags e Letras",
    "Configurações de biblioteca",
    "Organizar biblioteca",
    "Scrobbling (Last.fm)",
    "Vídeo de letras",
    "Funções avançadas",
    "Nota final",
    "Conclusão"
)

@Composable
fun HelpScreen(
    onClose: () -> Unit,
    onFinishedFirstRun: (() -> Unit)? = null
) {
    var page by remember { mutableIntStateOf(0) }
    val totalPages = 17
    var showIndex by remember { mutableStateOf(false) }

    fun skipTutorial() {
        onFinishedFirstRun?.invoke()
        onClose()
    }

    fun goNext() {
        if (page < totalPages - 1) page++
        else skipTutorial()
    }

    fun goBack() {
        if (page > 0) page--
        else skipTutorial()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(HelpBg)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { /* consome cliques */ }
    ) {
        FloatingIconsBackground(kind = "help")
        Box(Modifier.fillMaxSize().background(Color(0xFF0E1014).copy(alpha = 0.55f)))

        // Conteúdo da página (sem os botões Voltar/Próximo internos nas páginas que usam HelpNavScaffold)
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = 100.dp) // espaço para a barra de vidro inferior
        ) {
            when (page) {
                0 -> HelpPageWelcome(
                    onSkip = { skipTutorial() },
                    onNext = { page = 1 },
                    hideBottomButtons = true
                )
                1 -> HelpPageThanks(onNext = { page = 2 }, onBack = { page = 0 }, hideBottomButtons = true)
                2 -> HelpPageWarning(onNext = { page = 3 }, onBack = { page = 1 }, hideBottomButtons = true)
                3 -> HelpPageGuide(
                    onDone = { page = 4 },
                    onBack = { page = 2 },
                    doneLabel = "Próximo",
                    hideBottomButtons = true
                )
                4 -> HelpPageNowPlaying(onNext = { page = 5 }, onBack = { page = 3 }, hideBottomButtons = true)
                5 -> HelpPageThreeDots(onNext = { page = 6 }, onBack = { page = 4 }, hideBottomButtons = true)
                6 -> HelpPageVisualizer(onNext = { page = 7 }, onBack = { page = 5 }, hideBottomButtons = true)
                7 -> HelpPageFullLyrics(onNext = { page = 8 }, onBack = { page = 6 }, hideBottomButtons = true)
                8 -> HelpPageQueue(onNext = { page = 9 }, onBack = { page = 7 }, hideBottomButtons = true)
                9 -> HelpPageTagEditor(onNext = { page = 10 }, onBack = { page = 8 }, hideBottomButtons = true)
                10 -> HelpPageLibrarySettings(onNext = { page = 11 }, onBack = { page = 9 }, hideBottomButtons = true)
                11 -> HelpPageOrganize(onNext = { page = 12 }, onBack = { page = 10 }, hideBottomButtons = true)
                12 -> HelpPageScrobbler(onNext = { page = 13 }, onBack = { page = 11 }, hideBottomButtons = true)
                13 -> HelpPageLyricsVideo(onNext = { page = 14 }, onBack = { page = 12 }, hideBottomButtons = true)
                14 -> HelpPageAdvanced(onNext = { page = 15 }, onBack = { page = 13 }, hideBottomButtons = true)
                15 -> HelpPageClosingNote(onNext = { page = 16 }, onBack = { page = 14 }, hideBottomButtons = true)
                16 -> HelpPageFinal(
                    onDone = { skipTutorial() },
                    onBack = { page = 15 },
                    hideBottomButtons = true
                )
            }
        }

        // Barra inferior de vidro: Pular | Índice | Voltar | Próximo
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // Indicador de página
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(totalPages) { i ->
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (i == page) 9.dp else 6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (i == page) Color.White else Color.White.copy(0.35f)
                            )
                    )
                }
            }

            // Glass bar
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(28.dp))
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pular
                    TextButton(
                        onClick = { skipTutorial() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Pular", color = Color.White.copy(0.9f), fontSize = 14.sp)
                    }
                    // Índice
                    IconButton(
                        onClick = { showIndex = true },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(0.10f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.List,
                            contentDescription = "Índice",
                            tint = Color.White
                        )
                    }
                    // Voltar
                    TextButton(
                        onClick = { goBack() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            if (page == 0) "Fechar" else "Voltar",
                            color = Color.White.copy(0.9f),
                            fontSize = 14.sp
                        )
                    }
                    // Próximo / Concluir
                    Button(
                        onClick = { goNext() },
                        modifier = Modifier.weight(1.2f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        Text(
                            if (page == totalPages - 1) "Concluir" else "Próximo",
                            color = Color.Black,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        if (showIndex) {
            AppAlertDialog(
                onDismissRequest = { showIndex = false },
                title = { Text("Índice do tutorial") },
                text = {
                    LazyColumn {
                        itemsIndexed(helpPageTitles) { index, title ->
                            Text(
                                text = "${index + 1}. $title",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        page = index
                                        showIndex = false
                                    }
                                    .padding(vertical = 10.dp),
                                color = if (index == page) MaterialTheme.colorScheme.primary else Color.Unspecified,
                                fontWeight = if (index == page) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showIndex = false }) {
                        Text("Fechar")
                    }
                }
            )
        }
    }
}


private fun helpFont(resId: Int): FontFamily =
    FontFamily(Font(resId))

@Composable
private fun HelpPageWelcome(onSkip: () -> Unit, onNext: () -> Unit, hideBottomButtons: Boolean = false) {
    var showWelcome by remember { mutableStateOf(false) }
    var showLogo by remember { mutableStateOf(false) }
    var showTitle by remember { mutableStateOf(false) }
    var showButtons by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(200)
        showWelcome = true
        delay(450)
        showLogo = true
        delay(450)
        showTitle = true
        delay(2100)
        showButtons = true
    }

    val shareit = helpFont(R.font.shareit)
    val julia = helpFont(R.font.juliametal_regular)

    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedVisibility(
                visible = showWelcome,
                enter = fadeIn(tween(600)) + slideInVertically(tween(600, easing = FastOutSlowInEasing)) { -60 }
            ) {
                Text(
                    "bem vindo ao",
                    fontFamily = shareit,
                    fontSize = 28.sp,
                    color = Color.White.copy(0.92f),
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(28.dp))
            AnimatedVisibility(
                visible = showLogo,
                enter = fadeIn(tween(700)) + scaleIn(tween(700, easing = FastOutSlowInEasing), initialScale = 0.6f)
            ) {
                Image(
                    painter = painterResource(R.drawable.lyron_welcome),
                    contentDescription = null,
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(24.dp)),
                    contentScale = ContentScale.Fit
                )
            }
            Spacer(Modifier.height(28.dp))
            AnimatedVisibility(
                visible = showTitle,
                enter = fadeIn(tween(650)) + slideInVertically(tween(650, easing = FastOutSlowInEasing)) { 50 }
            ) {
                Text(
                    "Lyron Music Player",
                    fontFamily = julia,
                    fontSize = 30.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (!hideBottomButtons) {
            AnimatedVisibility(
                visible = showButtons,
                enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { 40 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = onSkip,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Pular", color = Color.White.copy(0.85f), fontSize = 16.sp)
                    }
                    Button(
                        onClick = onNext,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Text("Próximo", color = Color.Black, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpPageThanks(onNext: () -> Unit, onBack: () -> Unit, hideBottomButtons: Boolean = false) {
    var showTitle by remember { mutableStateOf(false) }
    var showBody by remember { mutableStateOf(false) }
    var showNav by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(150); showTitle = true
        delay(400); showBody = true
        delay(500); showNav = true
    }
    val buba = helpFont(R.font.buba_outline)
    HelpScrollPage(
        showNav = showNav,
        onBack = onBack,
        onNext = onNext,
        nextLabel = "Próximo"
    ,
        hideBottomButtons = hideBottomButtons) {
        AnimatedVisibility(
            visible = showTitle,
            enter = fadeIn(tween(500)) + slideInHorizontally(tween(550)) { -80 }
        ) {
            Text(
                "Obrigado por baixar o app",
                fontFamily = buba,
                fontSize = 26.sp,
                color = Color.White,
                modifier = Modifier.padding(bottom = 18.dp)
            )
        }
        AnimatedVisibility(
            visible = showBody,
            enter = fadeIn(tween(700, delayMillis = 80)) + slideInVertically(tween(700)) { 40 }
        ) {
            Text(
                "Eu não estou ganhando nada com isso mas fico feliz que alguém esteja de fato usando isso por conta própria.\n\n" +
                    "Eu fiz esse app pra uso pessoal e inicialmente não tava levando a sério, mas conforme fui adicionando funções e tentando corrigir problemas eu realmente comecei a gostar de fato do projeto. Por isso decidi mudar o nome e publicá-lo no Github. Então, apesar dos problemas que o app pode apresentar durante o uso e de ele ter sido feito em vibe coding, espero que goste.",
                color = Color.White.copy(0.88f),
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
private fun HelpPageWarning(onNext: () -> Unit, onBack: () -> Unit, hideBottomButtons: Boolean = false) {
    var showTitle by remember { mutableStateOf(false) }
    var showBody by remember { mutableStateOf(false) }
    var showNav by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(150); showTitle = true
        delay(400); showBody = true
        delay(500); showNav = true
    }
    val grim = helpFont(R.font.grim_creeper)
    HelpScrollPage(
        showNav = showNav,
        onBack = onBack,
        onNext = onNext,
        nextLabel = "Próximo"
    ,
        hideBottomButtons = hideBottomButtons) {
        AnimatedVisibility(
            visible = showTitle,
            enter = fadeIn(tween(450)) + scaleIn(tween(500), initialScale = 0.7f)
        ) {
            Text(
                "Aviso",
                fontFamily = grim,
                fontSize = 34.sp,
                color = Color(0xFFE53935),
                modifier = Modifier.padding(bottom = 18.dp)
            )
        }
        AnimatedVisibility(
            visible = showBody,
            enter = fadeIn(tween(700)) + slideInVertically(tween(700)) { 50 }
        ) {
            Text(
                "Esse app foi feito praticamente com IA. Se você é completamente contra o uso de IA, apenas feche o app ou me xingue, não ligo. Eu também sou contra o uso de inteligência artificial de diversas formas e estou muito ciente dos impactos MUITO negativos causados por isso, mas se EU desistisse do app, não iria mudar em nada. Então eu o fiz mesmo assim, porque eu não tenho tempo de aprender programação (e sou burro) muito menos condições de contratar pessoas para desenvolver o app.\n\n" +
                    "Tendo tudo isso em mente, se você não se importa, espero que aproveite o app.",
                color = Color.White.copy(0.88f),
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
private fun HelpPageGuide(onDone: () -> Unit, onBack: () -> Unit, doneLabel: String = "Concluir", hideBottomButtons: Boolean = false) {
    var showText by remember { mutableStateOf(false) }
    var showImage by remember { mutableStateOf(false) }
    var showNav by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(150); showText = true
        delay(450); showImage = true
        delay(500); showNav = true
    }
    HelpScrollPage(
        showNav = showNav,
        onBack = onBack,
        onNext = onDone,
        nextLabel = "Concluir"
    ,
        hideBottomButtons = hideBottomButtons) {
        AnimatedVisibility(
            visible = showText,
            enter = fadeIn(tween(550)) + slideInVertically(tween(550)) { -30 }
        ) {
            Text(
                "Essa é a página inicial do app. Deslizar para os lados alterna entre as abas principais do app. Acredito que os botões com texto sejam autoexplicativos, então quanto aos botões superiores:\n\n" +
                    "1- Permite buscar músicas, álbuns e artistas.\n" +
                    "2- Toca todas as músicas em uma fila aleatória.\n" +
                    "3- Abre o gerenciador de arquivos integrado no app.\n" +
                    "4- Botão de ajuda. Provavelmente te trouxe aqui.\n" +
                    "5- Botão de configurações.\n\n" +
                    "Todas as abas possuem opções de Organização, e a aba de artistas pode ser alternada entre Artistas e Compositores.",
                color = Color.White.copy(0.88f),
                fontSize = 15.sp,
                lineHeight = 22.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        AnimatedVisibility(
            visible = showImage,
            enter = fadeIn(tween(600)) + scaleIn(tween(600), initialScale = 0.85f)
        ) {
            Image(
                painter = painterResource(R.drawable.help_first_page),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    
            )
            Text(
                "Toque na imagem para ampliar",
                fontSize = 12.sp,
                color = Color.White.copy(0.5f),
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
    if (expanded) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(0.92f))
                .clickable { expanded = false }
                .statusBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            var scale by remember { mutableFloatStateOf(1f) }
            Image(
                painter = painterResource(R.drawable.help_first_page),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer(scaleX = scale, scaleY = scale)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 4f)
                        }
                    }
            )
        }
    }
}

@Composable
private fun HelpScrollPage(
    showNav: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    nextLabel: String,
    hideBottomButtons: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(top = 20.dp, bottom = if (hideBottomButtons) 8.dp else 56.dp)
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 12.dp),
            content = content
        )
        if (!hideBottomButtons) {
            AnimatedVisibility(
                visible = showNav,
                enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { 30 }
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                        Text("Voltar", color = Color.White.copy(0.85f))
                    }
                    Button(
                        onClick = onNext,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Text(nextLabel, color = Color.Black, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}


@Composable
private fun OptionalHelpDrawable(resName: String, expandable: Boolean = false) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val id = remember(resName) {
        context.resources.getIdentifier(resName, "drawable", context.packageName)
    }
    if (id == 0) {
        Text(
            "(Coloque $resName em res/drawable)",
            color = Color.White.copy(0.4f),
            fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        return
    }
    Image(
        painter = painterResource(id),
        contentDescription = null,
        contentScale = ContentScale.FillWidth,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .padding(vertical = 8.dp)
    )
}

@Composable
private fun OptionalHelpVideo(resName: String = "help_video_1") {
    val context = androidx.compose.ui.platform.LocalContext.current
    val id = remember(resName) {
        context.resources.getIdentifier(resName, "raw", context.packageName)
    }
    if (id == 0) {
        Text(
            "(Coloque $resName.mp4 em res/raw)",
            color = Color.White.copy(0.4f),
            fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        return
    }
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            android.widget.VideoView(ctx).apply {
                setVideoURI(android.net.Uri.parse("android.resource://${ctx.packageName}/$id"))
                setOnPreparedListener { it.isLooping = true; start() }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(14.dp))
    )
}

@Composable
private fun HelpPageNowPlaying(onNext: () -> Unit, onBack: () -> Unit, hideBottomButtons: Boolean = false) {
    var show by remember { mutableStateOf(false) }
    var nav by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(150); show = true; delay(500); nav = true }
    HelpScrollPage(
        showNav = nav,
        onBack = onBack,
        onNext = onNext,
        nextLabel = "Próximo",
        hideBottomButtons = hideBottomButtons) {
        AnimatedVisibility(show, enter = fadeIn(tween(500)) + slideInVertically(tween(550)) { 40 }) {
            Column {
                Text(
                    "Essa é a tela de reprodução, e como ela tem algumas coisinhas a mais, acho interessante explicar:",
                    color = Color.White.copy(0.9f),
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
                OptionalHelpDrawable("help_foto_1")
                Text(
                    "1° - A parte de cima. Da esquerda pra direita, um botão de minimiza a tela, o nome da tela e o botão do menu de três pontos. Logo abaixo, um mini-card com algumas informações sobre o arquivo de Música, que ao clicar abre mais informações.\n\n" +
                    "2° - A exibição da imagem da capa da música, com um botão de favoritar e desfavoritar. Clicar diretamente na imagem ativa o Visualizador.\n\n" +
                    "3° - Abaixo da capa, há o mostrador de letras, que quando clicado abre o visualizador de letras em tela cheia.\n\n" +
                    "4° - Controles padrão de um reprodutor de música. Acima a seekbar, e abaixo da esquerda para a direita: Aleatório, Repetir (Quando colorido: Repete 1 Música, Quando cinza: repete a fila inteira), voltar Música, play/pausa, pular música, Equalizador e Fila de Reprodução.",
                    color = Color.White.copy(0.88f),
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

@Composable
private fun HelpPageThreeDots(onNext: () -> Unit, onBack: () -> Unit, hideBottomButtons: Boolean = false) {
    var show by remember { mutableStateOf(false) }
    var nav by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(150); show = true; delay(500); nav = true }
    val better = helpFont(R.font.better_vcr)
    HelpScrollPage(
        showNav = nav,
        onBack = onBack,
        onNext = onNext,
        nextLabel = "Próximo",
        hideBottomButtons = hideBottomButtons) {
        AnimatedVisibility(show, enter = fadeIn(tween(500)) + scaleIn(tween(500), initialScale = 0.9f)) {
            Column {
                Text("O menu de três pontos", fontFamily = better, fontSize = 26.sp, color = Color.White, modifier = Modifier.padding(bottom = 12.dp))
                OptionalHelpDrawable("help_foto_2")
                Text(
                    "Aqui já temos funções interessantes, como Adicionar uma Música às playlists, editar tags das Músicas, buscar letra da Música online, programar o app para encerrar automaticamente, mudar a velocidade, compartilhar o trecho da letra sendo reproduzido atualmente, e a opção de excluir o arquivo.",
                    color = Color.White.copy(0.88f),
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

@Composable
private fun HelpPageVisualizer(onNext: () -> Unit, onBack: () -> Unit, hideBottomButtons: Boolean = false) {
    var show by remember { mutableStateOf(false) }
    var nav by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(150); show = true; delay(500); nav = true }
    val better = helpFont(R.font.better_vcr)
    HelpScrollPage(
        showNav = nav,
        onBack = onBack,
        onNext = onNext,
        nextLabel = "Próximo",
        hideBottomButtons = hideBottomButtons) {
        AnimatedVisibility(show, enter = fadeIn(tween(550)) + slideInHorizontally(tween(550)) { 60 }) {
            Column {
                Text("O Visualizador", fontFamily = better, fontSize = 26.sp, color = Color.White, modifier = Modifier.padding(bottom = 12.dp))
                OptionalHelpVideo("help_video_1")
                Text(
                    "O visualizador é controlado por gestos: Deslizar para cima impede de trocar o preset exibido e deslizar para baixo faz com que a troca volte a acontecer; deslizar para esquerda ou direita troca de preset aleatoriamente. Abaixo há o mini card de presets, com um botão de favoritar presets e clicar diretamente abre o menu de presets",
                    color = Color.White.copy(0.88f),
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
                OptionalHelpDrawable("help_foto_6")
                Text(
                    "com opção de buscar presets pelo nome.",
                    color = Color.White.copy(0.88f),
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

@Composable
private fun HelpPageFullLyrics(onNext: () -> Unit, onBack: () -> Unit, hideBottomButtons: Boolean = false) {
    var show by remember { mutableStateOf(false) }
    var nav by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(150); show = true; delay(500); nav = true }
    val better = helpFont(R.font.better_vcr)
    HelpScrollPage(
        showNav = nav,
        onBack = onBack,
        onNext = onNext,
        nextLabel = "Próximo",
        hideBottomButtons = hideBottomButtons) {
        AnimatedVisibility(show, enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { -35 }) {
            Column {
                Text("Letras em tela cheia", fontFamily = better, fontSize = 26.sp, color = Color.White, modifier = Modifier.padding(bottom = 12.dp))
                OptionalHelpDrawable("help_foto_3")
                Text(
                    "Aqui você pode acompanhar a letra completa da Música. Em letras sincronizadas, clicar em uma linha faz o reprodutor pular o momento da música que essa linha representa. O botão de cima à direita, permite que você selecione algumas linhas, e ao clicar novamente o app irá gerar uma foto, como a foto de exemplo abaixo:",
                    color = Color.White.copy(0.88f),
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
                OptionalHelpDrawable("help_foto_4")
            }
        }
    }
}

@Composable
private fun HelpPageQueue(onNext: () -> Unit, onBack: () -> Unit, hideBottomButtons: Boolean = false) {
    var show by remember { mutableStateOf(false) }
    var nav by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(150); show = true; delay(500); nav = true }
    val better = helpFont(R.font.better_vcr)
    HelpScrollPage(
        showNav = nav,
        onBack = onBack,
        onNext = onNext,
        nextLabel = "Próximo",
        hideBottomButtons = hideBottomButtons) {
        AnimatedVisibility(show, enter = fadeIn(tween(500)) + scaleIn(tween(550), initialScale = 0.88f)) {
            Column {
                Text("Fila de Reprodução", fontFamily = better, fontSize = 26.sp, color = Color.White, modifier = Modifier.padding(bottom = 12.dp))
                OptionalHelpDrawable("help_foto_8")
                Text(
                    "Na caixa de texto você pode pesquisar por uma música e arrastando uma música pelo ícone à direita dela você pode reorganizar sua posição na fila.\nClicar e selecionar uma música abre o mini-menu e o modo de seleção.",
                    color = Color.White.copy(0.88f),
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
                OptionalHelpDrawable("help_foto_7")
                Text(
                    "Clicar fora do mini-menu fecha ele e clicar em 'Ações' o abre novamente.",
                    color = Color.White.copy(0.88f),
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
            }
        }
    }
}


@Composable
private fun HelpPageTagEditor(onNext: () -> Unit, onBack: () -> Unit, hideBottomButtons: Boolean = false) {
    var show by remember { mutableStateOf(false) }
    var nav by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(150); show = true; delay(500); nav = true }
    val julia = helpFont(R.font.juliametal_regular)
    HelpScrollPage(
        showNav = nav,
        onBack = onBack,
        onNext = onNext,
        nextLabel = "Próximo",
        hideBottomButtons = hideBottomButtons) {
        AnimatedVisibility(show, enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { 30 }) {
            Column {
                Text("Editor de Tags", fontFamily = julia, fontSize = 26.sp, color = Color.White, modifier = Modifier.padding(bottom = 8.dp))
                OptionalHelpDrawable("help_foto_01")
                Text(
                    "Aqui você pode editar manualmente as tags de um arquivo ou pesquisar online (no momento, apenas a fonte Last.fm é suportada).",
                    color = Color.White.copy(0.88f), fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(bottom = 16.dp)
                )
                Text("Editor de Letras", fontFamily = julia, fontSize = 26.sp, color = Color.White, modifier = Modifier.padding(bottom = 8.dp))
                Text(
                    "Aqui você cola letras manualmente ou edita letras já pesquisadas. Existem quatro abas:",
                    color = Color.White.copy(0.88f), fontSize = 15.sp, lineHeight = 22.sp
                )
                Text("Texto", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                OptionalHelpDrawable("help_foto_02")
                Text("Linhas", color = Color.White, fontWeight = FontWeight.SemiBold)
                OptionalHelpDrawable("help_foto_03")
                Text("Palavras", color = Color.White, fontWeight = FontWeight.SemiBold)
                OptionalHelpDrawable("help_foto_04")
                Text(
                    "Nos modos linhas e palavras, basta clicar no ícone de relógio no momento certo para sincronizar.\nNos botões - e + você controla a velocidade da música.",
                    color = Color.White.copy(0.88f), fontSize = 15.sp, lineHeight = 22.sp
                )
                Text("Tradução", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp))
                OptionalHelpDrawable("help_foto_traducao")
                Text(
                    "Na aba Tradução você escreve a versão traduzida de cada linha. O botão FX aplica efeitos de animação em palavras específicas da tradução.",
                    color = Color.White.copy(0.88f), fontSize = 15.sp, lineHeight = 22.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Text("Efeitos de legenda (FX)", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                OptionalHelpDrawable("help_foto_fx_palavra")
                OptionalHelpDrawable("help_foto_fx_efeito")
                Text(
                    "Fluxo: toque em FX → escolha a palavra → escolha o efeito (Piscar, Agitar, Destacar, Sumir, Eco, Zoom, Deslizar, Digitar, Flash, Tremor, Arco-íris, Congelar, Sombra, Onda…).\n\n" +
                        "Na tradução, se a letra principal já tiver palavras com efeito na mesma linha, o app pede para sincronizar o tempo (ex.: des1, des2). Assim a animação da tradução dispara no mesmo instante da palavra da letra.\n\n" +
                        "Os marcadores ({des}, {ded}, {eco}…) e os tempos ficam salvos no LRC ou nas tags embutidas. Na tradução, só as palavras com efeito animam — o resto da linha não faz karaokê palavra a palavra.",
                    color = Color.White.copy(0.88f), fontSize = 15.sp, lineHeight = 22.sp
                )
            }
        }
    }
}

@Composable
private fun HelpPageLibrarySettings(onNext: () -> Unit, onBack: () -> Unit, hideBottomButtons: Boolean = false) {
    var show by remember { mutableStateOf(false) }
    var nav by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(150); show = true; delay(500); nav = true }
    val julia = helpFont(R.font.juliametal_regular)
    HelpScrollPage(
        showNav = nav,
        onBack = onBack,
        onNext = onNext,
        nextLabel = "Próximo",
        hideBottomButtons = hideBottomButtons) {
        AnimatedVisibility(show, enter = fadeIn(tween(500)) + slideInHorizontally(tween(500)) { -40 }) {
            Column {
                Text("Configurações de biblioteca", fontFamily = julia, fontSize = 26.sp, color = Color.White, modifier = Modifier.padding(bottom = 8.dp))
                Text(
                    "Na configuração inicial ou em Configurações → Biblioteca, escolha como ler a biblioteca: Músicas e áudio usa a permissão do Android e o índice MediaStore; pode não encontrar alguns arquivos não indexados (como certos DSF) e não libera gerenciamento geral de arquivos. Acesso a todos os arquivos faz uma busca mais ampla, mas requer a permissão especial do Android.\n\nVocê também encontra o separador de tags, a blacklist de pastas, a duração mínima e as opções Last.fm.",
                    color = Color.White.copy(0.88f), fontSize = 15.sp, lineHeight = 22.sp
                )
                OptionalHelpDrawable("help_foto_05")
            }
        }
    }
}

@Composable
private fun HelpPageOrganize(onNext: () -> Unit, onBack: () -> Unit, hideBottomButtons: Boolean = false) {
    var show by remember { mutableStateOf(false) }
    var nav by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(150); show = true; delay(500); nav = true }
    val julia = helpFont(R.font.juliametal_regular)
    HelpScrollPage(
        showNav = nav,
        onBack = onBack,
        onNext = onNext,
        nextLabel = "Próximo",
        hideBottomButtons = hideBottomButtons) {
        AnimatedVisibility(show, enter = fadeIn(tween(500)) + scaleIn(tween(500), initialScale = 0.9f)) {
            Column {
                Text("Organizar biblioteca", fontFamily = julia, fontSize = 26.sp, color = Color.White, modifier = Modifier.padding(bottom = 8.dp))
                OptionalHelpDrawable("help_foto_06")
                Text(
                    "Uma das funções mais interessantes. Aqui você pode renomear todas as músicas de uma pasta e suas subpastas para o padrão que você escolher e também pode mover todas as músicas de uma pasta escolhida e suas subpastas para uma pasta que o usuário escolher.",
                    color = Color.White.copy(0.88f), fontSize = 15.sp, lineHeight = 22.sp
                )
            }
        }
    }
}


@Composable
private fun HelpPageScrobbler(onNext: () -> Unit, onBack: () -> Unit, hideBottomButtons: Boolean = false) {
    var show by remember { mutableStateOf(false) }
    var nav by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(150); show = true; delay(500); nav = true }
    val julia = helpFont(R.font.juliametal_regular)
    HelpScrollPage(
        showNav = nav,
        onBack = onBack,
        onNext = onNext,
        nextLabel = "Próximo",
        hideBottomButtons = hideBottomButtons
    ) {
        AnimatedVisibility(show, enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { 30 }) {
            Column {
                Text(
                    "Scrobbling (Last.fm)",
                    fontFamily = julia,
                    fontSize = 26.sp,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OptionalHelpDrawable("help_foto_scrobble_api")
                Text(
                    "Em Configurações → Scrobbling você conecta sua conta do Last.fm. Cadastre um app em last.fm/api/account/create e cole a API Key e o Shared Secret (do mesmo app). Os dados ficam só neste aparelho.",
                    color = Color.White.copy(0.88f), fontSize = 15.sp, lineHeight = 22.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Text(
                    "Com \"Enviar scrobbles\" ativo, o app envia \"ouvindo agora\" e, quando o play é contado (veja Player → Histórico / Mais tocadas), envia o scrobble com a hora em que a faixa começou. Faixas com menos de 30 s não são enviadas.",
                    color = Color.White.copy(0.88f), fontSize = 15.sp, lineHeight = 22.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OptionalHelpDrawable("help_foto_scrobble_edits")
                Text(
                    "Edições automáticas (só no envio ao Last.fm, não alteram as tags do arquivo):\n" +
                        "• Só o primeiro artista — em faixas com vários artistas, usa o primeiro (mesmos separadores da Biblioteca).\n" +
                        "• Remover texto entre parênteses — tira trechos como (Ao Vivo), (Remastered) e [Bonus] do título, artista e álbum antes de scrobblar.\n\n" +
                        "Faixas com edição manual salva são ignoradas pelas regras automáticas. Você ainda pode editar qualquer faixa pela notificação do scrobbler.\n\n" +
                        "Edições manuais ficam em Lyron/scrobble_edits.json. Use \"Editar tags\" na notificação ou importe um arquivo no mesmo formato.",
                    color = Color.White.copy(0.88f), fontSize = 15.sp, lineHeight = 22.sp
                )
            }
        }
    }
}

@Composable
private fun HelpPageAdvanced(onNext: () -> Unit, onBack: () -> Unit, hideBottomButtons: Boolean = false) {
    var show by remember { mutableStateOf(false) }
    var nav by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(150); show = true; delay(500); nav = true }
    val better = helpFont(R.font.better_vcr)
    HelpScrollPage(
        showNav = nav,
        onBack = onBack,
        onNext = onNext,
        nextLabel = "Próximo",
        hideBottomButtons = hideBottomButtons
    ) {
        AnimatedVisibility(show, enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { 30 }) {
            Column {
                Text(
                    "Funções avançadas",
                    fontFamily = better,
                    fontSize = 26.sp,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                OptionalHelpDrawable("help_foto_avancado_tema")
                Text(
                    "Tema por foto (Aparência)\n" +
                        "Escolha uma imagem de fundo com blur ajustável. Enquanto uma faixa toca, as cores da capa dominam o gradiente e as cores flutuantes. Ative \"Usar capa enquanto toca\" para trocar a foto pela capa automaticamente.\n\n" +
                        "Índice alfabético\n" +
                        "Em Aparência você escolhe entre o índice em vidro (com língua e prefixos DA/DB…) ou o clássico: uma barrinha colorida com letras, arrastável.\n\n" +
                        "Sincronização de letras (karaokê)\n" +
                        "No editor de letras você sincroniza palavra a palavra. Há botão para marcar o fim (endMs) só da última palavra de cada linha. A linha só herda o tempo da primeira palavra se o timestamp da linha estiver maior que o dela — evita puxar linhas já corretas para trás.\n\n" +
                        "Retrospectivas\n" +
                        "O app pode avisar no sábado (semana), no último dia do mês e no último dia do ano, a partir das 20h, para exportar a retrospectiva (Last.fm ou histórico local).\n\n" +
                        "Gerenciar espaço\n" +
                        "Em Configurações → Armazenamento você vê o total e limpa por categoria: cache, biblioteca/capas, playlists e histórico, fontes/presets .milk, configurações e letras exportadas.",
                    color = Color.White.copy(0.88f),
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
                OptionalHelpDrawable("help_foto_avancado_espaco")
                OptionalHelpDrawable("help_foto_avancado_karaoke")
            }
        }
    }
}

@Composable
private fun HelpPageLyricsVideo(onNext: () -> Unit, onBack: () -> Unit, hideBottomButtons: Boolean = false) {
    var show by remember { mutableStateOf(false) }
    var nav by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(150); show = true; delay(500); nav = true }
    val matcha = helpFont(R.font.matcha_style)
    HelpScrollPage(
        showNav = nav,
        onBack = onBack,
        onNext = onNext,
        nextLabel = "Próximo",
        hideBottomButtons = hideBottomButtons) {
        AnimatedVisibility(show, enter = fadeIn(tween(500)) + scaleIn(tween(500), initialScale = 0.9f)) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(
                        "Gerador de vídeo com letras",
                        fontFamily = matcha,
                        fontSize = 24.sp,
                        color = Color.White,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF7C5CFF))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("Novo", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    "Agora você pode gerar vídeos de letras diretamente do app! E é muito simples, tudo que você precisa fazer é:",
                    color = Color.White.copy(0.88f), fontSize = 15.sp, lineHeight = 22.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OptionalHelpDrawable("help_video")
                Text(
                    "Abrir o menu de três pontos e clicar em gerar vídeo. Ao clicar, abrirá essa tela:",
                    color = Color.White.copy(0.88f), fontSize = 15.sp, lineHeight = 22.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                OptionalHelpDrawable("help_generate")
                Text(
                    "Nesta tela você define:",
                    color = Color.White.copy(0.88f), fontSize = 15.sp, lineHeight = 22.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
                )
                Text(
                    "• Fonte da letra: arquivo .lrc ao lado da música, tags embutidas, ou Sem letra (só capa/preset + título e artista).\n" +
                        "• Trecho: intervalo livre, música inteira, ou por linhas da letra sincronizada (do início da linha inicial até o fim da linha final).\n" +
                        "• Fundo: capa borrada ou preset milk (reage à música; demora mais para gerar).\n" +
                        "• Proporção, FPS e resolução do vídeo.\n\n" +
                        "Animações de legenda ({des}, {ded}, zoom, eco…) também aparecem no vídeo gerado, na letra e na tradução.\n\n" +
                        "(Taxas de frame e resoluções maiores demoram mais, principalmente em aparelhos mais fracos. 1440p pode não ser compatível com celulares antigos.)",
                    color = Color.White.copy(0.88f), fontSize = 15.sp, lineHeight = 22.sp
                )
            }
        }
    }
}

@Composable
private fun HelpPageClosingNote(onNext: () -> Unit, onBack: () -> Unit, hideBottomButtons: Boolean = false) {
    var show by remember { mutableStateOf(false) }
    var nav by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(150); show = true; delay(500); nav = true }
    HelpScrollPage(
        showNav = nav,
        onBack = onBack,
        onNext = onNext,
        nextLabel = "Próximo",
        hideBottomButtons = hideBottomButtons) {
        AnimatedVisibility(show, enter = fadeIn(tween(600))) {
            Text(
                "Isso é tudo que eu acho que deveria explicar aqui. As outras funções já tem explicações o bastante em suas próprias páginas. Se no futuro eu adicionar novas funções complexas, terão mais explicações por aqui.",
                color = Color.White.copy(0.9f), fontSize = 16.sp, lineHeight = 24.sp
            )
        }
    }
}

@Composable
private fun HelpPageFinal(onDone: () -> Unit, onBack: () -> Unit, hideBottomButtons: Boolean = false) {
    var show by remember { mutableStateOf(false) }
    var nav by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(150); show = true; delay(500); nav = true }
    val buba = helpFont(R.font.buba_outline)
    HelpScrollPage(
        showNav = nav,
        onBack = onBack,
        onNext = onDone,
        nextLabel = "Concluir",
        hideBottomButtons = hideBottomButtons) {
        AnimatedVisibility(show, enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { 40 }) {
            Column {
                Text(
                    "Lembrando que este ainda é um app em desenvolvimento",
                    fontFamily = buba,
                    fontSize = 22.sp,
                    color = Color(0xFFE53935),
                    modifier = Modifier.padding(bottom = 14.dp)
                )
                Text(
                    "Se você notar qualquer bug ou tiver alguma sugestão de melhoria, por favor acesse 'Reportar bugs e sugerir melhorias' no final da página de configurações. Desde já obrigado e aproveite o app :)",
                    color = Color.White.copy(0.9f), fontSize = 15.sp, lineHeight = 22.sp
                )
            }
        }
    }
}
