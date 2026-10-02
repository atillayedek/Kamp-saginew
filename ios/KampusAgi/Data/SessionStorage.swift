import Foundation
import os
import Supabase

/// The storage interface the Auth client uses (named here so tests need not import Supabase).
typealias SessionBackingStore = AuthLocalStorage

/// Where the Auth session is kept: the Keychain, plus a copy in memory for this launch.
///
/// A build without a code signature (the CI simulator app, e.g. on Appetize) has no Keychain access.
/// Without the in-memory copy the client would lose the session right after sign-in and send every
/// request without the user's token, so the database would answer as an anonymous visitor. With it,
/// such a build stays signed in until the app is closed; a signed build keeps using the Keychain.
final class SessionStorage: AuthLocalStorage, @unchecked Sendable {
    private let persistent: any SessionBackingStore
    private let lock = NSLock()
    private var memory: [String: Data] = [:]
    private let log = Logger(subsystem: "com.kampusagi.ios", category: "SessionStorage")

    init(persistent: any SessionBackingStore = KeychainLocalStorage()) {
        self.persistent = persistent
    }

    func store(key: String, value: Data) throws {
        lock.withLock { memory[key] = value }
        do {
            try persistent.store(key: key, value: value)
        } catch {
            log.error("Session could not be saved to the Keychain; keeping it in memory for this launch")
        }
    }

    func retrieve(key: String) throws -> Data? {
        if let value = lock.withLock({ memory[key] }) { return value }
        do {
            let value = try persistent.retrieve(key: key)
            if let value { lock.withLock { memory[key] = value } }
            return value
        } catch {
            log.error("Session could not be read from the Keychain")
            return nil
        }
    }

    func remove(key: String) throws {
        lock.withLock { memory[key] = nil }
        do {
            try persistent.remove(key: key)
        } catch {
            log.error("Session could not be removed from the Keychain")
        }
    }
}
