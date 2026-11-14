//
//  MainViewController.swift
//  GamoEkkIOS
//
//  Port de MainActivity.java: WebView principal, vista offline, puente JS,
//  sesión de usuario, cámara, notificaciones y comprobación de actualizaciones.
//

import UIKit
import WebKit
import AVFoundation

final class MainViewController: UIViewController {

    // MARK: - Vistas

    private var contentView: UIView!
    private var webView: WKWebView!
    private var offlineView: WKWebView!

    private var updateOverlay: UIView?
    private var updateView: WKWebView?
    private var updateBridge: WebViewBridge?

    // MARK: - Puentes

    private var mainBridge: WebViewBridge!
    private var offlineBridge: WebViewBridge!

    // MARK: - Estado

    private var pageLoaded = false
    private var loadingSite = false
    private var navigationColor = UIColor.white
    private var statusStyle: UIStatusBarStyle = .darkContent
    private let baseSiteURL = AppConfig.siteURL.hasSuffix("/")
        ? String(AppConfig.siteURL.dropLast())
        : AppConfig.siteURL

    private var comprobandoActualizacion = false
    private var actualizacionComprobada = false

    private var updateVersionCode = 0
    private var updateVersionName = ""
    private var updateNotes = ""
    private var updateURL = ""

    // MARK: - Ciclo de vida

    override func viewDidLoad() {
        super.viewDidLoad()

        view.backgroundColor = navigationColor

        setupContentView()
        setupWebViews()
        setupObservers()

        NotificationController.shared.refreshState(nil)
        refreshCameraCache()

        NetworkMonitor.shared.onStatusChange = { [weak self] connected in
            self?.handleNetworkChange(connected)
        }
        NetworkMonitor.shared.start()

        if NetworkMonitor.shared.isConnected {
            actualizacionComprobada = true
            loadSite()
            comprobarActualizacion()
        } else {
            showOffline()
        }
    }

    override var preferredStatusBarStyle: UIStatusBarStyle {
        return statusStyle
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
        NetworkMonitor.shared.stop()
    }

    // MARK: - Configuración de vistas

