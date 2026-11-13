//
//  NotificationController.swift
//  GamoEkkIOS
//
//  Equivalente a NotificationChannel + NotificationManager de Android,
//  usando UNUserNotificationCenter.
//

import Foundation
import UserNotifications

final class NotificationController: NSObject, UNUserNotificationCenterDelegate {

    static let shared = NotificationController()

    /// Estado cacheado que el shim JS devuelve de forma sincrónica.
    private(set) var cachedState = "default"

    /// Se invoca en el hilo principal cuando cambia el estado del permiso.
    var onStateChange: ((String) -> Void)?

    private override init() {
        super.init()
    }

    func configure() {
        UNUserNotificationCenter.current().delegate = self
        refreshState(nil)
    }

    /// Estado actual con formato Android: "granted" | "denied" | "default".
    func refreshState(_ completion: ((String) -> Void)?) {
        UNUserNotificationCenter.current().getNotificationSettings { [weak self] settings in
            let state = NotificationController.stateString(from: settings)
            guard let self = self else { return }
            self.cachedState = state
            DispatchQueue.main.async {
                completion?(state)
                self.onStateChange?(state)
            }
        }
    }

    /// Solicita permiso y devuelve el estado resultante.
    func requestPermission(completion: @escaping (String) -> Void) {
        UserDefaults.standard.set(true, forKey: AppConfig.keyNotifPedido)

        UNUserNotificationCenter.current().requestAuthorization(
            options: [.alert, .badge, .sound]
        ) { _, _ in
            NotificationController.shared.refreshState { state in
                completion(state)
            }
        }
    }

    /// Publica una notificación local (equivale a NotificationManager.notify).
    func show(title: String, body: String) {
        let tituloFinal = title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? "GAMO"
            : title

        let content = UNMutableNotificationContent()
        content.title = tituloFinal
        content.body = body
        content.sound = .default

        let identifier = "gamo-\(Int(Date().timeIntervalSince1970 * 1000))"
        let request = UNNotificationRequest(identifier: identifier,
                                            content: content,
                                            trigger: nil)

        UNUserNotificationCenter.current().add(request, withCompletionHandler: nil)
    }

    // MARK: - UNUserNotificationCenterDelegate

    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        if #available(iOS 14.0, *) {
            completionHandler([.banner, .sound])
        } else {
            completionHandler([.alert, .sound])
        }
    }

    // MARK: - Helpers

    private static func stateString(from settings: UNNotificationSettings) -> String {
        switch settings.authorizationStatus {
        case .authorized, .provisional:
            return "granted"
        case .denied:
            return "denied"
        case .notDetermined:
            return "default"
        default:
            return "default"
        }
    }
}
