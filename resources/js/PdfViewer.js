/**
 * PdfViewer Plugin for NativePHP Mobile
 *
 * Opens a native PDF viewer popup with pinch-to-zoom, a close button,
 * and a native share button.
 *
 * @example
 * import { PdfViewer } from '@pteal/plugin-pdf-viewer';
 *
 * await PdfViewer.open('/path/to/document.pdf', 'My Document');
 */

const baseUrl = '/_native/api/call';

async function bridgeCall(method, params = {}) {
    const response = await fetch(baseUrl, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'X-CSRF-TOKEN': document.querySelector('meta[name="csrf-token"]')?.content || '',
        },
        body: JSON.stringify({ method, params }),
    });

    const result = await response.json();

    if (result.status === 'error') {
        throw new Error(result.message || 'Native call failed');
    }

    const nativeResponse = result.data;
    if (nativeResponse && nativeResponse.data !== undefined) {
        return nativeResponse.data;
    }

    return nativeResponse;
}

/**
 * Open a PDF file in the native viewer.
 *
 * @param {string} filePath - Absolute path to the PDF file on the device
 * @param {string} [title='PDF Viewer'] - Title shown in the viewer toolbar
 * @returns {Promise<{opened: boolean}>}
 */
async function open(filePath, title = 'PDF Viewer') {
    return bridgeCall('PdfViewer.Open', { filePath, title });
}

export const PdfViewer = { open };

export const Events = {
    PdfViewerClosed: 'Pteal\\PdfViewer\\Events\\PdfViewerClosed',
};

export default PdfViewer;
