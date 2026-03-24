import Foundation
import PDFKit
import UIKit

// MARK: - Bridge Functions

enum PdfViewerFunctions {

    class Open: BridgeFunction {
        func execute(parameters: [String: Any]) throws -> [String: Any] {
            let type = parameters["type"] as? String ?? "path"

            guard let source = parameters["source"] as? String, !source.isEmpty else {
                throw BridgeError.invalidParameters("source is required")
            }

            let title = parameters["title"] as? String ?? ""
            let description = parameters["description"] as? String ?? ""

            let url: URL
            if type == "url" {
                guard let remoteURL = URL(string: source) else {
                    throw BridgeError.invalidParameters("Invalid URL: \(source)")
                }
                url = remoteURL
            } else {
                guard FileManager.default.fileExists(atPath: source) else {
                    throw BridgeError.executionFailed("File not found: \(source)")
                }
                url = URL(fileURLWithPath: source)
            }

            DispatchQueue.main.async {
                PdfViewerPresenter.present(url: url, source: source, title: title, description: description)
            }

            return ["opened": true]
        }
    }
}

// MARK: - Presenter

enum PdfViewerPresenter {

    static func present(url: URL, source: String, title: String, description: String) {
        let keyWindow = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first(where: { $0.activationState == .foregroundActive })?
            .windows
            .first(where: { $0.isKeyWindow })
            ?? UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first?
            .windows
            .first

        guard let rootVC = keyWindow?.rootViewController else { return }

        var topVC = rootVC
        while let presented = topVC.presentedViewController {
            topVC = presented
        }

        let pdfVC = PdfViewerViewController(pdfURL: url, source: source, title: title, description: description)
        let nav = UINavigationController(rootViewController: pdfVC)
        nav.modalPresentationStyle = .fullScreen
        topVC.present(nav, animated: true)
    }
}

// MARK: - PdfViewerViewController

final class PdfViewerViewController: UIViewController {

    private let pdfURL: URL
    private let source: String
    private let pdfTitle: String
    private let pdfDescription: String
    private var pdfView: PDFView!
    private var activityIndicator: UIActivityIndicatorView!
    private var downloadedFileURL: URL?

    init(pdfURL: URL, source: String, title: String, description: String) {
        self.pdfURL = pdfURL
        self.source = source
        self.pdfTitle = title
        self.pdfDescription = description
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        setupNavigationBar()
        setupPdfView()
        setupActivityIndicator()
        loadPdf()
    }

    // MARK: - Setup

    private func setupNavigationBar() {
        navigationItem.leftBarButtonItem = UIBarButtonItem(
            image: UIImage(systemName: "square.and.arrow.up"),
            style: .plain,
            target: self,
            action: #selector(shareTapped)
        )

        navigationItem.rightBarButtonItem = UIBarButtonItem(
            image: UIImage(systemName: "xmark"),
            style: .plain,
            target: self,
            action: #selector(closeTapped)
        )

        if !pdfTitle.isEmpty && !pdfDescription.isEmpty {
            navigationItem.titleView = makeTitleView(title: pdfTitle, subtitle: pdfDescription)
        } else {
            navigationItem.title = pdfTitle.isEmpty ? nil : pdfTitle
        }

        let appearance = UINavigationBarAppearance()
        appearance.configureWithOpaqueBackground()
        appearance.backgroundColor = UIColor(red: 0.11, green: 0.11, blue: 0.12, alpha: 1.0)
        appearance.titleTextAttributes = [.foregroundColor: UIColor.white]
        navigationController?.navigationBar.standardAppearance = appearance
        navigationController?.navigationBar.scrollEdgeAppearance = appearance
        navigationController?.navigationBar.tintColor = .white
    }

    private func makeTitleView(title: String, subtitle: String) -> UIView {
        let titleLabel = UILabel()
        titleLabel.text = title
        titleLabel.font = .systemFont(ofSize: 17, weight: .semibold)
        titleLabel.textColor = .white
        titleLabel.textAlignment = .center

        let subtitleLabel = UILabel()
        subtitleLabel.text = subtitle
        subtitleLabel.font = .systemFont(ofSize: 12, weight: .regular)
        subtitleLabel.textColor = UIColor.white.withAlphaComponent(0.7)
        subtitleLabel.textAlignment = .center

        let stack = UIStackView(arrangedSubviews: [titleLabel, subtitleLabel])
        stack.axis = .vertical
        stack.alignment = .center
        stack.spacing = 1
        stack.sizeToFit()
        return stack
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
            downloadedFileURL = pdfURL
            pdfView.document = PDFDocument(url: pdfURL)
        } else {
            activityIndicator.startAnimating()
            URLSession.shared.dataTask(with: pdfURL) { [weak self] data, _, error in
                guard let self, let data, error == nil else {
                    DispatchQueue.main.async { self?.activityIndicator.stopAnimating() }
                    return
                }
                // Write to a temp file so we can share the actual PDF
                let filename = self.pdfURL.lastPathComponent.isEmpty ? "document.pdf" : self.pdfURL.lastPathComponent
                let tempURL = FileManager.default.temporaryDirectory.appendingPathComponent(filename)
                try? data.write(to: tempURL)
                DispatchQueue.main.async {
                    self.activityIndicator.stopAnimating()
                    self.downloadedFileURL = tempURL
                    self.pdfView.document = PDFDocument(data: data)
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
        LaravelBridge.shared.send?(
            "Pteal\\PdfViewer\\Events\\PdfViewerClosed",
            ["filePath": source]
        )
        dismiss(animated: true)
    }

    @objc private func shareTapped() {
        let item: Any = downloadedFileURL ?? pdfURL
        let activityVC = UIActivityViewController(activityItems: [item], applicationActivities: nil)
        if let popover = activityVC.popoverPresentationController {
            popover.barButtonItem = navigationItem.leftBarButtonItem
        }
        present(activityVC, animated: true)
    }
}
