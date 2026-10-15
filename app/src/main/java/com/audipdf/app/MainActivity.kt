
package com.audipdf.app

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private val backgroundColor = Color.rgb(10, 12, 11)
    private val panelColor = Color.rgb(23, 27, 24)
    private val greenColor = Color.rgb(50, 220, 120)
    private val textColor = Color.rgb(245, 248, 246)
    private val mutedColor = Color.rgb(155, 166, 158)
    private val highlightColor = Color.rgb(35, 83, 53)

    private val executor = Executors.newSingleThreadExecutor()

    private lateinit var statusText: TextView
    private lateinit var fileNameText: TextView
    private lateinit var contentText: TextView
    private lateinit var importButton: Button
    private lateinit var playButton: Button
    private lateinit var progressBar: SeekBar
    private lateinit var progressText: TextView
    private lateinit var speedText: TextView
    private lateinit var voiceButton: Button
    private lateinit var readingIndicator: TextView

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var isPlaying = false
    private var isPaused = false

    private var documentText = ""
    private var chunks = listOf<SpeechChunk>()
    private var currentChunkIndex = 0
    private var currentCharOffset = 0
    private var activeUtteranceId = ""
    private var sessionId = 0
    private var utteranceBaseOffset = 0

    private var speechRate = 1.0f
    private val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    private var speedIndex = 2

    private var availableVoices = listOf<android.speech.tts.Voice>()
    private var voiceIndex = 0

    private data class SpeechChunk(
        val text: String,
        val start: Int,
        val end: Int
    )

    private val pdfPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) importPdf(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor

        PDFBoxResourceLoader.init(applicationContext)

        buildInterface()
        initializeVoice()
    }

    private fun buildInterface() {
        val scrollView = ScrollView(this).apply {
            setBackgroundColor(backgroundColor)
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(30))
            setBackgroundColor(backgroundColor)
        }

        // Cabeçalho
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.audipdf_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Logo do AudiPDF"
        }

        header.addView(logo, LinearLayout.LayoutParams(dp(58), dp(58)))

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }

        brand.addView(makeText("AudiPDF", 25f, textColor, true))
        brand.addView(
            makeText("SUA BIBLIOTECA EM ÁUDIO", 10f, greenColor, true).apply {
                setPadding(0, dp(4), 0, 0)
            }
        )

        header.addView(brand)
        root.addView(header)

        // Apresentação
        val hero = makePanel()
        hero.addView(makeText("ESCUTE. APRENDA. EVOLUA.", 10f, greenColor, true))

        hero.addView(
            makeText("Seu PDF. Sua experiência de áudio.", 25f, textColor, true).apply {
                setPadding(0, dp(12), 0, dp(10))
            }
        )

        hero.addView(
            makeText(
                "Transforme documentos em uma experiência de leitura em voz alta.",
                14f,
                mutedColor
            )
        )

        root.addView(
            hero,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(24)
            }
        )

        // Biblioteca
        root.addView(
            makeText("Sua biblioteca", 22f, textColor, true).apply {
                setPadding(0, dp(26), 0, dp(14))
            }
        )

        val library = makePanel()
        library.addView(makeText("DOCUMENTO ATUAL", 10f, greenColor, true))

        fileNameText = makeText(
            "Nenhum PDF selecionado",
            17f,
            textColor,
            true
        ).apply {
            setPadding(0, dp(10), 0, dp(8))
        }
        library.addView(fileNameText)

        statusText = makeText("Importe um PDF para começar.", 13f, mutedColor)
        library.addView(statusText)

        importButton = makeButton("＋  Importar PDF", greenColor, Color.rgb(8, 20, 12)) {
            pdfPicker.launch(arrayOf("application/pdf"))
        }

        library.addView(
            importButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
            ).apply {
                topMargin = dp(16)
            }
        )

        root.addView(library)

        // Player
        root.addView(
            makeText("Player AudiPDF", 22f, textColor, true).apply {
                setPadding(0, dp(26), 0, dp(14))
            }
        )

        val player = makePanel()

        player.addView(makeText("TOCANDO DO SEU JEITO", 10f, greenColor, true))

        player.addView(
            makeText("Leitura narrada", 20f, textColor, true).apply {
                setPadding(0, dp(8), 0, dp(4))
            }
        )

        player.addView(
            makeText(
                "Acompanhe o texto enquanto a voz faz a leitura.",
                12f,
                mutedColor
            )
        )

        progressBar = SeekBar(this).apply {
            max = 1
            progress = 0
            progressTintList =
                android.content.res.ColorStateList.valueOf(greenColor)
            thumbTintList =
                android.content.res.ColorStateList.valueOf(greenColor)
            isEnabled = false
        }

        progressBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                if (fromUser && chunks.isNotEmpty()) {
                    progressText.text = "Trecho ${progress + 1} de ${chunks.size}"
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (chunks.isEmpty()) return

                val resumeAfterSeek = isPlaying
                stopSpeech()

                currentChunkIndex =
                    progressBar.progress.coerceIn(0, chunks.lastIndex)
                currentCharOffset = 0

                updateProgress()
                clearReadingHighlight()

                if (resumeAfterSeek) {
                    startSpeech()
                } else {
                    statusText.text = "Trecho selecionado."
                }
            }
        })

        player.addView(
            progressBar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(42)
            ).apply {
                topMargin = dp(12)
            }
        )

        progressText = makeText("Nenhum trecho carregado", 12f, mutedColor)
        player.addView(progressText)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(8))
        }

        controls.addView(
            makeButton("−1 trecho", panelColor, textColor) {
                moveChunk(-1)
            },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                marginEnd = dp(6)
            }
        )

        playButton = makeButton("▶  Play", greenColor, Color.rgb(8, 20, 12)) {
            togglePlayback()
        }

        controls.addView(
            playButton,
            LinearLayout.LayoutParams(0, dp(52), 1.5f).apply {
                marginEnd = dp(6)
            }
        )

        controls.addView(
            makeButton("+1 trecho", panelColor, textColor) {
                moveChunk(1)
            },
            LinearLayout.LayoutParams(0, dp(48), 1f)
        )

        player.addView(controls)

        player.addView(
            makeText("VELOCIDADE", 10f, greenColor, true).apply {
                setPadding(0, dp(14), 0, dp(8))
            }
        )

        speedText = makeText("1,0×", 16f, textColor, true)
        player.addView(speedText)

        val speedRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        speedRow.addView(
            makeButton("−", panelColor, textColor) {
                changeSpeed(-1)
            },
            LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                marginEnd = dp(8)
            }
        )

        speedRow.addView(
            makeButton("+", panelColor, textColor) {
                changeSpeed(1)
            },
            LinearLayout.LayoutParams(0, dp(44), 1f)
        )

        player.addView(
            speedRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
        )

        voiceButton = makeButton("Voz: carregando...", panelColor, textColor) {
            changeVoice()
        }

        player.addView(
            voiceButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(46)
            ).apply {
                topMargin = dp(12)
            }
        )

        root.addView(player)

        // Área de leitura organizada
        root.addView(
            makeText("Leitura do documento", 22f, textColor, true).apply {
                setPadding(0, dp(28), 0, dp(8))
            }
        )

        root.addView(
            makeText(
                "Parágrafos organizados para acompanhar a narração.",
                13f,
                mutedColor
            ).apply {
                setPadding(0, 0, 0, dp(14))
            }
        )

        val readingPanel = makePanel()

        readingIndicator = makeText(
            "■  PRONTO PARA LER",
            11f,
            greenColor,
            true
        ).apply {
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = roundedBackground(Color.rgb(20, 48, 31), 8)
        }

        readingPanel.addView(
            readingIndicator,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(16)
            }
        )

        contentText = makeText(
            "Importe um PDF para visualizar o texto organizado aqui.",
            15f,
            mutedColor
        ).apply {
            setTextIsSelectable(true)
            setLineSpacing(dp(7).toFloat(), 1.08f)
        }

        readingPanel.addView(contentText)
        root.addView(readingPanel)

        root.addView(
            makeText(
                "FOCO  •  CONHECIMENTO  •  LIBERDADE",
                10f,
                mutedColor,
                true
            ).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(28), 0, 0)
            }
        )

        scrollView.addView(root)
        setContentView(scrollView)
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
                val languageResult = engine.setLanguage(Locale("pt", "BR"))

                if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
                    languageResult == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    engine.setLanguage(Locale.getDefault())
                    Toast.makeText(
                        this,
                        "Português pode não estar instalado no mecanismo de voz.",
                        Toast.LENGTH_LONG
                    ).show()
                }

                availableVoices = engine.voices
                    ?.filter { it.locale.language == "pt" }
                    ?.sortedBy { it.name }
                    ?: emptyList()

                if (availableVoices.isNotEmpty()) {
                    val brazilianIndex = availableVoices.indexOfFirst {
                        it.locale.country.equals("BR", ignoreCase = true)
                    }

                    voiceIndex = if (brazilianIndex >= 0) brazilianIndex else 0
                    engine.voice = availableVoices[voiceIndex]
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
                                    currentChunkIndex !in chunks.indices
                                ) return@runOnUiThread

                                val chunk = chunks[currentChunkIndex]
                                val absoluteStart =
                                    (chunk.start + utteranceBaseOffset + start)
                                        .coerceIn(0, documentText.length)

                                val absoluteEnd =
                                    (chunk.start + utteranceBaseOffset + end)
                                        .coerceIn(absoluteStart, documentText.length)

                                currentCharOffset = (utteranceBaseOffset + end)
                                    .coerceIn(0, chunk.text.length)

                                highlightReadingPosition(absoluteStart, absoluteEnd)
                            }
                        }

                        override fun onDone(utteranceId: String?) {
                            runOnUiThread {
                                if (utteranceId != activeUtteranceId || !isPlaying) {
                                    return@runOnUiThread
                                }

                                currentChunkIndex++
                                currentCharOffset = 0

                                if (currentChunkIndex >= chunks.size) {
                                    isPlaying = false
                                    isPaused = false
                                    playButton.text = "▶  Play"
                                    statusText.text = "Leitura concluída."
                                    readingIndicator.text = "■  LEITURA CONCLUÍDA"
                                    updateProgress()
                                } else {
                                    updateProgress()
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
                statusText.text = "Voz pronta. Importe um PDF para começar."
            }
        }
    }

    private fun importPdf(uri: Uri) {
        stopSpeech()

        fileNameText.text = getFileName(uri)
        statusText.text = "Lendo o documento..."
        contentText.text = "Extraindo texto do PDF. Aguarde..."
        contentText.setTextColor(mutedColor)
        importButton.isEnabled = false

        executor.execute {
            try {
                val extractedText = contentResolver.openInputStream(uri)?.use { input ->
                    PDDocument.load(input).use { document ->
                        PDFTextStripper().getText(document)
                    }
                } ?: throw IllegalStateException("Não foi possível abrir o arquivo.")

                val organizedText = formatDocumentText(extractedText)

                runOnUiThread {
                    importButton.isEnabled = true
                    documentText = organizedText

                    if (documentText.isBlank()) {
                        chunks = emptyList()
                        currentChunkIndex = 0
                        currentCharOffset = 0
                        progressBar.isEnabled = false
                        progressText.text = "Nenhum trecho disponível"
                        statusText.text = "Nenhum texto encontrado neste PDF."
                        contentText.text =
                            "O documento pode conter páginas digitalizadas como imagens. OCR ainda não está implementado."
                        contentText.setTextColor(mutedColor)
                        readingIndicator.text = "■  SEM TEXTO DISPONÍVEL"
                        updateProgress()
                    } else {
                        chunks = splitIntoChunks(documentText)
                        currentChunkIndex = 0
                        currentCharOffset = 0

                        contentText.text = documentText
                        contentText.setTextColor(textColor)

                        val words = documentText.split(Regex("\\s+")).size
                        statusText.text = "PDF pronto • $words palavras"
                        readingIndicator.text = "■  PRONTO PARA LER"

                        updateProgress()
                        clearReadingHighlight()

                        if (!ttsReady) {
                            statusText.text = "PDF pronto. Aguardando a voz do Android..."
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    importButton.isEnabled = true
                    statusText.text = "Não foi possível ler este PDF."
                    contentText.text =
                        "Verifique se o arquivo é válido e tente novamente.\n\nDetalhes: ${e.localizedMessage ?: "erro desconhecido"}"
                    contentText.setTextColor(mutedColor)
                    readingIndicator.text = "■  ERRO AO ABRIR PDF"
                }
            }
        }
    }

    private fun formatDocumentText(rawText: String): String {
        val normalized = rawText
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
                val bestBreak = when {
                    paragraphBreak > start + maxLength / 2 -> paragraphBreak
                    sentenceBreak > start + maxLength / 2 -> sentenceBreak + 1
                    spaceBreak > start + maxLength / 2 -> spaceBreak
                    else -> end
                }
                end = bestBreak.coerceAtLeast(start + 1)
            }

            while (end > start && text[end - 1].isWhitespace()) end--

            if (end <= start) {
                end = (start + maxLength).coerceAtMost(text.length)
            }

            result.add(
                SpeechChunk(
                    text = text.substring(start, end),
                    start = start,
                    end = end
                )
            )

            start = end
        }

        return result
    }

    private fun togglePlayback() {
        if (chunks.isEmpty()) {
            Toast.makeText(this, "Importe um PDF primeiro.", Toast.LENGTH_SHORT).show()
            return
        }

        if (!ttsReady || tts == null) {
            Toast.makeText(this, "A voz ainda está iniciando.", Toast.LENGTH_SHORT).show()
            return
        }

        if (isPlaying) {
            pauseSpeech()
        } else {
            if (currentChunkIndex >= chunks.size) {
                currentChunkIndex = 0
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
    }

    private fun stopSpeech() {
        sessionId++
        isPlaying = false
        isPaused = false
        tts?.stop()
        activeUtteranceId = ""
        currentChunkIndex = 0
        currentCharOffset = 0

        if (::playButton.isInitialized) {
            playButton.text = "▶  Play"
        }
        if (::readingIndicator.isInitialized) {
            readingIndicator.text = "■  PRONTO PARA LER"
        }
    }

    private fun speakCurrentChunk() {
        val engine = tts ?: return
        if (!ttsReady || !isPlaying || currentChunkIndex !in chunks.indices) return

        val chunk = chunks[currentChunkIndex]
        val baseOffset = currentCharOffset.coerceIn(0, chunk.text.length)

        if (baseOffset >= chunk.text.length) {
            currentChunkIndex++
            currentCharOffset = 0

            if (currentChunkIndex < chunks.size) {
                updateProgress()
                speakCurrentChunk()
            } else {
                isPlaying = false
                playButton.text = "▶  Play"
                statusText.text = "Leitura concluída."
                readingIndicator.text = "■  LEITURA CONCLUÍDA"
            }
            return
        }

        utteranceBaseOffset = baseOffset
        activeUtteranceId =
            "audi_${sessionId}_${currentChunkIndex}_${System.nanoTime()}"

        engine.setSpeechRate(speechRate)
        engine.speak(
            chunk.text.substring(baseOffset),
            TextToSpeech.QUEUE_FLUSH,
            null,
            activeUtteranceId
        )

        updateProgress()
    }

    private fun moveChunk(direction: Int) {
        if (chunks.isEmpty()) {
            Toast.makeText(this, "Importe um PDF primeiro.", Toast.LENGTH_SHORT).show()
            return
        }

        val resumeAfterMove = isPlaying
        stopSpeech()

        currentChunkIndex = (currentChunkIndex + direction)
            .coerceIn(0, chunks.lastIndex)
        currentCharOffset = 0

        updateProgress()
        clearReadingHighlight()
        statusText.text = "Trecho ${currentChunkIndex + 1} selecionado."

        if (resumeAfterMove) startSpeech()
    }

    private fun changeSpeed(direction: Int) {
        speedIndex = (speedIndex + direction).coerceIn(0, speeds.lastIndex)
        speechRate = speeds[speedIndex]
        tts?.setSpeechRate(speechRate)
        speedText.text = "${speechRate.toString().replace('.', ',')}×"
    }

    private fun changeVoice() {
        if (availableVoices.isEmpty()) {
            Toast.makeText(
                this,
                "Nenhuma voz em português foi encontrada no mecanismo do Android.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        voiceIndex = (voiceIndex + 1) % availableVoices.size
        tts?.voice = availableVoices[voiceIndex]
        updateVoiceButton()

        Toast.makeText(this, "Voz alterada.", Toast.LENGTH_SHORT).show()
    }

    private fun updateVoiceButton() {
        voiceButton.text = if (availableVoices.isEmpty()) {
            "Voz padrão do Android"
        } else {
            "Voz ${voiceIndex + 1} de ${availableVoices.size}  ▾"
        }
    }

    private fun highlightReadingPosition(start: Int, end: Int) {
        if (documentText.isEmpty()) return

        val safeStart = start.coerceIn(0, documentText.length)
        val safeEnd = end.coerceIn(safeStart, documentText.length)
        if (safeEnd <= safeStart) return

        val spannable = SpannableString(documentText)
        spannable.setSpan(
            BackgroundColorSpan(highlightColor),
            safeStart,
            safeEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannable.setSpan(
            ForegroundColorSpan(greenColor),
            safeStart,
            safeEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        contentText.text = spannable
        readingIndicator.text = "■  LENDO AGORA"
    }

    private fun clearReadingHighlight() {
        if (!::contentText.isInitialized) return
        contentText.text = documentText
    }

    private fun updateProgress() {
        if (chunks.isEmpty()) {
            progressBar.max = 1
            progressBar.progress = 0
            progressBar.isEnabled = false
            progressText.text = "Nenhum trecho carregado"
            return
        }

        progressBar.max = (chunks.size - 1).coerceAtLeast(1)
        progressBar.progress = currentChunkIndex.coerceIn(0, progressBar.max)
        progressBar.isEnabled = chunks.size > 1
        progressText.text = "Trecho ${currentChunkIndex + 1} de ${chunks.size}"
    }

    private fun getFileName(uri: Uri): String {
        var name = "Documento PDF"

        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && index >= 0) {
                name = cursor.getString(index) ?: name
            }
        }

        return name
    }

    private fun makePanel(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(20))
            background = roundedBackground(panelColor, 18)
        }
    }

    private fun makeButton(
        label: String,
        buttonColor: Int,
        foregroundColor: Int,
        action: () -> Unit
    ): Button {
        return Button(this).apply {
            text = label
            textSize = 14f
            isAllCaps = false
            setTextColor(foregroundColor)
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBackground(buttonColor, 14)
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setOnClickListener { action() }
        }
    }

    private fun makeText(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setLineSpacing(dp(3).toFloat(), 1f)
        }
    }

    private fun roundedBackground(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        stopSpeech()
        tts?.shutdown()
        executor.shutdown()
        super.onDestroy()
    }
}
