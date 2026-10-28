package com.tolete.player.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tolete.player.data.LibrarySettings
import com.tolete.player.data.SourceFolder
import com.tolete.player.ui.ChangelogEntry
import com.tolete.player.ui.MusicViewModel.AppThemeId
import com.tolete.player.ui.MusicViewModel.ShuffleType
import java.io.File

@Composable
internal fun AboutPage(onOpenChangelog: () -> Unit = {}, onOpenLicenses: () -> Unit = {}) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Text(
            "Lyron Music Player",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            "Versão ${com.tolete.player.data.UpdateManager.installedVersion(context)} (${com.tolete.player.data.UpdateManager.installedCode(context)})",
            fontSize = 13.sp,
            color = Color.White.copy(0.55f),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        CreditLine(
            label = "Realizador do projeto",
            value = "PGCR Gui",
            isLink = true,
            onLinkClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://linktr.ee/pgcrgui"))
                context.startActivity(intent)
            }
        )
        CreditLine(
            label = "Idealizador do projeto original",
            value = "Matheus França",
            isLink = true,
            onLinkClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/matheusflm4?vrfl=MTRyaDhwNDByNzhycA=="))
                context.startActivity(intent)
            }
        )
        Column(Modifier.padding(vertical = 8.dp)) {
            Text("Beta testers", fontSize = 13.sp, color = Color.White.copy(0.55f))
            Row(Modifier.padding(top = 2.dp)) {
                Text(
                    "Gean Carlos, ",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF82B1FF),
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://www.instagram.com/cgean0276?srtk=dG4zbDNuNm1vdXBy")
                        )
                        context.startActivity(intent)
                    }
                )
                Text(
                    "Ariel Rodrigues Pires",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF82B1FF),
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://www.instagram.com/ariel_rodrigues147?utm_source=qr&dlrf=MTJ1ejhoOWZna2didA==")
                        )
                        context.startActivity(intent)
                    }
                )
            }
        }
        CreditLine(label = "Desenhista do ícone do app", value = "Taci")
        CreditLine(
            label = "Ícones Flutuantes e da Tela Inicial",
            value = "Good Ware",
            isLink = true,
            onLinkClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.flaticon.com/br/autores/good-ware"))
                context.startActivity(intent)
            }
        )
        CreditLine(
            label = "Ícones do Now Playing",
            value = "Magnific",
            isLink = true,
            onLinkClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.flaticon.com/br/autores/magnific"))
                context.startActivity(intent)
            }
        )

        Spacer(Modifier.height(16.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(0.08f))
                .clickable { com.tolete.player.data.UpdateManager.check(context, manual = true) }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, tint = Color.White)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Verificar atualizações", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text("Checa novas versões no GitHub Releases", fontSize = 12.sp, color = Color.White.copy(0.5f))
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(0.4f)
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(0.08f))
                .clickable(onClick = onOpenChangelog)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Info, contentDescription = null, tint = Color.White)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Logs de atualização", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text("Mudanças por versão", fontSize = 12.sp, color = Color.White.copy(0.5f))
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(0.4f)
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(0.08f))
                .clickable(onClick = onOpenLicenses)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Description, contentDescription = null, tint = Color.White)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Licenças dos serviços", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text("Bibliotecas e serviços usados", fontSize = 12.sp, color = Color.White.copy(0.5f))
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(0.4f)
            )
        }

        Spacer(Modifier.height(16.dp))
        CreditLine(
            label = "Outras versões do app",
            value = "MediaFire",
            isLink = true,
            onLinkClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.mediafire.com/folder/o020wt1jub0sl/Lyrion_Player"))
                context.startActivity(intent)
            }
        )

        Spacer(Modifier.height(12.dp))
        Column(Modifier.padding(vertical = 8.dp)) {
            Text("Splash Screen", fontSize = 13.sp, color = Color.White.copy(0.55f))
            Text(
                "Foto tirada por PGCR Gui e música extraída de Diamond Rush",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Spacer(Modifier.height(20.dp))
        Row {
            Text(
                "Feito com Grok, Claude.Ai e ",
                fontSize = 14.sp,
                color = Color.White.copy(0.7f)
            )
            Text(
                "Code on the Go",
                fontSize = 14.sp,
                color = Color(0xFF82B1FF),
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://appdevforall.org/code-on-the-go/")
                    )
                    context.startActivity(intent)
                }
            )
        }
    }
}



