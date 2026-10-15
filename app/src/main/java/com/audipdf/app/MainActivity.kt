package com.audipdf.app

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private data class SpeechChunk(
        val text: String,
        val start: Int,
        val end: Int
    )

    private data class PageRange(
        val page: Int,
        val start: Int,
        val end: Int
    )

    private data class ChapterRange(
        val title: String,
        val start: Int,
        val end: Int
    )

    private val backgroundColor = Color.rgb(12, 15, 14)
    private val panelColor = Color.rgb(25, 31, 28)
    private val greenColor = Color.rgb(30, 215, 96)
    private val whiteColor = Color.rgb(245, 248, 246)
    private val mutedColor = Color.rgb(155, 166, 159)

    private lateinit var appFolder: File
    private lateinit var pdfFolder: File
    private lateinit var progressFile: File

    private var currentFile: File? = null
    private var currentBookName = ""

    private var documentText = ""
    private var chunks: List<SpeechChunk> = emptyList()

    private var pageRanges: List<PageRange> = emptyList()
    private var chapterRanges: List<ChapterRange> = emptyList()

    private var currentChunk = 0
    private var currentCharOffset = 0
    private var utteranceBaseOffset = 0

    private var activeStartOffset = 0
    private var activeEndOffset = 0

    private var rangeMode = "Sem limite"
    private var rangeDescription = "Sem limite"

    private var isPlaying = false
    private var isTtsReady = false
    private var isImporting = false

    private var speechRate = 1.0f
    private var selectedVoiceName = ""
    private var currentUtteranceId = 0

    private lateinit var rootLayout: LinearLayout
    private lateinit var contentLayout: LinearLayout

    private lateinit var titleText: TextView
    private lateinit var subtitleText: TextView
    private lateinit var bookText: TextView
    private lateinit var rangeText: TextView
    private lateinit var progressText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var readingText: TextView
    private lateinit var playButton: Button
    private lateinit var speedText: TextView
    private lateinit var libraryLayout: LinearLayout

    private var currentTab = "Início"

    private lateinit var textToSpeech: TextToSpeech

    private val pdfPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                importPdf(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appFolder = File(filesDir, "AudiPDF")
        pdfFolder = File(appFolder, "PDFs")
        progressFile = File(appFolder, "progresso.json")

        appFolder.mkdirs()
        pdfFolder.mkdirs()

        PDFBoxResourceLoader.init(applicationContext)

        buildInterface()
        textToSpeech = TextToSpeech(this, this)
        loadLibrary()
    }

    override fun onDestroy() {
        saveCurrentProgress()

        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }

        super.onDestroy()
    }

    override fun onInit(status: Int) {
        isTtsReady = status == TextToSpeech.SUCCESS

        if (isTtsReady) {
            val result = textToSpeech.setLanguage(Locale("pt", "BR"))

            if (
                result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                textToSpeech.language = Locale.getDefault()
            }

            textToSpeech.setSpeechRate(speechRate)
            configureTtsListener()
            refreshPlayer()
        }
    }

    // ---------------------------------------------------------
    // INTERFACE
    // ---------------------------------------------------------

    private fun buildInterface() {
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
            setPadding(dp(18), dp(12), dp(18), dp(8))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(16))
        }

        val brand = TextView(this).apply {
            text = "AudiPDF"
            textSize = 29f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(greenColor)
        }

        val tagline = TextView(this).apply {
            text = "Seus PDFs. Sua leitura. Sua voz."
            textSize = 13f
            setTextColor(mutedColor)
        }

        header.addView(brand)
        header.addView(tagline)
        rootLayout.addView(header)

        val navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        navigation.addView(
            makeButton("Início") { showTab("Início") },
            LinearLayout.LayoutParams(0, dp(44), 1f)
        )

        navigation.addView(
            makeButton("Biblioteca") { showTab("Biblioteca") },
            LinearLayout.LayoutParams(0, dp(44), 1f)
        )

        rootLayout.addView(navigation)

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
        }

        contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(16), 0, dp(20))
        }

        scrollView.addView(contentLayout)

        rootLayout.addView(
            scrollView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(rootLayout)
        showTab("Início")
    }

    private fun showTab(tab: String) {
        currentTab = tab
        contentLayout.removeAllViews()

        if (tab == "Biblioteca") {
            buildLibrary()
        } else {
            buildHome()
        }
    }

    private fun buildHome() {
        titleText = makeText("Seu espaço de leitura", 22f, true)
        subtitleText = makeText(
            "Transforme documentos em áudio.",
            14f,
            false,
            mutedColor
        )

        contentLayout.addView(titleText)
        contentLayout.addView(subtitleText)
        contentLayout.addView(space(16))

        contentLayout.addView(
            makeButton("＋  Importar PDF") {
                pdfPicker.launch(arrayOf("application/pdf"))
            }.apply {
                textSize = 16f
            },
            matchWidth(dp(52))
        )

        contentLayout.addView(space(18))

        val playerPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(panelColor)
        }

        bookText = makeText("Nenhum PDF selecionado", 18f, true)

        rangeText = makeText(
            "Intervalo: Sem limite",
            13f,
            false,
            mutedColor
        )

        progressText = makeText(
            "Importe um PDF para começar",
            13f,
            false,
            mutedColor
        )

        progressBar = ProgressBar(
            this,
            null,
            android.R.attr.progressBarStyleHorizontal
        ).apply {
            max = 100
            progress = 0
            progressTintList =
                android.content.res.ColorStateList.valueOf(greenColor)
        }

        playerPanel.addView(bookText)
        playerPanel.addView(space(8))
        playerPanel.addView(rangeText)
        playerPanel.addView(space(12))
        playerPanel.addView(progressBar)
        playerPanel.addView(space(6))
        playerPanel.addView(progressText)
        playerPanel.addView(space(14))

        playerPanel.addView(
            makeButton("Selecionar intervalo") {
                showRangeDialog()
            },
            matchWidth(dp(46))
        )

        playerPanel.addView(space(14))

        val playbackControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        playbackControls.addView(
            makeButton("−1") { moveChunk(-1) },
            LinearLayout.LayoutParams(0, dp(48), 1f)
        )

        playButton = makeButton("▶ Reproduzir") {
            togglePlayback()
        }

        playbackControls.addView(
            playButton,
            LinearLayout.LayoutParams(0, dp(48), 2f)
        )

        playbackControls.addView(
            makeButton("+1") { moveChunk(1) },
            LinearLayout.LayoutParams(0, dp(48), 1f)
        )

        playerPanel.addView(playbackControls)
        playerPanel.addView(space(12))

        val speedControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        speedText = makeText("Velocidade: ${speechRate}x", 14f, true)

        speedControls.addView(
            speedText,
            LinearLayout.LayoutParams(0, dp(44), 1f)
        )

        speedControls.addView(
            makeButton("−") { changeSpeed(-0.1f) },
            LinearLayout.LayoutParams(dp(48), dp(44))
        )

        speedControls.addView(
            makeButton("+") { changeSpeed(0.1f) },
            LinearLayout.LayoutParams(dp(48), dp(44))
        )

        playerPanel.addView(speedControls)
        playerPanel.addView(space(8))

        playerPanel.addView(
            makeButton("Escolher voz") { showVoiceDialog() },
            matchWidth(dp(44))
        )

        contentLayout.addView(
            playerPanel,
            matchWidth(ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        contentLayout.addView(space(18))
        contentLayout.addView(makeText("Texto do documento", 19f, true))
        contentLayout.addView(space(8))

        readingText = makeText(
            "O texto extraído do PDF aparecerá aqui.",
            15f,
            false,
            whiteColor
        ).apply {
            setTextIsSelectable(true)
            setLineSpacing(dp(4).toFloat(), 1f)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(panelColor)
        }

        contentLayout.addView(
            readingText,
            matchWidth(ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        refreshPlayer()
    }

    private fun buildLibrary() {
        contentLayout.addView(makeText("Sua biblioteca", 23f, true))
        contentLayout.addView(space(6))

        contentLayout.addView(
            makeText(
                "Seus PDFs ficam salvos neste aparelho.",
                14f,
                false,
                mutedColor
            )
        )

        contentLayout.addView(space(16))

        contentLayout.addView(
            makeButton("＋  Adicionar PDF") {
                pdfPicker.launch(arrayOf("application/pdf"))
            },
            matchWidth(dp(48))
        )

        contentLayout.addView(space(16))

        libraryLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        contentLayout.addView(libraryLayout)
        refreshLibraryUI()
    }

    // ---------------------------------------------------------
    // IMPORTAÇÃO E EXTRAÇÃO
    // ---------------------------------------------------------

    private fun importPdf(uri: Uri) {
        if (isImporting) return

        isImporting = true

        Toast.makeText(
            this,
            "Importando PDF...",
            Toast.LENGTH_SHORT
        ).show()

        Thread {
            try {
                val displayName = getDisplayName(uri)
                val safeName = sanitizeFileName(displayName)
                val destination = uniquePdfFile(safeName)

                contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) {
                        "Não foi possível abrir o arquivo."
                    }

                    destination.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val extracted = extractDocument(destination)

                runOnUiThread {
                    isImporting = false

                    if (extracted.text.isBlank()) {
                        destination.delete()

                        Toast.makeText(
                            this,
                            "Não encontrei texto neste PDF. Ele pode estar digitalizado como imagem.",
                            Toast.LENGTH_LONG
                        ).show()

                        return@runOnUiThread
                    }

                    stopSpeech()

                    currentFile = destination
                    currentBookName = displayName

                    documentText = extracted.text
                    pageRanges = extracted.pages
                    chapterRanges = detectChapters(documentText)

                    activeStartOffset = 0
                    activeEndOffset = documentText.length

                    rangeMode = "Sem limite"
                    rangeDescription = "Sem limite"

                    chunks = splitIntoChunks(documentText, 0)

                    currentChunk = 0
                    currentCharOffset = 0
                    utteranceBaseOffset = 0

                    saveCurrentProgress()
                    showTab("Início")
                    refreshPlayer()

                    val message = if (chapterRanges.isNotEmpty()) {
                        "PDF importado. ${chapterRanges.size} capítulos encontrados."
                    } else {
                        "PDF importado. ${pageRanges.size} páginas identificadas."
                    }

                    Toast.makeText(
                        this,
                        message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    isImporting = false

                    Toast.makeText(
                        this,
                        "Erro ao importar PDF: ${e.message ?: "erro desconhecido"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private data class ExtractedDocument(
        val text: String,
        val pages: List<PageRange>
    )

    private fun extractDocument(file: File): ExtractedDocument {
        PDDocument.load(file).use { document ->
            val output = StringBuilder()
            val pages = mutableListOf<PageRange>()

            for (pageNumber in 1..document.numberOfPages) {
                val stripper = PDFTextStripper().apply {
                    startPage = pageNumber
                    endPage = pageNumber
                }

                val pageText = formatDocumentText(
                    stripper.getText(document)
                )

                if (output.isNotEmpty() && pageText.isNotBlank()) {
                    output.append("\n\n")
                }

                val start = output.length

                if (pageText.isNotBlank()) {
                    output.append(pageText)
                }

                val end = output.length

                pages.add(PageRange(pageNumber, start, end))
            }

            return ExtractedDocument(output.toString(), pages)
        }
    }

    private fun formatDocumentText(raw: String): String {
        val lines = raw
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lines()

        val result = StringBuilder()
        var previousWasBlank = true

        for (line in lines) {
            val cleaned = line.trim()

            if (cleaned.isEmpty()) {
                if (!previousWasBlank && result.isNotEmpty()) {
                    result.append("\n\n")
                }

                previousWasBlank = true
            } else {
                if (result.isNotEmpty() && !previousWasBlank) {
                    result.append(' ')
                }

                result.append(cleaned)
                previousWasBlank = false
            }
        }

        return result.toString().trim()
    }

    private fun getDisplayName(uri: Uri): String {
        var name = uri.lastPathSegment ?: "Documento.pdf"

        if (name.contains('/')) {
            name = name.substringAfterLast('/')
        }

        if (!name.endsWith(".pdf", ignoreCase = true)) {
            name += ".pdf"
        }

        return name
    }

    private fun sanitizeFileName(name: String): String {
        return name
            .replace(Regex("[^A-Za-z0-9À-ÿ._ -]"), "_")
            .ifBlank { "Documento.pdf" }
    }

    private fun uniquePdfFile(name: String): File {
        val baseName = name.removeSuffix(".pdf")
        var file = File(pdfFolder, "$baseName.pdf")
        var counter = 2

        while (file.exists()) {
            file = File(pdfFolder, "${baseName}_$counter.pdf")
            counter++
        }

        return file
    }

    // ---------------------------------------------------------
    // DETECÇÃO DE CAPÍTULOS
    // ---------------------------------------------------------

    private fun detectChapters(text: String): List<ChapterRange> {
        val detected = mutableListOf<Pair<String, Int>>()

        val chapterPattern = Regex(
            """(?im)^\s*(?:capítulo|capitulo|chapter|cap\.?|parte|part)\s+(?:\d+|[IVXLCDM]+)\b[^\n]*"""
        )

        for (match in chapterPattern.findAll(text)) {
            val title = match.value.trim()

            if (title.isNotBlank()) {
                detected.add(title to match.range.first)
            }
        }

        if (detected.isEmpty()) return emptyList()

        val unique = detected
            .distinctBy { it.second }
            .sortedBy { it.second }

        return unique.mapIndexed { index, item ->
            val end = if (index < unique.lastIndex) {
                unique[index + 1].second
            } else {
                text.length
            }

            ChapterRange(item.first, item.second, end)
        }
    }

    // ---------------------------------------------------------
    // SELEÇÃO DE INTERVALO
    // ---------------------------------------------------------

    private fun showRangeDialog() {
        if (documentText.isBlank()) {
            Toast.makeText(
                this,
                "Importe um PDF primeiro.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val options = if (chapterRanges.isNotEmpty()) {
            arrayOf(
                "Sem limite",
                "Selecionar capítulos (${chapterRanges.size} encontrados)"
            )
        } else {
            arrayOf(
                "Sem limite",
                "Selecionar páginas (${pageRanges.size} identificadas)"
            )
        }

        AlertDialog.Builder(this)
            .setTitle("Selecionar intervalo")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> applyRange(
                        0,
                        documentText.length,
                        "Sem limite",
                        "Sem limite"
                    )

                    1 -> {
                        if (chapterRanges.isNotEmpty()) {
                            showChapterRangeDialog()
                        } else {
                            showPageRangeDialog()
                        }
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showChapterRangeDialog() {
        if (chapterRanges.isEmpty()) return

        val names = chapterRanges.mapIndexed { index, chapter ->
            "${index + 1}. ${chapter.title}"
        }.toTypedArray()

        showTwoSelectorDialog(
            title = "Intervalo de capítulos",
            firstLabel = "Capítulo inicial",
            secondLabel = "Capítulo final",
            options = names
        ) { startIndex, endIndex ->
            val start = min(startIndex, endIndex)
            val end = max(startIndex, endIndex)

            val firstChapter = chapterRanges[start]
            val lastChapter = chapterRanges[end]

            applyRange(
                firstChapter.start,
                lastChapter.end,
                "Capítulos",
                "Capítulos ${start + 1}–${end + 1}"
            )
        }
    }

    private fun showPageRangeDialog() {
        if (pageRanges.isEmpty()) return

        val names = pageRanges.map {
            "Página ${it.page}"
        }.toTypedArray()

        showTwoSelectorDialog(
            title = "Intervalo de páginas",
            firstLabel = "Página inicial",
            secondLabel = "Página final",
            options = names
        ) { startIndex, endIndex ->
            val start = min(startIndex, endIndex)
            val end = max(startIndex, endIndex)

            val firstPage = pageRanges[start]
            val lastPage = pageRanges[end]

            applyRange(
                firstPage.start,
                lastPage.end,
                "Páginas",
                "Páginas ${firstPage.page}–${lastPage.page}"
            )
        }
    }

    private fun showTwoSelectorDialog(
        title: String,
        firstLabel: String,
        secondLabel: String,
        options: Array<String>,
        onApply: (Int, Int) -> Unit
    ) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), dp(4))
        }

        val firstTitle = makeText(firstLabel, 14f, true)
        val secondTitle = makeText(secondLabel, 14f, true)

        val firstSpinner = Spinner(this)
        val secondSpinner = Spinner(this)

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            options
        ).apply {
            setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
            )
        }

        firstSpinner.adapter = adapter
        secondSpinner.adapter = adapter

        firstSpinner.setSelection(0)
        secondSpinner.setSelection(options.lastIndex)

        container.addView(firstTitle)
        container.addView(firstSpinner)
        container.addView(space(12))
        container.addView(secondTitle)
        container.addView(secondSpinner)

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(container)
            .setPositiveButton("Aplicar") { _, _ ->
                onApply(
                    firstSpinner.selectedItemPosition,
                    secondSpinner.selectedItemPosition
                )
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun applyRange(
        requestedStart: Int,
        requestedEnd: Int,
        mode: String,
        description: String
    ) {
        if (documentText.isBlank()) return

        val start = requestedStart.coerceIn(0, documentText.length)
        val end = requestedEnd.coerceIn(start, documentText.length)

        if (end <= start || documentText.substring(start, end).isBlank()) {
            Toast.makeText(
                this,
                "Esse intervalo não contém texto para narrar.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val oldPosition = currentAbsolutePosition()

        stopSpeech()

        activeStartOffset = start
        activeEndOffset = end
        rangeMode = mode
        rangeDescription = description

        chunks = splitIntoChunks(
            documentText.substring(start, end),
            start
        )

        if (chunks.isEmpty()) {
            Toast.makeText(
                this,
                "Não há texto narrável nesse intervalo.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val targetPosition = if (
            oldPosition >= activeStartOffset &&
            oldPosition < activeEndOffset
        ) {
            oldPosition
        } else {
            activeStartOffset
        }

        restorePosition(targetPosition)
        saveCurrentProgress()
        refreshPlayer()

        Toast.makeText(
            this,
            "Intervalo selecionado: $rangeDescription",
            Toast.LENGTH_SHORT
        ).show()
    }

    // ---------------------------------------------------------
    // DIVISÃO EM TRECHOS
    // ---------------------------------------------------------

    private fun splitIntoChunks(
        text: String,
        baseOffset: Int
    ): List<SpeechChunk> {
        val result = mutableListOf<SpeechChunk>()

        if (text.isBlank()) return result

        val maxLength = 900
        var position = 0

        while (position < text.length) {
            var end = min(position + maxLength, text.length)

            if (end < text.length) {
                val paragraphBreak = text.lastIndexOf("\n\n", end)
                val sentenceBreak = text.lastIndexOfAny(
                    charArrayOf('.', '!', '?', ';', '\n'),
                    end
                )
                val spaceBreak = text.lastIndexOf(' ', end)

                end = when {
                    paragraphBreak > position + maxLength / 2 ->
                        paragraphBreak + 2

                    sentenceBreak > position + maxLength / 2 ->
                        sentenceBreak + 1

                    spaceBreak > position ->
                        spaceBreak + 1

                    else -> end
                }.coerceAtMost(text.length)
            }

            if (end <= position) {
                end = min(position + maxLength, text.length)
            }

            result.add(
                SpeechChunk(
                    text.substring(position, end),
                    baseOffset + position,
                    baseOffset + end
                )
            )

            position = end
        }

        return result
    }

    // ---------------------------------------------------------
    // POSIÇÃO E PROGRESSO
    // ---------------------------------------------------------

    private fun currentAbsolutePosition(): Int {
        if (chunks.isEmpty()) return activeStartOffset

        if (currentChunk >= chunks.size) return activeEndOffset

        val chunk = chunks[currentChunk]

        return (chunk.start + currentCharOffset)
            .coerceIn(activeStartOffset, activeEndOffset)
    }

    private fun restorePosition(position: Int) {
        if (chunks.isEmpty()) {
            currentChunk = 0
            currentCharOffset = 0
            return
        }

        val target = position.coerceIn(
            activeStartOffset,
            activeEndOffset
        )

        if (target >= activeEndOffset) {
            currentChunk = chunks.size
            currentCharOffset = 0
            return
        }

        val index = chunks.indexOfFirst {
            target >= it.start && target < it.end
        }

        currentChunk = if (index >= 0) index else 0

        currentCharOffset = (
            target - chunks[currentChunk].start
        ).coerceIn(
            0,
            chunks[currentChunk].text.length
        )
    }

    private fun saveCurrentProgress() {
        if (!::progressFile.isInitialized) return

        val file = currentFile ?: return

        try {
            val root = readProgressJson()
            val books = root.optJSONArray("books") ?: JSONArray()
            val newBooks = JSONArray()

            val position = currentAbsolutePosition()

            val percentage = if (documentText.isNotEmpty()) {
                (
                    position.toFloat() /
                        documentText.length.toFloat() * 100f
                    ).toInt().coerceIn(0, 100)
            } else {
                0
            }

            var replaced = false

            for (i in 0 until books.length()) {
                val book = books.optJSONObject(i) ?: continue

                if (book.optString("fileName") == file.name) {
                    newBooks.put(
                        JSONObject()
                            .put("name", currentBookName)
                            .put("fileName", file.name)
                            .put("progress", position)
                            .put("percentage", percentage)
                    )

                    replaced = true
                } else {
                    newBooks.put(book)
                }
            }

            if (!replaced) {
                newBooks.put(
                    JSONObject()
                        .put("name", currentBookName)
                        .put("fileName", file.name)
                        .put("progress", position)
                        .put("percentage", percentage)
                )
            }

            root.put("books", newBooks)
            progressFile.writeText(root.toString())
        } catch (_: Exception) {
            // Não interrompe a narração caso o salvamento falhe.
        }
    }

    private fun readProgressJson(): JSONObject {
        return try {
            if (progressFile.exists()) {
                JSONObject(progressFile.readText())
            } else {
                JSONObject()
            }
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun loadLibrary() {
        if (!::progressFile.isInitialized) return
        refreshLibraryUI()
    }

    private fun refreshLibraryUI() {
        if (!::libraryLayout.isInitialized) return

        libraryLayout.removeAllViews()

        val root = readProgressJson()
        val books = root.optJSONArray("books") ?: JSONArray()

        var displayed = 0

        for (i in 0 until books.length()) {
            val item = books.optJSONObject(i) ?: continue

            val name = item.optString("name", "Documento.pdf")
            val fileName = item.optString("fileName")
            val progress = item.optInt("percentage", 0)
            val file = File(pdfFolder, fileName)

            if (!file.exists()) continue

            displayed++

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(14), dp(14), dp(14))
                setBackgroundColor(panelColor)
            }

            card.addView(makeText(name, 16f, true))
            card.addView(space(6))

            card.addView(
                makeText(
                    "Progresso salvo: $progress%",
                    13f,
                    false,
                    mutedColor
                )
            )

            card.addView(space(10))

            card.addView(
                makeButton("Abrir documento") {
                    openSavedBook(file, item.optInt("progress", 0))
                },
                matchWidth(dp(44))
            )

            card.addView(space(6))

            card.addView(
                makeButton("Excluir da biblioteca") {
                    confirmDeleteBook(name, file)
                },
                matchWidth(dp(40))
            )

            libraryLayout.addView(card)
            libraryLayout.addView(space(12))
        }

        if (displayed == 0) {
            libraryLayout.addView(
                makeText(
                    "Sua biblioteca está vazia. Importe um PDF para começar.",
                    14f,
                    false,
                    mutedColor
                )
            )
        }
    }

    private fun openSavedBook(file: File, savedPosition: Int) {
        Thread {
            try {
                val extracted = extractDocument(file)

                runOnUiThread {
                    if (extracted.text.isBlank()) {
                        Toast.makeText(
                            this,
                            "Não foi possível extrair texto deste PDF.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@runOnUiThread
                    }

                    stopSpeech()

                    currentFile = file
                    currentBookName = file.name

                    documentText = extracted.text
                    pageRanges = extracted.pages
                    chapterRanges = detectChapters(documentText)

                    activeStartOffset = 0
                    activeEndOffset = documentText.length

                    rangeMode = "Sem limite"
                    rangeDescription = "Sem limite"

                    chunks = splitIntoChunks(documentText, 0)

                    restorePosition(
                        savedPosition.coerceIn(0, documentText.length)
                    )

                    showTab("Início")
                    refreshPlayer()

                    Toast.makeText(
                        this,
                        "Documento aberto. Progresso restaurado.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Erro ao abrir PDF: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun confirmDeleteBook(name: String, file: File) {
        AlertDialog.Builder(this)
            .setTitle("Excluir PDF")
            .setMessage("Deseja excluir \"$name\" da biblioteca?")
            .setPositiveButton("Excluir") { _, _ ->
                if (currentFile?.absolutePath == file.absolutePath) {
                    stopSpeech()

                    currentFile = null
                    currentBookName = ""
                    documentText = ""
                    chunks = emptyList()
                    pageRanges = emptyList()
                    chapterRanges = emptyList()

                    currentChunk = 0
                    currentCharOffset = 0
                    activeStartOffset = 0
                    activeEndOffset = 0
                }

                file.delete()
                removeSavedBook(file.name)

                refreshLibraryUI()
                refreshPlayer()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun removeSavedBook(fileName: String) {
        try {
            val root = readProgressJson()
            val oldBooks = root.optJSONArray("books") ?: JSONArray()
            val newBooks = JSONArray()

            for (i in 0 until oldBooks.length()) {
                val item = oldBooks.optJSONObject(i) ?: continue

                if (item.optString("fileName") != fileName) {
                    newBooks.put(item)
                }
            }

            root.put("books", newBooks)
            progressFile.writeText(root.toString())
        } catch (_: Exception) {
        }
    }

    // ---------------------------------------------------------
    // NARRAÇÃO
    // ---------------------------------------------------------

    private fun configureTtsListener() {
        textToSpeech.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {

                override fun onStart(utteranceId: String?) {
                    runOnUiThread {
                        isPlaying = true
                        refreshPlayer()
                    }
                }

                override fun onDone(utteranceId: String?) {
                    runOnUiThread {
                        if (!isPlaying) return@runOnUiThread

                        currentChunk++
                        currentCharOffset = 0
                        utteranceBaseOffset = 0

                        if (currentChunk >= chunks.size) {
                            currentChunk = chunks.size
                            isPlaying = false

                            saveCurrentProgress()
                            refreshPlayer()
                        } else {
                            saveCurrentProgress()
                            speakCurrentChunk()
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    runOnUiThread {
                        isPlaying = false
                        refreshPlayer()
                    }
                }

                override fun onRangeStart(
                    utteranceId: String?,
                    start: Int,
                    end: Int,
                    frame: Int
                ) {
                    runOnUiThread {
                        if (
                            currentChunk !in chunks.indices ||
                            !isPlaying
                        ) {
                            return@runOnUiThread
                        }

                        val chunk = chunks[currentChunk]

                        val absoluteStart = (
                            chunk.start + utteranceBaseOffset + start
                        ).coerceIn(0, documentText.length)

                        val absoluteEnd = (
                            chunk.start + utteranceBaseOffset + end
                        ).coerceIn(absoluteStart, documentText.length)

                        currentCharOffset = (
                            absoluteStart - chunk.start
                        ).coerceIn(0, chunk.text.length)

                        highlightText(absoluteStart, absoluteEnd)
                        updateProgress()
                    }
                }
            }
        )
    }

    private fun togglePlayback() {
        if (documentText.isBlank()) {
            Toast.makeText(
                this,
                "Importe um PDF primeiro.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (!isTtsReady) {
            Toast.makeText(
                this,
                "A voz ainda está sendo preparada.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (isPlaying) {
            pauseSpeech()
        } else {
            if (currentChunk >= chunks.size) {
                restorePosition(activeStartOffset)
            }

            startSpeech()
        }
    }

    private fun startSpeech() {
        if (chunks.isEmpty()) return

        isPlaying = true
        refreshPlayer()
        speakCurrentChunk()
    }

    private fun pauseSpeech() {
        if (!isPlaying) return

        textToSpeech.stop()
        isPlaying = false

        saveCurrentProgress()
        refreshPlayer()
    }

    private fun stopSpeech() {
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
        }

        isPlaying = false
    }

    private fun speakCurrentChunk() {
        if (!isPlaying || !isTtsReady) return

        if (currentChunk !in chunks.indices) {
            isPlaying = false
            refreshPlayer()
            saveCurrentProgress()
            return
        }

        val chunk = chunks[currentChunk]

        val start = currentCharOffset.coerceIn(
            0,
            chunk.text.length
        )

        if (start >= chunk.text.length) {
            currentChunk++
            currentCharOffset = 0
            speakCurrentChunk()
            return
        }

        val textToSpeak = chunk.text.substring(start)

        utteranceBaseOffset = start
        currentUtteranceId++

        val utteranceId = "audi_pdf_$currentUtteranceId"

        val params = Bundle().apply {
            putString(
                TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID,
                utteranceId
            )
        }

        textToSpeech.speak(
            textToSpeak,
            TextToSpeech.QUEUE_FLUSH,
            params,
            utteranceId
        )

        refreshPlayer()
    }

    private fun moveChunk(direction: Int) {
        if (chunks.isEmpty()) return

        val wasPlaying = isPlaying
        stopSpeech()

        currentChunk = (currentChunk + direction)
            .coerceIn(0, chunks.lastIndex)

        currentCharOffset = 0
        utteranceBaseOffset = 0

        saveCurrentProgress()
        refreshPlayer()

        if (wasPlaying) {
            startSpeech()
        }
    }

    // ---------------------------------------------------------
    // VELOCIDADE E VOZ
    // ---------------------------------------------------------

    private fun changeSpeed(amount: Float) {
        speechRate = (speechRate + amount).coerceIn(0.5f, 2.0f)

        if (isTtsReady) {
            textToSpeech.setSpeechRate(speechRate)
        }

        if (::speedText.isInitialized) {
            speedText.text = "Velocidade: %.1fx".format(
                Locale.getDefault(),
                speechRate
            )
        }

        if (isPlaying) {
            stopSpeech()
            startSpeech()
        }
    }

    private fun showVoiceDialog() {
        if (!isTtsReady) {
            Toast.makeText(
                this,
                "A voz ainda não está pronta.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val voices = textToSpeech.voices
            ?.filter { it.locale.language == "pt" }
            ?.sortedBy { it.name }
            ?: emptyList()

        if (voices.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Vozes disponíveis")
                .setMessage(
                    "O Android não disponibilizou outras vozes em português. " +
                        "Você pode instalar vozes adicionais nas configurações " +
                        "de conversão de texto em fala do aparelho."
                )
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val names = voices.map { voice ->
            val quality = when {
                voice.quality >= 500 -> "Alta qualidade"
                voice.quality >= 300 -> "Qualidade normal"
                else -> "Qualidade básica"
            }

            "${voice.name} — $quality"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Escolher voz em português")
            .setItems(names) { _, which ->
                val wasPlaying = isPlaying
                stopSpeech()

                val selected = voices[which]
                textToSpeech.voice = selected
                selectedVoiceName = selected.name

                Toast.makeText(
                    this,
                    "Voz selecionada: ${selected.name}",
                    Toast.LENGTH_SHORT
                ).show()

                if (wasPlaying) {
                    startSpeech()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ---------------------------------------------------------
    // DESTAQUE DO TEXTO
    // ---------------------------------------------------------

    private fun highlightText(start: Int, end: Int) {
        if (!::readingText.isInitialized || documentText.isEmpty()) return

        val safeStart = start.coerceIn(0, documentText.length)
        val safeEnd = end.coerceIn(safeStart, documentText.length)

        val spannable = SpannableString(documentText)

        if (safeEnd > safeStart) {
            spannable.setSpan(
                BackgroundColorSpan(Color.rgb(41, 105, 65)),
                safeStart,
                safeEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        readingText.text = spannable

        if (safeStart < documentText.length) {
            readingText.post {
                val layout = readingText.layout ?: return@post
                val line = layout.getLineForOffset(safeStart)
                val y = layout.getLineTop(line)

                readingText.scrollTo(0, max(0, y - dp(80)))
            }
        }
    }

    // ---------------------------------------------------------
    // ATUALIZAÇÃO DO PLAYER
    // ---------------------------------------------------------

    private fun refreshPlayer() {
        if (!::bookText.isInitialized) return

        if (currentFile == null || documentText.isBlank()) {
            bookText.text = "Nenhum PDF selecionado"
            rangeText.text = "Intervalo: Sem limite"
            progressText.text = "Importe um PDF para começar"
            progressBar.progress = 0
            playButton.text = "▶ Reproduzir"

            if (::readingText.isInitialized) {
                readingText.text =
                    "O texto extraído do PDF aparecerá aqui."
            }

            return
        }

        bookText.text = currentBookName
        rangeText.text = "Intervalo: $rangeDescription"

        if (::readingText.isInitialized) {
            readingText.text = documentText
        }

        playButton.text = if (isPlaying) {
            "Ⅱ Pausar"
        } else {
            "▶ Reproduzir"
        }

        if (::speedText.isInitialized) {
            speedText.text = "Velocidade: %.1fx".format(
                Locale.getDefault(),
                speechRate
            )
        }

        updateProgress()
    }

    private fun updateProgress() {
        if (!::progressText.isInitialized) return

        if (documentText.isEmpty()) {
            progressText.text = "Sem documento"
            progressBar.progress = 0
            return
        }

        val position = currentAbsolutePosition()

        val percentage = (
            position.toFloat() /
                documentText.length.toFloat() * 100f
            ).toInt().coerceIn(0, 100)

        progressBar.progress = percentage

        val positionDescription = when {
            position >= documentText.length -> "Concluído"
            position <= 0 -> "Início"
            else -> "$percentage% do documento"
        }

        val chunkDescription = if (chunks.isNotEmpty()) {
            val shownChunk = if (currentChunk >= chunks.size) {
                chunks.size
            } else {
                currentChunk + 1
            }

            "Trecho $shownChunk de ${chunks.size}"
        } else {
            "Sem trechos"
        }

        progressText.text = "$positionDescription • $chunkDescription"
        saveCurrentProgress()
    }

    // ---------------------------------------------------------
    // AUXILIARES DE INTERFACE
    // ---------------------------------------------------------

    private fun makeText(
        value: String,
        size: Float,
        bold: Boolean,
        color: Int = whiteColor
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)

            if (bold) {
                typeface = Typeface.DEFAULT_BOLD
            }
        }
    }

    private fun makeButton(
        label: String,
        action: () -> Unit
    ): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 14f
            setTextColor(whiteColor)
            setBackgroundColor(panelColor)

            setOnClickListener {
                action()
            }
        }
    }

    private fun space(heightDp: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                1,
                dp(heightDp)
            )
        }
    }

    private fun matchWidth(height: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            height
        )
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}