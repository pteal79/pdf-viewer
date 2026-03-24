<?php

namespace Pteal\PdfViewer\Facades;

use Illuminate\Support\Facades\Facade;

/**
 * @method static mixed url(string $url, string $title = '', string $description = '')
 * @method static mixed path(string $path, string $title = '', string $description = '')
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
