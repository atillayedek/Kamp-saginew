import SwiftUI

struct MyProfileView: View {
    let profile: Profile
    @Environment(SessionStore.self) private var session
    @State private var confirmingSignOut = false

    var body: some View {
        List {
            Section {
                HStack(spacing: Spacing.md) {
                    AvatarView(name: profile.fullName ?? profile.username, size: 64)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(profile.fullName ?? "").font(.title3.bold())
                        if let username = profile.username { Text("@" + username).foregroundStyle(.secondary) }
                        Text([profile.universityName, profile.department].compactMap { $0 }.joined(separator: " · "))
                            .font(.caption).foregroundStyle(.secondary)
                    }
                }
                .padding(.vertical, Spacing.xs)
                if let bio = profile.bio, !bio.isEmpty { Text(bio) }
            }
            Section {
                NavigationLink(value: AppRoute.user(profile.id)) { Label("Gönderilerim", systemImage: "text.bubble") }
                NavigationLink(value: AppRoute.savedPosts) { Label("Kaydedilenler", systemImage: "bookmark") }
            }
            Section {
                NavigationLink(value: AppRoute.settings) { Label("Ayarlar", systemImage: "gearshape") }
                NavigationLink(value: AppRoute.privacy) { Label("Gizlilik ve KVKK", systemImage: "hand.raised") }
            }
            Section {
                Button("Çıkış yap", role: .destructive) { confirmingSignOut = true }
            }
        }
        .navigationTitle("Profil")
        .refreshable { await session.reload() }
        .confirmationDialog("Çıkış yapılsın mı?", isPresented: $confirmingSignOut, titleVisibility: .visible) {
            Button("Çıkış yap", role: .destructive) { Task { await session.signOut() } }
        }
    }
}

struct UserProfileView: View {
    let userId: String
    @State private var profile: UserProfile?
    @State private var error: AppError?
    @State private var model: PostListModel
    @State private var reporting = false
    @State private var confirmingBlock = false
    @State private var runner = ActionRunner()
    @Environment(\.dismiss) private var dismiss

    init(userId: String) {
        self.userId = userId
        _model = State(initialValue: PostListModel { cursor in try await CommunityService().userPosts(userId: userId, cursor: cursor) })
    }

    var body: some View {
        Group {
            if let error, profile == nil {
                ErrorStateView(error: error, title: error == .notFound ? "Profil bulunamadı" : "Profil yüklenemedi") { Task { await load() } }
            } else if let profile {
                PostListView(model: model, emptyTitle: "Henüz gönderi yok", emptyText: "") {
                    header(profile).listRowSeparator(.hidden)
                }
            } else {
                ProgressView()
            }
        }
        .navigationTitle(profile?.username.map { "@" + $0 } ?? "Profil")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if let profile, !profile.isMe {
                Menu {
                    Button("Şikâyet et", systemImage: "exclamationmark.bubble") { reporting = true }
                    Button("Engelle", systemImage: "hand.raised", role: .destructive) { confirmingBlock = true }
                } label: { Image(systemName: "ellipsis.circle") }
            }
        }
        .sheet(isPresented: $reporting) { ReportSheet(target: .user, id: userId) }
        .confirmationDialog("Engellensin mi?", isPresented: $confirmingBlock, titleVisibility: .visible) {
            Button("Engelle", role: .destructive) {
                runner.run {
                    try await ModerationService().block(userId: userId)
                    NotificationCenter.default.post(name: .postRemoved, object: userId, userInfo: ["author": true])
                    dismiss()
                }
            }
        } message: {
            Text("Birbirinizin paylaşımlarını görmezsiniz ve mesajlaşamazsınız. Engellediğin kişiye bildirilmez.")
        }
        .task { await load() }
    }

    private func header(_ profile: UserProfile) -> some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            HStack(spacing: Spacing.md) {
                AvatarView(name: profile.fullName ?? profile.username, size: 64)
                VStack(alignment: .leading, spacing: 2) {
                    Text(profile.fullName ?? "").font(.title3.bold())
                    Text([profile.university, profile.department].compactMap { $0 }.joined(separator: " · "))
                        .font(.caption).foregroundStyle(.secondary)
                    Text("\(profile.postCount) gönderi").font(.caption).foregroundStyle(.secondary)
                }
            }
            if let bio = profile.bio, !bio.isEmpty { Text(bio) }
            InlineError(error: runner.error)
        }
        .padding(.vertical, Spacing.xs)
    }

    private func load() async {
        error = nil
        do {
            profile = try await CommunityService().userProfile(id: userId)
            await model.load()
        } catch {
            self.error = ErrorMapping.appError(error)
        }
    }
}

