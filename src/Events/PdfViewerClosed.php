<?php

namespace Pteal\PdfViewer\Events;

use Illuminate\Foundation\Events\Dispatchable;
use Illuminate\Queue\SerializesModels;

class PdfViewerClosed
{
    use Dispatchable, SerializesModels;

    public function __construct(
        public string $filePath,
        public ?string $id = null
    ) {}
}
