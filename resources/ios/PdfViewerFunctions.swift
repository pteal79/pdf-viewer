import Foundation
import PDFKit
import UIKit

enum PdfViewerFunctions {

    class Open: BridgeFunction {
        func execute(parameters: [String: Any]) throws -> [String: Any] {
            guard let filePath = parameters["filePath"] as? String else {
                return BridgeResponse.error(message: "filePath parameter is required")
            }

            let title = parameters["title"] as? String ?? "PDF Viewer"
            let fileURL = URL(fileURLWithPath: filePath)

            guard FileManager.default.fileExists(atPath: filePath) else {
                return BridgeResponse.error(message: "File not found: \(filePath)")
            }

            DispatchQueue.main.async {
                guard let topVC = UIApplication.shared.nativeTopViewController() else { return }

                let pdfVC = PdfViewerViewController(fileURL: fileURL, pdfTitle: title)
                let navController = UINavigationController(rootViewController: pdfVC)
                navController.modalPresentationStyle = .fullScreen
                topVC.present(navController, animated: true)
            }

            return BridgeResponse.success(data: ["opened": true])
        }
    }
}

// MARK: - PdfViewerViewController

final class PdfViewerViewController: UIViewController {

    private let fileURL: URL
    private let pdfTitle: String
    private var pdfView: PDFView!

    init(fileURL: URL, pdfTitle: String) {
        self.fileURL = fileURL
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
        loadPdf()
    }

    // MARK: - Setup

    private func setupNavigationBar() {
        // Close button — left side
        navigationItem.leftBarButtonItem = UIBarButtonItem(
            image: UIImage(systemName: "xmark"),
            style: .plain,
            target: self,
            action: #selector(closeTapped)
        )

        // Share button — right side
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

        // Continuous vertical scroll with built-in pinch-to-zoom
        pdfView.autoScales = true
        pdfView.displayMode = .singlePageContinuous
        pdfView.displayDirection = .vertical

        // Allow zooming up to 4× the fit-to-page scale
        pdfView.minScaleFactor = pdfView.scaleFactorForSizeToFit
        pdfView.maxScaleFactor = 4.0

        view.addSubview(pdfView)

        NSLayoutConstraint.activate([
            pdfView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            pdfView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            pdfView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            pdfView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
    }

    private func loadPdf() {
        guard let document = PDFDocument(url: fileURL) else { return }
        pdfView.document = document
        // Reset zoom scale after document loads so minScaleFactor is correct
        pdfView.minScaleFactor = pdfView.scaleFactorForSizeToFit
        pdfView.scaleFactor = pdfView.scaleFactorForSizeToFit
    }

    // MARK: - Actions

    @objc private func closeTapped() {
        dismiss(animated: true)
    }

    @objc private func shareTapped() {
        let activityVC = UIActivityViewController(
            activityItems: [fileURL],
            applicationActivities: nil
        )
        // iPad popover anchor
        if let popover = activityVC.popoverPresentationController {
            popover.barButtonItem = navigationItem.rightBarButtonItem
        }
        present(activityVC, animated: true)
    }
}

// MARK: - UIApplication helper

private extension UIApplication {
    func nativeTopViewController() -> UIViewController? {
        guard
            let windowScene = connectedScenes.first as? UIWindowScene,
            let window = windowScene.windows.first(where: { $0.isKeyWindow }),
            let root = window.rootViewController
        else { return nil }
        return topVC(from: root)
    }

    private func topVC(from vc: UIViewController) -> UIViewController {
        if let presented = vc.presentedViewController {
            return topVC(from: presented)
        }
        if let nav = vc as? UINavigationController, let visible = nav.visibleViewController {
            return topVC(from: visible)
        }
        if let tab = vc as? UITabBarController, let selected = tab.selectedViewController {
            return topVC(from: selected)
        }
        return vc
    }
}
