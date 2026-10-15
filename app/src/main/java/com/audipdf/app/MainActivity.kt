package com.audipdf.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private val backgroundColor = Color.rgb(12, 12, 15)
    private val panelColor = Color.rgb(23, 23, 28)
    private val accentColor = Color.rgb(255, 55, 75)
    private val textColor = Color.rgb(245, 245, 248)
    private val mutedColor = Color.rgb(155, 155, 168)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor
        window.decorView.systemUiVisibility = 0

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(20))
            setBackgroundColor(backgroundColor)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        header.addView(makeText(
            "AudiPDF",
            30f,
            textColor,
            true
        ))

        header.addView(makeText(
            "Seus documentos. Sua experiência de áudio.",
            14f,
            mutedColor
        ).apply {
            setPadding(0, dp(6), 0, 0)
        })

        root.addView(header)

        val welcome = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(22))
            setBackgroundColor(panelColor)
        }

        welcome.addView(makeText(
            "OUÇA SEUS PDFs",
            12f,
            accentColor,
            true
        ))

        welcome.addView(makeText(
            "Transforme leitura em áudio.",
            23f,
            textColor,
            true
        ).apply {
            setPadding(0, dp(12), 0, dp(8))
        })

        welcome.addView(makeText(
            "Importe um documento e acompanhe o conteúdo narrado, no seu ritmo.",
            14f,
            mutedColor
        ))

        val welcomeParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(30)
        }

        root.addView(welcome, welcomeParams)

        root.addView(makeText(
            "Sua biblioteca",
            21f,
            textColor,
            true
        ).apply {
            setPadding(0, dp(30), 0, dp(12))
        })

        val emptyLibrary = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(28), dp(20), dp(28))
            setBackgroundColor(panelColor)
        }

        emptyLibrary.addView(makeText(
            "♫",
            38f,
            accentColor,
            true
        ).apply {
            gravity = Gravity.CENTER
        })

        emptyLibrary.addView(makeText(
            "Sua próxima leitura começa aqui",
            17f,
            textColor,
            true
        ).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(8))
        })

        emptyLibrary.addView(makeText(
            "Em breve você poderá importar PDFs e ouvi-los por aqui.",
            13f,
            mutedColor
        ).apply {
            gravity = Gravity.CENTER
        })

        root.addView(emptyLibrary)

        root.addView(makeText(
            "LEITURA • ÁUDIO • FOCO",
            11f,
            mutedColor,
            true
        ).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, 0)
        })

        setContentView(root)
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
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
