package com.pteal.plugins.pdfviewer

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nativephp.mobile.bridge.BridgeFunction
import com.nativephp.mobile.bridge.BridgeResponse
import com.nativephp.mobile.utils.NativeActionCoordinator
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
                ?: return BridgeResponse.error("INVALID_PARAMETERS", "source parameter is required")
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

class PdfPageAdapter(private val renderer: PdfRenderer) : RecyclerView.Adapter<PdfPageAdapter.PageViewHolder>() {

    // Single thread ensures only one PdfRenderer page is open at a time
    private val executor = Executors.newSingleThreadExecutor()

    class PageViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val imageView = ImageView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            )
            adjustViewBounds = true
        }
        return PageViewHolder(imageView)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val width = holder.imageView.context.resources.displayMetrics.widthPixels
        executor.execute {
            val bitmap = try {
                val page = renderer.openPage(position)
                val height = (width.toFloat() / page.width * page.height).toInt()
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                Canvas(bmp).drawColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                bmp
            } catch (e: Exception) {
                null
            }
            Handler(Looper.getMainLooper()).post {
                if (bitmap != null && holder.adapterPosition == position) {
                    holder.imageView.setImageBitmap(bitmap)
                }
            }
        }
    }

    override fun getItemCount() = renderer.pageCount

    fun shutdown() = executor.shutdown()
}

class PdfViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TYPE = "type"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_TITLE = "title"
        const val EXTRA_DESCRIPTION = "description"
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private var pdfRenderer: PdfRenderer? = null
    private var parcelFileDescriptor: ParcelFileDescriptor? = null
    private var source: String = ""
    private var type: String = "path"
    private var downloadedFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        type = intent.getStringExtra(EXTRA_TYPE) ?: "path"
        source = intent.getStringExtra(EXTRA_SOURCE) ?: ""
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val description = intent.getStringExtra(EXTRA_DESCRIPTION) ?: ""

        val rootLayout = RelativeLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#F2F2F7"))
        }

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
            )
        }

        val toolbarHeight = (56 * resources.displayMetrics.density).toInt()

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#1C1C1E"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                toolbarHeight
            )
        }

        val shareButton = ImageButton(this).apply {
            setImageDrawable(resources.getDrawable(android.R.drawable.ic_menu_share, theme))
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
            val pad = (12 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            setOnClickListener { sharePdf() }
        }

        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        if (title.isNotEmpty()) {
            textContainer.addView(TextView(this).apply {
                text = title
                setTextColor(Color.WHITE)
                textSize = 18f
            })
        }

        if (description.isNotEmpty()) {
            textContainer.addView(TextView(this).apply {
                text = description
                setTextColor(Color.argb(179, 255, 255, 255))
                textSize = 14f
            })
        }

        val closeButton = ImageButton(this).apply {
            setImageDrawable(resources.getDrawable(android.R.drawable.ic_menu_close_clear_cancel, theme))
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
            val pad = (12 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            setOnClickListener { finish() }
        }

        toolbar.addView(shareButton)
        toolbar.addView(textContainer)
        toolbar.addView(closeButton)

        contentLayout.addView(toolbar)

        recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@PdfViewerActivity)
        }
        contentLayout.addView(recyclerView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ))

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

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }

        if (type == "url") {
            loadFromUrl(source)
        } else {
            loadFromFile(source)
        }
    }

    override fun finish() {
        (recyclerView.adapter as? PdfPageAdapter)?.shutdown()
        pdfRenderer?.close()
        parcelFileDescriptor?.close()
        val payload = JSONObject().apply { put("filePath", source) }
        NativeActionCoordinator.dispatchEvent(
            this,
            "Pteal\\PdfViewer\\Events\\PdfViewerClosed",
            payload.toString()
        )
        super.finish()
    }

    private fun loadFromFile(path: String) {
        progressBar.visibility = View.GONE
        val file = File(path)
        downloadedFile = file
        openPdfFile(file)
    }

    private fun loadFromUrl(urlString: String) {
        progressBar.visibility = View.VISIBLE
        Executors.newSingleThreadExecutor().execute {
            try {
                val connection = URL(urlString).openConnection() as HttpURLConnection
                connection.connect()
                val bytes = connection.inputStream.readBytes()
                connection.disconnect()

                val rawName = Uri.parse(urlString).lastPathSegment?.takeIf { it.isNotEmpty() } ?: "document"
                val filename = if (rawName.endsWith(".pdf", ignoreCase = true)) rawName else "$rawName.pdf"
                val tempFile = File(filesDir, filename)
                tempFile.writeBytes(bytes)

                runOnUiThread {
                    progressBar.visibility = View.GONE
                    downloadedFile = tempFile
                    openPdfFile(tempFile)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    finish()
                }
            }
        }
    }

    private fun openPdfFile(file: File) {
        parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        pdfRenderer = PdfRenderer(parcelFileDescriptor!!)
        recyclerView.adapter = PdfPageAdapter(pdfRenderer!!)
    }

    private fun sharePdf() {
        val file = downloadedFile ?: return
        if (!file.exists()) return

        val uri: Uri? = try {
            FileProvider.getUriForFile(this, "${packageName}.provider", file)
        } catch (e: Exception) {
            null
        }

        if (uri != null) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TITLE, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share PDF"))
            return
        }

        // FileProvider not configured — copy into MediaStore Downloads and share from there
        try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val mediaUri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
            contentResolver.openOutputStream(mediaUri)?.use { file.inputStream().copyTo(it) }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            contentResolver.update(mediaUri, values, null, null)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, mediaUri)
                putExtra(Intent.EXTRA_TITLE, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share PDF"))
        } catch (e: Exception) {
            // sharing not available on this device
        }
    }
}
