import Foundation
import PDFKit
import UIKit

// MARK: - Bridge Functions

enum PdfViewerFunctions {

    class Open: BridgeFunction {
        func execute(parameters: [String: Any]) throws -> [String: Any] {
            guard let source = parameters["filePath"] as? String, !source.isEmpty else {
                throw BridgeError.invalidParameters("filePath is required")
            }

            let title = parameters["title"] as? String ?? "PDF Viewer"

            // Support both remote URLs (http/https) and local file paths
            let isRemote = source.lowercased().hasPrefix("http://")
                || source.lowercased().hasPrefix("https://")

            let url: URL
            if isRemote {
                guard let remoteURL = URL(string: source) else {
                    throw BridgeError.invalidParameters("Invalid URL: \(source)")
                }
                url = remoteURL
            } else {
                url = URL(fileURLWithPath: source)
                guard FileManager.default.fileExists(atPath: source) else {
                    throw BridgeError.executionFailed("File not found: \(source)")
                }
            }

            DispatchQueue.main.async {
                PdfViewerPresenter.present(url: url, title: title)
            }

            return ["opened": true]
        }
    }
}

// MARK: - Window-based Presenter
//
// We create a dedicated UIWindow at .alert level rather than calling
// present() on the UIHostingController. This is the reliable pattern
// for NativePHP plugins because SwiftUI manages its own UIHostingController
// presentation stack and imperative `present` calls on it can be silently ignored.

private final class PdfViewerWindowHolder {
    static let shared = PdfViewerWindowHolder()
    var window: UIWindow?
}

enum PdfViewerPresenter {

    static func present(url: URL, title: String) {
        // Find the active foreground scene; fall back to the first available one
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first(where: { $0.activationState == .foregroundActive })
            ?? UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first

        guard let windowScene = scene else { return }

        let pdfVC = PdfViewerViewController(pdfURL: url, pdfTitle: title)
        let nav = UINavigationController(rootViewController: pdfVC)

        let overlayWindow = UIWindow(windowScene: windowScene)
        overlayWindow.rootViewController = nav
        overlayWindow.windowLevel = .alert + 1
        overlayWindow.alpha = 0
        overlayWindow.makeKeyAndVisible()

        // Keep a strong reference — the window is dismissed by releasing it
        PdfViewerWindowHolder.shared.window = overlayWindow

        pdfVC.onDismiss = {
            UIView.animate(withDuration: 0.25, animations: {
                overlayWindow.alpha = 0
            }, completion: { _ in
                overlayWindow.isHidden = true
                PdfViewerWindowHolder.shared.window = nil
            })
        }

        // Slide-up entrance animation
        let screenHeight = windowScene.screen.bounds.height
        nav.view.transform = CGAffineTransform(translationX: 0, y: screenHeight)
        UIView.animate(withDuration: 0.35, delay: 0, options: .curveEaseOut) {
            overlayWindow.alpha = 1
            nav.view.transform = .identity
        }
    }
}

// MARK: - PdfViewerViewController

final class PdfViewerViewController: UIViewController {

    var onDismiss: (() -> Void)?

    private let pdfURL: URL
    private let pdfTitle: String
    private var pdfView: PDFView!
    private var activityIndicator: UIActivityIndicatorView!

    init(pdfURL: URL, pdfTitle: String) {
        self.pdfURL = pdfURL
        self.pdfTitle = pdfTitle
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = pdfTitle
        view.backgroundColor = .systemBackground
        setupNavigationBar()
        setupPdfView()
        setupActivityIndicator()
        loadPdf()
    }

    // MARK: - Setup

    private func setupNavigationBar() {
        navigationItem.leftBarButtonItem = UIBarButtonItem(
            image: UIImage(systemName: "xmark"),
            style: .plain,
            target: self,
            action: #selector(closeTapped)
        )
        navigationItem.rightBarButtonItem = UIBarButtonItem(
            image: UIImage(systemName: "square.and.arrow.up"),
            style: .plain,
            target: self,
            action: #selector(shareTapped)
        )

        let appearance = UINavigationBarAppearance()
        appearance.configureWithOpaqueBackground()
        appearance.backgroundColor = UIColor(red: 0.11, green: 0.11, blue: 0.12, alpha: 1.0)
        appearance.titleTextAttributes = [.foregroundColor: UIColor.white]
        navigationController?.navigationBar.standardAppearance = appearance
        navigationController?.navigationBar.scrollEdgeAppearance = appearance
        navigationController?.navigationBar.tintColor = .white
    }

    private func setupPdfView() {
        pdfView = PDFView()
        pdfView.translatesAutoresizingMaskIntoConstraints = false
        pdfView.autoScales = true
        pdfView.displayMode = .singlePageContinuous
        pdfView.displayDirection = .vertical
        view.addSubview(pdfView)

        NSLayoutConstraint.activate([
            pdfView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            pdfView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            pdfView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            pdfView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
    }

    private func setupActivityIndicator() {
        activityIndicator = UIActivityIndicatorView(style: .large)
        activityIndicator.translatesAutoresizingMaskIntoConstraints = false
        activityIndicator.hidesWhenStopped = true
        view.addSubview(activityIndicator)

        NSLayoutConstraint.activate([
            activityIndicator.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            activityIndicator.centerYAnchor.constraint(equalTo: view.centerYAnchor),
        ])
    }

    // MARK: - Loading

    private func loadPdf() {
        if pdfURL.isFileURL {
            // Local file — load immediately
            if let document = PDFDocument(url: pdfURL) {
                pdfView.document = document
            }
        } else {
            // Remote URL — download on a background thread
            activityIndicator.startAnimating()
            URLSession.shared.dataTask(with: pdfURL) { [weak self] data, _, error in
                guard let self else { return }
                DispatchQueue.main.async {
                    self.activityIndicator.stopAnimating()
                    if let data, error == nil {
                        self.pdfView.document = PDFDocument(data: data)
                    }
                }
            }.resume()
        }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        guard pdfView.document != nil else { return }
        let fitScale = pdfView.scaleFactorForSizeToFit
        guard fitScale > 0 else { return }
        pdfView.minScaleFactor = fitScale
        pdfView.maxScaleFactor = fitScale * 4.0
        if pdfView.scaleFactor < fitScale {
            pdfView.scaleFactor = fitScale
        }
    }

    // MARK: - Actions

    @objc private func closeTapped() {
        onDismiss?()
    }

    @objc private func shareTapped() {
        let activityVC = UIActivityViewController(
            activityItems: [pdfURL],
            applicationActivities: nil
        )
        if let popover = activityVC.popoverPresentationController {
            popover.barButtonItem = navigationItem.rightBarButtonItem
        }
        present(activityVC, animated: true)
    }
}
