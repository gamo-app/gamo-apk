//
//  WebViewBridge.swift
//  GamoEkkIOS
//
//  Equivalente a WebViewClient + WebChromeClient + JavascriptInterface.
//  Recibe los mensajes que el shim JS envía por "AndroidApp".
//

import UIKit
import WebKit
import AVFoundation

final class WebViewBridge: NSObject, WKScriptMessageHandler,
                           WKNavigationDelegate, WKUIDelegate {

    enum Kind {
        case main
        case offline
        case update
    }

    let kind: Kind
    weak var presenter: UIViewController?

    var onMessage: (([String: Any]) -> Void)?
    var onDidFinish: ((WKWebView, URL?) -> Void)?
    var onDidFail: ((WKWebView, Error) -> Void)?

    /// En la vista offline se bloquea la navegación (igual que Android).
    var blockNavigation = false

    private var filePicker: FilePickerCoordinator?

    init(kind: Kind, presenter: UIViewController) {
        self.kind = kind
        self.presenter = presenter
        super.init()
    }

    // MARK: - WKScriptMessageHandler

    func userContentController(_ userContentController: WKUserContentController,
                               didReceive message: WKScriptMessage) {
        guard message.name == "AndroidApp",
              let body = message.body as? [String: Any] else {
            return
        }
        onMessage?(body)
    }

    // MARK: - WKNavigationDelegate

    func webView(_ webView: WKWebView,
                 decidePolicyFor navigationAction: WKNavigationAction,
                 decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        if blockNavigation {
            // Se permite la carga programática (loadFileURL) y los archivos
            // locales; se bloquean los enlaces activados por el usuario,
            // igual que shouldOverrideUrlLoading en Android.
            let isFile = navigationAction.request.url?.isFileURL == true
            let isProgrammatic = navigationAction.navigationType == .other
            if isFile || isProgrammatic {
                decisionHandler(.allow)
            } else {
                decisionHandler(.cancel)
            }
            return
        }
        decisionHandler(.allow)
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        onDidFinish?(webView, webView.url)
    }

    func webView(_ webView: WKWebView,
                 didFail navigation: WKNavigation!,
                 withError error: Error) {
        onDidFail?(webView, error)
    }

    func webView(_ webView: WKWebView,
                 didFailProvisionalNavigation navigation: WKNavigation!,
                 withError error: Error) {
        onDidFail?(webView, error)
    }

    // MARK: - WKUIDelegate (selector de archivos)

    // En iOS 18.4+ se puede interceptar el selector para ofrecer Cámara,
    // Fototeca y Archivos. En versiones anteriores WKWebView muestra su
    // selector de archivos del sistema automáticamente.
    @available(iOS 18.4, *)
    func webView(_ webView: WKWebView,
                 runOpenPanelWith parameters: WKOpenPanelParameters,
                 initiatedByFrame frame: WKFrameInfo,
                 completionHandler: @escaping ([URL]?) -> Void) {

        guard let presenter = presenter else {
            completionHandler(nil)
            return
        }

        let coordinator = FilePickerCoordinator(presenter: presenter) { [weak self] urls in
            completionHandler(urls)
            self?.filePicker = nil
        }
        filePicker = coordinator
        coordinator.present(multiple: parameters.allowsMultipleSelection)
    }

    // MARK: - WKUIDelegate (permiso de cámara para getUserMedia)

    @available(iOS 15.0, *)
    func webView(_ webView: WKWebView,
                 requestMediaCapturePermissionFor origin: WKSecurityOrigin,
                 initiatedByFrame frame: WKFrameInfo,
                 type: WKMediaCaptureType,
                 decisionHandler: @escaping (WKPermissionDecision) -> Void) {

        guard type == .camera || type == .cameraAndMicrophone else {
            decisionHandler(.prompt)
            return
        }

        if AVCaptureDevice.authorizationStatus(for: .video) == .authorized {
            decisionHandler(.grant)
            return
        }

        AVCaptureDevice.requestAccess(for: .video) { granted in
            DispatchQueue.main.async {
                let literal = granted ? "true" : "false"
                webView.evaluateJavaScript(
                    "window.__gamoCamPerm=\(literal);",
                    completionHandler: nil
                )
                decisionHandler(granted ? .grant : .deny)
            }
        }
    }
}

