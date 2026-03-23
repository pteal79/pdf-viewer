package com.pteal.plugins.pdfviewer

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle
import com.nativephp.mobile.bridge.BridgeFunction
import com.nativephp.mobile.bridge.BridgeResponse
import java.io.File

object PdfViewerFunctions {

    class Open(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val filePath = parameters["filePath"] as? String
                ?: return BridgeResponse.error("filePath parameter is required")

            val title = parameters["title"] as? String ?: "PDF Viewer"

            val file = File(filePath)
            if (!file.exists()) {
                return BridgeResponse.error("File not found: $filePath")
            }

            activity.runOnUiThread {
                val intent = Intent(activity, PdfViewerActivity::class.java).apply {
                    putExtra(PdfViewerActivity.EXTRA_FILE_PATH, filePath)
                    putExtra(PdfViewerActivity.EXTRA_TITLE, title)
                }
                activity.startActivity(intent)
            }

            return BridgeResponse.success(mapOf("opened" to true))
        }
    }
}

class PdfViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FILE_PATH = "filePath"
        const val EXTRA_TITLE = "title"
        private const val MENU_ID_SHARE = 1
    }

    private lateinit var pdfView: PDFView
    private var filePath: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        filePath = intent.getStringExtra(EXTRA_FILE_PATH) ?: ""
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "PDF Viewer"

        // Build layout programmatically — no XML resource file needed
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val toolbar = Toolbar(this).apply {
            setBackgroundColor(Color.parseColor("#1C1C1E"))
            setTitleTextColor(Color.WHITE)
            this.title = title
        }
        rootLayout.addView(
            toolbar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(androidx.appcompat.R.dimen.abc_action_bar_default_height_material)
            )
        )

        pdfView = PDFView(this, null)
        rootLayout.addView(
            pdfView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        )

        setContentView(rootLayout)
        setSupportActionBar(toolbar)

        // Close (back) button on the left
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(android.R.drawable.ic_menu_close_clear_cancel)

        loadPdf()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(Menu.NONE, MENU_ID_SHARE, Menu.NONE, "Share").apply {
            setIcon(android.R.drawable.ic_menu_share)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            MENU_ID_SHARE -> {
                sharePdf()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadPdf() {
        if (filePath.isEmpty()) return

        pdfView.fromFile(File(filePath))
            .enableSwipe(true)
            .swipeHorizontal(false)
            .enableDoubletap(true)
            .defaultPage(0)
            .enableAntialiasing(true)
            .spacing(0)
            // Page scroll indicator on the side
            .scrollHandle(DefaultScrollHandle(this))
            .load()
    }

    private fun sharePdf() {
        val file = File(filePath)
        if (!file.exists()) return

        try {
            // Android 7+ requires a content URI via FileProvider
            val uri: Uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.provider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share PDF"))
        } catch (e: IllegalArgumentException) {
            // Fallback for files already in a publicly accessible location
            val uri = Uri.fromFile(file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
            }
            startActivity(Intent.createChooser(shareIntent, "Share PDF"))
        }
    }
}
