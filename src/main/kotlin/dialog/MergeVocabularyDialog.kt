package dialog

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import data.*
import java.io.File
import java.util.concurrent.FutureTask
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.filechooser.FileSystemView

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MergeVocabularyDialog(
    futureFileChooser: FutureTask<JFileChooser>,
    close: () -> Unit){
    Dialog(
        title = "合并词库",
        icon = painterResource("logo/logo.svg"),
        onCloseRequest = {close()},
        resizable = false,
        state = rememberDialogState(
            position = WindowPosition(Alignment.Center),
            size = DpSize(600.dp,600.dp)
        ),
    ) {
        Surface(
            elevation = 5.dp,
            shape = RectangleShape,
            border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f)),
        ) {

            Box{
                var merging by remember { mutableStateOf(false) }
                Column (verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize()){
                    var mergeEnabled by remember{ mutableStateOf(false)}
                    var selectedFileList = remember { mutableStateListOf<File>() }
                    var newVocabulary by remember { mutableStateOf<Vocabulary?>(null) }
                    var isOutOfRange by remember { mutableStateOf(false) }
                    var size by remember{ mutableStateOf(0)}
                    var fileName by remember{ mutableStateOf("")}
                    val updateSize :(Int)->Unit = {
                        size = it
                    }
                    val updateFileName :(String) -> Unit = {
                        fileName = it
                    }
                    if(!merging){
                        val height = if(selectedFileList.size<9) (selectedFileList.size * 48+10).dp else 450.dp
                        Box(Modifier.fillMaxWidth().height(height)){
                            val stateVertical = rememberScrollState(0)
                            Column (verticalArrangement = Arrangement.Top,
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.align(Alignment.TopCenter)
                                    .fillMaxSize()
                                    .verticalScroll(stateVertical)){
                                selectedFileList.forEach { file ->
                                    Row(horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()){
                                        Text(text = file.nameWithoutExtension,
                                            modifier = Modifier.width(420.dp))
                                        IconButton(onClick = {
                                            updateSize(0)
                                            selectedFileList.remove(file)
                                            mergeEnabled = selectedFileList.size>1
                                        }){
                                            Icon( Icons.Filled.Close, contentDescription = "",tint = MaterialTheme.colors.primary)
                                        }
                                    }
                                    Divider(Modifier.width(468.dp))
                                }
                            }
                            if(selectedFileList.size>=9){
                                VerticalScrollbar(
                                    style = LocalScrollbarStyle.current.copy(shape = RectangleShape),
                                    modifier = Modifier.align(Alignment.CenterEnd)
                                        .fillMaxHeight(),
                                    adapter = rememberScrollbarAdapter(stateVertical)
                                )
                            }

                        }

                    }
                    if(isOutOfRange){
                        Text(text = "词库数量不能超过100个",
                            color = Color.Red,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }

                    if(merging){
                        Row(horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()){
                            Text(text = "正在读取 $fileName")
                        }
                    }

                    if(size>0){
                        Row(horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp,top = 10.dp)){
                            Text(text = "总计：")
                            Text(text = "$size",color = MaterialTheme.colors.primary)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()){
                        OutlinedButton(onClick = {
                            Thread(Runnable {
                                val fileChooser = futureFileChooser.get()
                                fileChooser.dialogTitle = "选择词库"
                                fileChooser.fileSystemView = FileSystemView.getFileSystemView()
                                fileChooser.fileSelectionMode = JFileChooser.FILES_ONLY
                                fileChooser.isAcceptAllFileFilterUsed = false
                                fileChooser.isMultiSelectionEnabled = true
                                val fileFilter = FileNameExtensionFilter("词库", "json")
                                fileChooser.addChoosableFileFilter(fileFilter)
                                fileChooser.selectedFile = null
                                if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                    if(fileChooser.selectedFiles.size<101){
                                        isOutOfRange = false
                                        fileChooser.selectedFiles.forEach { file ->
                                            if (!selectedFileList.contains(file)) {
                                                selectedFileList.add(file)
                                            }
                                        }
                                    }else{
                                        isOutOfRange = true
                                    }
                                    mergeEnabled = fileChooser.selectedFiles.isNotEmpty()
                                    if(fileChooser.selectedFiles.isNotEmpty()){
                                        updateSize(0)
                                    }
                                }
                                fileChooser.selectedFile = null
                                fileChooser.isMultiSelectionEnabled = false
                                fileChooser.removeChoosableFileFilter(fileFilter)
                            }).start()
                        },modifier = Modifier.padding(end = 10.dp)){
                            Text("添加词库")
                        }

                        OutlinedButton(
                            enabled = mergeEnabled,
                            onClick = {
                                Thread(Runnable {
                                    merging = true
                                    newVocabulary = Vocabulary(
                                        name = "",
                                        type = VocabularyType.DOCUMENT,
                                        language = "english",
                                        size = 0,
                                        relateVideoPath = "",
                                        subtitlesTrackId = 0,
                                        wordList = mutableListOf()
                                    )
                                    val wordList = mutableListOf<Word>()
                                    selectedFileList.forEach { file ->
                                        updateFileName(file.nameWithoutExtension)
                                        val vocabulary = loadVocabulary(file.absolutePath)
                                        vocabulary.wordList.forEach { word ->
                                            val index = wordList.indexOf(word)
                                            // wordList 没有这个单词
                                            if (index == -1) {
                                                // 如果是视频词库或字幕词库，需要把字幕变成外部字幕
                                                if(word.captions.isNotEmpty()){
                                                    word.captions.forEach { caption ->
                                                        // 创建一条外部字幕
                                                        val externalCaption = ExternalCaption(
                                                            relateVideoPath = vocabulary.relateVideoPath,
                                                            subtitlesTrackId = vocabulary.subtitlesTrackId,
                                                            subtitlesName = vocabulary.name,
                                                            start = caption.start,
                                                            end = caption.end,
                                                            content = caption.content
                                                        )
                                                        word.externalCaptions.add(externalCaption)
                                                    }
                                                    word.captions.clear()
                                                }
                                                wordList.add(word)
                                                // wordList 有这个单词
                                            }else{
                                                val oldWord = wordList[index]
                                                // 如果单词有外部字幕，同时已经加入到列表的单词的外部字幕没有超过3个就导入
                                                if(word.externalCaptions.isNotEmpty()){
                                                    word.externalCaptions.forEach { externalCaption ->
                                                        if(oldWord.externalCaptions.size<3){
                                                            oldWord.externalCaptions.add(externalCaption)
                                                        }
                                                    }
                                                // 如果单词是视频或字幕词库中的单词
                                                }else if(word.captions.isNotEmpty()){
                                                    word.captions.forEach { caption ->
                                                        // 创建一条外部字幕
                                                        val externalCaption = ExternalCaption(
                                                            relateVideoPath = vocabulary.relateVideoPath,
                                                            subtitlesTrackId = vocabulary.subtitlesTrackId,
                                                            subtitlesName = vocabulary.name,
                                                            start = caption.start,
                                                            end = caption.end,
                                                            content = caption.content
                                                        )
                                                        if(oldWord.externalCaptions.size<3){
                                                            oldWord.externalCaptions.add(externalCaption)
                                                        }
                                                    }
                                                }

                                            }
                                        }
                                        updateSize(wordList.size)
                                    }
                                    newVocabulary!!.wordList = wordList
                                    newVocabulary!!.size = wordList.size
                                    merging = false
                                    mergeEnabled = false
                                }).start()
                            },modifier = Modifier.padding(end = 10.dp)){
                            Text("合并词库")
                        }
                        OutlinedButton(
                            enabled = !merging && size>0,
                            onClick = {
                                Thread(Runnable {
                                    val fileChooser = futureFileChooser.get()
                                    fileChooser.dialogType = JFileChooser.SAVE_DIALOG
                                    fileChooser.dialogTitle = "保存词库"
                                    val myDocuments = FileSystemView.getFileSystemView().defaultDirectory.path
                                    fileChooser.selectedFile = File("$myDocuments${File.separator}*.json")
                                    val userSelection = fileChooser.showSaveDialog(window)
                                    if (userSelection == JFileChooser.APPROVE_OPTION) {
                                        val fileToSave = fileChooser.selectedFile
                                        if(newVocabulary != null){
                                            newVocabulary!!.name = fileToSave.nameWithoutExtension
                                            saveVocabulary(newVocabulary!!, fileToSave.absolutePath)
                                            saveToRecentList(fileToSave.nameWithoutExtension, fileToSave.absolutePath)
                                        }
                                        newVocabulary = null
                                        fileChooser.selectedFile = null
                                        close()
                                    }

                                }).start()
                            }){
                            Text("保存词库")
                        }
                    }

                }

                if(merging){
                    CircularProgressIndicator(Modifier.align(Alignment.Center).padding(bottom = 120.dp))
                }
            }

        }
    }
}