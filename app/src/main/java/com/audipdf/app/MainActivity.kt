
package com.audipdf.app

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private val bg = Color.rgb(10, 12, 11)
    private val panel = Color.rgb(23, 27, 24)
    private val green = Color.rgb(50, 220, 120)
    private val white = Color.rgb(245, 248, 246)
    private val muted = Color.rgb(155, 166, 158)
    private val highlight = Color.rgb(35, 83, 53)

    private val executor = Executors.newSingleThreadExecutor()

    private lateinit var homeContent: LinearLayout
    private lateinit var libraryContent: LinearLayout
    private lateinit var bookList: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var fileNameText: TextView
    private lateinit var contentText: TextView
    private lateinit var importButton: Button
    private lateinit var playButton: Button
    private lateinit var seekBar: SeekBar
    private lateinit var seekText: TextView
    private lateinit var speedText: TextView
    private lateinit var voiceButton: Button
    private lateinit var readingIndicator: TextView
    private lateinit var folderStatus: TextView
    private lateinit var librarySummary: TextView
    private lateinit var homeTab: Button
    private lateinit var libraryTab: Button

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var isPlaying = false
    private var isPaused = false

    private var documentText = ""
    private var chunks = listOf<SpeechChunk>()
    private var currentChunk = 0
    private var currentCharOffset = 0
    private var utteranceBaseOffset = 0
    private var activeUtteranceId = ""
    private var sessionId = 0

    private var speechRate = 1.0f
    private val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    private var speedIndex = 2
    private var voices = listOf<android.speech.tts.Voice>()
    private var voiceIndex = 0

    private var treeUri: Uri? = null
    private var rootFolderUri: Uri? = null
    private var pdfFolderUri: Uri? = null

    private var currentBookFile = ""
    private var currentBookName = ""
    private var lastSavedPercent = -1
    private var savedBooks = mutableListOf<BookProgress>()

    private data class SpeechChunk(
        val text: String,
        val start: Int,
        val end: Int
    )

    private data class BookProgress(
        val fileName: String,
        val displayName: String,
        val charOffset: Int,
        val percent: Int,
        val chunkIndex: Int
    )

    private data class StoredDocument(
        val name: String,
        val displayName: String,
        val uri: Uri
    )

    private val pdfPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importPdf(uri)
        }

    private val folderPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    treeUri = uri
                    rootFolderUri = DocumentsContract.buildDocumentUriUsingTree(
                        uri,
                        DocumentsContract.getTreeDocumentId(uri)
                    )
                    ensurePdfFolder()
                } catch (e: Exception) {
                    toast("Não foi possível acessar essa pasta.")
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg

        PDFBoxResourceLoader.init(applicationContext)
        buildInterface()
        initializeVoice()
    }

    private fun buildInterface() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(bg)
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(30))
            setBackgroundColor(bg)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.audipdf_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Logo AudiPDF"
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(54), dp(54)))

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        brand.addView(makeText("AudiPDF", 25f, white, true))
        brand.addView(makeText("SUA BIBLIOTECA EM ÁUDIO", 10f, green, true))
        header.addView(brand)
        root.addView(header)

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(22), 0, dp(8))
        }

        homeTab = makeButton("⌂  Início", green, Color.rgb(8, 20, 12)) {
            showTab(false)
        }
        libraryTab = makeButton("▤  Biblioteca", panel, white) {
            showTab(true)
            refreshLibrary()
        }

        tabs.addView(homeTab, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
            marginEnd = dp(8)
        })
        tabs.addView(libraryTab, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(tabs)

        homeContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        libraryContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        buildHome()
        buildLibrary()

        root.addView(homeContent)
        root.addView(libraryContent)

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun buildHome() {
        val hero = makePanel()
        hero.addView(makeText("ESCUTE. APRENDA. EVOLUA.", 10f, green, true))
        hero.addView(makeText("Seu PDF. Sua experiência de áudio.", 25f, white, true).apply {
            setPadding(0, dp(10), 0, dp(10))
        })
        hero.addView(makeText(
            "Transforme documentos em uma experiência de leitura em voz alta.",
            14f, muted
        ))
        homeContent.addView(hero, topMargin(18))

        homeContent.addView(sectionTitle("Documento atual"))

        val documentPanel = makePanel()
        fileNameText = makeText("Nenhum PDF selecionado", 17f, white, true)
        statusText = makeText("Configure a pasta para salvar sua biblioteca.", 13f, muted)

        documentPanel.addView(fileNameText)
        documentPanel.addView(statusText.apply {
            setPadding(0, dp(8), 0, 0)
        })

        val folderButton = makeButton("▣  Escolher pasta de salvamento", panel, white) {
            folderPicker.launch(null)
        }
        documentPanel.addView(folderButton, topMargin(14))

        folderStatus = makeText(
            "Escolha Documentos/AudiPDF ou crie uma pasta AudiPDF.",
            12f, muted
        ).apply { setPadding(0, dp(8), 0, 0) }
        documentPanel.addView(folderStatus)

        importButton = makeButton("＋  Importar PDF", green, Color.rgb(8, 20, 12)) {
            if (pdfFolderUri == null) {
                toast("Escolha primeiro a pasta de salvamento.")
                folderPicker.launch(null)
            } else {
                pdfPicker.launch(arrayOf("application/pdf"))
            }
        }
        documentPanel.addView(importButton, topMargin(14))
        homeContent.addView(documentPanel)

        homeContent.addView(sectionTitle("Player AudiPDF"))

        val player = makePanel()
        player.addView(makeText("Leitura narrada", 20f, white, true))
        player.addView(makeText(
            "Acompanhe o texto enquanto a voz faz a leitura.",
            12f, muted
        ).apply { setPadding(0, dp(6), 0, 0) })

        seekBar = SeekBar(this).apply {
            max = 1
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(green)
            thumbTintList = android.content.res.ColorStateList.valueOf(green)
            isEnabled = false
        }
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser && chunks.isNotEmpty()) {
                    seekText.text = "Trecho ${p + 1} de ${chunks.size}"
                }
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}

            override fun onStopTrackingTouch(s: SeekBar?) {
                if (chunks.isEmpty()) return
                val resume = isPlaying
                stopSpeech()
                currentChunk = seekBar.progress.coerceIn(0, chunks.lastIndex)
                currentCharOffset = 0
                updateProgress()
                clearHighlight()
                saveCurrentProgress()
                if (resume) startSpeech()
            }
        })
        player.addView(seekBar, topMargin(12))

        seekText = makeText("Nenhum trecho carregado", 12f, muted)
        player.addView(seekText)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(8))
        }
        controls.addView(makeButton("−1 trecho", panel, white) {
            moveChunk(-1)
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
            marginEnd = dp(5)
        })

        playButton = makeButton("▶  Play", green, Color.rgb(8, 20, 12)) {
            togglePlayback()
        }
        controls.addView(playButton, LinearLayout.LayoutParams(0, dp(50), 1.4f).apply {
            marginEnd = dp(5)
        })

        controls.addView(makeButton("+1 trecho", panel, white) {
            moveChunk(1)
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        player.addView(controls)

        player.addView(makeText("VELOCIDADE", 10f, green, true).apply {
            setPadding(0, dp(12), 0, dp(6))
        })
        speedText = makeText("1,0×", 16f, white, true)
        player.addView(speedText)

        val speedControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        speedControls.addView(makeButton("−", panel, white) {
            changeSpeed(-1)
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
            marginEnd = dp(8)
        })
        speedControls.addView(makeButton("+", panel, white) {
            changeSpeed(1)
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
        player.addView(speedControls, topMargin(8))

        voiceButton = makeButton("Voz: carregando...", panel, white) {
            changeVoice()
        }
        player.addView(voiceButton, topMargin(10))
        homeContent.addView(player)

        homeContent.addView(sectionTitle("Leitura do documento"))

        val reading = makePanel()
        readingIndicator = makeText("■  PRONTO PARA LER", 11f, green, true).apply {
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = rounded(Color.rgb(20, 48, 31), 8)
        }
        reading.addView(readingIndicator, bottomMargin(14))

        contentText = makeText(
            "Importe um PDF para visualizar o texto organizado aqui.",
            15f, muted
        ).apply {
            setTextIsSelectable(true)
            setLineSpacing(dp(7).toFloat(), 1.08f)
        }
        reading.addView(contentText)
        homeContent.addView(reading)

        homeContent.addView(makeText(
            "FOCO  •  CONHECIMENTO  •  LIBERDADE", 10f, muted, true
        ).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, 0)
        })
    }

    private fun buildLibrary() {
        libraryContent.addView(makeText("Sua biblioteca", 27f, white, true).apply {
            setPadding(0, dp(22), 0, dp(6))
        })
        libraryContent.addView(makeText(
            "Seus documentos e o progresso salvo neste aparelho.",
            13f, muted
        ).apply { setPadding(0, 0, 0, dp(16)) })

        librarySummary = makeText("0 documentos", 12f, green, true)
        libraryContent.addView(librarySummary, bottomMargin(12))

        bookList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        libraryContent.addView(bookList)

        libraryContent.addView(makeButton("Escolher / recuperar pasta", panel, white) {
            folderPicker.launch(null)
        }, topMargin(12))
    }

    private fun showTab(library: Boolean) {
        homeContent.visibility = if (library) View.GONE else View.VISIBLE
        libraryContent.visibility = if (library) View.VISIBLE else View.GONE

        homeTab.background = rounded(
            if (library) panel else green, 14
        )
        homeTab.setTextColor(if (library) white else Color.rgb(8, 20, 12))

        libraryTab.background = rounded(
            if (library) green else panel, 14
        )
        libraryTab.setTextColor(if (library) Color.rgb(8, 20, 12) else white)
    }

    private fun initializeVoice() {
        tts = TextToSpeech(this) { result ->
            runOnUiThread {
                if (result != TextToSpeech.SUCCESS) {
                    ttsReady = false
                    voiceButton.text = "Voz indisponível"
                    statusText.text = "Não foi possível iniciar a voz do Android."
                    return@runOnUiThread
                }

                val engine = tts ?: return@runOnUiThread
                val language = engine.setLanguage(Locale("pt", "BR"))
                if (language == TextToSpeech.LANG_MISSING_DATA ||
                    language == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    engine.setLanguage(Locale.getDefault())
                    toast("Se necessário, instale uma voz em português nas configurações do Android.")
                }

                voices = engine.voices
                    ?.filter { it.locale.language == "pt" }
                    ?.sortedBy { it.name }
                    ?: emptyList()

                if (voices.isNotEmpty()) {
                    val br = voices.indexOfFirst {
                        it.locale.country.equals("BR", true)
                    }
                    voiceIndex = if (br >= 0) br else 0
                    engine.voice = voices[voiceIndex]
                    updateVoiceButton()
                } else {
                    voiceButton.text = "Voz padrão do Android"
                }

                engine.setSpeechRate(speechRate)
                engine.setOnUtteranceProgressListener(
                    object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}

                        override fun onRangeStart(
                            utteranceId: String?,
                            start: Int,
                            end: Int,
                            frame: Int
                        ) {
                            runOnUiThread {
                                if (utteranceId != activeUtteranceId ||
                                    currentChunk !in chunks.indices
                                ) return@runOnUiThread

                                val chunk = chunks[currentChunk]
                                val a = (chunk.start + utteranceBaseOffset + start)
                                    .coerceIn(0, documentText.length)
                                val b = (chunk.start + utteranceBaseOffset + end)
                                    .coerceIn(a, documentText.length)

                                currentCharOffset = (utteranceBaseOffset + end)
                                    .coerceIn(0, chunk.text.length)

                                highlightPosition(a, b)
                                saveCurrentProgress()
                            }
                        }

                        override fun onDone(utteranceId: String?) {
                            runOnUiThread {
                                if (utteranceId != activeUtteranceId || !isPlaying) {
                                    return@runOnUiThread
                                }

                                currentChunk++
                                currentCharOffset = 0

                                if (currentChunk >= chunks.size) {
                                    currentChunk = chunks.size
                                    isPlaying = false
                                    isPaused = false
                                    playButton.text = "▶  Play"
                                    statusText.text = "Leitura concluída."
                                    readingIndicator.text = "■  LEITURA CONCLUÍDA"
                                    updateProgress()
                                    saveCurrentProgress()
                                } else {
                                    updateProgress()
                                    saveCurrentProgress()
                                    speakCurrentChunk()
                                }
                            }
                        }

                        override fun onError(utteranceId: String?) {
                            runOnUiThread {
                                if (utteranceId == activeUtteranceId) {
                                    isPlaying = false
                                    playButton.text = "▶  Play"
                                    statusText.text = "Erro durante a narração."
                                    readingIndicator.text = "■  ERRO NA NARRAÇÃO"
                                }
                            }
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            onError(utteranceId)
                        }
                    }
                )

                ttsReady = true
                if (treeUri == null) {
                    statusText.text = "Escolha a pasta de salvamento para começar."
                } else {
                    statusText.text = "Voz pronta. Importe um PDF para começar."
                }
            }
        }
    }

    private fun ensurePdfFolder() {
        val root = rootFolderUri ?: return
        executor.execute {
            try {
                val existing = listChildren(root).firstOrNull {
                    it.displayName == "PDFs" &&
                        it.mimeType == DocumentsContract.Document.MIME_TYPE_DIR
                }

                val folder = existing?.uri ?: DocumentsContract.createDocument(
                    contentResolver,
                    root,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    "PDFs"
                )

                runOnUiThread {
                    pdfFolderUri = folder
                    folderStatus.text = "Pasta vinculada. PDFs e progresso serão salvos nela."
                    statusText.text = "Pasta pronta. Importe um PDF."
                    refreshLibrary()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    folderStatus.text = "Não foi possível abrir a pasta. Tente selecioná-la novamente."
                    toast("Erro ao preparar a pasta AudiPDF.")
                }
            }
        }
    }

    private data class ChildInfo(
        val displayName: String,
        val mimeType: String,
        val uri: Uri
    )

    private fun listChildren(parent: Uri): List<ChildInfo> {
        val tree = treeUri ?: return emptyList()
        val parentId = DocumentsContract.getDocumentId(parent)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val result = mutableListOf<ChildInfo>()

        contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null, null, null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID
            )
            val nameCol = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            )
            val mimeCol = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )

            while (cursor.moveToNext()) {
                val id = cursor.getString(idCol)
                val name = cursor.getString(nameCol) ?: "Documento"
                val mime = cursor.getString(mimeCol) ?: ""
                val childUri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                result.add(ChildInfo(name, mime, childUri))
            }
        }
        return result
    }

    private fun importPdf(sourceUri: Uri) {
        val pdfFolder = pdfFolderUri
        if (pdfFolder == null) {
            toast("Escolha primeiro a pasta de salvamento.")
            return
        }

        stopSpeech()
        importButton.isEnabled = false
        statusText.text = "Copiando e lendo o PDF..."
        contentText.text = "Preparando o documento..."
        contentText.setTextColor(muted)

        val originalName = getFileName(sourceUri)
        executor.execute {
            try {
                val safeName = makeUniquePdfName(originalName)
                val savedUri = DocumentsContract.createDocument(
                    contentResolver,
                    pdfFolder,
                    "application/pdf",
                    safeName
                ) ?: throw IllegalStateException("Não foi possível criar a cópia do PDF.")

                contentResolver.openInputStream(sourceUri).use { input ->
                    if (input == null) throw IllegalStateException("Não foi possível abrir o PDF.")
                    contentResolver.openOutputStream(savedUri, "w").use { output ->
                        if (output == null) throw IllegalStateException("Não foi possível salvar o PDF.")
                        input.copyTo(output)
                    }
                }

                val extracted = contentResolver.openInputStream(savedUri).use { input ->
                    if (input == null) throw IllegalStateException("Não foi possível reler a cópia.")
                    PDDocument.load(input).use { doc -> PDFTextStripper().getText(doc) }
                }

                val organized = formatDocumentText(extracted)
                runOnUiThread {
                    importButton.isEnabled = true
                    currentBookFile = safeName
                    currentBookName = originalName
                    documentText = organized
                    currentChunk = 0
                    currentCharOffset = 0
                    lastSavedPercent = -1

                    if (documentText.isBlank()) {
                        chunks = emptyList()
                        contentText.text =
                            "Nenhum texto foi encontrado. O PDF pode conter páginas digitalizadas como imagens; OCR ainda não está disponível."
                        contentText.setTextColor(muted)
                        statusText.text = "PDF copiado, mas sem texto extraível."
                        readingIndicator.text = "■  SEM TEXTO DISPONÍVEL"
                    } else {
                        chunks = splitIntoChunks(documentText)
                        contentText.text = documentText
                        contentText.setTextColor(white)
                        fileNameText.text = originalName
                        statusText.text = "PDF pronto • ${documentText.split(Regex("\\s+")).size} palavras"
                        readingIndicator.text = "■  PRONTO PARA LER"
                        updateProgress()
                        clearHighlight()
                        saveCurrentProgress()
                    }
                    refreshLibrary()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    importButton.isEnabled = true
                    statusText.text = "Não foi possível importar este PDF."
                    contentText.text =
                        "Verifique o arquivo e o espaço disponível.\n\n${e.localizedMessage ?: "Erro desconhecido"}"
                    contentText.setTextColor(muted)
                    readingIndicator.text = "■  ERRO AO ABRIR PDF"
                }
            }
        }
    }

    private fun makeUniquePdfName(original: String): String {
        val cleaned = original
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .ifBlank { "documento.pdf" }
        return "${System.currentTimeMillis()}_$cleaned"
    }

    private fun refreshLibrary() {
        val root = rootFolderUri
        val pdfFolder = pdfFolderUri
        if (root == null || pdfFolder == null) {
            if (::bookList.isInitialized) {
                bookList.removeAllViews()
                bookList.addView(makeText(
                    "Escolha a pasta de salvamento para ver os documentos guardados.",
                    14f, muted
                ))
                librarySummary.text = "Pasta não selecionada"
            }
            return
        }

        executor.execute {
            try {
                val progress = readProgressFile(root)
                val pdfs = listChildren(pdfFolder).filter {
                    it.mimeType == "application/pdf" ||
                        it.displayName.endsWith(".pdf", true)
                }

                val progressByFile = progress.associateBy { it.fileName }
                val combined = pdfs.map { pdf ->
                    progressByFile[pdf.displayName] ?: BookProgress(
                        fileName = pdf.displayName,
                        displayName = pdf.displayName.substringAfter('_', pdf.displayName),
                        charOffset = 0,
                        percent = 0,
                        chunkIndex = 0
                    )
                }.toMutableList()

                runOnUiThread {
                    savedBooks = combined
                    renderLibrary(pdfs)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    bookList.removeAllViews()
                    bookList.addView(makeText(
                        "Não foi possível ler a biblioteca. Verifique se a pasta continua acessível.",
                        14f, muted
                    ))
                }
            }
        }
    }

    private fun renderLibrary(documents: List<ChildInfo>) {
        bookList.removeAllViews()
        librarySummary.text =
            "${documents.size} documento(s) • progresso salvo no aparelho"

        if (documents.isEmpty()) {
            val empty = makePanel()
            empty.addView(makeText("Sua biblioteca está vazia", 18f, white, true))
            empty.addView(makeText(
                "Importe um PDF pela aba Início. Uma cópia será guardada na pasta escolhida.",
                13f, muted
            ).apply { setPadding(0, dp(8), 0, 0) })
            bookList.addView(empty)
            return
        }

        documents.forEach { doc ->
            val progress = savedBooks.firstOrNull { it.fileName == doc.displayName }
            val percent = progress?.percent ?: 0

            val card = makePanel()
            card.addView(makeText(
                progress?.displayName ?: doc.displayName,
                16f, white, true
            ))
            card.addView(makeText(
                if (percent >= 100) "Concluído" else "$percent% lido",
                12f, green, true
            ).apply { setPadding(0, dp(8), 0, dp(6)) })

            val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                this.progress = percent.coerceIn(0, 100)
                progressTintList = android.content.res.ColorStateList.valueOf(green)
                progressBackgroundTintList =
                    android.content.res.ColorStateList.valueOf(Color.rgb(48, 55, 49))
            }
            card.addView(bar, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(6)
            ))

            card.addView(makeButton(
                if (percent > 0 && percent < 100) "▶  Continuar lendo" else
                    if (percent >= 100) "↻  Ler novamente" else "▶  Abrir documento",
                green, Color.rgb(8, 20, 12)
            ) {
                openSavedBook(doc, progress)
            }, topMargin(12))

            bookList.addView(card, bottomMargin(12))
        }
    }

    private fun openSavedBook(doc: ChildInfo, progress: BookProgress?) {
        stopSpeech()
        statusText.text = "Abrindo documento salvo..."
        showTab(false)

        executor.execute {
            try {
                val extracted = contentResolver.openInputStream(doc.uri).use { input ->
                    if (input == null) throw IllegalStateException("Não foi possível abrir o PDF salvo.")
                    PDDocument.load(input).use { pdf -> PDFTextStripper().getText(pdf) }
                }
                val organized = formatDocumentText(extracted)

                runOnUiThread {
                    currentBookFile = doc.displayName
                    currentBookName = progress?.displayName
                        ?: doc.displayName.substringAfter('_', doc.displayName)
                    documentText = organized
                    chunks = splitIntoChunks(documentText)

                    val savedOffset = (progress?.charOffset ?: 0)
                        .coerceIn(0, documentText.length)
                    currentChunk = chunks.indexOfFirst {
                        savedOffset >= it.start && savedOffset < it.end
                    }.let { if (it < 0) 0 else it }
                    currentCharOffset = if (chunks.isNotEmpty()) {
                        (savedOffset - chunks[currentChunk].start)
                            .coerceIn(0, chunks[currentChunk].text.length)
                    } else 0

                    fileNameText.text = currentBookName
                    contentText.text = documentText
                    contentText.setTextColor(white)
                    statusText.text = "Documento recuperado da biblioteca."
                    readingIndicator.text = "■  PRONTO PARA CONTINUAR"
                    updateProgress()
                    clearHighlight()
                    saveCurrentProgress()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = "Não foi possível abrir este documento."
                    toast("Confira o acesso à pasta AudiPDF.")
                }
            }
        }
    }

    private fun saveCurrentProgress() {
        if (currentBookFile.isBlank() || documentText.isBlank() ||
            rootFolderUri == null
        ) return

        val absolute = if (currentChunk >= chunks.size) {
            documentText.length
        } else if (currentChunk in chunks.indices) {
            (chunks[currentChunk].start + currentCharOffset)
                .coerceIn(0, documentText.length)
        } else 0

        val percent = if (documentText.isEmpty()) 0 else
            ((absolute.toDouble() / documentText.length) * 100)
                .toInt().coerceIn(0, 100)

        if (percent == lastSavedPercent) return
        lastSavedPercent = percent

        val entry = BookProgress(
            fileName = currentBookFile,
            displayName = currentBookName,
            charOffset = absolute,
            percent = percent,
            chunkIndex = currentChunk
        )

        savedBooks.removeAll { it.fileName == entry.fileName }
        savedBooks.add(entry)

        val root = rootFolderUri ?: return
        val snapshot = savedBooks.toList()

        executor.execute {
            try {
                writeProgressFile(root, snapshot)
            } catch (_: Exception) {
                // O progresso continua em memória; uma próxima gravação tentará novamente.
            }
        }
    }

    private fun readProgressFile(root: Uri): MutableList<BookProgress> {
        val file = listChildren(root).firstOrNull {
            it.displayName == "progresso.json"
        } ?: return mutableListOf()

        val text = contentResolver.openInputStream(file.uri)?.bufferedReader()?.use {
            it.readText()
        } ?: return mutableListOf()

        val array = JSONArray(text)
        val result = mutableListOf<BookProgress>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            result.add(
                BookProgress(
                    fileName = item.optString("fileName"),
                    displayName = item.optString("displayName"),
                    charOffset = item.optInt("charOffset", 0),
                    percent = item.optInt("percent", 0).coerceIn(0, 100),
                    chunkIndex = item.optInt("chunkIndex", 0)
                )
            )
        }
        return result
    }

    private fun writeProgressFile(root: Uri, entries: List<BookProgress>) {
        val existing = listChildren(root).firstOrNull {
            it.displayName == "progresso.json"
        }

        val fileUri = existing?.uri ?: DocumentsContract.createDocument(
            contentResolver, root, "application/json", "progresso.json"
        ) ?: throw IllegalStateException("Não foi possível criar progresso.json")

        val array = JSONArray()
        entries.forEach { book ->
            array.put(JSONObject().apply {
                put("fileName", book.fileName)
                put("displayName", book.displayName)
                put("charOffset", book.charOffset)
                put("percent", book.percent)
                put("chunkIndex", book.chunkIndex)
            })
        }

        contentResolver.openOutputStream(fileUri, "wt").use { output ->
            if (output == null) throw IllegalStateException("Não foi possível salvar o progresso.")
            output.write(array.toString(2).toByteArray(Charsets.UTF_8))
        }
    }

    private fun formatDocumentText(raw: String): String {
        val normalized = raw
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace('\u0000', ' ')

        val output = StringBuilder()
        val paragraph = StringBuilder()

        fun flushParagraph() {
            val value = paragraph.toString().trim()
            if (value.isNotEmpty()) {
                if (output.isNotEmpty()) output.append("\n\n")
                output.append(value)
            }
            paragraph.clear()
        }

        normalized.lines().forEach { rawLine ->
            val line = rawLine.replace(Regex("[\\t ]+"), " ").trim()
            if (line.isEmpty()) {
                flushParagraph()
            } else {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(line)
            }
        }

        flushParagraph()
        return output.toString().trim()
    }

    private fun splitIntoChunks(text: String): List<SpeechChunk> {
        val result = mutableListOf<SpeechChunk>()
        val maxLength = 1000
        var start = 0

        while (start < text.length) {
            while (start < text.length && text[start].isWhitespace()) start++
            if (start >= text.length) break

            var end = (start + maxLength).coerceAtMost(text.length)
            if (end < text.length) {
                val paragraphBreak = text.lastIndexOf("\n\n", end)
                val sentenceBreak = maxOf(
                    text.lastIndexOf(". ", end),
                    text.lastIndexOf("! ", end),
                    text.lastIndexOf("? ", end),
                    text.lastIndexOf('\n', end)
                )
                val spaceBreak = text.lastIndexOf(' ', end)
                end = when {
                    paragraphBreak > start + maxLength / 2 -> paragraphBreak
                    sentenceBreak > start + maxLength / 2 -> sentenceBreak + 1
                    spaceBreak > start + maxLength / 2 -> spaceBreak
                    else -> end
                }.coerceAtLeast(start + 1)
            }

            while (end > start && text[end - 1].isWhitespace()) end--
            if (end <= start) end = (start + maxLength).coerceAtMost(text.length)

            result.add(SpeechChunk(text.substring(start, end), start, end))
            start = end
        }
        return result
    }

    private fun togglePlayback() {
        if (chunks.isEmpty()) {
            toast("Importe um PDF primeiro.")
            return
        }
        if (!ttsReady || tts == null) {
            toast("A voz ainda está iniciando.")
            return
        }
        if (isPlaying) {
            pauseSpeech()
        } else {
            if (currentChunk >= chunks.size) {
                currentChunk = 0
                currentCharOffset = 0
            }
            startSpeech()
        }
    }

    private fun startSpeech() {
        if (!ttsReady || chunks.isEmpty()) return
        isPlaying = true
        isPaused = false
        playButton.text = "Ⅱ  Pause"
        statusText.text = "Narrando..."
        readingIndicator.text = "■  LENDO AGORA"
        speakCurrentChunk()
    }

    private fun pauseSpeech() {
        isPlaying = false
        isPaused = true
        sessionId++
        tts?.stop()
        activeUtteranceId = ""
        playButton.text = "▶  Continuar"
        statusText.text = "Leitura pausada."
        readingIndicator.text = "■  LEITURA PAUSADA"
        saveCurrentProgress()
    }

    private fun stopSpeech() {
        sessionId++
        isPlaying = false
        isPaused = false
        tts?.stop()
        activeUtteranceId = ""
        if (::playButton.isInitialized) playButton.text = "▶  Play"
    }

    private fun speakCurrentChunk() {
        val engine = tts ?: return
        if (!ttsReady || !isPlaying) return

        if (currentChunk !in chunks.indices) {
            isPlaying = false
            playButton.text = "▶  Play"
            statusText.text = "Leitura concluída."
            readingIndicator.text = "■  LEITURA CONCLUÍDA"
            updateProgress()
            saveCurrentProgress()
            return
        }

        val chunk = chunks[currentChunk]
        val base = currentCharOffset.coerceIn(0, chunk.text.length)
        if (base >= chunk.text.length) {
            currentChunk++
            currentCharOffset = 0
            speakCurrentChunk()
            return
        }

        utteranceBaseOffset = base
        activeUtteranceId = "audi_${sessionId}_${currentChunk}_${System.nanoTime()}"
        engine.setSpeechRate(speechRate)
        engine.speak(
            chunk.text.substring(base),
            TextToSpeech.QUEUE_FLUSH,
            null,
            activeUtteranceId
        )
        updateProgress()
    }

    private fun moveChunk(direction: Int) {
        if (chunks.isEmpty()) {
            toast("Importe um PDF primeiro.")
            return
        }
        val resume = isPlaying
        stopSpeech()
        currentChunk = (currentChunk + direction).coerceIn(0, chunks.lastIndex)
        currentCharOffset = 0
        updateProgress()
        clearHighlight()
        saveCurrentProgress()
        statusText.text = "Trecho ${currentChunk + 1} selecionado."
        if (resume) startSpeech()
    }

    private fun changeSpeed(direction: Int) {
        speedIndex = (speedIndex + direction).coerceIn(0, speeds.lastIndex)
        speechRate = speeds[speedIndex]
        tts?.setSpeechRate(speechRate)
        speedText.text = "${speechRate.toString().replace('.', ',')}×"
    }

    private fun changeVoice() {
        if (voices.isEmpty()) {
            toast("Nenhuma voz em português foi encontrada.")
            return
        }
        voiceIndex = (voiceIndex + 1) % voices.size
        tts?.voice = voices[voiceIndex]
        updateVoiceButton()
        toast("Voz alterada.")
    }

    private fun updateVoiceButton() {
        voiceButton.text = if (voices.isEmpty()) "Voz padrão do Android"
        else "Voz ${voiceIndex + 1} de ${voices.size}  ▾"
    }

    private fun highlightPosition(start: Int, end: Int) {
        if (documentText.isEmpty()) return
        val a = start.coerceIn(0, documentText.length)
        val b = end.coerceIn(a, documentText.length)
        if (b <= a) return

        val span = SpannableString(documentText)
        span.setSpan(BackgroundColorSpan(highlight), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        span.setSpan(ForegroundColorSpan(green), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        contentText.text = span
        readingIndicator.text = "■  LENDO AGORA"
    }

    private fun clearHighlight() {
        if (::contentText.isInitialized) contentText.text = documentText
    }

    private fun updateProgress() {
        if (chunks.isEmpty()) {
            seekBar.max = 1
            seekBar.progress = 0
            seekBar.isEnabled = false
            seekText.text = "Nenhum trecho carregado"
            return
        }

        seekBar.max = (chunks.size - 1).coerceAtLeast(1)
        seekBar.progress = currentChunk.coerceIn(0, seekBar.max)
        seekBar.isEnabled = chunks.size > 1
        seekText.text = "Trecho ${minOf(currentChunk + 1, chunks.size)} de ${chunks.size}"
    }

    private fun getFileName(uri: Uri): String {
        var name = "Documento.pdf"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && index >= 0) {
                name = cursor.getString(index) ?: name
            }
        }
        return name
    }

    private fun makePanel(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = rounded(panel, 18)
        }

    private fun sectionTitle(value: String) =
        makeText(value, 21f, white, true).apply {
            setPadding(0, dp(24), 0, dp(12))
        }

    private fun makeButton(
        label: String,
        color: Int,
        foreground: Int,
        action: () -> Unit
    ): Button = Button(this).apply {
        text = label
        textSize = 14f
        isAllCaps = false
        setTextColor(foreground)
        typeface = Typeface.DEFAULT_BOLD
        background = rounded(color, 14)
        setPadding(dp(8), dp(6), dp(8), dp(6))
        setOnClickListener { action() }
    }

    private fun makeText(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setLineSpacing(dp(3).toFloat(), 1f)
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

    private fun topMargin(value: Int) =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(value) }

    private fun bottomMargin(value: Int) =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(value) }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        saveCurrentProgress()
        stopSpeech()
        tts?.shutdown()
        executor.shutdown()
        super.onDestroy()
    }
}