internal val changelogEntries = listOf(
        ChangelogEntry(
            "0.60",
            "220",
            listOf(
                "Re-redesign da tela do now playing (espero que seja o último)",
                "Redesign da notificação (porque não né kkkkkkk)",
                "Letras word-by-word com animação mais suave",
                "Botão de troca de velocidade adicionado",
                "Letras word-by-word no Now Playing",
                "Sincronizador de letras agora conta com sincronizador word-by-word (apenas .elrc)",
                "Corrigido parser do ttml",
                "Seletor de cores HSV (Color Wheel)",
                "Melhoria de visibilidade nas cores das letras exportadas",
                "Corrigido layout de Favoritos, Histórico e Mais Tocadas que deixava um vão na parte inferior da página",
                "Mais opções nas Mais Tocadas (Artistas, Álbuns, Períodos de tempo)",
                "Opção de exportar top 10 mais tocadas em imagem",
                "Backup atualizado para guardar novas configurações",
                "Busca de Tags online pelo Last.fm e Deezer",
                "Nova opção de exportar presets .milk favoritos em .zip (função pensada para o dev)",
                "Removidos itens que não funcionavam porque... não funcionavam né kkkkk",
                "Novas fontes de busca de letras (KuGou, Netease, QQ Music)"
            )
        ),
        ChangelogEntry(
            "0.55",
            "215",
            listOf(
                "Mudança no layout da página de Artista",
                "Mudança em exibição de foto na lista de Artistas",
                "Aumento de limite de linhas exportáveis em foto",
                "Remoção de fontes embutidas pra evitar problemas (se 50 dólares é só pra comprar e poder usar no app, imagina se vier processo)",
                "Mudança no visualizador de letras do Now Playing e no Layout",
                "Opção de personalizar fontes de início e de foto gerada separadamente",
                "Mudanças na forma em que as letras buscadas pelo app são salvas",
                "Adicionado um formulário para relatório de bugs e sugestões de melhorias e funções",
                "Músicas em Favoritos agora selecionáveis (eu esqueci desse na última Atualização kkkkkkkkkk)",
                "Opção de marcar todas e acessar informações no menu pop-up de seleção de Músicas",
                "Melhorias na Fila de reprodução",
                "Tocar na tela durante o splash screen pula a animação",
                "animação da seekbar, sei lá, alguém me mata logo",
            )
        ),
        ChangelogEntry(
            "0.50",
            "210",
            listOf(
                "(Tentativa de) mudança no layout do Now Playing",
                "Removido notificações desnecessárias",
                "Músicas em Artista, Histórico e Mais Tocadas agora selecionáveis",
                "Mais informações sobre arquivos de música",
                "Letras simples agora exibidas em Tela Cheia (sim isso não tava antes lkkkkkkkkk)",
                "Suporte à edição e sincronização de .lrc",
                "Botão de exclusão no menu do now playing",
                "Melhoria no menu de escolha de presets",
                "Correções gramáticas no campo de busca de letras",
                "Opção de gerar fotos ao compartilhar trechos de letra de Música (experimental)",
                "Novos métodos de pesquisa automática de imagem de artista"
            )
        ),
       ChangelogEntry(
            "0.45",
            "200",
            listOf(
                "Tela não apaga mais no Now Playing e na letra em tela cheia",
                "Verificação automática de atualizações no GitHub ao abrir o app",
                "Download e instalação do APK direto pelo app",
                "Pop-up com as novidades aparece automaticamente após atualizar",
                "Seletor de pastas via SAF na opção de BlackList",
                "Ícones flutuantes em tela de pesquisa",
                "Animação de scroll em troca de abas (meh)",
                "FINALMENTE corrigido comportamento da barra de presets que não mostrava o nome correto na troca automática"
            )
        ),
          ChangelogEntry(
            "0.44.2",
            "199",
            listOf(
            "Corrigido a duração da animação de desfavoritar",
            "Álbuns com vários discos agora apresenta divisão de discos",
            "Seção com todas as músicas agora disponível abaixo dos álbuns na página de Artista",
            "Seletor de tom e transparência de menu em Now Playing e Informações técnicas (ainda tá um tolete)"
            )
        ),
           ChangelogEntry(
            "0.44.1",
            "198",
            listOf(
            "Opção \"Desmarcar Todas\" agora funcional",
            "Correção de Ícones Flutuantes em Configurações",
            "Mudança no Layout do Now Playing com nova aparência para os botões",
            "Adicionada função de BlackList (Ainda incompleta)"
            )
        ),
           ChangelogEntry(
            "0.44",
            "197",
            listOf(
                "Novos ícones flutuantes na tela de configurações (Que não funcionaram kkkkkkkk)",
                "Novos ícones e layout ajustado no Now Playing (Que também não funcionaram kkkkkkkkk)",
                "Removida subconfiguração duplicada de fonte das letras; mantido apenas Visualizador de legendas",
                "Opção \"Desmarcar Todas\" no menu de seleção de músicas (Advinha só? Não funcionoukkkkkkkkkkkkk)",
                "Edição em lote: campos Gênero, Ano, Total de Faixas e Total de discos",
                "Botões Tocar todas e Aleatório na página do artista"
            )
        ),
       ChangelogEntry(
            "0.43.3",
            "196",
            listOf(
            "Corrigido comportamento de arrastar em fila de reprodução",
            "Opção de organizar e deletar playlists adicionada",
            "Corrigido a opção de editar foto em tags"
            )
        ),
       ChangelogEntry(
            "0.43.2",
            "195",
            listOf(
                "As músicas selecionadas agora entram na fila de reprodução ou em Playlists na ordem que são selecionadas",
                "Retiradas duplicats de título \"Início\" e \"Favoritas\" de Playlists",
                "Corrigido bug que impedia de salvar Playlists",
                "Corrigidas importação e exportação de Playlists"
            )
        ),
        ChangelogEntry(
            "0.43.1",
            "194",
            listOf(
                "Bug corrigido - Equalizador finalmente funcional",
                "Animação de ícones flutuantes melhorada e otimizada",
                "Layout dos botões do menu inicial alterado",
                "Revisão humana do log de atualizações (finalmente né kkkkkk)"
            )
        ),
        ChangelogEntry(
            "0.43",
            "193",
            listOf(
                "Layout da aba Início redesenhado com botões liquid glass com ícones",
                "Ícones flutuantes no fundo das telas abas principais com opção de ativar/desativar em Aparência",
                "Fontes do Player e das letras agrupadas em submenu em Aparência",
                "Seletor de cor do tema dinâmico agora usa sistema hexadecimal",
                "Caixa de ajuda atualizada com instruções úteis: fontes, presets .milk e gestos no Now Playing (Precisa de melhorias futuras)",
                "Correção de crash ao compartilhar trecho de letra na tela de letras em tela cheia",
                "Botão que redireciona para a página do github para checar atualizações",
                "Equalizador refeito do zero"
            )
        ),
        ChangelogEntry(
            "0.42",
            "192",
            listOf(
                "Equalizador redesenhado no estilo liquid glass (que já nasceu defasado kkkkkkkk)",
                "Opção de trocar foto no editor de tags (não funciona ainda)",
                "Compartilhar trechos de letra (somente em texto por enquanto)",
                "Otimizações internas para melhor navegação entre códigos da tela de configurações (pra mim e pra quem quiser mexer no código fonte)",
                "Visualizer: opção de mudar de música aleatoriamente no intervalo configurado e opção de trocar preset com a mudança de música",
                "Seleção de cores com espectro RGB (HSV) nas opções de cor (defasou rápido kkkkkkk)"
            )
        ),
        ChangelogEntry(
            "0.41",
            "191",
            listOf(
                "Mudanças no visual liquid glass",
                "Ajustes de estabilidade e performance",
                "Correções diversas de UI e biblioteca",
            )
        ),
        ChangelogEntry(
            "0.40",
            "190",
            listOf(
                "Removida a skin NyxT e toda a opção de Skins do app (Só tava quebrando)",
                "Removida a opção \"Visualizer\" do menu de três pontos do Now Playing (Agora acessível por submenu próprio)",
                "Opção de adicionar foto de artista manualmente",
                "Infos de artistas (manuais e automáticas) agora são salvas",
                "Preview de letras no buscador manual",
                "Animação de letras karaokê aprimorada (ainda tá um tolete)",
                "Trocador de presets abaixo da capa; troca manual aleatória; nome correto",
                "Texto de artista/álbum expansível ao toque",
                "Scroll na tela do artista também move foto e bio (inacreditável que isso não tava antes kkkkkkk)",
                "Bug corrigido na fila de reprodução; adicionar duplicata não faz mais crashar",
                "Backup em ZIP com mais preferências e dados do usuário",
                "Barra de abas minimiza/maximiza com gesto de scroll"
            )
        ),
        ChangelogEntry(
            "0.37",
            "187",
            listOf(
                "Visualizer: troca de presets por gestos (deslizar esquerda/direita na capa); chip simplificado com nome + favoritar",
                "Gestos: deslizar para baixo desbloqueia preset; para cima bloqueia",
                "API Wikidata/Wikimedia: foto e biografia na tela do artista; info do álbum abaixo dos artistas (editável manualmente)",
                "Cor padrão do tema dinâmico configurável em Aparência (quando nenhuma música está tocando)",
                "Layout da tela de álbum ajustado com seção de informações do Wikidata"
            )
        ),
        ChangelogEntry(
            "0.36",
            "186",
            listOf(
                "Suporte à codec ALAC no aplocativo",
                "Caixa liquid glass no Now Playing com preset atual, favoritar e anterior/próximo; toque abre a lista",
                "Contagem de plays só após atingir percentual da faixa (70–100%, ajustável em Configurações › Player)",
                "Mais Tocadas mostra o número de plays ao lado de cada música"
            )
        ),
        ChangelogEntry(
            "0.35",
            "185",
            listOf(
                "Correção do seekbar: arrastar a barra de progresso agora busca corretamente a posição",
                "Botão para embaralhar todas as músicas e iniciar reprodução na aba Todas",
                "Tema AMOLED (preto puro) para a skin Ly's Place",
                "Histórico de reprodução com contagem de plays (Mais Tocadas e Últimas Reproduzidas)",
                "Aba Playlists removida da navegação principal",
                "Nova aba Início na skin Ly's Place: Mais Tocadas, Últimas, Playlists, Favoritas (feiiiii kkkkkkkkkkkkkkkkkk)",
                "Now Playing: caixa liquid glass com preset do visualizer, favoritar preset, anterior/próximo; toque abre lista",
                "Removido o link do meu perfil do AO3 (não sei o que eu tava pensando na hora que incluí isso kkkkkkk)"
            )
        ),
        ChangelogEntry(
            "0.30",
            "182",
            listOf(
                "Álbuns mesclados quando têm o mesmo nome e o mesmo artista do álbum nas tags",
                "Animações de favoritar/desfavoritar no Now Playing substituídas por GIFs",
                "Renderizar e exportar vídeo .mp4 no Now Playing (quebradaço, ideia abandonada por enquanto)",
                "Fila de reprodução redesenhada no estilo liquid glass com capa borrada de fundo (Precisa de correção) ",
                "Menu de multi-seleção: renomear arquivos em lote por tags (padrões editáveis)",
                "Abertura de músicas a partir de outros apps com miniplayer (por enquanto tá um tolete lkkkkk)"
            )
        ),
        ChangelogEntry(
            "0.26",
            "178",
            listOf(
                "Contraste corrigido nos diálogos Buscar letra e Informações técnicas (demorei até pra corrigir isso kkkkkkkkk)",
                "Campos de texto na Biblioteca com fonte clara em fundo escuro (outro que demorei corrigir)",
                "Botão para atrasar/adiantar todas as letras em ms na tela de sincronização",
                "Presets .milk embutidos no app (adicionarei mais no futuro quando tiver uma lista mais enxuta e curada)",
                "Menu Visualizer no Now Playing: lista de presets, favoritos no topo, auto-scroll ao preset atual",
                "Gestos no Now Playing: deslizar para cima bloqueia o preset (ícone cadeado); deslizar para a esquerda (era pra ser deslizar pra baixo mas ferrei completamente com isso na época kkkkkkkkkkkk) desbloqueia",
                "Animações mais suaves ao deslizar músicas na fila atual (mas ainda quebradas)",
                "Visualizador para de renderizar ao pausar; sincronia e precisão melhoradas (eu acho)",
            )
        ),
        ChangelogEntry(
            "0.25",
            "177",
            listOf(
                "Implementação experimental do projectM",
                "O projeto agora está no github ʕ•̫͡•ʔ",
            )
        ),
        ChangelogEntry(
            "0.20",
            "170",
            listOf(
                "Transições ao trocar de tela (Tava um tolete seco antes kkkkkkk)",
                "Scan em segundo plano por novas músicas",
                "Opção para desativar completamente a tela de splash (Mas eu sei que você não vai querer fazer isso :)",
                "Fade in/out ao reproduzir/pausar (preciso de corrigir)",
                "Navegação por gestos (deslizar esquerda/direita) para trocar abas na tela principal",
                "BitDepth nas informações técnicas do Now Playing",
                "Ícones de codec (MP3, FLAC, AAC, etc.) no lugar dos nomes de texto",
                "Fonte KOMIKABB removida (estava quebrada)",
                "Edição de tags em lote (Artista, Álbum, Artista do Álbum) no menu de multi-seleção",
                "Submenu de licenças dos serviços na tela de créditos",
                "Links extras e seção Splash Screen na tela de créditos",
                "Otimizações: contador de scan menos agressivo, álbuns calculados em background, keys nas listas Lazy, fade cancelado ao pular faixa"
            )
        ),
        ChangelogEntry(
            "New Glass!",
            "157",
            listOf(
                "Barra de navegação da skin Ly's Place com indicador deslizável de vidro (liquid glass)",
                "Efeitos liquid glass do app refinados",
                "Indicador animado com spring bounce ao trocar de aba (talvez eu mude depois)"
            )
        ),
        ChangelogEntry(
            "0.10",
            "150",
            listOf(
                "Sincronizador de letras movido para o editor de tags (edita letras embutidas no arquivo)",
                "Modo simples do sincronizador: uma única caixa de texto; linhas viram caixas no modo sincronizado",
                "Miniplayer do sincronizador: botões −10s e +10s no lugar de anterior/próxima",
                "Página de artista: grade de álbuns, contagem de músicas e duração total",
                "Página de álbum: capa grande com fade, Reproduzir/Shuffle, artistas clicáveis e lista de faixas"
            )
        ),
        ChangelogEntry(
            "0.9",
            "145",
            listOf(
                "Animação da capa no Now Playing: próxima rola da direita para a esquerda; anterior da esquerda para a direita",
                "Pacote de fontes embutidas para player e legendas",
                "Splash screen com cor e fonte aleatórias a cada abertura",
                "Submenu de logs de atualização em Sobre"
            )
        ),
        ChangelogEntry(
            "0.8",
            "142",
            listOf(
                "Daqui pra trás eu não lembro kkkkkkkkkk"
            )
        )
    )