// MARK: - Settings

struct SettingsView: View {
    var body: some View {
        List {
            Section("Hesap") {
                NavigationLink(value: AppRoute.privacy) { Label("Gizlilik ve KVKK", systemImage: "hand.raised") }
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    Link(destination: url) { Label("Bildirim izinleri", systemImage: "bell.badge") }
                }
            }
            Section("Hukuki metinler") {
                NavigationLink(value: AppRoute.legal(LegalDocTypes.privacyNotice)) { Text("Aydınlatma Metni") }
                NavigationLink(value: AppRoute.legal(LegalDocTypes.privacyPolicy)) { Text("Gizlilik Politikası") }
                NavigationLink(value: AppRoute.legal(LegalDocTypes.terms)) { Text("Kullanım Koşulları") }
                NavigationLink(value: AppRoute.legal(LegalDocTypes.communityRules)) { Text("Topluluk Kuralları") }
            }
            Section("Yardım") {
                if let faq = AppConfig.websitePage("/sss") { Link("Sık sorulan sorular", destination: faq) }
                if let copyright = AppConfig.websitePage("/telif-bildirimi") { Link("Telif bildirimi", destination: copyright) }
            }
            Section {
                LabeledContent("Sürüm", value: AppConfig.appVersion)
            }
        }
        .navigationTitle("Ayarlar")
    }
}

// MARK: - Privacy and KVKK

struct PrivacyView: View {
    @Environment(SessionStore.self) private var session
    @State private var consents: [ConsentStatus]?
    @State private var requests: [DataSubjectRequest] = []
    @State private var loadError: AppError?
    @State private var runner = ActionRunner()
    @State private var export: DataExport?
    @State private var requestType: DataRequestType = .access
    @State private var requestDetails = ""
    @State private var submittedRequestNo: String?
    @State private var confirmingDeletion = false
    @State private var deletionScheduled: Date?

    private var profile: Profile? {
        if case .some(.loaded(let profile)) = session.profile { return profile }
        return nil
    }

    var body: some View {
        Form {
            if let loadError { Section { InlineError(error: loadError) } }
            Section {
                InlineError(error: runner.error)
            }
            consentSection
            if let profile {
                Section {
                    Toggle("Soyadımı diğer öğrencilere göster", isOn: Binding(
                        get: { profile.showFullName },
                        set: { value in runner.run { try await ComplianceService().setShowFullName(value); await session.reloadProfile() } }
                    ))
                } footer: {
                    Text("Kapalıyken diğer öğrenciler adını “Ayşe Y.” biçiminde görür. E-posta adresin hiçbir zaman gösterilmez.")
                }
            }
            Section {
                Button("Verilerimi hazırla") {
                    runner.run { export = try await ComplianceService().exportMyData() }
                }
                .disabled(runner.isRunning)
                if let export {
                    Link("Okunabilir sürümü aç", destination: export.htmlURL)
                    Link("JSON dosyası", destination: export.jsonURL)
                    Text("Bağlantılar \(Formatting.dateTime(export.expiresAt)) tarihine kadar geçerli.").font(.caption).foregroundStyle(.secondary)
                }
            } header: {
                Text("Verilerimi indir")
            } footer: {
                Text("Profilin, paylaşımların, mesajların, ders notların, onay geçmişin ve giriş kayıtların okunabilir (HTML) ve makine tarafından okunabilir (JSON) dosya olarak hazırlanır. 24 saatte en fazla 3 kez.")
            }
            requestSection
            Section("Hukuki metinler") {
                NavigationLink(value: AppRoute.legal(LegalDocTypes.privacyNotice)) { Text("Aydınlatma Metni") }
                NavigationLink(value: AppRoute.legal(LegalDocTypes.privacyPolicy)) { Text("Gizlilik Politikası") }
            }
            Section {
                Button("Hesabımı sil", role: .destructive) { confirmingDeletion = true }
            } footer: {
                Text("Talepten sonra \(session.config?.deletionGraceDays ?? 30) gün içinde vazgeçebilirsin; süre dolunca hesabın ve içeriklerin kalıcı olarak silinir. Yasal olarak saklanması gereken giriş kayıtları profilinden ayrılarak saklama süresi boyunca tutulur.")
            }
        }
        .navigationTitle("Gizlilik ve KVKK")
        .task { await load() }
        .refreshable { await load() }
        .confirmationDialog("Hesabın silinsin mi?", isPresented: $confirmingDeletion, titleVisibility: .visible) {
            Button("Hesabımı sil", role: .destructive) {
                runner.run {
                    deletionScheduled = try await AccountService().requestDeletion()
                }
            }
        } message: {
            Text("Bu işlemi geri alma süresi içinde iptal edebilirsin.")
        }
        .alert("Silme talebin alındı", isPresented: Binding(get: { deletionScheduled != nil }, set: { if !$0 { deletionScheduled = nil } })) {
            Button("Tamam") { Task { await session.reload() } }
        } message: {
            Text("Hesabın \(deletionScheduled.map(Formatting.day) ?? "") tarihinde kalıcı olarak silinecek.")
        }
        .alert("Başvurun alındı", isPresented: Binding(get: { submittedRequestNo != nil }, set: { if !$0 { submittedRequestNo = nil } })) {
            Button("Tamam", role: .cancel) {}
        } message: {
            Text("Başvuru numaran: \(submittedRequestNo ?? ""). Yanıt bu ekranda ve bildirim olarak gelecek.")
        }
    }

