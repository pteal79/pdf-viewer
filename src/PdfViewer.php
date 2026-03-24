<?php

namespace Pteal\PdfViewer;

class PdfViewer
{
    public function url(string $url, string $title = '', string $description = ''): mixed
    {
        return $this->call('url', $url, $title, $description);
    }

    public function path(string $path, string $title = '', string $description = ''): mixed
    {
        return $this->call('path', $path, $title, $description);
    }

    private function call(string $type, string $source, string $title = '', string $description = ''): mixed
    {
        if (function_exists('nativephp_call')) {
            $result = nativephp_call('PdfViewer.Open', json_encode([
                'type' => $type,
                'source' => $source,
                'title' => $title,
                'description' => $description,
            ]));

            if ($result) {
                $decoded = json_decode($result);

                return $decoded->data ?? null;
            }
        }

        return null;
    }
}
