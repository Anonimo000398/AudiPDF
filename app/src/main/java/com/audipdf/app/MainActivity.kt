
package com.audipdf.app

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private val backgroundColor = Color.rgb(10, 12, 11)
    private val panelColor = Color.rgb(23, 27, 24)
    private val greenColor = Color.rgb(50, 220, 120)
    private val textColor = Color.rgb(245, 248, 246)
    private val mutedColor = Color.rgb(155, 166, 158)

    private val executor = Executors.newSingleThreadExecutor()

    private lateinit var statusText: TextView
    private lateinit var fileNameText: TextView
    private lateinit var contentText: TextView
    private lateinit var importButton: Button

    private val pdfPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) importPdf(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor

        PDFBoxResourceLoader.init(applicationContext)

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(backgroundColor)
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(30))
            setBackgroundColor(backgroundColor)
        }

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

        val hero = makePanel()
        hero.addView(makeText("SEUS DOCUMENTOS, SUA LIBERDADE", 10f, greenColor, true))
        hero.addView(
            makeText("Transforme seus PDFs em conhecimento.", 26f, textColor, true).apply {
                setPadding(0, dp(12), 0, dp(10))
            }
        )
        hero.addView(
            makeText(
                "Importe um documento e extraia o texto para preparar sua experiência de leitura em áudio.",
                14f,
                mutedColor
            )
        )

        root.addView(
            hero,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(26) }
        )

        root.addView(
            makeText("Sua biblioteca", 22f, textColor, true).apply {
                setPadding(0, dp(28), 0, dp(14))
            }
        )

        val library = makePanel()
        library.addView(makeText("DOCUMENTO ATUAL", 10f, greenColor, true))

        fileNameText = makeText("Nenhum PDF selecionado", 17f, textColor, true).apply {
            setPadding(0, dp(10), 0, dp(8))
        }
        library.addView(fileNameText)

        statusText = makeText("Escolha um PDF para começar.", 13f, mutedColor).apply {
            setPadding(0, 0, 0, dp(16))
        }
        library.addView(statusText)

        importButton = Button(this).apply {
            text = "＋  Importar PDF"
            textSize = 15f
            isAllCaps = false
            setTextColor(Color.rgb(8, 20, 12))
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBackground(greenColor, 14)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener {
                pdfPicker.launch(arrayOf("application/pdf"))
            }
        }

        library.addView(
            importButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(54)
            )
        )
        root.addView(library)

        root.addView(
            makeText("Conteúdo do documento", 21f, textColor, true).apply {
                setPadding(0, dp(28), 0, dp(14))
            }
        )

        val contentPanel = makePanel()
        contentText = makeText(
            "O texto do PDF aparecerá aqui depois da importação.",
            14f,
            mutedColor
        ).apply {
            isTextSelectable = true
            setLineSpacing(dp(5).toFloat(), 1f)
        }
        contentPanel.addView(contentText)
        root.addView(contentPanel)

        root.addView(
            makeText("FOCO  •  CONHECIMENTO  •  LIBERDADE", 10f, mutedColor, true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(28), 0, 0)
            }
        )

        scrollView.addView(root)
        setContentView(scrollView)
    }

    private fun importPdf(uri: Uri) {
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

                runOnUiThread {
                    importButton.isEnabled = true

                    if (extractedText.isBlank()) {
                        statusText.text = "Nenhum texto encontrado neste PDF."
                        contentText.text =
                            "O documento pode conter apenas páginas digitalizadas como imagens. O reconhecimento por OCR ainda não está implementado."
                    } else {
                        val wordCount = extractedText.trim()
                            .split(Regex("\\s+"))
                            .size

                        statusText.text =
                            "Texto extraído • $wordCount ${if (wordCount == 1) "palavra" else "palavras"}"

                        contentText.text = extractedText.trim()
                        contentText.setTextColor(textColor)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    importButton.isEnabled = true
                    statusText.text = "Não foi possível ler este PDF."
                    contentText.text =
                        "Verifique se o arquivo é válido e tente novamente.\n\nDetalhes: ${e.localizedMessage ?: "erro desconhecido"}"
                    contentText.setTextColor(mutedColor)
                }
            }
        }
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
        executor.shutdown()
        super.onDestroy()
    }
}
