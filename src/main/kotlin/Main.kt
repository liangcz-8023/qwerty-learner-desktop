import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import components.*
import components.flatlaf.UpdateFlatLaf
import data.Caption
import data.VocabularyType
import data.loadMutableVocabulary
import dialog.*
import kotlinx.serialization.ExperimentalSerializationApi
import player.*
import state.AppState
import state.getResourcesFile
import state.rememberAppState
import theme.DarkColorScheme
import theme.LightColorScheme
import java.awt.Component
import java.awt.EventQueue
import java.awt.Rectangle
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileSystemView


@ExperimentalFoundationApi
@ExperimentalAnimationApi
@OptIn(ExperimentalComposeUiApi::class,
    ExperimentalMaterialApi::class,
    ExperimentalSerializationApi::class
)
fun main() = application {
    var isOpen by remember { mutableStateOf(true) }
    val state = rememberAppState()
    UpdateFlatLaf(state.typing.isDarkTheme, state)


    val defaultSelectionColor = Color(0xFF4286F4)
    val backgroundColor = defaultSelectionColor.copy(alpha = 0.4f)
    val textSelectionColors =
        TextSelectionColors(handleColor = defaultSelectionColor, backgroundColor = backgroundColor)
    CompositionLocalProvider(
        LocalMediaPlayerComponent provides rememberMediaPlayerComponent(),
        LocalCtrl provides rememberCtrl(),
        LocalTextSelectionColors provides textSelectionColors
    ) {
        val audioPlayerComponent = LocalMediaPlayerComponent.current
        val windowState = rememberWindowState(
            position = WindowPosition(Alignment.Center),
            placement = WindowPlacement.Maximized,
            size = DpSize(1030.dp, 862.dp),
        )

        val title = computeTitle(state)
        if (isOpen) {
            Window(
                title = title,
                icon = painterResource("logo/logo.svg"),
                state = windowState,
                onCloseRequest = {
                    isOpen = false
                    audioPlayerComponent.mediaPlayer().release()
                    state.videoPlayerComponent.mediaPlayer().release()
                },
            ) {
                MaterialTheme(colors = if (state.typing.isDarkTheme) DarkColorScheme else LightColorScheme) {
                    WindowMenuBar(state)
                    MenuDialogs(state)
                    // 视频播放器的位置，大小
                    val videoBounds = computeVideoBounds(windowState,state.openSettings)
                    Box(Modifier.background(
                        MaterialTheme.colors.background
                    ).onPreviewKeyEvent {
                        globalShortcuts(
                            it,
                            state,
                            state.videoPlayerComponent,
                            audioPlayerComponent,
                            videoBounds,
                        )
                    }
                    ) {

                        Row {
                            TypingSidebar(state)
                            if (state.openSettings) {
                                val topPadding = if(isMacOS()) 30.dp else 0.dp
                                Divider(Modifier.fillMaxHeight().width(1.dp).padding(top = topPadding))
                            }
                            Box(Modifier.fillMaxSize()) {
                                val endPadding = 0.dp
                                if(isMacOS()){
                                    Text(text = title, color = MaterialTheme.colors.onBackground,
                                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp))
                                    window.rootPane.putClientProperty("apple.awt.fullWindowContent",true)
                                    window.rootPane.putClientProperty("apple.awt.transparentTitleBar",true)
                                    window.rootPane.putClientProperty("apple.awt.windowTitleVisible",false)
                                }
                                Typing(
                                    state = state,
                                    videoBounds = videoBounds,
                                    modifier = Modifier.align(Alignment.Center)
                                        .padding(end = endPadding)
                                )
                                val speedAlignment = Alignment.TopEnd
                                Speed(
                                   speedVisible = state.typing.speedVisible,
                                    speed = state.speed,
                                    modifier = Modifier
                                        .width(IntrinsicSize.Max)
                                        .align(speedAlignment)
                                        .padding(end = endPadding)
                                )


                            }
                        }
                        Settings(state, modifier = Modifier.align(Alignment.TopStart))
                    }
                }
            }

        }
    }

}

