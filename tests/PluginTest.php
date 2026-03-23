<?php

beforeEach(function () {
    $this->pluginPath = dirname(__DIR__);
    $this->manifestPath = $this->pluginPath . '/nativephp.json';
});

describe('Plugin Manifest', function () {
    it('has a valid nativephp.json file', function () {
        expect(file_exists($this->manifestPath))->toBeTrue();

        $content = file_get_contents($this->manifestPath);
        $manifest = json_decode($content, true);

        expect(json_last_error())->toBe(JSON_ERROR_NONE);
    });

    it('has required fields', function () {
        $manifest = json_decode(file_get_contents($this->manifestPath), true);

        expect($manifest)->toHaveKeys(['namespace', 'bridge_functions']);
    });

    it('has a PdfViewer.Open bridge function for both platforms', function () {
        $manifest = json_decode(file_get_contents($this->manifestPath), true);

        $openFn = collect($manifest['bridge_functions'])
            ->firstWhere('name', 'PdfViewer.Open');

        expect($openFn)->not->toBeNull();
        expect($openFn)->toHaveKeys(['android', 'ios']);
    });
});

describe('Native Code', function () {
    it('has Android Kotlin file', function () {
        expect(file_exists($this->pluginPath . '/resources/android/PdfViewerFunctions.kt'))->toBeTrue();
    });

    it('has iOS Swift file', function () {
        expect(file_exists($this->pluginPath . '/resources/ios/PdfViewerFunctions.swift'))->toBeTrue();
    });
});

describe('PHP Classes', function () {
    it('has service provider', function () {
        expect(file_exists($this->pluginPath . '/src/PdfViewerServiceProvider.php'))->toBeTrue();
    });

    it('has facade', function () {
        expect(file_exists($this->pluginPath . '/src/Facades/PdfViewer.php'))->toBeTrue();
    });

    it('has main implementation class', function () {
        expect(file_exists($this->pluginPath . '/src/PdfViewer.php'))->toBeTrue();
    });

    it('has PdfViewerClosed event', function () {
        expect(file_exists($this->pluginPath . '/src/Events/PdfViewerClosed.php'))->toBeTrue();
    });
});

describe('Composer Configuration', function () {
    it('has valid composer.json with correct type', function () {
        $composerPath = $this->pluginPath . '/composer.json';
        expect(file_exists($composerPath))->toBeTrue();

        $composer = json_decode(file_get_contents($composerPath), true);

        expect(json_last_error())->toBe(JSON_ERROR_NONE);
        expect($composer['type'])->toBe('nativephp-plugin');
        expect($composer['name'])->toBe('pteal/plugin-pdf-viewer');
    });
});
