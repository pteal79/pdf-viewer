## pteal/plugin-pdf-viewer

A NativePHP plugin that opens PDF documents in a native popup viewer with pinch-to-zoom, a close button, and a native share button. Works on iOS and Android.

### Installation

```bash
composer require pteal/plugin-pdf-viewer
php artisan vendor:publish --tag=nativephp-plugins-provider
php artisan native:plugin:register pteal/plugin-pdf-viewer
php artisan native:plugin:list
```

### PHP Usage (Livewire/Blade)

@verbatim
<code-snippet name="Opening a PDF" lang="php">
use Pteal\PdfViewer\Facades\PdfViewer;

// Open a PDF from a local file path
PdfViewer::open(storage_path('app/documents/report.pdf'), 'Annual Report');
</code-snippet>
@endverbatim

### Available Methods

- `PdfViewer::open(string $filePath, string $title = 'PDF Viewer')`: Opens the PDF viewer popup

### Events

- `PdfViewerClosed`: Fired when the user closes the viewer — listen with `#[OnNative(PdfViewerClosed::class)]`

@verbatim
<code-snippet name="Listening for close event" lang="php">
use Native\Mobile\Attributes\OnNative;
use Pteal\PdfViewer\Events\PdfViewerClosed;

#[OnNative(PdfViewerClosed::class)]
public function onPdfClosed(string $filePath, ?string $id = null): void
{
    // PDF viewer was dismissed
}
</code-snippet>
@endverbatim

### JavaScript Usage (Vue/React/Inertia)

@verbatim
<code-snippet name="Using PdfViewer in JavaScript" lang="javascript">
import { PdfViewer, Events } from '@pteal/plugin-pdf-viewer';
import { on, off } from '@nativephp/native';

// Open the viewer
await PdfViewer.open('/absolute/path/to/file.pdf', 'My Document');

// Listen for the viewer being closed
const handler = (payload) => console.log('Closed:', payload);
on(Events.PdfViewerClosed, handler);

// Clean up
off(Events.PdfViewerClosed, handler);
</code-snippet>
@endverbatim

### Notes

- **iOS**: Uses `PDFKit` (built-in, no external dependencies). Requires iOS 11+.
- **Android**: Uses `barteksc/android-pdf-viewer` via JitPack. Requires API 21+.
- The `filePath` must be an absolute path accessible to the app (e.g., `storage_path('app/...')`).
- On Android, sharing requires a `FileProvider` declared in the host app's `AndroidManifest.xml` with authority `${applicationId}.provider`.