@OptIn(ExperimentalSerializationApi::class)
private fun computeTitle(state: AppState):String {
    return  if(state.vocabulary.wordList.isNotEmpty()){
        val suffix = if (state.isDictation) {
            if (state.isReviewWrongList) {
                "复习错误单词 - ${state.dictationIndex + 1}"
            } else "默写模式 - ${state.dictationIndex + 1}"
        } else {
            "${state.typing.index + 1}"
        }
        "${state.typing.vocabularyName} - $suffix"
    }else{
        "请选择词库"
    }
}
/**
 * 菜单栏
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalSerializationApi::class)
@Composable
private fun FrameWindowScope.WindowMenuBar(state: AppState) = MenuBar {
    Menu("词库(V)", mnemonic = 'V') {
        Item("选择词库", onClick = {
            state.loadingFileChooserVisible = true
            Thread(Runnable {
                val fileChooser =  state.futureFileChooser.get()
                fileChooser.dialogTitle = "选择词库"
                fileChooser.fileSystemView = FileSystemView.getFileSystemView()
                fileChooser.currentDirectory = getResourcesFile("vocabulary")
                fileChooser.fileSelectionMode = JFileChooser.FILES_ONLY
                fileChooser.selectedFile = null
                if (fileChooser.showOpenDialog(window) == JFileChooser.APPROVE_OPTION) {
                    val file = fileChooser.selectedFile
                    state.vocabulary = loadMutableVocabulary(file.absolutePath)
                    state.typing.vocabularyName = file.nameWithoutExtension
                    state.typing.vocabularyPath = file.absolutePath
                    if (state.isDictation) {
                        state.exitDictationMode()
                        state.resetChapterTime()
                    }
                    state.typing.chapter = 1
                    state.typing.index = 0
                    state.wordCorrectTime = 0
                    state.wordWrongTime = 0
                    state.saveTypingState()
                    state.loadingFileChooserVisible = false
                } else {
                    state.loadingFileChooserVisible = false
                }
            }).start()

        }, shortcut = KeyShortcut(Key.O, ctrl = true)
        )


        Item("合并词库", onClick = {
            state.mergeVocabulary = true
        })
        Item("从文档生成词库", onClick = {
            state.generateVocabularyFromDocument = true
        })
        Item("从字幕生成词库", onClick = {
            state.generateVocabularyFromSubtitles = true
        })
        Item("从 MKV 生成词库", onClick = {
            state.generateVocabularyFromMKV = true
        })
    }
    Menu("字幕(S)",mnemonic = 'S'){
        var showLinkVocabulary by remember { mutableStateOf(false) }
        if (showLinkVocabulary) {
            LinkVocabularyDialog(
                state = state,
                close = {
                    showLinkVocabulary = false
                }
            )
        }
        //如果当前词库类型为文档就启用
        val enableLinkVocabulary = state.vocabulary.type == VocabularyType.DOCUMENT
        Item(
            "链接字幕词库",
            enabled = enableLinkVocabulary,
            onClick = { showLinkVocabulary = true },
        )
    }
    Menu("章节(C)", mnemonic = 'C') {
        Item(
            "选择章节",
            onClick = {
                state.openSelectChapter = true
            },
        )
    }
    var aboutDialogVisible by remember { mutableStateOf(false) }
    Menu("帮助(H)", mnemonic = 'H') {
        Item("检查更新(U)", mnemonic = 'U', onClick = { println("点击 检查更新") })
        Item("关于(A)", mnemonic = 'A', onClick = {aboutDialogVisible = true })
        if(aboutDialogVisible){
            AboutDialog(
                close = {aboutDialogVisible = false}
            )
        }

    }
}

/**
 * 设置
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class,
    ExperimentalSerializationApi::class
)
@Composable
fun Settings(state: AppState, modifier: Modifier) {
    Box(modifier = modifier) {
        val topPadding = if(isMacOS()) 30.dp else 0.dp
        Column (Modifier.width(IntrinsicSize.Max).padding(top = topPadding)){
            if(isMacOS()  && state.openSettings ) Divider(Modifier.fillMaxWidth())
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .width(if (state.openSettings) 217.dp else 48.dp)
                    //.padding(top = topPadding)
                    .shadow(
                        elevation =  0.dp,
                        shape = if (state.openSettings) RectangleShape else RoundedCornerShape(50)
                    )
                    .background(MaterialTheme.colors.background)
                    .clickable {
                        state.openSettings = !state.openSettings
                    }) {

                TooltipArea(
                    tooltip = {
                        Surface(
                            elevation = 4.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f)),
                            shape = RectangleShape
                        ) {
                            val ctrl = LocalCtrl.current
                            Text(text = "设置 $ctrl+1", modifier = Modifier.padding(10.dp))
                        }
                    },
                    delayMillis = 300,
                    tooltipPlacement = TooltipPlacement.ComponentRect(
                        anchor = Alignment.BottomEnd,
                        alignment = Alignment.BottomEnd,
                        offset = DpOffset.Zero
                    )
                ) {

                    Icon(
                        if (state.openSettings) Icons.Filled.ArrowBack else Icons.Filled.Tune,
                        contentDescription = "Localized description",
                        tint = MaterialTheme.colors.primary,
                        modifier = Modifier.clickable { state.openSettings = !state.openSettings }
                            .size(48.dp, 48.dp).padding(13.dp)
                    )

                }

                if (state.openSettings) {
                    Divider(Modifier.height(48.dp).width(1.dp))
                }
            }
            if(isMacOS()  && state.openSettings ) Divider(Modifier.fillMaxWidth())
        }
    }
}

val LocalCtrl = staticCompositionLocalOf<String> {
    error("LocalCtrl isn't provided")
}

/**
 * 本地的 Ctrl 键
 */
