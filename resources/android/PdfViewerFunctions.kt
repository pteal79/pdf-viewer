package com.pteal.plugins.pdfviewer

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle
import com.nativephp.mobile.bridge.BridgeFunction
import com.nativephp.mobile.bridge.BridgeResponse
import com.nativephp.mobile.core.NativeActionCoordinator
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object PdfViewerFunctions {

    class Open(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val type = parameters["type"] as? String ?: "path"
            val source = parameters["source"] as? String
                ?: return BridgeResponse.error("source parameter is required")
            val title = parameters["title"] as? String ?: ""
            val description = parameters["description"] as? String ?: ""

            activity.runOnUiThread {
                val intent = Intent(activity, PdfViewerActivity::class.java).apply {
                    putExtra(PdfViewerActivity.EXTRA_TYPE, type)
                    putExtra(PdfViewerActivity.EXTRA_SOURCE, source)
                    putExtra(PdfViewerActivity.EXTRA_TITLE, title)
                    putExtra(PdfViewerActivity.EXTRA_DESCRIPTION, description)
                }
                activity.startActivity(intent)
            }

            return BridgeResponse.success(mapOf("opened" to true))
        }
    }
}

class PdfViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TYPE = "type"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_TITLE = "title"
        const val EXTRA_DESCRIPTION = "description"
        private const val MENU_CLOSE = 1
    }

    private lateinit var pdfView: PDFView
    private lateinit var progressBar: ProgressBar
    private var source: String = ""
    private var type: String = "path"
    private var downloadedFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        type = intent.getStringExtra(EXTRA_TYPE) ?: "path"
        source = intent.getStringExtra(EXTRA_SOURCE) ?: ""
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val description = intent.getStringExtra(EXTRA_DESCRIPTION) ?: ""

        // Build layout programmatically — no XML resource file needed
        val rootLayout = RelativeLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
            )
        }

        val toolbar = Toolbar(this).apply {
            setBackgroundColor(Color.parseColor("#1C1C1E"))
            setTitleTextColor(Color.WHITE)
            setSubtitleTextColor(Color.argb(179, 255, 255, 255)) // white at ~70% opacity
            this.title = title.ifEmpty { null }
            subtitle = description.ifEmpty { null }
        }
        contentLayout.addView(
            toolbar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        pdfView = PDFView(this, null)
        contentLayout.addView(
            pdfView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        )

        progressBar = ProgressBar(this).apply {
            val params = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            )
            params.addRule(RelativeLayout.CENTER_IN_PARENT)
            layoutParams = params
            isIndeterminate = true
        }

        rootLayout.addView(contentLayout)
        rootLayout.addView(progressBar)

        setContentView(rootLayout)
        setSupportActionBar(toolbar)

        // Share on the left (navigation icon), close on the right (action item)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_share)
        toolbar.setNavigationOnClickListener { sharePdf() }

        if (type == "url") {
            loadFromUrl(source)
        } else {
            loadFromFile(source)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(Menu.NONE, MENU_SHARE, Menu.NONE, "Close").apply {
            setIcon(android.R.drawable.ic_menu_close_clear_cancel)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            MENU_CLOSE -> { finish(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun finish() {
        val payload = JSONObject().apply { put("filePath", source) }
        Handler(Looper.getMainLooper()).post {
            NativeActionCoordinator.dispatchEvent(
                this,
                "Pteal\\PdfViewer\\Events\\PdfViewerClosed",
                payload.toString()
            )
        }
        super.finish()
    }

    private fun loadFromFile(path: String) {
        progressBar.visibility = View.GONE
        downloadedFile = File(path)
        pdfView.fromFile(File(path))
            .enableSwipe(true)
            .swipeHorizontal(false)
            .enableDoubletap(true)
            .defaultPage(0)
            .enableAntialiasing(true)
            .spacing(0)
            .scrollHandle(DefaultScrollHandle(this))
            .load()
    }

    private fun loadFromUrl(urlString: String) {
        progressBar.visibility = View.VISIBLE
        Executors.newSingleThreadExecutor().execute {
            try {
                val connection = URL(urlString).openConnection() as HttpURLConnection
                connection.connect()
                val bytes = connection.inputStream.readBytes()
                connection.disconnect()

                // Write to a temp file so we can share the actual PDF
                val filename = Uri.parse(urlString).lastPathSegment?.takeIf { it.isNotEmpty() } ?: "document.pdf"
                val tempFile = File(cacheDir, filename)
                tempFile.writeBytes(bytes)

                runOnUiThread {
                    progressBar.visibility = View.GONE
                    downloadedFile = tempFile
                    pdfView.fromBytes(bytes)
                        .enableSwipe(true)
                        .swipeHorizontal(false)
                        .enableDoubletap(true)
                        .defaultPage(0)
                        .enableAntialiasing(true)
                        .spacing(0)
                        .scrollHandle(DefaultScrollHandle(this))
                        .load()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    finish()
                }
            }
        }
    }

    private fun sharePdf() {
        val file = downloadedFile ?: return
        if (!file.exists()) return

        try {
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
            val uri = Uri.fromFile(file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
            }
            startActivity(Intent.createChooser(shareIntent, "Share PDF"))
        }
    }
}
