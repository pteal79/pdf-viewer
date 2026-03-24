# PdfViewer Plugin for NativePHP Mobile

Opens PDF documents in a full-screen native viewer with **pinch-to-zoom**, a **share button** (left), and a **close button** (right). Supports both remote URLs and local file paths. Works on iOS and Android.

- **iOS** — uses `PDFKit` (built-in, no external dependencies, iOS 11+)
- **Android** — uses the built-in `PdfRenderer` (no external dependencies, API 21+)

## Installation

```bash
composer require pteal/plugin-pdf-viewer

# Register the plugin
php artisan native:plugin:register pteal/plugin-pdf-viewer

# Verify
php artisan native:plugin:list
```

## Requirements

### Android
- Minimum API 21 (Android 5.0)
- No external dependencies or Gradle repository changes required
- **Optional:** declare a `FileProvider` with authority `${applicationId}.provider` for the best share experience (see [Android FileProvider Setup](#android-fileprovider-setup)). Without it, the plugin falls back to sharing via `MediaStore.Downloads` (API 29+)

### iOS
- iOS 11+, Xcode 13+
- No additional configuration needed

## Usage

### Open a remote URL

```php
use Pteal\PdfViewer\Facades\PdfViewer;

PdfViewer::url('https://example.com/document.pdf');

// With title and description
PdfViewer::url('https://example.com/report.pdf', 'Annual Report', 'Q4 2025 financial summary');
```

### Open a local file

```php
use Pteal\PdfViewer\Facades\PdfViewer;

PdfViewer::path('/absolute/path/to/file.pdf');

// With title and description
PdfViewer::path(storage_path('app/documents/invoice.pdf'), 'Invoice #1042', 'Due 2026-04-01');
```

### Method signatures

```php
PdfViewer::url(string $url, string $title = '', string $description = ''): mixed
PdfViewer::path(string $path, string $title = '', string $description = ''): mixed
```

- **`$title`** — displayed in the viewer toolbar. If empty, no title is shown.
- **`$description`** — displayed as a subtitle below the title. If empty, no subtitle is shown.

## Viewer UI

| Control | Position | Behaviour |
|---------|----------|-----------|
| Share | Left of toolbar | Opens the system native share sheet for the PDF file |
| Close | Right of toolbar | Dismisses the viewer and fires `PdfViewerClosed` |

Remote PDFs are downloaded before display. The share sheet always shares the actual PDF file (not just a link), even for remote URLs.

## Events

| Event | Payload | Description |
|-------|---------|-------------|
| `PdfViewerClosed` | `{ filePath: string }` | Fired when the user dismisses the viewer. `filePath` contains the original URL or file path that was opened. |

### Listening in Livewire

```php
use Native\Mobile\Attributes\OnNative;
use Pteal\PdfViewer\Events\PdfViewerClosed;

#[OnNative(PdfViewerClosed::class)]
public function onPdfClosed(string $filePath): void
{
    // $filePath is the URL or path that was opened
}
```

## Android FileProvider Setup

Adding a `FileProvider` gives the best share experience on all Android versions. Without it, the plugin falls back to `MediaStore.Downloads` (API 29+) — sharing will silently fail on API 21–28 without FileProvider.

Add the following inside `<application>` in your `AndroidManifest.xml`:

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.provider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

Create `res/xml/file_paths.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <files-path name="files" path="." />
    <external-files-path name="external_files" path="." />
</paths>
```

## License

MIT