@Composable
fun rememberCtrl(): String = remember {
    if (isMacOS()) "⌃" else "Ctrl"
}

/**
 * 全局快捷键
 */
@OptIn(ExperimentalSerializationApi::class)
@ExperimentalComposeUiApi
fun globalShortcuts(
    it: KeyEvent,
    state: AppState,
    videoPlayerComponent: Component,
    audioPlayerComponent: Component,
    videoBounds: Rectangle,
): Boolean {
    return when {
        (it.isCtrlPressed && it.key == Key.A && it.type == KeyEventType.KeyUp) -> {
            state.typing.isAuto = !state.typing.isAuto
            true
        }
        (it.isCtrlPressed && it.key == Key.D && it.type == KeyEventType.KeyUp) -> {
            state.typing.isDarkTheme = !state.typing.isDarkTheme
            true
        }
        (it.isCtrlPressed && it.key == Key.P && it.type == KeyEventType.KeyUp) -> {
            state.typing.phoneticVisible = !state.typing.phoneticVisible
            true
        }
        (it.isCtrlPressed && it.key == Key.L && it.type == KeyEventType.KeyUp) -> {
            state.typing.morphologyVisible = !state.typing.morphologyVisible
            true
        }
        (it.isCtrlPressed && it.key == Key.F && it.type == KeyEventType.KeyUp) -> {
            state.typing.definitionVisible = !state.typing.definitionVisible
            true
        }
        (it.isCtrlPressed && it.key == Key.K && it.type == KeyEventType.KeyUp) -> {
            state.typing.translationVisible = !state.typing.translationVisible
            true
        }
        (it.isCtrlPressed && it.key == Key.V && it.type == KeyEventType.KeyUp) -> {
            state.typing.wordVisible = !state.typing.wordVisible
            true
        }

        (it.isCtrlPressed && it.key == Key.J && it.type == KeyEventType.KeyUp) -> {
            val word = state.getCurrentWord()
            playAudio(
                word = word.value,
                volume = state.typing.audioVolume,
                pronunciation = state.typing.pronunciation,
                mediaPlayerComponent = audioPlayerComponent,
                changePlayerState = {},
                setIsAutoPlay = {}
            )
            true
        }
        (it.isCtrlPressed && it.isShiftPressed && it.key == Key.Z && it.type == KeyEventType.KeyUp) -> {
            if (state.vocabulary.type == VocabularyType.DOCUMENT) {
                val playTriple = getPayTriple(state, 0)
                shortcutPlay(state,  playTriple, videoPlayerComponent, videoBounds)
            } else {
                val caption = state.getCurrentWord().captions[0]
                val playTriple = Triple(caption, state.vocabulary.relateVideoPath, state.vocabulary.subtitlesTrackId)
                shortcutPlay(state,  playTriple, videoPlayerComponent, videoBounds)
            }
            true
        }
        (it.isCtrlPressed &&  it.isShiftPressed && it.key == Key.X && it.type == KeyEventType.KeyUp) -> {
            if (state.getCurrentWord().externalCaptions.size >= 2) {
                val playTriple = getPayTriple(state, 1)
                shortcutPlay(state,  playTriple, videoPlayerComponent, videoBounds)

            } else if (state.getCurrentWord().captions.size >= 2) {
                val caption = state.getCurrentWord().captions[1]
                val playTriple = Triple(caption, state.vocabulary.relateVideoPath, state.vocabulary.subtitlesTrackId)
                shortcutPlay(state,  playTriple, videoPlayerComponent, videoBounds)
            }
            true
        }
        (it.isCtrlPressed &&  it.isShiftPressed && it.key == Key.C && it.type == KeyEventType.KeyUp) -> {
            if (state.getCurrentWord().externalCaptions.size >= 3) {
                val playTriple = getPayTriple(state, 2)
                shortcutPlay(state,  playTriple, videoPlayerComponent, videoBounds)
            } else if (state.getCurrentWord().captions.size >= 3) {
                val caption = state.getCurrentWord().captions[2]
                val playTriple = Triple(caption, state.vocabulary.relateVideoPath, state.vocabulary.subtitlesTrackId)
                shortcutPlay(state,  playTriple, videoPlayerComponent, videoBounds)
            }
            true
        }
        (it.isCtrlPressed && it.key == Key.S && it.type == KeyEventType.KeyUp) -> {
            state.typing.subtitlesVisible = !state.typing.subtitlesVisible
            true
        }

        (it.isCtrlPressed && it.key == Key.M && it.type == KeyEventType.KeyUp) -> {
            state.typing.isPlayKeystrokeSound = !state.typing.isPlayKeystrokeSound
            true
        }
        (it.isCtrlPressed && it.key == Key.W && it.type == KeyEventType.KeyUp) -> {
            state.typing.isPlaySoundTips = !state.typing.isPlaySoundTips
            true
        }
        (it.isCtrlPressed && it.key == Key.One && it.type == KeyEventType.KeyUp) -> {
            state.openSettings = !state.openSettings
            true
        }
        (it.isCtrlPressed && it.key == Key.N && it.type == KeyEventType.KeyUp) -> {
            state.typing.speedVisible = !state.typing.speedVisible
            true
        }
        (it.isCtrlPressed && it.isShiftPressed && it.key == Key.Spacebar && it.type == KeyEventType.KeyUp) -> {
            reset(state.speed)
            true
        }

        (it.isCtrlPressed && it.key == Key.Spacebar && it.type == KeyEventType.KeyUp) -> {
            startTimer(state.speed)
            true
        }

        else -> false
    }
}