    @ViewBuilder
    private var consentSection: some View {
        Section {
            if let consents {
                if consents.isEmpty { Text("İsteğe bağlı izin yok.").foregroundStyle(.secondary) }
                ForEach(consents) { consent in
                    Toggle(consent.title, isOn: Binding(
                        get: { consent.granted },
                        set: { value in
                            runner.run {
                                try await ComplianceService().setConsent(docType: consent.docType, granted: value)
                                self.consents = try await ComplianceService().consents()
                            }
                        }
                    ))
                }
            } else if loadError == nil {
                ProgressView()
            }
        } header: {
            Text("İzinlerim")
        } footer: {
            Text("İzinleri istediğin an geri çekebilirsin; geri çekme hemen geçerli olur.")
        }
    }

    @ViewBuilder
    private var requestSection: some View {
        Section {
            Picker("Başvuru türü", selection: $requestType) {
                ForEach(DataRequestType.allCases) { Text($0.title).tag($0) }
            }
            TextField("Talebini yaz (en az 10 karakter)", text: $requestDetails, axis: .vertical).lineLimit(3...8)
            Button("Başvuruyu gönder") {
                runner.run {
                    submittedRequestNo = try await ComplianceService().submitRequest(type: requestType, details: requestDetails)
                    requestDetails = ""
                    requests = try await ComplianceService().myRequests()
                }
            }
            .disabled(runner.isRunning || requestDetails.trimmingCharacters(in: .whitespacesAndNewlines).count < 10)
            ForEach(requests) { request in
                VStack(alignment: .leading, spacing: 2) {
                    Text("\(request.requestNo) · \(request.type?.title ?? "")").font(.subheadline.weight(.semibold))
                    Text(request.statusTitle + (request.dueAt.map { " · son gün \(Formatting.day($0))" } ?? ""))
                        .font(.caption).foregroundStyle(.secondary)
                    if let response = request.response { Text(response).font(.caption) }
                }
            }
        } header: {
            Text("KVKK başvurusu")
        } footer: {
            Text("KVKK md.11 kapsamındaki haklarını kullanmak için başvur; en geç 30 gün içinde yanıtlanır.")
        }
    }

    private func load() async {
        loadError = nil
        do {
            async let loadedConsents = ComplianceService().consents()
            async let loadedRequests = ComplianceService().myRequests()
            consents = try await loadedConsents
            requests = try await loadedRequests
        } catch {
            loadError = ErrorMapping.appError(error)
        }
    }
}
