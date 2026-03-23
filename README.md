# PdfViewer Plugin for NativePHP Mobile

Opens PDF documents in a native popup viewer with **pinch-to-zoom**, a **close button**, and a **native share button**. Works on iOS and Android.

- **iOS** — uses `PDFKit` (built-in, no external dependencies, iOS 11+)
- **Android** — uses `barteksc/android-pdf-viewer` via JitPack (API 21+)

## Installation

```bash
# Add path repository to your Laravel app's composer.json
# "repositories": [{"type": "path", "url": "./packages/pteal/plugin-pdf-viewer"}]

composer require pteal/plugin-pdf-viewer

# Publish the plugins provider (first time only)
php artisan vendor:publish --tag=nativephp-plugins-provider

# Register the plugin
php artisan native:plugin:register pteal/plugin-pdf-viewer

# Verify
php artisan native:plugin:list
```

## Requirements

### Android
- Minimum API 21 (Android 5.0)
- JitPack repository must be available in the project's Gradle config
- For the share button: a `FileProvider` with authority `${applicationId}.provider` must be declared in `AndroidManifest.xml`

### iOS
- iOS 11+, Xcode 13+
- No additional configuration needed

## Usage

### PHP (Livewire / Blade)

```php
use Pteal\PdfViewer\Facades\PdfViewer;

PdfViewer::open(storage_path('app/documents/report.pdf'), 'Annual Report');
```

### JavaScript (Vue / React / Inertia)

```javascript
import { PdfViewer, Events } from '@pteal/plugin-pdf-viewer';
import { on, off } from '@nativephp/native';

// Open the viewer
await PdfViewer.open('/absolute/path/to/file.pdf', 'My Document');

// Listen for close
const handler = (payload) => console.log('Viewer closed', payload);
on(Events.PdfViewerClosed, handler);

// Clean up
off(Events.PdfViewerClosed, handler);
```

## Events

| Event | Payload | Description |
|-------|---------|-------------|
| `PdfViewerClosed` | `{ filePath: string }` | Fired when the user dismisses the viewer |

### Listening in Livewire

```php
use Native\Mobile\Attributes\OnNative;
use Pteal\PdfViewer\Events\PdfViewerClosed;

#[OnNative(PdfViewerClosed::class)]
public function onPdfClosed(string $filePath, ?string $id = null): void
{
    // Handle dismissal
}
```

## Android FileProvider Setup

Add to your app's `AndroidManifest.xml` inside `<application>`:

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
    <cache-path name="cache" path="." />
    <external-files-path name="external_files" path="." />
</paths>
```

## License

MIT