/**
 * 用快捷键播放视频
 * @param state 应用程序的状态
 * @param playTriple 视频播放参数，Caption 表示要播放的字幕，String 表示视频的地址，Int 表示字幕的轨道 ID。
 * @param mediaPlayerComponent 视频播放组件
 * @param videoBounds 视频播放器的位置和大小
 */
@OptIn(ExperimentalSerializationApi::class)
private fun shortcutPlay(
    state: AppState,
    playTriple: Triple<Caption, String, Int>?,
    mediaPlayerComponent: Component,
    videoBounds: Rectangle
) {
    if (playTriple != null) {
        if (!state.isPlaying) {
            val file = File(playTriple.second)
            if (file.exists()) {
                state.isPlaying = true
                Thread(Runnable {
                    EventQueue.invokeLater {
                        play(
                            window = state.videoPlayerWindow,
                            setIsPlaying = { state.isPlaying = it },
                            state.typing.videoVolume,
                            playTriple,
                            mediaPlayerComponent,
                            videoBounds
                        )
                    }

                }).start()
            }
        } else {
            println("通知用户，视频地址错误")
        }
    }
}

/**
 * 对话框
 */
@ExperimentalFoundationApi
@OptIn(ExperimentalMaterialApi::class, ExperimentalSerializationApi::class)
@ExperimentalComposeUiApi
@Composable
fun MenuDialogs(state: AppState) {
    if (state.openSelectChapter) {
        SelectChapterDialog(state)
    }

    if(state.loadingFileChooserVisible){
        LoadingDialog()
    }
    if(state.mergeVocabulary){
        MergeVocabularyDialog(
            futureFileChooser = state.futureFileChooser,
            close = {state.mergeVocabulary = false})
    }
    if (state.generateVocabularyFromDocument) {
        GenerateVocabularyDialog(
            state = state,
            title = "从文档生成词库",
            type = VocabularyType.DOCUMENT
        )
    }
    if (state.generateVocabularyFromSubtitles) {
        GenerateVocabularyDialog(
            state = state,
            title = "从字幕生成词库",
            type = VocabularyType.SUBTITLES
        )
    }

    if (state.generateVocabularyFromMKV) {
        GenerateVocabularyDialog(
            state = state,
            title = "从 MKV 生成词库",
            type = VocabularyType.MKV
        )
    }
}


/**
 * 等待窗口
 */
@Composable
fun LoadingDialog() {
    Dialog(
        title = "正在加载文件选择器",
        icon = painterResource("logo/logo.svg"),
        onCloseRequest = {},
        undecorated = true,
        resizable = false,
        state = rememberDialogState(
            position = WindowPosition(Alignment.Center),
            size = DpSize(300.dp,300.dp)
        ),
    ) {
        Surface(
            elevation = 5.dp,
            shape = RectangleShape,
            border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f)),
        ) {
            Box(Modifier.width(300.dp).height(300.dp)) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }
}
