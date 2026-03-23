<?php

namespace Pteal\PdfViewer\Commands;

use Native\Mobile\Plugins\Commands\NativePluginHookCommand;

class CopyAssetsCommand extends NativePluginHookCommand
{
    protected $signature = 'nativephp:pdf-viewer:copy-assets';

    protected $description = 'Copy assets for PdfViewer plugin';

    public function handle(): int
    {
        if ($this->isAndroid()) {
            $this->copyAndroidAssets();
        }

        if ($this->isIos()) {
            $this->copyIosAssets();
        }

        return self::SUCCESS;
    }

    protected function copyAndroidAssets(): void
    {
        $this->info('Android assets copied for PdfViewer');
    }

    protected function copyIosAssets(): void
    {
        $this->info('iOS assets copied for PdfViewer');
    }
}
