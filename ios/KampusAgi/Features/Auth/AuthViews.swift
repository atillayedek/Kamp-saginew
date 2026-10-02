import SwiftUI

struct AuthFlowView: View {
    var body: some View {
        NavigationStack {
            SignInView()
        }
    }
}

// MARK: - Sign in

struct SignInView: View {
    @Environment(SessionStore.self) private var session
    @State private var email = ""
    @State private var password = ""
    @State private var inputErrors: Set<InputError> = []
    @State private var runner = ActionRunner()
    @State private var showVerifyNotice = false

    var body: some View {
        Form {
            Section {
                VStack(alignment: .leading, spacing: Spacing.sm) {
                    Image(systemName: "graduationcap.fill")
                        .font(.system(size: 34))
                        .foregroundStyle(.white)
                        .frame(width: 64, height: 64)
                        .background(Brand.blue, in: RoundedRectangle(cornerRadius: 16))
                    Text("KampüsAğı'na hoş geldin").font(.title2.bold())
                    Text("Doğrulanmış üniversite öğrencileriyle bağlantı kur.").foregroundStyle(.secondary)
                }
                .padding(.vertical, Spacing.sm)
                .listRowBackground(Color.clear)
            }
            Section {
                TextField("E-posta", text: $email)
                    .textContentType(.username)
                    .keyboardType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                SecureField("Şifre", text: $password)
                    .textContentType(.password)
            } footer: {
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    ForEach(Array(inputErrors).sorted { $0.message < $1.message }, id: \.self) { error in
                        Text(error.message).foregroundStyle(Brand.danger)
                    }
                    InlineError(error: runner.error ?? session.linkError)
                }
            }
            Section {
                Button {
                    signIn()
                } label: {
                    if runner.isRunning { ProgressView().tint(.white) } else { Text("Giriş yap") }
                }
                .buttonStyle(.primary)
                .disabled(runner.isRunning)
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.clear)
            } footer: {
                // 5651: the person is told before signing in that access is logged (text from the settings).
                Text(session.config?.loginLogNotice ?? "Giriş yaparak güvenlik amacıyla işlem kayıtlarının (log) tutulduğunu kabul edersin.")
                    + Text(" ")
                    + Text(markdown: "[Aydınlatma Metni](\(LinkedText.scheme)://legal/\(LegalDocTypes.privacyNotice))")
            }
            Section {
                NavigationLink("Şifremi unuttum") { ForgotPasswordView(email: email) }
                NavigationLink("Hesabın yok mu? Kayıt ol") { SignUpView() }
            }
            if runner.error == .emailNotConfirmed {
                Section {
                    Button("Doğrulama e-postasını tekrar gönder") {
                        runner.run { try await AuthService().resendVerification(email: email); showVerifyNotice = true }
                    }
                }
            }
        }
        .navigationTitle("Giriş")
        .navigationBarTitleDisplayMode(.inline)
        .legalLinkSheet()
        .alert("E-posta gönderildi", isPresented: $showVerifyNotice) {
            Button("Tamam", role: .cancel) {}
        } message: {
            Text("Gelen kutundaki bağlantıya dokunarak e-postanı doğrula.")
        }
        .task { await session.loadConfig() }
    }

    private func signIn() {
        inputErrors = AuthInputValidator.validateSignIn(email: email, password: password)
        session.linkError = nil
        guard inputErrors.isEmpty else { return }
        runner.run {
            do {
                try await AuthService().signIn(email: email, password: password)
                await session.signedIn()
            } catch let error as AppError {
                if error == .invalidCredentials { await ComplianceService().logFailedSignIn(email: email) }
                throw error
            }
        }
    }
}

// MARK: - Sign up

struct SignUpView: View {
    @Environment(SessionStore.self) private var session
    @State private var email = ""
    @State private var password = ""
    @State private var confirm = ""
    @State private var birthDate: Date?
    @State private var noticeRead = false
    @State private var termsAccepted = false
    @State private var consentChoices: [String: Bool] = [:]
    @State private var documents: [LegalDocumentInfo]?
    @State private var loadError: AppError?
    @State private var inputErrors: Set<InputError> = []
    @State private var runner = ActionRunner()
    @State private var verificationSent = false