    private func setupContentView() {
        contentView = UIView()
        contentView.translatesAutoresizingMaskIntoConstraints = false
        contentView.backgroundColor = .clear
        view.addSubview(contentView)

        NSLayoutConstraint.activate([
            contentView.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor),
            contentView.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor),
            contentView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            contentView.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor)
        ])
    }

    private func setupWebViews() {
        let (mainWebView, mainBridge) = makeWebView(kind: .main)
        webView = mainWebView
        self.mainBridge = mainBridge

        mainBridge.onMessage = { [weak self] body in
            self?.handleMainMessage(body)
        }
        mainBridge.onDidFinish = { [weak self] view, url in
            self?.handleMainFinish(view, url: url)
        }
        mainBridge.onDidFail = { [weak self] _, error in
            // Las navegaciones canceladas (por ejemplo, al recargar) no son
            // errores reales, igual que en onReceivedError de Android.
            if (error as NSError).code == NSURLErrorCancelled { return }
            guard let self = self else { return }
            self.loadingSite = false
            if !self.pageLoaded {
                self.showOffline()
            }
        }

        addPinnedWebView(webView)

        let (offlineWebView, offlineBridge) = makeWebView(kind: .offline)
        offlineView = offlineWebView
        self.offlineBridge = offlineBridge
        offlineBridge.blockNavigation = true
        offlineBridge.onMessage = { [weak self] body in
            self?.handleOfflineMessage(body)
        }

        addPinnedWebView(offlineView)
        offlineView.isHidden = false
        webView.isHidden = false
        offlineView.isHidden = true

        loadLocalPage(AppConfig.offlinePage, in: offlineView)
    }

    private func addPinnedWebView(_ target: WKWebView) {
        target.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(target)

        NSLayoutConstraint.activate([
            target.leadingAnchor.constraint(equalTo: contentView.leadingAnchor),
            target.trailingAnchor.constraint(equalTo: contentView.trailingAnchor),
            target.topAnchor.constraint(equalTo: contentView.topAnchor),
            target.bottomAnchor.constraint(equalTo: contentView.bottomAnchor)
        ])
    }

    private func makeWebView(kind: WebViewBridge.Kind) -> (WKWebView, WebViewBridge) {
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = WKWebsiteDataStore.default()
        configuration.preferences.javaScriptCanOpenWindowsAutomatically = false

        // Las páginas offline se cargan con loadFileURL(allowingReadAccessTo:),
        // que habilita sus recursos locales (CSS, SVG y qrcodegen.js).
        let controller = WKUserContentController()

        let session = UserDefaults.standard.string(forKey: AppConfig.keyUsuarioSesion) ?? "{}"
        let cameraGranted = AVCaptureDevice.authorizationStatus(for: .video) == .authorized
        let notifState = NotificationController.shared.cachedState

        let bootstrap = AppConfig.bootstrapScript(sessionJSON: session,
                                                  cameraGranted: cameraGranted,
                                                  notifState: notifState)

        controller.addUserScript(WKUserScript(source: bootstrap,
                                              injectionTime: .atDocumentStart,
                                              forMainFrameOnly: false))
        controller.addUserScript(WKUserScript(source: AppConfig.androidAppShim(),
                                              injectionTime: .atDocumentStart,
                                              forMainFrameOnly: false))
        controller.addUserScript(WKUserScript(source: AppConfig.notificationShimJS,
                                              injectionTime: .atDocumentStart,
                                              forMainFrameOnly: false))
        controller.addUserScript(WKUserScript(source: AppConfig.dynamicBackgroundJS,
                                              injectionTime: .atDocumentEnd,
                                              forMainFrameOnly: true))

        let bridge = WebViewBridge(kind: kind, presenter: self)
        controller.add(bridge, name: "AndroidApp")
        configuration.userContentController = controller

        let webView = WKWebView(frame: .zero, configuration: configuration)
        webView.navigationDelegate = bridge
        webView.uiDelegate = bridge
        webView.allowsBackForwardNavigationGestures = true
        webView.scrollView.contentInsetAdjustmentBehavior = .never
        webView.isOpaque = false
        webView.backgroundColor = .clear

        return (webView, bridge)
    }

    private func setupObservers() {
        NotificationController.shared.onStateChange = { [weak self] _ in
            self?.refreshNotifCache()
        }

        NotificationCenter.default.addObserver(
            self,
            selector: #selector(applicationDidBecomeActive),
            name: UIApplication.didBecomeActiveNotification,
            object: nil
        )
    }

    @objc private func applicationDidBecomeActive() {
        refreshCameraCache()
        NotificationController.shared.refreshState(nil)
    }

    // MARK: - Red

    private func handleNetworkChange(_ connected: Bool) {
        if connected {
            if !pageLoaded && !loadingSite {
                loadSite()
            }
        } else {
            showOffline()
        }
    }

    private func loadSite() {
        guard let url = URL(string: AppConfig.siteURL) else { return }

        pageLoaded = false
        loadingSite = true
        offlineView.isHidden = true
        webView.isHidden = false

        let request = URLRequest(url: url,
                                 cachePolicy: .reloadIgnoringLocalAndRemoteCacheData,
                                 timeoutInterval: 30)
        webView.load(request)
    }

    private func handleMainFinish(_ view: WKWebView, url: URL?) {
        loadingSite = false

        if let string = url?.absoluteString, string.hasPrefix(baseSiteURL) {
            pageLoaded = true
            webView.isHidden = false
            offlineView.isHidden = true

            // Igual que el arranque de MainActivity: comprobar una vez que el
            // sitio cargó y hay conexión.
            if !actualizacionComprobada {
                actualizacionComprobada = true
                comprobarActualizacion()
            }
        }

        // Se reinyectan los scripts, como en onPageFinished del original.
        view.evaluateJavaScript(AppConfig.dynamicBackgroundJS, completionHandler: nil)
        view.evaluateJavaScript(AppConfig.notificationShimJS, completionHandler: nil)
    }

    private func showOffline() {
        offlineView.isHidden = false
        webView.isHidden = true
    }

    private func mostrarQrSinConexion() {
        loadLocalPage(AppConfig.qrOfflinePage, in: offlineView)
        offlineView.isHidden = false
        webView.isHidden = true
    }

    private func loadLocalPage(_ page: String, in target: WKWebView) {
        guard let url = Bundle.main.url(forResource: page,
                                        withExtension: "html",
                                        subdirectory: AppConfig.webAssetsFolder) else {
            return
        }
        let folder = url.deletingLastPathComponent()
        target.loadFileURL(url, allowingReadAccessTo: folder)
    }

    // MARK: - Mensajes del sitio principal

    private func handleMainMessage(_ body: [String: Any]) {
        guard let action = body["action"] as? String else { return }

        switch action {
        case "setNavigationBackground":
            if let color = body["color"] as? String {
                applyNavigationColor(color)
            }

        case "guardarIdUsuario":
            if let id = body["id"] as? String {
                guardarIdUsuario(id)
            }

        case "guardarUsuarioSesion":
            if let datos = body["datos"] as? String {
                guardarUsuarioSesion(datos)
            }

        case "limpiarUsuarioSesion":
            limpiarUsuarioSesion()

        case "abrirQrSinConexion":
            mostrarQrSinConexion()

        case "mostrarNotificacion":
            let titulo = body["titulo"] as? String ?? ""
            let cuerpo = body["cuerpo"] as? String ?? ""
            NotificationController.shared.show(title: titulo, body: cuerpo)

        case "pedirPermisoNotificaciones":
            if let callbackId = body["callbackId"] as? String {
                solicitarPermisoNotificaciones(callbackId)
            }

        case "comprobarActualizacion":
            comprobarActualizacion()

        default:
            break
        }
    }

    // MARK: - Mensajes de la vista offline

    private func handleOfflineMessage(_ body: [String: Any]) {
        guard let action = body["action"] as? String else { return }

        switch action {
        case "retry":
            if NetworkMonitor.shared.isConnected {
                loadSite()
            } else {
                showOffline()
            }

        case "abrirQrSinConexion":
            mostrarQrSinConexion()

        case "volverSinConexion":
            loadLocalPage(AppConfig.offlinePage, in: offlineView)

        case "guardarIdUsuario":
            if let id = body["id"] as? String {
                guardarIdUsuario(id)
            }

        default:
            break
        }
    }

    // MARK: - Sesión (equivalente a SharedPreferences "GamoPrefs")

    private func guardarIdUsuario(_ id: String) {
        UserDefaults.standard.set(id, forKey: AppConfig.keyIdUsuario)
        updateSessionCache()
    }

    private func guardarUsuarioSesion(_ datos: String) {
        guard datos.utf8.count <= 8192 else { return }
        guard let data = datos.data(using: .utf8),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return
        }

        let perfil: [String: Any] = [
            "uuid_usuario": (obj["uuid_usuario"] as? String) ?? "",
            "nombre": (obj["nombre"] as? String) ?? "",
            "apellido": (obj["apellido"] as? String) ?? "",
            "email": (obj["email"] as? String) ?? "",
            "rol": (obj["rol"] as? String) ?? "",
            "codigo_credencial": (obj["codigo_credencial"] as? String) ?? ""
        ]

        let defaults = UserDefaults.standard

        if let perfilData = try? JSONSerialization.data(withJSONObject: perfil),
           let perfilString = String(data: perfilData, encoding: .utf8) {
            defaults.set(perfilString, forKey: AppConfig.keyUsuarioSesion)
        }

        defaults.set((perfil["uuid_usuario"] as? String) ?? "",
                     forKey: AppConfig.keyIdUsuario)

        updateSessionCache()
    }

    private func limpiarUsuarioSesion() {
        let defaults = UserDefaults.standard
        let keys = [
            AppConfig.keyIdUsuario,
            AppConfig.keyUsuarioSesion,
            AppConfig.keyUuidUsuario,
            AppConfig.keyNombre,
            AppConfig.keyApellido,
            AppConfig.keyEmail,
            AppConfig.keyRol
        ]
        keys.forEach { defaults.removeObject(forKey: $0) }
        updateSessionCache()
    }

    private func updateSessionCache() {
        let session = UserDefaults.standard.string(forKey: AppConfig.keyUsuarioSesion) ?? "{}"
        let literal = AppConfig.jsStringLiteral(session)
        let script = "window.__gamoUsuarioSesion=\(literal);"

        webView?.evaluateJavaScript(script, completionHandler: nil)
        offlineView?.evaluateJavaScript(script, completionHandler: nil)
    }

    // MARK: - Color de navegación

    private func applyNavigationColor(_ hex: String) {
        guard let color = colorFromHex(hex) else { return }

        navigationColor = color
        UIView.animate(withDuration: 0.2) {
            self.view.backgroundColor = color
        }

        var red: CGFloat = 0
        var green: CGFloat = 0
        var blue: CGFloat = 0
        var alpha: CGFloat = 0

        if color.getRed(&red, green: &green, blue: &blue, alpha: &alpha) {
            let luminance = 0.2126 * red + 0.7152 * green + 0.0722 * blue
            statusStyle = luminance > 0.5 ? .darkContent : .lightContent
            setNeedsStatusBarAppearanceUpdate()
        }
    }

    private func colorFromHex(_ hex: String) -> UIColor? {
        var value = hex.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        if value.hasPrefix("#") {
            value.removeFirst()
        }

        guard let number = UInt64(value, radix: 16) else { return nil }

        if value.count == 6 {
            return UIColor(red: CGFloat((number & 0xFF0000) >> 16) / 255.0,
                           green: CGFloat((number & 0x00FF00) >> 8) / 255.0,
                           blue: CGFloat(number & 0x0000FF) / 255.0,
                           alpha: 1.0)
        }

        if value.count == 8 {
            return UIColor(red: CGFloat((number & 0x00FF0000) >> 16) / 255.0,
                           green: CGFloat((number & 0x0000FF00) >> 8) / 255.0,
                           blue: CGFloat(number & 0x000000FF) / 255.0,
                           alpha: CGFloat((number & 0xFF000000) >> 24) / 255.0)
        }

        return nil
    }

    // MARK: - Notificaciones

    private func solicitarPermisoNotificaciones(_ callbackId: String) {
        NotificationController.shared.requestPermission { [weak self] state in
            guard let self = self else { return }
            self.refreshNotifCache()

            let idLiteral = AppConfig.jsStringLiteral(callbackId)
            let stateLiteral = AppConfig.jsStringLiteral(state)
            let script = "window.__gamoNotifResultado&&window.__gamoNotifResultado(\(idLiteral),\(stateLiteral));"

            self.webView?.evaluateJavaScript(script, completionHandler: nil)
            self.offlineView?.evaluateJavaScript(script, completionHandler: nil)
        }
    }

    private func refreshNotifCache() {
        let state = NotificationController.shared.cachedState
        let literal = AppConfig.jsStringLiteral(state)
        let script = "window.__gamoNotifState=\(literal);"

        webView?.evaluateJavaScript(script, completionHandler: nil)
        offlineView?.evaluateJavaScript(script, completionHandler: nil)
    }

    // MARK: - Cámara

    private func refreshCameraCache() {
        let granted = AVCaptureDevice.authorizationStatus(for: .video) == .authorized
        let literal = granted ? "true" : "false"
        let script = "window.__gamoCamPerm=\(literal);"

        webView?.evaluateJavaScript(script, completionHandler: nil)
        offlineView?.evaluateJavaScript(script, completionHandler: nil)
    }

    // MARK: - Actualizaciones

    private func comprobarActualizacion() {
        guard !comprobandoActualizacion else { return }
        guard let url = URL(string: AppConfig.updateVersionURL) else { return }

        comprobandoActualizacion = true

        var request = URLRequest(url: url,
                                 cachePolicy: .reloadIgnoringLocalAndRemoteCacheData,
                                 timeoutInterval: 6)
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("no-cache", forHTTPHeaderField: "Cache-Control")

        URLSession.shared.dataTask(with: request) { [weak self] data, _, _ in
            guard let self = self else { return }

            defer {
                DispatchQueue.main.async {
                    self.comprobandoActualizacion = false
                }
            }

            guard let data = data,
                  let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                return
            }

            let remoteCode = (obj["versionCode"] as? NSNumber)?.intValue ?? 0
            let currentString = (Bundle.main.infoDictionary?["CFBundleVersion"] as? String) ?? "0"
            let currentCode = Int(currentString) ?? 0

            if remoteCode <= currentCode { return }

            let ignored = UserDefaults.standard.integer(forKey: AppConfig.keyVersionIgnorada)
            if ignored >= remoteCode { return }

            let name = (obj["versionName"] as? String) ?? String(remoteCode)
            let notes = (obj["notes"] as? String) ?? ""
            var link = (obj["iosUrl"] as? String)
                ?? (obj["url"] as? String)
                ?? AppConfig.updateFallbackURL
            if link.isEmpty { link = AppConfig.updateFallbackURL }
            // En iOS no se instala un APK: se evita enviar al usuario a uno.
            if link.lowercased().hasSuffix(".apk") {
                link = AppConfig.siteURL
            }

            DispatchQueue.main.async {
                self.mostrarPantallaActualizacion(code: remoteCode,
                                                  nombre: name,
                                                  notas: notes,
                                                  url: link)
            }
        }.resume()
    }

    private func mostrarPantallaActualizacion(code: Int,
                                              nombre: String,
                                              notas: String,
                                              url: String) {
        guard updateOverlay == nil else { return }

        updateVersionCode = code
        updateVersionName = nombre
        updateNotes = notas
        updateURL = url

        let (updateWebView, bridge) = makeWebView(kind: .update)
        updateView = updateWebView
        updateBridge = bridge
        bridge.blockNavigation = true
        bridge.onMessage = { [weak self] body in
            self?.handleUpdateMessage(body)
        }
        bridge.onDidFinish = { [weak self] target, _ in
            self?.enviarDatosActualizacion(target)
        }

        let overlay = UIView()
        overlay.translatesAutoresizingMaskIntoConstraints = false
        overlay.backgroundColor = UIColor(red: CGFloat(0x12) / 255.0,
                                          green: CGFloat(0x1C) / 255.0,
                                          blue: CGFloat(0x2E) / 255.0,
                                          alpha: 1.0)

        updateWebView.translatesAutoresizingMaskIntoConstraints = false
        overlay.addSubview(updateWebView)

        NSLayoutConstraint.activate([
            updateWebView.leadingAnchor.constraint(equalTo: overlay.leadingAnchor),
            updateWebView.trailingAnchor.constraint(equalTo: overlay.trailingAnchor),
            updateWebView.topAnchor.constraint(equalTo: overlay.topAnchor),
            updateWebView.bottomAnchor.constraint(equalTo: overlay.bottomAnchor)
        ])

        view.addSubview(overlay)
        NSLayoutConstraint.activate([
            overlay.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            overlay.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            overlay.topAnchor.constraint(equalTo: view.topAnchor),
            overlay.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])

        updateOverlay = overlay
        loadLocalPage(AppConfig.updatePage, in: updateWebView)
    }

    private func enviarDatosActualizacion(_ target: WKWebView) {
        let datos: [String: Any] = [
            "versionName": updateVersionName,
            "notes": updateNotes
        ]
        let json = AppConfig.jsonObjectString(datos)
        target.evaluateJavaScript("window.mostrarActualizacion(\(json));",
                                  completionHandler: nil)
    }

    private func handleUpdateMessage(_ body: [String: Any]) {
        guard let action = body["action"] as? String else { return }

        switch action {
        case "descargarActualizacion":
            iniciarActualizacion()

        case "omitirActualizacion":
            UserDefaults.standard.set(updateVersionCode,
                                      forKey: AppConfig.keyVersionIgnorada)
            cerrarPantallaActualizacion()

        default:
            break
        }
    }

    private func iniciarActualizacion() {
        marcarDescargaEnCurso()

        guard let url = URL(string: updateURL) else {
            avisarErrorActualizacion("No se pudo iniciar la descarga.")
            return
        }

        // iOS no permite instalar un APK. El flujo equivalente es abrir la
        // página de actualización (App Store / TestFlight / web) en el sistema.
        UIApplication.shared.open(url, options: [:]) { [weak self] success in
            guard let self = self else { return }
            if success {
                self.updateView?.evaluateJavaScript(
                    "window.actualizacionError && window.actualizacionError('');",
                    completionHandler: nil
                )
            } else {
                self.avisarErrorActualizacion("No se pudo abrir la página de actualización.")
            }
        }
    }

    private func cerrarPantallaActualizacion() {
        guard let overlay = updateOverlay else { return }

        overlay.removeFromSuperview()
        updateView?.removeFromSuperview()

        updateOverlay = nil
        updateView = nil
        updateBridge = nil
    }

    private func marcarDescargaEnCurso() {
        updateView?.evaluateJavaScript(
            "window.actualizacionDescargando && window.actualizacionDescargando();",
            completionHandler: nil
        )
    }

    private func avisarErrorActualizacion(_ mensaje: String) {
        let literal = AppConfig.jsStringLiteral(mensaje)
        updateView?.evaluateJavaScript(
            "window.actualizacionError && window.actualizacionError(\(literal));",
            completionHandler: nil
        )

        let alert = UIAlertController(title: nil,
                                      message: mensaje,
                                      preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default))
        present(alert, animated: true)
    }
}
