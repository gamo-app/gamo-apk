//
//  AppDelegate.swift
//  GamoEkkIOS
//
//  Punto de entrada equivalente a la Activity de Android.
//  Se usa UIKit (no storyboard) para reflejar el flujo imperativo original.
//

import UIKit

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {

    var window: UIWindow?
    private var rootController: MainViewController?

    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {

        // Equivalente a crearCanalNotificaciones() del original.
        NotificationController.shared.configure()

        let controller = MainViewController()
        let window = UIWindow(frame: UIScreen.main.bounds)
        window.rootViewController = controller
        window.backgroundColor = .white
        window.makeKeyAndVisible()
        self.window = window
        self.rootController = controller

        return true
    }
}
