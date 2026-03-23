<?php

namespace Pteal\PdfViewer;

use Illuminate\Support\ServiceProvider;
use Pteal\PdfViewer\Commands\CopyAssetsCommand;

class PdfViewerServiceProvider extends ServiceProvider
{
    public function register(): void
    {
        $this->app->singleton(PdfViewer::class, function () {
            return new PdfViewer();
        });
    }

    public function boot(): void
    {
        if ($this->app->runningInConsole()) {
            $this->commands([
                CopyAssetsCommand::class,
            ]);
        }
    }
}