// MARK: - Selector de archivos (equivalente a onShowFileChooser)

final class FilePickerCoordinator: NSObject,
                                   UIImagePickerControllerDelegate,
                                   UINavigationControllerDelegate,
                                   UIDocumentPickerDelegate {

    private weak var presenter: UIViewController?
    private let completion: ([URL]?) -> Void
    private var multiple = false

    init(presenter: UIViewController, completion: @escaping ([URL]?) -> Void) {
        self.presenter = presenter
        self.completion = completion
        super.init()
    }

    func present(multiple: Bool) {
        self.multiple = multiple

        let sheet = UIAlertController(title: "Seleccionar archivo",
                                      message: nil,
                                      preferredStyle: .actionSheet)
        sheet.addAction(UIAlertAction(title: "Cámara", style: .default) { [weak self] _ in
            self?.openCamera()
        })
        sheet.addAction(UIAlertAction(title: "Fototeca", style: .default) { [weak self] _ in
            self?.openPhotos()
        })
        sheet.addAction(UIAlertAction(title: "Archivos", style: .default) { [weak self] _ in
            self?.openFiles()
        })
        sheet.addAction(UIAlertAction(title: "Cancelar", style: .cancel) { [weak self] _ in
            self?.finish(nil)
        })

        if let popover = sheet.popoverPresentationController,
           let view = presenter?.view {
            popover.sourceView = view
            popover.sourceRect = CGRect(x: view.bounds.midX,
                                        y: view.bounds.midY,
                                        width: 0,
                                        height: 0)
            popover.permittedArrowDirections = []
        }

        presenter?.present(sheet, animated: true)
    }

    private func presentController(_ controller: UIViewController) {
        DispatchQueue.main.async {
            guard let presenter = self.presenter else { return }

            // La hoja de opciones todavía puede estar cerrándose: se espera a
            // que termine para presentar el selector real.
            if presenter.presentedViewController != nil {
                presenter.dismiss(animated: true) {
                    presenter.present(controller, animated: true)
                }
            } else {
                presenter.present(controller, animated: true)
            }
        }
    }

    private func openCamera() {
        guard UIImagePickerController.isSourceTypeAvailable(.camera) else {
            finish(nil)
            return
        }

        AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
            guard let self = self else { return }
            DispatchQueue.main.async {
                if !granted {
                    self.finish(nil)
                    return
                }
                let picker = UIImagePickerController()
                picker.sourceType = .camera
                picker.delegate = self
                self.presentController(picker)
            }
        }
    }

    private func openPhotos() {
        let picker = UIImagePickerController()
        picker.sourceType = .photoLibrary
        picker.delegate = self
        presentController(picker)
    }

    private func openFiles() {
        let picker = UIDocumentPickerViewController(documentTypes: ["public.item"],
                                                     in: .import)
        picker.delegate = self
        picker.allowsMultipleSelection = multiple
        presentController(picker)
    }

    // MARK: - UIImagePickerControllerDelegate

    func imagePickerController(_ picker: UIImagePickerController,
                               didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
        picker.dismiss(animated: true)

        guard let image = info[.originalImage] as? UIImage,
              let data = image.jpegData(compressionQuality: 0.9) else {
            finish(nil)
            return
        }

        let folder = FileManager.default.temporaryDirectory
            .appendingPathComponent("capturas", isDirectory: true)

        try? FileManager.default.createDirectory(at: folder,
                                                 withIntermediateDirectories: true)

        let file = folder.appendingPathComponent(
            "foto_\(Int(Date().timeIntervalSince1970 * 1000)).jpg"
        )

        do {
            try data.write(to: file)
            finish([file])
        } catch {
            finish(nil)
        }
    }

    func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
        picker.dismiss(animated: true)
        finish(nil)
    }

    // MARK: - UIDocumentPickerDelegate

    func documentPicker(_ controller: UIDocumentPickerViewController,
                        didPickDocumentsAt urls: [URL]) {
        finish(urls.isEmpty ? nil : urls)
    }

    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        finish(nil)
    }

    private func finish(_ urls: [URL]?) {
        completion(urls)
    }
}
