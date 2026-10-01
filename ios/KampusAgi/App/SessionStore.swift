import Foundation
import Observation
import Supabase

/// Who is signed in and what must happen before the main app opens. One instance for the app.
@MainActor
@Observable
final class SessionStore {
    enum AuthState: Equatable {
        case loading
        case signedOut
        case signedIn(userId: String, email: String)
    }

    enum Loadable<T: Equatable>: Equatable {
        case loading
        case loaded(T)
        case failed(AppError)
    }

    private(set) var auth: AuthState = .loading
    private(set) var profile: Loadable<Profile>?
    private(set) var gate: Loadable<AccountGate>?
    private(set) var config: ComplianceConfig?
    /// Set after a password-recovery link: the app asks for a new password first.
    var isRecoveringPassword = false
    /// A sign-in/verification link that could not be used, shown on the sign-in screen.
    var linkError: AppError?

    private let authService = AuthService()
    private let profiles = ProfileService()
    private let compliance = ComplianceService()
    private var listenTask: Task<Void, Never>?

    var userId: String? {
        if case .signedIn(let id, _) = auth { return id }
        return nil
    }

    func start() {
        guard listenTask == nil else { return }
        guard let client = Backend.shared.client else {
            auth = .signedOut
            return
        }
        listenTask = Task { [weak self] in
            for await (event, session) in client.auth.authStateChanges {
                guard let self else { return }
                if event == .passwordRecovery { self.isRecoveringPassword = true }
                // A stored session that cannot be refreshed offline is kept: every request then reports
                // a network error until the connection is back.
                if let user = session?.user {
                    let changed = self.userId != user.id.uuidString.lowercased()
                    self.auth = .signedIn(userId: user.id.uuidString.lowercased(), email: user.email ?? "")
                    if changed { await self.reload() }
                } else if event != .tokenRefreshed {
                    self.auth = .signedOut
                    self.profile = nil
                    self.gate = nil
                }
            }
        }
        Task { await loadConfig() }
    }

    func loadConfig() async {
        if config == nil { config = try? await compliance.config() }
    }

    /// Profile and gate after sign-in, after the profile changes and on pull-to-refresh.
    func reload() async {
        guard let userId else { return }
        if case .some(.loaded) = profile {} else { profile = .loading }
        if case .some(.loaded) = gate {} else { gate = .loading }
        async let loadedProfile = attempt { try await profiles.load(userId: userId) }
        async let loadedGate = attempt { try await compliance.accountGate() }
        switch await loadedProfile {
        case .success(let value): profile = .loaded(value)
        case .failure(let error): if case .some(.loaded) = profile {} else { profile = .failed(error) }
        }
        switch await loadedGate {
        case .success(let value): gate = .loaded(value)
        case .failure(let error): if case .some(.loaded) = gate {} else { gate = .failed(error) }
        }
    }

    func reloadProfile() async {
        guard let userId else { return }
        if case .success(let value) = await attempt({ try await profiles.load(userId: userId) }) {
            profile = .loaded(value)
        }
    }

    func signedIn() async {
        await compliance.logAccess(event: "login")
    }

    func signOut() async {
        await compliance.logAccess(event: "logout")
        await authService.signOut()
        auth = .signedOut
        profile = nil
        gate = nil
    }

    func handle(url: URL) async {
        switch await authService.handle(url: url) {
        case .notAnAuthLink, .signedIn: break
        case .passwordRecovery: isRecoveringPassword = true
        case .linkExpired: linkError = .sessionExpired
        case .failed(let error): linkError = error
        }
    }
}
