//
//  NetworkMonitor.swift
//  GamoEkkIOS
//
//  Equivalente a ConnectivityManager + NetworkCallback de Android.
//

import Foundation
import Network

final class NetworkMonitor {

    static let shared = NetworkMonitor()

    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "com.gamoekk.network")
    private var started = false

    /// Se invoca en el hilo principal cuando cambia la conectividad.
    var onStatusChange: ((Bool) -> Void)?

    private(set) var isConnected = false

    private init() {}

    func start() {
        guard !started else { return }
        started = true

        monitor.pathUpdateHandler = { [weak self] path in
            guard let self = self else { return }
            let connected = (path.status == .satisfied)
            self.isConnected = connected
            DispatchQueue.main.async {
                self.onStatusChange?(connected)
            }
        }

        monitor.start(queue: queue)
    }

    func stop() {
        guard started else { return }
        started = false
        monitor.cancel()
    }
}