@Composable
internal fun ChangelogPage() {
    val entries = changelogEntries
    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        items(entries) { e ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(0.08f))
                    .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text(
                    "v${e.version}  ·  code ${e.code}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Spacer(Modifier.height(8.dp))
                e.changes.forEach { change ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text("•  ", color = Color.White.copy(0.5f), fontSize = 14.sp)
                        Text(change, color = Color.White.copy(0.85f), fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

internal data class ChangelogEntry(val version: String, val code: String, val changes: List<String>)


@Composable
internal fun LicensesPage() {
    val licenses = listOf(
        "AndroidX / Jetpack Compose" to "Apache License 2.0",
        "Media3 (ExoPlayer)" to "Apache License 2.0",
        "Coil" to "Apache License 2.0",
        "jaudiotagger" to "LGPL 2.1 / Apache 2.0",
        "projectM" to "LGPL 2.1",
        "Milkdrop (preset format / community presets)" to "Public domain / author licenses",
        "Kotlin Coroutines" to "Apache License 2.0",
        "Material Icons" to "Apache License 2.0",
        "Palette" to "Apache License 2.0"
    )
    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        items(licenses) { (name, license) ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(0.08f))
                    .padding(16.dp)
            ) {
                Text(name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(license, fontSize = 13.sp, color = Color.White.copy(0.55f), modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
internal fun CreditLine(
    label: String,
    value: String,
    isLink: Boolean = false,
    onLinkClick: (() -> Unit)? = null
) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(label, fontSize = 13.sp, color = Color.White.copy(0.55f))
        if (isLink && onLinkClick != null) {
            Text(
                value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF82B1FF),
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clickable(onClick = onLinkClick)
            )
        } else {
            Text(
                value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}


