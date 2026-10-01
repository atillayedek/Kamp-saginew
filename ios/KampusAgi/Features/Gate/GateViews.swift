import SwiftUI
import UniformTypeIdentifiers

// MARK: - Changed legal texts

/// A changed agreement must be accepted again; a changed notice is shown and its reading recorded.
struct LegalUpdateView: View {
    @Environment(SessionStore.self) private var session
    let documents: [LegalDocumentInfo]
    @State private var runner = ActionRunner()

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Text("Aşağıdaki metinler güncellendi. Devam etmek için okuyup onaylaman gerekiyor.")
                        .foregroundStyle(.secondary)
                }
                Section {
                    ForEach(documents) { document in
                        NavigationLink {
                            LegalDocumentView(docType: document.docType)
                        } label: {
                            Label(document.title, systemImage: document.kind == .agreement ? "signature" : "doc.text")
                        }
                    }
                }
                Section {
                    InlineError(error: runner.error)
                    Button(documents.contains { $0.kind == .agreement } ? "Okudum, kabul ediyorum" : "Okudum") {
                        runner.run {
                            let compliance = ComplianceService()
                            for document in documents { try await compliance.acknowledge(document, channel: "login") }
                            await session.reload()
                        }
                    }
                    .buttonStyle(.primary)
                    .disabled(runner.isRunning)
                    .listRowInsets(EdgeInsets())
                    .listRowBackground(Color.clear)
                }
                Section {
                    Button("Çıkış yap", role: .destructive) { Task { await session.signOut() } }
                }
            }
            .navigationTitle("Güncellenen metinler")
        }
    }
}

// MARK: - Account deletion pending

struct DeletionPendingView: View {
    @Environment(SessionStore.self) private var session
    let scheduledFor: Date
    @State private var runner = ActionRunner()

    var body: some View {
        ContentUnavailableView {
            Label("Hesabın silinecek", systemImage: "trash")
        } description: {
            Text("Hesabın \(Formatting.day(scheduledFor)) tarihinde kalıcı olarak silinecek. O zamana kadar vazgeçebilirsin; vazgeçmezsen başka bir işlem yapamazsın.")
        } actions: {
            InlineError(error: runner.error)
            Button("Silmekten vazgeç") {
                runner.run {
                    try await ComplianceService().cancelDeletion()
                    await session.reload()
                }
            }
            .buttonStyle(.borderedProminent)
            .disabled(runner.isRunning)
            Button("Çıkış yap", role: .destructive) { Task { await session.signOut() } }
        }
    }
}

// MARK: - Profile setup

struct ProfileSetupView: View {
    @Environment(SessionStore.self) private var session
    @State private var fullName = ""
    @State private var username = ""
    @State private var department = ""
    @State private var university: University?
    @State private var runner = ActionRunner()

    private var isValid: Bool {
        ProfileInputValidator.isValidFullName(fullName)
            && ProfileInputValidator.isValidUsername(ProfileInputValidator.normalizeUsername(username))
            && ProfileInputValidator.isValidDepartment(department)
            && university != nil
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text("Diğer öğrenciler seni bu bilgilerle görecek. Soyadın varsayılan olarak baş harfiyle gösterilir.")
                        .foregroundStyle(.secondary)
                }
                Section("Profil") {
                    TextField("Ad soyad", text: $fullName).textContentType(.name)
                    TextField("Kullanıcı adı", text: $username)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    if !username.isEmpty && !ProfileInputValidator.isValidUsername(ProfileInputValidator.normalizeUsername(username)) {
                        Text("3–30 karakter; yalnızca harf (a-z), rakam, nokta ve alt çizgi.").font(.footnote).foregroundStyle(Brand.danger)
                    }
                }
                Section("Üniversite") {
                    NavigationLink {
                        UniversityPickerView(selection: $university)
                    } label: {
                        LabeledContent("Üniversite", value: university?.name ?? "Seç")
                    }
                    TextField("Bölüm", text: $department)
                }
                Section {
                    InlineError(error: runner.error)
                    Button("Devam et") {
                        guard let university else { return }
                        runner.run {
                            try await ProfileService().completeProfile(fullName: fullName, username: username,
                                                                       universityId: university.id, department: department)
                            await session.reloadProfile()
                        }
                    }
                    .buttonStyle(.primary)
                    .disabled(!isValid || runner.isRunning)
                    .listRowInsets(EdgeInsets())
                    .listRowBackground(Color.clear)
                }
                Section {
                    Button("Çıkış yap", role: .destructive) { Task { await session.signOut() } }
                }
            }
            .navigationTitle("Profilini tamamla")
        }
    }
}

