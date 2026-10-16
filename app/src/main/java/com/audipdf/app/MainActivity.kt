package com.audipdf.app

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.NumberPicker
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
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private val bg = Color.rgb(10, 12, 11)
    private val panel = Color.rgb(23, 27, 24)
    private val green = Color.rgb(50, 220, 120)
    private val white = Color.rgb(245, 248, 246)
    private val muted = Color.rgb(155, 166, 158)

    // Fundo verde claro para destacar o parágrafo durante a narração.
    private val highlight = Color.rgb(50, 220, 120)
    private val highlightText = Color.rgb(8, 20, 12)

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
    private lateinit var highlightToggleButton: Button
    private lateinit var readingIndicator: TextView
    private lateinit var librarySummary: TextView
    private lateinit var homeTab: Button
    private lateinit var libraryTab: Button
    private lateinit var rangeButton: Button

    private lateinit var appFolder: File
    private lateinit var pdfFolder: File
    private lateinit var progressFile: File

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
    private val speeds = listOf(
        0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f
    )
    private var speedIndex = 2
    private var voices = listOf<android.speech.tts.Voice>()
    private var voiceIndex = 0

    private var currentBookFile = ""
    private var currentBookName = ""
    private var lastSavedPercent = -1
    private var lastSavedOffset = -1
    private var savedBooks = mutableListOf<BookProgress>()

    private var pageRanges = listOf<PageRange>()
    private var chapterRanges = listOf<ChapterRange>()
    private var activeRangeStart = 0
    private var activeRangeEnd = 0
    private var activeRangeLabel = "Sem limite"

    // Configuração persistida para manter a preferência entre sessões.
    private var paragraphHighlightEnabled = true

    // Documento formatado e spans reutilizados durante a narração.
    private var renderedDocument: SpannableStringBuilder? = null
    private var backgroundHighlightSpan: BackgroundColorSpan? = null
    private var foregroundHighlightSpan: ForegroundColorSpan? = null

    private var highlightedParagraphStart = -1
    private var highlightedParagraphEnd = -1

    private data class SpeechChunk(
        val text: String,
        val start: Int,
        val end: Int
    )

    private data class PageRange(
        val pageNumber: Int,
        val start: Int,
        val end: Int
    )

    private data class ChapterRange(
        val title: String,
        val start: Int,
        val end: Int
    )

    private data class ExtractedPdf(
        val text: String,
        val pages: List<PageRange>
    )

    private data class BookProgress(
        val fileName: String,
        val displayName: String,
        val charOffset: Int,
        val percent: Int,
        val chunkIndex: Int
    )

    private val pdfPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importPdf(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = bg
        window.navigationBarColor = bg

        appFolder = File(filesDir, "AudiPDF")
        pdfFolder = File(appFolder, "PDFs")
        progressFile = File(appFolder, "progresso.json")

        appFolder.mkdirs()
        pdfFolder.mkdirs()

        paragraphHighlightEnabled = getSharedPreferences(
            "audipdf_settings",
            MODE_PRIVATE
        ).getBoolean("paragraph_highlight_enabled", true)

        PDFBoxResourceLoader.init(applicationContext)

        buildInterface()
        initializeVoice()
        refreshLibrary()
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

        tabs.addView(
            homeTab,
            LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                marginEnd = dp(8)
            }
        )

        tabs.addView(
            libraryTab,
            LinearLayout.LayoutParams(0, dp(48), 1f)
        )

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

        hero.addView(
            makeText("Seu PDF. Sua experiência de áudio.", 25f, white, true).apply {
                setPadding(0, dp(10), 0, dp(10))
            }
        )

        hero.addView(
            makeText(
                "Transforme documentos em uma experiência de leitura em voz alta.",
                14f,
                muted
            )
        )

        homeContent.addView(hero, topMargin(18))

        val warning = makePanel()
        warning.background = rounded(Color.rgb(35, 43, 31), 16)

        warning.addView(
            makeText("⚠  IMPORTANTE SOBRE SEUS DADOS", 12f, green, true)
        )

        warning.addView(
            makeText(
                "Atualizar o AudiPDF instalando uma nova versão por cima da atual " +
                    "normalmente preserva seus PDFs e seu progresso.\n\n" +
                    "Desinstalar o aplicativo ou limpar os dados dele pode apagar " +
                    "os documentos e o progresso salvos neste aparelho. Para evitar " +
                    "perdas, mantenha cópias dos PDFs originais.",
                13f,
                white
            ).apply {
                setPadding(0, dp(10), 0, 0)
            }
        )

        homeContent.addView(warning, topMargin(14))
        homeContent.addView(sectionTitle("Documento atual"))

        val documentPanel = makePanel()

        fileNameText = makeText("Nenhum PDF selecionado", 17f, white, true)
        statusText = makeText("Preparando o AudiPDF...", 13f, muted)

        documentPanel.addView(fileNameText)

        documentPanel.addView(
            statusText.apply {
                setPadding(0, dp(8), 0, 0)
            }
        )

        importButton = makeButton(
            "＋  Importar PDF",
            green,
            Color.rgb(8, 20, 12)
        ) {
            pdfPicker.launch(arrayOf("application/pdf"))
        }

        documentPanel.addView(importButton, topMargin(14))

        documentPanel.addView(
            makeText(
                "Seus PDFs serão copiados automaticamente para o armazenamento privado do app.",
                12f,
                muted
            ).apply {
                setPadding(0, dp(8), 0, 0)
            }
        )

        homeContent.addView(documentPanel)
        homeContent.addView(sectionTitle("Player AudiPDF"))

        val player = makePanel()

        player.addView(makeText("Leitura narrada", 20f, white, true))

        player.addView(
            makeText(
                "Acompanhe o texto enquanto a voz faz a leitura.",
                12f,
                muted
            ).apply {
                setPadding(0, dp(6), 0, 0)
            }
        )

        rangeButton = makeButton("Trecho: Sem limite", panel, white) {
            showRangeDialog()
        }

        player.addView(rangeButton, topMargin(12))

        seekBar = SeekBar(this).apply {
            max = 1
            progress = 0
            progressTintList =
                android.content.res.ColorStateList.valueOf(green)
            thumbTintList =
                android.content.res.ColorStateList.valueOf(green)
            isEnabled = false
        }

        seekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    s: SeekBar?,
                    p: Int,
                    fromUser: Boolean
                ) {
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
                    saveCurrentProgress(force = true)

                    if (resume) startSpeech()
                }
            }
        )

        player.addView(seekBar, topMargin(12))

        seekText = makeText("Nenhum trecho carregado", 12f, muted)
        player.addView(seekText)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(8))
        }

        controls.addView(
            makeButton("−1 trecho", panel, white) {
                moveChunk(-1)
            },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                marginEnd = dp(5)
            }
        )

        playButton = makeButton(
            "▶  Play",
            green,
            Color.rgb(8, 20, 12)
        ) {
            togglePlayback()
        }

        controls.addView(
            playButton,
            LinearLayout.LayoutParams(0, dp(50), 1.4f).apply {
                marginEnd = dp(5)
            }
        )

        controls.addView(
            makeButton("+1 trecho", panel, white) {
                moveChunk(1)
            },
            LinearLayout.LayoutParams(0, dp(48), 1f)
        )

        player.addView(controls)

        player.addView(
            makeText("VELOCIDADE", 10f, green, true).apply {
                setPadding(0, dp(12), 0, dp(6))
            }
        )

        speedText = makeText("1,0×", 16f, white, true)
        player.addView(speedText)

        val speedControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        speedControls.addView(
            makeButton("−", panel, white) {
                changeSpeed(-1)
            },
            LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                marginEnd = dp(8)
            }
        )

        speedControls.addView(
            makeButton("+", panel, white) {
                changeSpeed(1)
            },
            LinearLayout.LayoutParams(0, dp(44), 1f)
        )

        player.addView(speedControls, topMargin(8))

        voiceButton = makeButton("Voz: carregando...", panel, white) {
            changeVoice()
        }

        player.addView(voiceButton, topMargin(10))

        // Botão para ativar/desativar a marcação de parágrafo.
        highlightToggleButton = makeButton(
            "",
            panel,
            white
        ) {
            toggleParagraphHighlight()
        }

        player.addView(highlightToggleButton, topMargin(10))
        updateHighlightToggleButton()

        homeContent.addView(player)
        homeContent.addView(sectionTitle("Leitura do documento"))

        val reading = makePanel()

        readingIndicator = makeText(
            "■  PRONTO PARA LER",
            11f,
            green,
            true
        ).apply {
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = rounded(Color.rgb(20, 48, 31), 8)
        }

        reading.addView(readingIndicator, bottomMargin(14))

        contentText = makeText(
            "Importe um PDF para visualizar o texto organizado aqui.",
            15f,
            muted
        ).apply {
            setTextIsSelectable(true)
            setLineSpacing(dp(7).toFloat(), 1.08f)
        }

        reading.addView(contentText)
        homeContent.addView(reading)

        homeContent.addView(
            makeText(
                "FOCO  •  CONHECIMENTO  •  LIBERDADE",
                10f,
                muted,
                true
            ).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(24), 0, 0)
            }
        )
    }

    private fun buildLibrary() {
        libraryContent.addView(
            makeText("Sua biblioteca", 27f, white, true).apply {
                setPadding(0, dp(22), 0, dp(6))
            }
        )

        libraryContent.addView(
            makeText(
                "Seus documentos e o progresso salvo neste aparelho.",
                13f,
                muted
            ).apply {
                setPadding(0, 0, 0, dp(16))
            }
        )

        librarySummary = makeText("Carregando documentos...", 12f, green, true)
        libraryContent.addView(librarySummary, bottomMargin(12))

        bookList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        libraryContent.addView(bookList)

        libraryContent.addView(
            makeButton("↻  Atualizar biblioteca", panel, white) {
                refreshLibrary()
            },
            topMargin(12)
        )
    }

    private fun showTab(library: Boolean) {
        homeContent.visibility = if (library) View.GONE else View.VISIBLE
        libraryContent.visibility = if (library) View.VISIBLE else View.GONE

        homeTab.background = rounded(if (library) panel else green, 14)
        homeTab.setTextColor(if (library) white else Color.rgb(8, 20, 12))

        libraryTab.background = rounded(if (library) green else panel, 14)
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

                if (
                    language == TextToSpeech.LANG_MISSING_DATA ||
                    language == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    engine.setLanguage(Locale.getDefault())
                    toast(
                        "Se necessário, instale uma voz em português " +
                            "nas configurações do Android."
                    )
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
                                if (
                                    utteranceId != activeUtteranceId ||
                                    currentChunk !in chunks.indices
                                ) {
                                    return@runOnUiThread
                                }

                                val chunk = chunks[currentChunk]

                                val a = (
                                    chunk.start + utteranceBaseOffset + start
                                ).coerceIn(0, documentText.length)

                                val b = (
                                    chunk.start + utteranceBaseOffset + end
                                ).coerceIn(a, documentText.length)

                                currentCharOffset = (
                                    utteranceBaseOffset + end
                                ).coerceIn(0, chunk.text.length)

                                if (paragraphHighlightEnabled) {
                                    highlightPosition(a, b)
                                }

                                saveCurrentProgress()
                            }
                        }

                        override fun onDone(utteranceId: String?) {
                            runOnUiThread {
                                if (
                                    utteranceId != activeUtteranceId ||
                                    !isPlaying
                                ) {
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
                                    saveCurrentProgress(force = true)
                                } else {
                                    updateProgress()
                                    saveCurrentProgress(force = true)
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
                                    saveCurrentProgress(force = true)
                                }
                            }
                        }

                        override fun onError(
                            utteranceId: String?,
                            errorCode: Int
                        ) {
                            onError(utteranceId)
                        }
                    }
                )

                ttsReady = true

                statusText.text = if (documentText.isNotBlank()) {
                    "Documento carregado. Seu progresso está disponível."
                } else {
                    "Voz pronta. Importe um PDF para começar."
                }
            }
        }
    }

    private fun importPdf(sourceUri: Uri) {
        stopSpeech()
        importButton.isEnabled = false
        statusText.text = "Copiando e lendo o PDF..."
        contentText.text = "Preparando o documento..."
        contentText.setTextColor(muted)

        renderedDocument = null
        backgroundHighlightSpan = null
        foregroundHighlightSpan = null
        highlightedParagraphStart = -1
        highlightedParagraphEnd = -1

        val originalName = getFileName(sourceUri)

        executor.execute {
            try {
                if (!pdfFolder.exists() && !pdfFolder.mkdirs()) {
                    throw IllegalStateException(
                        "Não foi possível preparar o armazenamento."
                    )
                }

                val savedFile = makeUniquePdfFile(originalName)

                contentResolver.openInputStream(sourceUri).use { input ->
                    if (input == null) {
                        throw IllegalStateException(
                            "Não foi possível abrir o PDF original."
                        )
                    }

                    savedFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val extracted = extractPdfWithPages(savedFile)
                val organized = extracted.text

                runOnUiThread {
                    importButton.isEnabled = true
                    currentBookFile = savedFile.name
                    currentBookName = originalName
                    documentText = organized
                    pageRanges = extracted.pages
                    chapterRanges = detectChapters(documentText)

                    activeRangeStart = 0
                    activeRangeEnd = documentText.length
                    activeRangeLabel = "Sem limite"
                    rangeButton.text = "Trecho: Sem limite"

                    currentChunk = 0
                    currentCharOffset = 0
                    lastSavedPercent = -1
                    lastSavedOffset = -1
                    fileNameText.text = originalName

                    if (documentText.isBlank()) {
                        chunks = emptyList()

                        setDocumentTextView(
                            "Nenhum texto foi encontrado. O PDF pode conter páginas " +
                                "digitalizadas como imagens; OCR ainda não está disponível.",
                            muted
                        )

                        statusText.text = "PDF salvo, mas sem texto extraível."
                        readingIndicator.text = "■  SEM TEXTO DISPONÍVEL"
                    } else {
                        chunks = splitIntoChunks(documentText)

                        setDocumentTextView(documentText, white)

                        statusText.text =
                            "PDF pronto • ${documentText.split(Regex("\\s+")).size} palavras"

                        readingIndicator.text = "■  PRONTO PARA LER"
                        updateProgress()
                        clearHighlight()
                        saveCurrentProgress(force = true)
                    }

                    refreshLibrary()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    importButton.isEnabled = true
                    statusText.text = "Não foi possível importar este PDF."

                    setDocumentTextView(
                        "Verifique o arquivo e o espaço disponível.\n\n" +
                            (e.localizedMessage ?: "Erro desconhecido"),
                        muted
                    )

                    readingIndicator.text = "■  ERRO AO ABRIR PDF"
                }
            }
        }
    }

    private fun extractPdfWithPages(file: File): ExtractedPdf {
        val combinedText = StringBuilder()
        val pages = mutableListOf<PageRange>()

        PDDocument.load(file).use { document ->
            val stripper = PDFTextStripper()
            stripper.sortByPosition = true

            for (pageNumber in 1..document.numberOfPages) {
                stripper.startPage = pageNumber
                stripper.endPage = pageNumber

                val pageText = formatDocumentText(stripper.getText(document))

                if (pageText.isNotBlank()) {
                    if (combinedText.isNotEmpty()) combinedText.append("\n\n")

                    val start = combinedText.length
                    combinedText.append(pageText)

                    pages.add(
                        PageRange(pageNumber, start, combinedText.length)
                    )
                } else {
                    pages.add(
                        PageRange(
                            pageNumber,
                            combinedText.length,
                            combinedText.length
                        )
                    )
                }
            }
        }

        return ExtractedPdf(combinedText.toString().trim(), pages)
    }

    private fun detectChapters(text: String): List<ChapterRange> {
        if (text.isBlank()) return emptyList()

        val pattern = Regex(
            """(?im)^[ \t]*((?:cap[ií]tulo|chapter|parte|part)[ \t]+(?:\d+|[ivxlcdm]+)\b[^\n]*)"""
        )

        val matches = pattern.findAll(text).toList()
        if (matches.isEmpty()) return emptyList()

        return matches.mapIndexed { index, match ->
            val start = match.range.first

            val end = if (index + 1 < matches.size) {
                matches[index + 1].range.first
            } else {
                text.length
            }

            ChapterRange(match.groupValues[1].trim(), start, end)
        }.filter { it.end > it.start }
    }

    private fun showRangeDialog() {
        if (documentText.isBlank() || pageRanges.isEmpty()) {
            toast("Importe um PDF com texto primeiro.")
            return
        }

        val options = arrayOf(
            "Sem limite (documento inteiro)",
            "Selecionar intervalo de páginas",
            "Selecionar intervalo de capítulos"
        )

        AlertDialog.Builder(this)
            .setTitle("Trecho de leitura")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> applyReadingRange(
                        0,
                        documentText.length,
                        "Sem limite"
                    )

                    1 -> showPageRangePicker()
                    2 -> showChapterRangePicker()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showPageRangePicker() {
        val totalPages = pageRanges.size

        if (totalPages == 0) {
            toast("Não foi possível identificar as páginas.")
            return
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(12), dp(24), dp(8))
        }

        container.addView(makeText("Página inicial", 14f, white, true))

        val startPicker = NumberPicker(this).apply {
            minValue = 1
            maxValue = totalPages
            value = 1
            wrapSelectorWheel = false
        }

        container.addView(
            startPicker,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
            )
        )

        container.addView(
            makeText("Página final", 14f, white, true).apply {
                setPadding(0, dp(12), 0, 0)
            }
        )

        val endPicker = NumberPicker(this).apply {
            minValue = 1
            maxValue = totalPages
            value = totalPages
            wrapSelectorWheel = false
        }

        container.addView(
            endPicker,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
            )
        )

        startPicker.setOnValueChangedListener { _, newValue, _ ->
            if (endPicker.value < newValue) {
                endPicker.value = newValue
            }
        }

        endPicker.setOnValueChangedListener { _, newValue, _ ->
            if (startPicker.value > newValue) {
                startPicker.value = newValue
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Selecionar páginas")
            .setView(container)
            .setPositiveButton("Aplicar") { _, _ ->
                val firstPage = startPicker.value
                val lastPage = endPicker.value.coerceAtLeast(firstPage)
                val first = pageRanges[firstPage - 1]
                val last = pageRanges[lastPage - 1]

                applyReadingRange(
                    first.start,
                    last.end,
                    "Páginas $firstPage–$lastPage"
                )
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showChapterRangePicker() {
        if (chapterRanges.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Capítulos não identificados")
                .setMessage(
                    "Não encontrei títulos no formato esperado, como " +
                        "\"Capítulo 1\", \"Chapter 1\" ou \"Parte 1\".\n\n" +
                        "Você ainda pode selecionar um intervalo de páginas."
                )
                .setPositiveButton("Selecionar páginas") { _, _ ->
                    showPageRangePicker()
                }
                .setNegativeButton("Fechar", null)
                .show()

            return
        }

        val titles = chapterRanges.mapIndexed { index, chapter ->
            "${index + 1}. ${chapter.title}"
        }.toTypedArray()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(12), dp(24), dp(8))
        }

        container.addView(makeText("Capítulo inicial", 14f, white, true))

        val startPicker = NumberPicker(this).apply {
            minValue = 1
            maxValue = chapterRanges.size
            value = 1
            displayedValues = titles
            wrapSelectorWheel = false
        }

        container.addView(
            startPicker,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(60)
            )
        )

        container.addView(
            makeText("Capítulo final", 14f, white, true).apply {
                setPadding(0, dp(12), 0, 0)
            }
        )

        val endPicker = NumberPicker(this).apply {
            minValue = 1
            maxValue = chapterRanges.size
            value = chapterRanges.size
            displayedValues = titles
            wrapSelectorWheel = false
        }

        container.addView(
            endPicker,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(60)
            )
        )

        startPicker.setOnValueChangedListener { _, newValue, _ ->
            if (endPicker.value < newValue) {
                endPicker.value = newValue
            }
        }

        endPicker.setOnValueChangedListener { _, newValue, _ ->
            if (startPicker.value > newValue) {
                startPicker.value = newValue
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Selecionar capítulos")
            .setView(container)
            .setPositiveButton("Aplicar") { _, _ ->
                val firstIndex = startPicker.value - 1
                val lastIndex = endPicker.value - 1
                val first = chapterRanges[firstIndex]
                val last = chapterRanges[lastIndex]

                applyReadingRange(
                    first.start,
                    last.end,
                    if (firstIndex == lastIndex) {
                        first.title
                    } else {
                        "${first.title} – ${last.title}"
                    }
                )
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun applyReadingRange(
        requestedStart: Int,
        requestedEnd: Int,
        label: String
    ) {
        if (documentText.isBlank()) {
            toast("Importe um PDF primeiro.")
            return
        }

        stopSpeech()

        val start = requestedStart.coerceIn(0, documentText.length)
        val end = requestedEnd.coerceIn(start, documentText.length)

        if (
            end <= start ||
            documentText.substring(start, end).isBlank()
        ) {
            toast("Esse intervalo não contém texto para narrar.")
            return
        }

        activeRangeStart = start
        activeRangeEnd = end
        activeRangeLabel = label
        chunks = splitIntoChunks(documentText.substring(start, end), start)

        currentChunk = 0
        currentCharOffset = 0
        rangeButton.text = "Trecho: $activeRangeLabel"

        updateProgress()
        clearHighlight()
        saveCurrentProgress(force = true)

        statusText.text = "Trecho selecionado: $activeRangeLabel"
        readingIndicator.text = "■  PRONTO PARA LER"
        toast("Trecho de leitura atualizado.")
    }

    private fun makeUniquePdfFile(original: String): File {
        val cleaned = original
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .ifBlank { "documento.pdf" }

        val name = if (cleaned.endsWith(".pdf", true)) {
            cleaned
        } else {
            "$cleaned.pdf"
        }

        return File(pdfFolder, "${System.currentTimeMillis()}_$name")
    }

    private fun refreshLibrary() {
        if (!::bookList.isInitialized) return

        librarySummary.text = "Carregando documentos..."
        bookList.removeAllViews()
        bookList.addView(makeText("Lendo biblioteca...", 13f, muted))

        executor.execute {
            try {
                if (!pdfFolder.exists()) pdfFolder.mkdirs()
                if (!appFolder.exists()) appFolder.mkdirs()

                val progress = readProgressFile()
                val progressByFile = progress.associateBy { it.fileName }

                val pdfs = pdfFolder.listFiles()
                    ?.filter { it.isFile && it.extension.equals("pdf", true) }
                    ?.sortedByDescending { it.lastModified() }
                    ?: emptyList()

                val combined = pdfs.map { file ->
                    progressByFile[file.name] ?: BookProgress(
                        fileName = file.name,
                        displayName = file.name.substringAfter('_', file.name),
                        charOffset = 0,
                        percent = 0,
                        chunkIndex = 0
                    )
                }.toMutableList()

                runOnUiThread {
                    savedBooks = combined
                    renderLibrary(pdfs)
                }
            } catch (_: Exception) {
                runOnUiThread {
                    bookList.removeAllViews()
                    librarySummary.text = "Não foi possível carregar a biblioteca."

                    bookList.addView(
                        makeText(
                            "Tente atualizar a biblioteca novamente.",
                            14f,
                            muted
                        )
                    )
                }
            }
        }
    }

    private fun renderLibrary(documents: List<File>) {
        bookList.removeAllViews()

        librarySummary.text =
            "${documents.size} documento(s) • progresso salvo no aparelho"

        if (documents.isEmpty()) {
            val empty = makePanel()

            empty.addView(
                makeText("Sua biblioteca está vazia", 18f, white, true)
            )

            empty.addView(
                makeText(
                    "Importe um PDF pela aba Início. Ele será copiado automaticamente " +
                        "para o armazenamento privado do AudiPDF.",
                    13f,
                    muted
                ).apply {
                    setPadding(0, dp(8), 0, 0)
                }
            )

            bookList.addView(empty)
            return
        }

        documents.forEach { file ->
            val progress = savedBooks.firstOrNull {
                it.fileName == file.name
            }

            val percent = progress?.percent ?: 0
            val card = makePanel()

            card.addView(
                makeText(
                    progress?.displayName
                        ?: file.name.substringAfter('_', file.name),
                    16f,
                    white,
                    true
                )
            )

            card.addView(
                makeText(
                    if (percent >= 100) "Concluído" else "$percent% lido",
                    12f,
                    green,
                    true
                ).apply {
                    setPadding(0, dp(8), 0, dp(6))
                }
            )

            val bar = ProgressBar(
                this,
                null,
                android.R.attr.progressBarStyleHorizontal
            ).apply {
                max = 100
                progress = percent.coerceIn(0, 100)

                progressTintList =
                    android.content.res.ColorStateList.valueOf(green)

                progressBackgroundTintList =
                    android.content.res.ColorStateList.valueOf(
                        Color.rgb(48, 55, 49)
                    )
            }

            card.addView(
                bar,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(6)
                )
            )

            card.addView(
                makeButton(
                    when {
                        percent in 1..99 -> "▶  Continuar lendo"
                        percent >= 100 -> "↻  Ler novamente"
                        else -> "▶  Abrir documento"
                    },
                    green,
                    Color.rgb(8, 20, 12)
                ) {
                    openSavedBook(file, progress)
                },
                topMargin(12)
            )

            bookList.addView(card, bottomMargin(12))
        }
    }

    private fun openSavedBook(file: File, progress: BookProgress?) {
        stopSpeech()
        statusText.text = "Abrindo documento salvo..."
        showTab(false)

        executor.execute {
            try {
                val extracted = extractPdfWithPages(file)
                val organized = extracted.text

                runOnUiThread {
                    currentBookFile = file.name
                    currentBookName = progress?.displayName
                        ?: file.name.substringAfter('_', file.name)

                    documentText = organized
                    pageRanges = extracted.pages
                    chapterRanges = detectChapters(documentText)

                    activeRangeStart = 0
                    activeRangeEnd = documentText.length
                    activeRangeLabel = "Sem limite"
                    rangeButton.text = "Trecho: Sem limite"

                    chunks = splitIntoChunks(documentText)

                    val savedOffset = (progress?.charOffset ?: 0)
                        .coerceIn(0, documentText.length)

                    currentChunk = chunks.indexOfFirst {
                        savedOffset >= it.start && savedOffset < it.end
                    }.let {
                        when {
                            chunks.isEmpty() -> 0
                            it < 0 && savedOffset >= documentText.length ->
                                chunks.size
                            it < 0 -> 0
                            else -> it
                        }
                    }

                    currentCharOffset = if (currentChunk in chunks.indices) {
                        (savedOffset - chunks[currentChunk].start)
                            .coerceIn(0, chunks[currentChunk].text.length)
                    } else {
                        0
                    }

                    lastSavedPercent = progress?.percent ?: -1
                    lastSavedOffset = progress?.charOffset ?: -1
                    fileNameText.text = currentBookName

                    setDocumentTextView(
                        documentText,
                        if (documentText.isBlank()) muted else white
                    )

                    if (documentText.isBlank()) {
                        statusText.text =
                            "Este PDF não possui texto extraível."
                        readingIndicator.text = "■  SEM TEXTO DISPONÍVEL"
                    } else {
                        statusText.text =
                            "Documento recuperado da biblioteca."
                        readingIndicator.text = "■  PRONTO PARA CONTINUAR"
                    }

                    updateProgress()
                    clearHighlight()
                    saveCurrentProgress(force = true)
                }
            } catch (_: Exception) {
                runOnUiThread {
                    statusText.text =
                        "Não foi possível abrir este documento."

                    toast(
                        "Confira se o PDF ainda existe no armazenamento do app."
                    )
                }
            }
        }
    }

    private fun saveCurrentProgress(force: Boolean = false) {
        if (
            currentBookFile.isBlank() ||
            documentText.isBlank() ||
            !::progressFile.isInitialized
        ) {
            return
        }

        val absolute = when {
            chunks.isEmpty() -> 0
            currentChunk >= chunks.size -> activeRangeEnd
            currentChunk in chunks.indices ->
                (chunks[currentChunk].start + currentCharOffset)
                    .coerceIn(0, documentText.length)
            else -> 0
        }

        val percent = if (documentText.isEmpty()) {
            0
        } else {
            ((absolute.toDouble() / documentText.length) * 100)
                .toInt()
                .coerceIn(0, 100)
        }

        if (
            !force &&
            lastSavedPercent == percent &&
            lastSavedOffset >= 0 &&
            kotlin.math.abs(absolute - lastSavedOffset) < 80
        ) {
            return
        }

        lastSavedPercent = percent
        lastSavedOffset = absolute

        val entry = BookProgress(
            fileName = currentBookFile,
            displayName = currentBookName,
            charOffset = absolute,
            percent = percent,
            chunkIndex = currentChunk
        )

        savedBooks.removeAll { it.fileName == entry.fileName }
        savedBooks.add(entry)

        val snapshot = savedBooks.toList()

        executor.execute {
            try {
                writeProgressFile(snapshot)
            } catch (_: Exception) {
                // Uma gravação futura tentará salvar o progresso novamente.
            }
        }
    }

    private fun readProgressFile(): MutableList<BookProgress> {
        if (!progressFile.exists()) return mutableListOf()

        val text = progressFile.readText(Charsets.UTF_8)
        val array = JSONArray(text)
        val result = mutableListOf<BookProgress>()

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue

            result.add(
                BookProgress(
                    fileName = item.optString("fileName"),
                    displayName = item.optString("displayName"),
                    charOffset = item.optInt("charOffset", 0).coerceAtLeast(0),
                    percent = item.optInt("percent", 0).coerceIn(0, 100),
                    chunkIndex = item.optInt("chunkIndex", 0).coerceAtLeast(0)
                )
            )
        }

        return result
    }

    private fun writeProgressFile(entries: List<BookProgress>) {
        if (!appFolder.exists()) appFolder.mkdirs()

        val array = JSONArray()

        entries.forEach { book ->
            array.put(
                JSONObject().apply {
                    put("fileName", book.fileName)
                    put("displayName", book.displayName)
                    put("charOffset", book.charOffset)
                    put("percent", book.percent)
                    put("chunkIndex", book.chunkIndex)
                }
            )
        }

        val temporary = File(appFolder, "progresso.tmp")
        temporary.writeText(array.toString(2), Charsets.UTF_8)

        if (progressFile.exists() && !progressFile.delete()) {
            throw IllegalStateException(
                "Não foi possível atualizar o progresso."
            )
        }

        if (!temporary.renameTo(progressFile)) {
            temporary.copyTo(progressFile, overwrite = true)
            temporary.delete()
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
            val line = rawLine
                .replace(Regex("[\\t ]+"), " ")
                .trim()

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

    private fun splitIntoChunks(
        text: String,
        globalStart: Int = 0
    ): List<SpeechChunk> {
        val result = mutableListOf<SpeechChunk>()
        val maxLength = 1000
        var start = 0

        while (start < text.length) {
            while (start < text.length && text[start].isWhitespace()) {
                start++
            }

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
                    paragraphBreak > start + maxLength / 2 ->
                        paragraphBreak

                    sentenceBreak > start + maxLength / 2 ->
                        sentenceBreak + 1

                    spaceBreak > start + maxLength / 2 ->
                        spaceBreak

                    else -> end
                }.coerceAtLeast(start + 1)
            }

            while (end > start && text[end - 1].isWhitespace()) {
                end--
            }

            if (end <= start) {
                end = (start + maxLength).coerceAtMost(text.length)
            }

            result.add(
                SpeechChunk(
                    text = text.substring(start, end),
                    start = globalStart + start,
                    end = globalStart + end
                )
            )

            start = end
        }

        return result
    }

    private fun togglePlayback() {
        if (chunks.isEmpty()) {
            toast("Importe um PDF com texto primeiro.")
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

        readingIndicator.text = if (paragraphHighlightEnabled) {
            "■  LENDO AGORA"
        } else {
            "■  LENDO AGORA • MARCAÇÃO DESLIGADA"
        }

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

        saveCurrentProgress(force = true)
    }

    private fun stopSpeech() {
        sessionId++
        isPlaying = false
        isPaused = false
        tts?.stop()
        activeUtteranceId = ""

        if (::playButton.isInitialized) {
            playButton.text = "▶  Play"
        }
    }

    private fun speakCurrentChunk() {
        val engine = tts ?: return
        if (!ttsReady || !isPlaying) return

        if (currentChunk !in chunks.indices) {
            isPlaying = false
            isPaused = false
            playButton.text = "▶  Play"
            statusText.text = "Leitura concluída."
            readingIndicator.text = "■  LEITURA CONCLUÍDA"

            updateProgress()
            saveCurrentProgress(force = true)
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

        activeUtteranceId =
            "audipdf_${sessionId}_${currentChunk}_${System.nanoTime()}"

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
        saveCurrentProgress(force = true)

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
        voiceButton.text = if (voices.isEmpty()) {
            "Voz padrão do Android"
        } else {
            "Voz ${voiceIndex + 1} de ${voices.size}  ▾"
        }
    }

    // Atualiza o botão de configuração da marcação.
    private fun toggleParagraphHighlight() {
        paragraphHighlightEnabled = !paragraphHighlightEnabled

        getSharedPreferences(
            "audipdf_settings",
            MODE_PRIVATE
        ).edit()
            .putBoolean(
                "paragraph_highlight_enabled",
                paragraphHighlightEnabled
            )
            .apply()

        updateHighlightToggleButton()

        if (!paragraphHighlightEnabled) {
            clearHighlight()

            readingIndicator.text = if (isPlaying) {
                "■  LENDO AGORA • MARCAÇÃO DESLIGADA"
            } else {
                "■  PRONTO PARA LER"
            }
        } else if (isPlaying && currentChunk in chunks.indices) {
            val chunk = chunks[currentChunk]

            val position = (
                chunk.start + currentCharOffset
            ).coerceIn(0, documentText.length)

            highlightPosition(
                position,
                (position + 1).coerceAtMost(documentText.length)
            )
        }

        toast(
            if (paragraphHighlightEnabled) {
                "Marcação de parágrafo ativada."
            } else {
                "Marcação de parágrafo desativada."
            }
        )
    }

    private fun updateHighlightToggleButton() {
        if (!::highlightToggleButton.isInitialized) return

        highlightToggleButton.text = if (paragraphHighlightEnabled) {
            "🟢 Marcação de parágrafo: LIGADA"
        } else {
            "⚪ Marcação de parágrafo: DESLIGADA"
        }
    }

    private fun setDocumentTextView(text: String, color: Int) {
        val builder = SpannableStringBuilder(text)

        renderedDocument = builder
        backgroundHighlightSpan = null
        foregroundHighlightSpan = null
        highlightedParagraphStart = -1
        highlightedParagraphEnd = -1

        contentText.text = builder
        contentText.setTextColor(color)
    }

    private fun removeCurrentHighlight() {
        val text = renderedDocument

        if (text != null) {
            backgroundHighlightSpan?.let { span ->
                if (text.getSpanStart(span) >= 0) {
                    text.removeSpan(span)
                }
            }

            foregroundHighlightSpan?.let { span ->
                if (text.getSpanStart(span) >= 0) {
                    text.removeSpan(span)
                }
            }
        }

        backgroundHighlightSpan = null
        foregroundHighlightSpan = null
        highlightedParagraphStart = -1
        highlightedParagraphEnd = -1
    }

    // Destaca o parágrafo inteiro, usando fundo verde e texto escuro.
    private fun highlightPosition(start: Int, end: Int) {
        if (!paragraphHighlightEnabled) return

        val text = renderedDocument ?: return
        if (text.isEmpty()) return

        val a = start.coerceIn(0, text.length)
        val b = end.coerceIn(a, text.length)

        if (b <= a) return

        val previousBreak = text.lastIndexOf(
            "\n\n",
            (a - 1).coerceAtLeast(0)
        )

        val paragraphStart = if (previousBreak < 0) {
            0
        } else {
            previousBreak + 2
        }

        val nextBreak = text.indexOf("\n\n", a)

        val paragraphEnd = if (nextBreak < 0) {
            text.length
        } else {
            nextBreak
        }

        if (paragraphEnd <= paragraphStart) return

        if (
            paragraphStart == highlightedParagraphStart &&
            paragraphEnd == highlightedParagraphEnd
        ) {
            return
        }

        removeCurrentHighlight()

        val backgroundSpan = BackgroundColorSpan(highlight)
        val foregroundSpan = ForegroundColorSpan(highlightText)

        text.setSpan(
            backgroundSpan,
            paragraphStart,
            paragraphEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        text.setSpan(
            foregroundSpan,
            paragraphStart,
            paragraphEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        backgroundHighlightSpan = backgroundSpan
        foregroundHighlightSpan = foregroundSpan

        highlightedParagraphStart = paragraphStart
        highlightedParagraphEnd = paragraphEnd

        contentText.invalidate()
        readingIndicator.text = "■  LENDO PARÁGRAFO"
    }

    private fun clearHighlight() {
        removeCurrentHighlight()

        if (renderedDocument != null) {
            contentText.invalidate()
        }
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

        seekText.text =
            "Trecho ${minOf(currentChunk + 1, chunks.size)} de ${chunks.size}"
    }

    private fun getFileName(uri: Uri): String {
        var name = "Documento.pdf"

        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(
                android.provider.OpenableColumns.DISPLAY_NAME
            )

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
    ): Button =
        Button(this).apply {
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
    ): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)

            if (bold) {
                typeface = Typeface.DEFAULT_BOLD
            }

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
        ).apply {
            topMargin = dp(value)
        }

    private fun bottomMargin(value: Int) =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(value)
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onPause() {
        saveCurrentProgress(force = true)
        super.onPause()
    }

    override fun onDestroy() {
        saveCurrentProgress(force = true)
        stopSpeech()
        tts?.shutdown()
        executor.shutdown()
        super.onDestroy()
    }
}