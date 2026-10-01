import SwiftUI

/// Decides what the person sees: setup notice, sign-in, the steps after sign-in, or the app.
struct RootView: View {
    @Environment(SessionStore.self) private var session

    var body: some View {
        Group {
            if !AppConfig.isBackendConfigured {
                SetupRequiredView()
            } else {
                switch session.auth {
                case .loading:
                    ProgressView()
                case .signedOut:
                    AuthFlowView()
                case .signedIn:
                    SignedInView()
                }
            }
        }
        .sheet(isPresented: Binding(get: { session.isRecoveringPassword }, set: { session.isRecoveringPassword = $0 })) {
            NewPasswordView()
        }
    }
}

/// After sign-in: account deletion pending, changed legal texts, profile, verification — in that order.
private struct SignedInView: View {
    @Environment(SessionStore.self) private var session

    var body: some View {
        switch (session.profile, session.gate) {
        case (.some(.failed(let error)), _), (_, .some(.failed(let error))):
            ErrorStateView(error: error, title: "Hesap bilgileri yüklenemedi") { Task { await session.reload() } }
                .safeAreaInset(edge: .bottom) {
                    Button("Çıkış yap", role: .destructive) { Task { await session.signOut() } }.padding()
                }
        case (.some(.loaded(let profile)), .some(.loaded(let gate))):
            if let date = gate.deletionScheduledFor {
                DeletionPendingView(scheduledFor: date)
            } else if !gate.pending.isEmpty {
                LegalUpdateView(documents: gate.pending)
            } else {
                switch profile.status {
                case .profileIncomplete: ProfileSetupView()
                case .documentRequired, .rejected, .pendingReview: VerificationView(profile: profile)
                case .suspended: SuspendedView()
                case .approved: MainTabView(profile: profile)
                }
            }
        default:
            ProgressView("Yükleniyor…")
        }
    }
}

/// Shown when the build was made without the backend configuration (no fake data, no fake sign-in).
struct SetupRequiredView: View {
    var body: some View {
        ContentUnavailableView {
            Label("Henüz bağlı değil", systemImage: "wrench.and.screwdriver")
        } description: {
            Text("Bu sürüm sunucu yapılandırması olmadan derlendi. Kurulum gerekli: SUPABASE_URL ve SUPABASE_ANON_KEY ile yeniden derleyin.")
        }
    }
}

private struct SuspendedView: View {
    @Environment(SessionStore.self) private var session
    @State private var showingPrivacy = false

    var body: some View {
        ContentUnavailableView {
            Label("Hesabın askıya alındı", systemImage: "hand.raised")
        } description: {
            Text("Topluluk kurallarına aykırı bir durum nedeniyle hesabın şu an kullanılamıyor. Gerekçeyi ve itiraz yolunu Gizlilik ve KVKK bölümünde görebilirsin.")
        } actions: {
            Button("Gizlilik ve KVKK") { showingPrivacy = true }
            Button("Çıkış yap", role: .destructive) { Task { await session.signOut() } }
        }
        .sheet(isPresented: $showingPrivacy) { RoutedStack { PrivacyView() } }
    }
}