struct UniversityPickerView: View {
    @Binding var selection: University?
    @Environment(\.dismiss) private var dismiss
    @State private var universities: [University]?
    @State private var error: AppError?
    @State private var query = ""

    private var filtered: [University] {
        let all = universities ?? []
        let needle = BodyLinks.fold(query.trimmingCharacters(in: .whitespaces))
        guard !needle.isEmpty else { return all }
        return all.filter { BodyLinks.fold($0.name).contains(needle) || BodyLinks.fold($0.city).contains(needle) }
    }

    var body: some View {
        Group {
            if let error {
                ErrorStateView(error: error) { Task { await load() } }
            } else if universities == nil {
                ProgressView()
            } else {
                List(filtered) { university in
                    Button {
                        selection = university
                        dismiss()
                    } label: {
                        VStack(alignment: .leading) {
                            Text(university.name).foregroundStyle(.primary)
                            Text(university.city).font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
                .overlay {
                    if filtered.isEmpty { ContentUnavailableView.search(text: query) }
                }
            }
        }
        .searchable(text: $query, prompt: "Üniversite veya şehir (ör. KKTC)")
        .navigationTitle("Üniversite seç")
        .task { if universities == nil { await load() } }
    }

    private func load() async {
        error = nil
        do { universities = try await ProfileService().universities() } catch { self.error = ErrorMapping.appError(error) }
    }
}

// MARK: - Student document

struct VerificationView: View {
    @Environment(SessionStore.self) private var session
    let profile: Profile
    @State private var verification: Verification?
    @State private var importing = false
    @State private var runner = ActionRunner()
    @State private var showingPrivacy = false

    private var waiting: Bool { profile.status == .pendingReview }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    if waiting {
                        Label("Belgen inceleniyor. Onaylanınca bildirim alacaksın.", systemImage: "hourglass")
                    } else if profile.status == .rejected || verification?.status == .rejected {
                        Label {
                            VStack(alignment: .leading, spacing: Spacing.xs) {
                                Text("Belgen onaylanmadı.").bold()
                                if let reason = verification?.rejectionReason { Text(reason).foregroundStyle(.secondary) }
                                Text("Gerekçeye uygun yeni bir belge yükleyebilirsin.").foregroundStyle(.secondary)
                            }
                        } icon: { Image(systemName: "xmark.seal").foregroundStyle(Brand.danger) }
                    } else {
                        Label("Öğrenciliğini doğrulamak için güncel öğrenci belgeni (PDF, e-Devlet) yükle.", systemImage: "doc.badge.plus")
                    }
                }
                Section("Belgen hakkında") {
                    // KVKK: purpose, who sees it, how long it is kept — text from the compliance settings.
                    Text(session.config?.documentUploadNotice
                         ?? "Öğrenci belgen yalnızca öğrenciliğini doğrulamak için alınır ve yalnızca yetkili doğrulama personeli tarafından görülür.")
                        .font(.footnote)
                }
                if !waiting {
                    Section {
                        InlineError(error: runner.error)
                        Button {
                            importing = true
                        } label: {
                            if runner.isRunning { ProgressView().tint(.white) } else { Text("PDF seç ve yükle") }
                        }
                        .buttonStyle(.primary)
                        .disabled(runner.isRunning)
                        .listRowInsets(EdgeInsets())
                        .listRowBackground(Color.clear)
                    }
                }
                Section {
                    Button("Durumu yenile") { Task { await session.reload(); await loadVerification() } }
                    Button("Gizlilik ve KVKK") { showingPrivacy = true }
                    Button("Çıkış yap", role: .destructive) { Task { await session.signOut() } }
                }
            }
            .navigationTitle("Öğrenci doğrulama")
            .sheet(isPresented: $showingPrivacy) { RoutedStack { PrivacyView() } }
            .refreshable { await session.reload(); await loadVerification() }
            .task { await loadVerification() }
            .fileImporter(isPresented: $importing, allowedContentTypes: [.pdf]) { result in
                guard case .success(let url) = result else { return }
                upload(url)
            }
        }
    }

    private func loadVerification() async {
        verification = try? await VerificationService().latest()
    }

    private func upload(_ url: URL) {
        guard let userId = session.userId else { return }
        runner.run {
            let accessing = url.startAccessingSecurityScopedResource()
            defer { if accessing { url.stopAccessingSecurityScopedResource() } }
            let data = try Data(contentsOf: url)
            // The bucket and the Edge Function enforce the same limit; this gives instant feedback.
            guard data.count <= 10 * 1024 * 1024 else { throw AppError.documentTooLarge }
            guard data.starts(with: Array("%PDF".utf8)) else { throw AppError.documentNotPdf }
            try await VerificationService().submit(pdf: data, userId: userId)
            await session.reloadProfile()
        }
    }
}

