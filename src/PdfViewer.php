<?php

namespace Pteal\PdfViewer;

class PdfViewer
{
    /**
     * Open a PDF file in a native popup viewer.
     *
     * The viewer supports pinch-to-zoom, has a close button and a native
     * share button built into the toolbar.
     *
     * @param  string  $filePath  Absolute path to the PDF file on the device
     * @param  string  $title     Optional title shown in the viewer toolbar
     */
    public function open(string $filePath, string $title = 'PDF Viewer'): mixed
    {
        if (function_exists('nativephp_call')) {
            $result = nativephp_call('PdfViewer.Open', json_encode([
                'filePath' => $filePath,
                'title' => $title,
            ]));

            if ($result) {
                $decoded = json_decode($result);

                return $decoded->data ?? null;
            }
        }

        return null;
    }
}