    private var minAge: Int { session.config?.minAge ?? 18 }
    private var agreements: [LegalDocumentInfo] { (documents ?? []).filter { $0.kind == .agreement } }
    private var optionalConsents: [LegalDocumentInfo] { (documents ?? []).filter { $0.kind == .consent } }

    var body: some View {
        Form {
            Section {
                Text("Kayıttan sonra e-posta adresini doğrulayacak, ardından öğrenci belgeni yükleyeceksin.")
                    .foregroundStyle(.secondary)
            }
            Section("Hesap") {
                TextField("E-posta", text: $email)
                    .textContentType(.username)
                    .keyboardType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                SecureField("Şifre (en az \(AuthInputValidator.minPasswordLength) karakter)", text: $password)
                    .textContentType(.newPassword)
                SecureField("Şifre (tekrar)", text: $confirm)
                    .textContentType(.newPassword)
            }
            Section {
                DatePicker(
                    "Doğum tarihi",
                    selection: Binding(get: { birthDate ?? Self.defaultBirthDate }, set: { birthDate = $0 }),
                    in: ...Date(),
                    displayedComponents: .date
                )
                .environment(\.locale, Locale(identifier: "tr_TR"))
            } footer: {
                Text("\(minAge) yaş kontrolü için sorulur, saklanmaz.")
            }
            legalSection
            Section {
                InlineError(error: runner.error)
                ForEach(Array(inputErrors).sorted { $0.message < $1.message }, id: \.self) { error in
                    Text(error.message).font(.footnote).foregroundStyle(Brand.danger)
                }
                Button {
                    signUp()
                } label: {
                    if runner.isRunning { ProgressView().tint(.white) } else { Text("Kayıt ol") }
                }
                .buttonStyle(.primary)
                .disabled(runner.isRunning || documents == nil || !termsAccepted || !noticeRead)
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.clear)
            } footer: {
                if let notice = session.config?.registerLogNotice { Text(notice) }
            }
        }
        .navigationTitle("Hesap oluştur")
        .legalLinkSheet()
        .task { await loadDocuments() }
        .alert("E-postanı doğrula", isPresented: $verificationSent) {
            Button("Tamam", role: .cancel) {}
        } message: {
            Text("\(email) adresine bir doğrulama bağlantısı gönderdik. Bağlantıya dokunduktan sonra giriş yapabilirsin.")
        }
    }

    @ViewBuilder
    private var legalSection: some View {
        if let loadError {
            Section { ErrorStateView(error: loadError, title: "Metinler yüklenemedi") { Task { await loadDocuments() } } }
        } else if documents == nil {
            Section { ProgressView() }
        } else {
            Section {
                Toggle(isOn: $noticeRead) {
                    Text(markdown: "[Aydınlatma Metni](\(LinkedText.scheme)://legal/\(LegalDocTypes.privacyNotice))'ni okudum. (Bu bir onay değil, bilgilendirildiğinin kaydıdır.)")
                }
                // Unticked by default, required.
                Toggle(isOn: $termsAccepted) {
                    Text(markdown: "\(minAge) yaşını doldurduğumu beyan eder, [Kullanım Koşulları](\(LinkedText.scheme)://legal/\(LegalDocTypes.terms))'nı ve [Topluluk Kuralları](\(LinkedText.scheme)://legal/\(LegalDocTypes.communityRules))'nı kabul ederim.")
                }
            } header: {
                Text("Bilgilendirme ve koşullar")
            }
            if !optionalConsents.isEmpty {
                Section {
                    ForEach(optionalConsents) { document in
                        Toggle(isOn: Binding(get: { consentChoices[document.docType] ?? false },
                                             set: { consentChoices[document.docType] = $0 })) {
                            Text(markdown: "[\(document.title)](\(LinkedText.scheme)://legal/\(document.docType))")
                        }
                    }
                } header: {
                    Text("İsteğe bağlı izinler")
                } footer: {
                    Text("Vermesen de uygulamanın tamamını kullanabilirsin; istediğin zaman ayarlardan değiştirebilirsin.")
                }
            }
        }
    }

    private static var defaultBirthDate: Date {
        Calendar.turkey.date(byAdding: .year, value: -20, to: Date()) ?? Date()
    }

    private func loadDocuments() async {
        loadError = nil
        await session.loadConfig()
        do {
            documents = try await ComplianceService().legalDocuments()
        } catch {
            loadError = ErrorMapping.appError(error)
        }
    }

    private func signUp() {
        inputErrors = AuthInputValidator.validateSignUp(
            email: email, password: password, confirm: confirm, birthDate: birthDate,
            minAge: minAge, noticeRead: noticeRead, termsAccepted: termsAccepted
        )
        guard inputErrors.isEmpty, let birthDate, let documents else { return }
        let versions = Dictionary(uniqueKeysWithValues: documents.map { ($0.docType, $0.version) })
        let consents = SignUpConsents(
            birthDate: birthDate,
            accepted: Dictionary(uniqueKeysWithValues: agreements.map { ($0.docType, $0.version) }),
            informed: [LegalDocTypes.privacyNotice, LegalDocTypes.privacyPolicy]
                .reduce(into: [String: Int]()) { result, type in result[type] = versions[type] },
            consents: Dictionary(uniqueKeysWithValues: optionalConsents.map {
                ($0.docType, ConsentChoice(version: $0.version, granted: consentChoices[$0.docType] ?? false))
            })
        )
        runner.run {
            switch try await AuthService().signUp(email: email, password: password, consents: consents) {
            case .signedIn: await session.signedIn()
            case .verificationRequired: verificationSent = true
            }
        }
    }
}

