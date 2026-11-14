package com.sakinah.deen.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.sakinah.deen.ui.viewmodel.WealthUiState
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Generates an on-device Zakat & Sadaqah summary PDF using Android's [PdfDocument]
 * and launches the system share sheet via [FileProvider] without any network dependency.
 */
object ZakatPdfExporter {

    fun exportAndShare(context: Context, state: WealthUiState): Result<File> = runCatching {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, "sakinah_zakat_summary.pdf")

        val currency = NumberFormat.getCurrencyInstance(Locale.US)
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = Paint().apply {
            color = Color.rgb(11, 59, 44)
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = Paint().apply {
            color = Color.rgb(100, 116, 139)
            textSize = 11f
        }
        val headingPaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            textSize = 12f
        }
        val accentPaint = Paint().apply {
            color = Color.rgb(16, 185, 129)
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val dividerPaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }

        var y = 64f
        canvas.drawText("Sakinah — Zakat Calculation Summary", 48f, y, titlePaint)
        y += 22f
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy · HH:mm"))
        canvas.drawText("Generated on $stamp", 48f, y, subtitlePaint)
        y += 24f
        canvas.drawLine(48f, y, 547f, y, dividerPaint)
        y += 32f

        val standard = if (state.isGoldStandard) "Gold (85g)" else "Silver (595g)"
        canvas.drawText("Nisab Benchmark: $standard", 48f, y, headingPaint)
        y += 20f
        canvas.drawText(
            "Threshold Value: ${currency.format(state.nisabThreshold.toDouble())}",
            48f,
            y,
            bodyPaint
        )
        y += 32f

        canvas.drawText("Declared Assets & Liabilities", 48f, y, headingPaint)
        y += 24f

        val rows = listOf(
            "Cash in Hand & Bank" to state.cashInBank,
            "Gold Value Held" to state.goldHeld,
            "Silver Value Held" to state.silverHeld,
            "Trade Inventory" to state.tradeInventory,
            "Stocks & Investments" to state.stocksValue,
            "Short-Term Debts (Deducted)" to -state.shortTermDebts,
        )
        rows.forEach { (label, amount) ->
            canvas.drawText(label, 48f, y, bodyPaint)
            canvas.drawText(currency.format(amount.toDouble()), 380f, y, bodyPaint)
            y += 22f
        }

        y += 10f
        canvas.drawLine(48f, y, 547f, y, dividerPaint)
        y += 28f

        canvas.drawText("Gross Assets", 48f, y, bodyPaint)
        canvas.drawText(currency.format(state.grossAssets.toDouble()), 380f, y, headingPaint)
        y += 22f

        canvas.drawText("Net Zakatable Wealth", 48f, y, bodyPaint)
        canvas.drawText(currency.format(state.netEligible.toDouble()), 380f, y, headingPaint)
        y += 28f

        val statusLabel = if (state.isEligible) "Eligible — 2.5% Zakat Due" else "Below Nisab Threshold ($0.00)"
        canvas.drawText(statusLabel, 48f, y, headingPaint)
        y += 26f
        canvas.drawText("Zakat Payable: ${currency.format(state.zakatPayable.toDouble())}", 48f, y, accentPaint)
        y += 40f

        canvas.drawLine(48f, y, 547f, y, dividerPaint)
        y += 24f
        canvas.drawText(
            "Monthly Micro-Sadaqah Jar: ${currency.format(state.sadaqahJarCollected.toDouble())} / ${currency.format(state.sadaqahGoal.toDouble())}",
            48f,
            y,
            bodyPaint
        )

        document.finishPage(page)
        FileOutputStream(file).use { out -> document.writeTo(out) }
        document.close()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Sakinah Zakat Summary")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Export Zakat PDF Summary").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(chooser)
        } catch (e: android.content.ActivityNotFoundException) {
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(
                    Intent.createChooser(viewIntent, "Open Zakat PDF").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            } catch (viewerMissing: android.content.ActivityNotFoundException) {
                throw IllegalStateException(
                    "No PDF viewer or sharing app is installed. The PDF was saved to ${file.absolutePath}.",
                    viewerMissing
                )
            }
        }
        file
    }
}