// MARK: - Legal texts

struct LegalDocumentView: View {
    let docType: String
    @State private var document: LegalDocument?
    @State private var error: AppError?

    var body: some View {
        Group {
            if let error {
                ErrorStateView(error: error) { Task { await load() } }
            } else if let document {
                ScrollView {
                    VStack(alignment: .leading, spacing: Spacing.sm) {
                        Text("Sürüm \(document.version)").font(.caption).foregroundStyle(.secondary)
                        LegalMarkdownView(markdown: document.content)
                    }
                    .padding()
                }
                .navigationTitle(document.title)
            } else {
                ProgressView()
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .task { if document == nil { await load() } }
    }

    private func load() async {
        error = nil
        do { document = try await ComplianceService().legalDocument(type: docType) } catch { self.error = ErrorMapping.appError(error) }
    }
}

/// The Markdown subset the legal texts use: headings, bullet and numbered lists, paragraphs, inline
/// bold/italic/links (same subset as the Android and web renderers).
struct LegalMarkdownView: View {
    let markdown: String

    private enum Block: Hashable {
        case heading(Int, String)
        case bullet(String)
        case paragraph(String)
    }

    private var blocks: [Block] {
        var result: [Block] = []
        var paragraph: [String] = []
        func flush() {
            if !paragraph.isEmpty { result.append(.paragraph(paragraph.joined(separator: " "))); paragraph = [] }
        }
        for rawLine in markdown.components(separatedBy: .newlines) {
            let line = rawLine.trimmingCharacters(in: .whitespaces)
            if line.isEmpty || line == "---" { flush(); continue }
            let hashes = line.prefix(while: { $0 == "#" }).count
            if hashes > 0 && hashes <= 6 && line.dropFirst(hashes).hasPrefix(" ") {
                flush(); result.append(.heading(hashes, String(line.dropFirst(hashes + 1))))
            } else if line.hasPrefix("- ") || line.hasPrefix("* ") {
                flush(); result.append(.bullet(String(line.dropFirst(2))))
            } else if let range = line.range(of: "^\\d+\\. ", options: .regularExpression) {
                flush(); result.append(.bullet(line[range].trimmingCharacters(in: .whitespaces) + " " + line[range.upperBound...]))
            } else if line.hasPrefix(">") {
                flush(); result.append(.paragraph(String(line.dropFirst()).trimmingCharacters(in: .whitespaces)))
            } else {
                paragraph.append(line)
            }
        }
        flush()
        return result
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            ForEach(Array(blocks.enumerated()), id: \.offset) { _, block in
                switch block {
                case .heading(let level, let text):
                    Text(markdown: text).font(level <= 1 ? .title2.bold() : level == 2 ? .headline : .subheadline.bold())
                        .padding(.top, Spacing.sm)
                case .bullet(let text):
                    HStack(alignment: .firstTextBaseline, spacing: Spacing.sm) {
                        Text("•")
                        Text(markdown: text)
                    }
                case .paragraph(let text):
                    Text(markdown: text)
                }
            }
        }
        .textSelection(.enabled)
    }
}
