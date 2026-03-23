<?php

namespace Pteal\PdfViewer\Facades;

use Illuminate\Support\Facades\Facade;

/**
 * @method static mixed open(string $filePath, string $title = 'PDF Viewer')
 *
 * @see \Pteal\PdfViewer\PdfViewer
 */
class PdfViewer extends Facade
{
    protected static function getFacadeAccessor(): string
    {
        return \Pteal\PdfViewer\PdfViewer::class;
    }
}