// MARK: - Passwords

struct ForgotPasswordView: View {
    @State var email: String
    @State private var runner = ActionRunner()
    @State private var sent = false

    var body: some View {
        Form {
            Section {
                TextField("E-posta", text: $email)
                    .keyboardType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
            } footer: {
                Text("Şifreni yenilemen için bir bağlantı göndereceğiz. Bağlantıyı bu telefonda aç.")
            }
            Section {
                InlineError(error: runner.error)
                Button("Bağlantı gönder") {
                    runner.run { try await AuthService().sendPasswordReset(email: email); sent = true }
                }
                .disabled(runner.isRunning || !AuthInputValidator.isValidEmail(email))
            }
        }
        .navigationTitle("Şifremi unuttum")
        .alert("Bağlantı gönderildi", isPresented: $sent) {
            Button("Tamam", role: .cancel) {}
        } message: {
            Text("E-postandaki bağlantıya dokununca yeni şifreni belirleyebilirsin.")
        }
    }
}

struct NewPasswordView: View {
    @Environment(SessionStore.self) private var session
    @Environment(\.dismiss) private var dismiss
    @State private var password = ""
    @State private var confirm = ""
    @State private var runner = ActionRunner()

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    SecureField("Yeni şifre", text: $password).textContentType(.newPassword)
                    SecureField("Yeni şifre (tekrar)", text: $confirm).textContentType(.newPassword)
                } footer: {
                    if !confirm.isEmpty && password != confirm { Text(InputError.passwordsDoNotMatch.message).foregroundStyle(Brand.danger) }
                }
                Section {
                    InlineError(error: runner.error)
                    Button("Şifreyi kaydet") {
                        runner.run {
                            try await AuthService().updatePassword(password)
                            session.isRecoveringPassword = false
                            dismiss()
                        }
                    }
                    .disabled(runner.isRunning || password.count < AuthInputValidator.minPasswordLength || password != confirm)
                }
            }
            .navigationTitle("Yeni şifre")
        }
    }
}

// MARK: - Legal links inside form texts

private struct LegalLinkSheet: ViewModifier {
    @State private var docType: String?

    func body(content: Content) -> some View {
        content
            .environment(\.openURL, OpenURLAction { url in
                if url.scheme == LinkedText.scheme, url.host == "legal", let type = url.pathComponents.dropFirst().first {
                    docType = type
                    return .handled
                }
                return .systemAction
            })
            .sheet(item: Binding(get: { docType.map(IdentifiedString.init) }, set: { docType = $0?.value })) { item in
                NavigationStack {
                    LegalDocumentView(docType: item.value)
                        .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Kapat") { docType = nil } } }
                }
            }
    }
}

struct IdentifiedString: Identifiable {
    let value: String
    var id: String { value }
}

extension View {
    /// "kampusagi-app://legal/<type>" links in Text open the legal text in a sheet.
    func legalLinkSheet() -> some View { modifier(LegalLinkSheet()) }
}
