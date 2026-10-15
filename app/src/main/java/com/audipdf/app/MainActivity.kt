package com.audipdf.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private val backgroundColor = Color.rgb(10, 12, 11)
    private val panelColor = Color.rgb(23, 27, 24)
    private val greenColor = Color.rgb(50, 220, 120)
    private val textColor = Color.rgb(245, 248, 246)
    private val mutedColor = Color.rgb(155, 166, 158)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(backgroundColor)
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(28))
            setBackgroundColor(backgroundColor)
        }

        // Cabeçalho com a logo oficial
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.audipdf_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Logo do AudiPDF"
        }

        header.addView(
            logo,
            LinearLayout.LayoutParams(dp(58), dp(58))
        )

        val brandText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }

        brandText.addView(makeText(
            "AudiPDF",
            25f,
            textColor,
            true
        ))

        brandText.addView(makeText(
            "SUA BIBLIOTECA EM ÁUDIO",
            10f,
            greenColor,
            true
        ).apply {
            setPadding(0, dp(4), 0, 0)
        })

        header.addView(brandText)
        root.addView(header)

        // Área de apresentação
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
            setBackgroundColor(panelColor)
        }

        hero.addView(makeText(
            "LEIA MENOS COM OS OLHOS.",
            11f,
            greenColor,
            true
        ))

        hero.addView(makeText(
            "Seus PDFs.\nSua experiência de áudio.",
            27f,
            textColor,
            true
        ).apply {
            setPadding(0, dp(12), 0, dp(12))
        })

        hero.addView(makeText(
            "Transforme seus documentos em uma experiência de leitura mais prática. Importe um PDF e prepare-se para ouvir.",
            14f,
            mutedColor
        ))

        root.addView(hero, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(28)
        })

        // Biblioteca
        root.addView(makeText(
            "Sua biblioteca",
            22f,
            textColor,
            true
        ).apply {
            setPadding(0, dp(30), 0, dp(14))
        })

        val library = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(28), dp(18), dp(28))
            setBackgroundColor(panelColor)
        }

        val musicSymbol = makeText(
            "♫",
            42f,
            greenColor,
            true
        ).apply {
            gravity = Gravity.CENTER
        }

        library.addView(musicSymbol)

        library.addView(makeText(
            "Sua próxima leitura começa aqui",
            17f,
            textColor,
            true
        ).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(8))
        })

        library.addView(makeText(
            "Seus documentos aparecerão nesta biblioteca.",
            13f,
            mutedColor
        ).apply {
            gravity = Gravity.CENTER
        })

        root.addView(library)

        val footer = makeText(
            "FOCO  •  CONHECIMENTO  •  LIBERDADE",
            10f,
            mutedColor,
            true
        ).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(26), 0, 0)
        }

        root.addView(footer)

        scrollView.addView(root)
        setContentView(scrollView)
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
            if (bold) {
                typeface = Typeface.DEFAULT_BOLD
            }
            setLineSpacing(dp(3).toFloat(), 1f)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}