import Foundation
import Supabase

// Thin wrappers over the backend. Every call throws AppError (see ErrorMapping); the same database
// functions, parameters and error codes as the Android app.

enum AuthRedirect: Equatable {
    case notAnAuthLink
    case signedIn
    case passwordRecovery
    case linkExpired
    case failed(AppError)
}

struct AuthService {
    private let backend = Backend.shared

    func signIn(email: String, password: String) async throws {
        try await mapped { client in
            _ = try await client.auth.signIn(email: normalized(email), password: password)
        }
    }

    /// The form's answers travel in the user metadata: the database checks the age and records every
    /// acceptance, notice and optional consent against the exact text versions, then drops the birth date.
    func signUp(email: String, password: String, consents: SignUpConsents) async throws -> SignUpResult {
        try await mapped { client in
            var optional: [String: AnyJSON] = [:]
            for (type, choice) in consents.consents {
                optional[type] = .object(["version": .integer(choice.version), "granted": .bool(choice.granted)])
            }
            let kvkk: [String: AnyJSON] = [
                "birth_date": .string(DateParser.dayString(consents.birthDate)),
                "accepted": .object(consents.accepted.mapValues { .integer($0) }),
                "informed": .object(consents.informed.mapValues { .integer($0) }),
                "consents": .object(optional),
                "app_version": .string(AppConfig.appVersion),
                "platform": .string(AppConfig.platform),
                "user_agent": .string(AppConfig.userAgent),
            ]
            let response = try await client.auth.signUp(
                email: normalized(email),
                password: password,
                data: ["kvkk": .object(kvkk)],
                redirectTo: AppConfig.authRedirectURL
            )
            return response.session != nil ? .signedIn : .verificationRequired
        }
    }

    func resendVerification(email: String) async throws {
        try await mapped { client in try await client.auth.resend(email: normalized(email), type: .signup) }
    }

    func sendPasswordReset(email: String) async throws {
        try await mapped { client in
            try await client.auth.resetPasswordForEmail(normalized(email), redirectTo: AppConfig.passwordRecoveryRedirectURL)
        }
    }

    func updatePassword(_ password: String) async throws {
        try await mapped { client in _ = try await client.auth.update(user: UserAttributes(password: password)) }
    }

    /// Signing out on this device always succeeds; when the server cannot be told (offline), the
    /// refresh token expires on its own.
    func signOut() async {
        guard let client = backend.client else { return }
        do {
            try await client.auth.signOut()
        } catch {
            print("Remote sign-out failed, clearing the local session: \(ErrorMapping.appError(error))")
            try? await client.auth.signOut(scope: .local)
        }
    }

    func handle(url: URL) async -> AuthRedirect {
        guard let client = backend.client, url.scheme == "kampusagi", url.host == "auth-callback" else { return .notAnAuthLink }
        let params = Self.parameters(of: url)
        if params["error"] != nil || params["error_code"] != nil {
            let description = ((params["error_code"] ?? "") + " " + (params["error_description"] ?? "")).lowercased()
            return description.contains("expired") || description.contains("invalid") ? .linkExpired : .failed(.unknown)
        }
        guard params["code"] != nil || params["access_token"] != nil else { return .notAnAuthLink }
        do {
            _ = try await client.auth.session(from: url)
            return params["type"] == "recovery" ? .passwordRecovery : .signedIn
        } catch {
            let mapped = ErrorMapping.appError(error)
            return mapped == .network ? .failed(.network) : .linkExpired
        }
    }

    private func normalized(_ email: String) -> String {
        email.trimmingCharacters(in: .whitespaces).lowercased()
    }

    /// Query and fragment parameters (implicit-flow errors arrive in the fragment).
    static func parameters(of url: URL) -> [String: String] {
        var result: [String: String] = [:]
        let components = URLComponents(url: url, resolvingAgainstBaseURL: false)
        components?.queryItems?.forEach { result[$0.name] = $0.value }
        if let fragment = components?.fragment {
            var fragmentComponents = URLComponents()
            fragmentComponents.query = fragment
            fragmentComponents.queryItems?.forEach { result[$0.name] = $0.value }
        }
        return result
    }

    private func mapped<T>(_ body: (SupabaseClient) async throws -> T) async throws -> T {
        let client = try backend.requireClient()
        do {
            return try await body(client)
        } catch {
            throw ErrorMapping.appError(error)
        }
    }
}

struct ProfileService {
    private let backend = Backend.shared

    func load(userId: String) async throws -> Profile {
        do {
            let client = try backend.requireClient()
            // The client sends requests without a token when it has no usable session; fail as
            // "session expired" instead of reading the profile as an anonymous visitor.
            _ = try await client.auth.session
            let response = try await client.from("profiles").select(ProfileDTO.columns).eq("id", value: userId).single().execute()
            let dto = try JSONCoding.decoder.decode(ProfileDTO.self, from: response.data)
            guard let status = AccountStatus(rawValue: dto.accountStatus) else { throw AppError.server }
            return Profile(
                id: dto.id, email: dto.email, fullName: dto.fullName, username: dto.username,
                universityId: dto.universityId, universityName: dto.universities?.name, department: dto.department,
                status: status, bio: dto.bio, showFullName: dto.showFullName ?? false
            )
        } catch {
            throw ErrorMapping.appError(error)
        }
    }

    func completeProfile(fullName: String, username: String, universityId: String, department: String) async throws {
        do {
            try await backend.rpcVoid("complete_profile", [
                "p_full_name": .string(fullName.trimmingCharacters(in: .whitespaces)),
                "p_username": .string(ProfileInputValidator.normalizeUsername(username)),
                "p_university_id": .string(universityId),
                "p_department": .string(department.trimmingCharacters(in: .whitespaces)),
            ])
        } catch {
            throw ErrorMapping.appError(error)
        }
    }

    func universities() async throws -> [University] {
        do {
            let client = try backend.requireClient()
            let response = try await client.from("universities").select("id,name,city").eq("is_active", value: true)
                .order("name", ascending: true).execute()
            return try JSONCoding.decoder.decode([University].self, from: response.data)
        } catch {
            throw ErrorMapping.appError(error)
        }
    }
}

struct VerificationService {
    private let backend = Backend.shared

    func latest() async throws -> Verification? {
        do {
            let client = try backend.requireClient()
            let response = try await client.from("student_verifications").select("status,rejection_reason")
                .order("created_at", ascending: false).limit(1).execute()
            let rows = try JSONCoding.decoder.decode([VerificationDTO].self, from: response.data)
            return rows.first.flatMap { dto in
                VerificationStatus(rawValue: dto.status).map { Verification(status: $0, rejectionReason: dto.rejectionReason) }
            }
        } catch {
            throw ErrorMapping.appError(error)
        }
    }

    /// Uploads the PDF under a fresh name (uploads are never overwritten; they are review evidence),
    /// then asks the server to check it and open the review.
    func submit(pdf: Data, userId: String) async throws {
        do {
            let client = try backend.requireClient()
            let path = "\(userId)/\(UUID().uuidString.lowercased()).pdf"
            _ = try await client.storage.from(AppConfig.studentDocumentsBucket)
                .upload(path, data: pdf, options: FileOptions(contentType: "application/pdf", upsert: false))
            struct Ok: Decodable {}
            _ = try await backend.invoke(AppConfig.submitStudentDocumentFunction, body: ["path": .string(path)], as: Ok.self)
        } catch {
            throw ErrorMapping.appError(error)
        }
    }
}

struct ComplianceService {
    private let backend = Backend.shared

    func config() async throws -> ComplianceConfig {
        do {
            let values = try await backend.rpc("public_compliance_config", as: [String: AnyJSON].self)
            func int(_ key: String) throws -> Int {
                switch values[key] {
                case .integer(let v): return v
                case .double(let v): return Int(v)
                case .string(let v): if let n = Int(v) { return n }
                default: break
                }
                throw AppError.server
            }
            func text(_ key: String) throws -> String {
                if case .string(let v) = values[key] { return v }
                throw AppError.server
            }
            // {key} placeholders are filled from the settings themselves, e.g. the retention days.
            var upload = try text("document_upload_notice")
            for (key, value) in values {
                switch value {
                case .integer(let v): upload = upload.replacingOccurrences(of: "{\(key)}", with: String(v))
                case .string(let v): upload = upload.replacingOccurrences(of: "{\(key)}", with: v)
                default: break
                }
            }
            return ComplianceConfig(
                minAge: try int("min_age"),
                deletionGraceDays: try int("deletion_grace_days"),
                registerLogNotice: try text("register_log_notice"),
                loginLogNotice: try text("login_log_notice"),
                documentUploadNotice: upload
            )
        } catch {
            throw ErrorMapping.appError(error)
        }
    }

    func legalDocuments() async throws -> [LegalDocumentInfo] {
        try await call {
            try await backend.rpc("list_legal_documents", as: [LegalDocumentInfoDTO].self).compactMap(Self.info)
        }
    }

    func legalDocument(type: String, version: Int? = nil) async throws -> LegalDocument {
        try await call {
            let rows = try await backend.rpc("get_legal_document", ["p_doc_type": .string(type), "p_version": .optional(version)],
                                             as: [LegalDocumentDTO].self)
            guard let dto = rows.first else { throw AppError.notFound }
            return LegalDocument(docType: dto.docType, version: dto.version, title: dto.title, content: dto.content, publishedAt: dto.publishedAt)
        }
    }

    func accountGate() async throws -> AccountGate {
        try await call {
            let pending = try await backend.rpc("pending_legal_documents", as: [LegalDocumentInfoDTO].self).compactMap(Self.info)
            let deletion = try await backend.rpc("my_account_deletion", as: String?.self)
            return AccountGate(pending: pending, deletionScheduledFor: DateParser.parse(deletion))
        }
    }

    func acknowledge(_ document: LegalDocumentInfo, channel: String) async throws {
        try await call {
            try await backend.rpcVoid("acknowledge_legal_document", [
                "p_doc_type": .string(document.docType),
                "p_version": .integer(document.version),
                "p_channel": .string(channel),
                "p_app_version": .string(AppConfig.appVersion),
                "p_platform": .string(AppConfig.platform),
            ])
        }
    }

    func consents() async throws -> [ConsentStatus] {
        try await call {
            try await backend.rpc("my_consents", as: [ConsentStatusDTO].self).map {
                ConsentStatus(docType: $0.docType, title: $0.title, version: $0.activeVersion, granted: $0.granted)
            }
        }
    }

    func setConsent(docType: String, granted: Bool) async throws {
        try await call {
            try await backend.rpcVoid("set_consent", [
                "p_doc_type": .string(docType),
                "p_granted": .bool(granted),
                "p_channel": .string("settings"),
                "p_app_version": .string(AppConfig.appVersion),
                "p_platform": .string(AppConfig.platform),
            ])
        }
    }

    /// 5651: sign-in and sign-out events with device details (no personal data beyond the account).
    func logAccess(event: String) async {
        do {
            try await backend.rpcVoid("log_access_event", [
                "p_event": .string(event),
                "p_device_info": .string(AppConfig.deviceInfo),
                "p_app_version": .string(AppConfig.appVersion),
                "p_platform": .string(AppConfig.platform),
            ])
        } catch {
            print("Access event could not be logged: \(ErrorMapping.appError(error))")
        }
    }

    /// The database keeps only a SHA-256 of the address.
    func logFailedSignIn(email: String) async {
        do {
            try await backend.rpcVoid("log_failed_login", [
                "p_email": .string(email.trimmingCharacters(in: .whitespaces).lowercased()),
                "p_device_info": .string(AppConfig.deviceInfo),
                "p_app_version": .string(AppConfig.appVersion),
                "p_platform": .string(AppConfig.platform),
            ])
        } catch {
            print("Failed sign-in could not be logged: \(ErrorMapping.appError(error))")
        }
    }

    func cancelDeletion() async throws {
        try await call { try await backend.rpcVoid("cancel_account_deletion") }
    }

    func setShowFullName(_ show: Bool) async throws {
        try await call { try await backend.rpcVoid("set_show_full_name", ["p_show": .bool(show)]) }
    }

    func exportMyData() async throws -> DataExport {
        try await call {
            let dto = try await backend.invoke(AppConfig.exportMyDataFunction, body: [:], as: DataExportDTO.self)
            guard let html = URL(string: dto.htmlUrl), let json = URL(string: dto.jsonUrl),
                  let expires = DateParser.parse(dto.expiresAt) else { throw AppError.server }
            return DataExport(htmlURL: html, jsonURL: json, expiresAt: expires)
        }
    }

    func submitRequest(type: DataRequestType, details: String) async throws -> String {
        try await call {
            try await backend.rpc("submit_data_subject_request",
                                  ["p_type": .string(type.rawValue), "p_details": .string(details.trimmingCharacters(in: .whitespacesAndNewlines))],
                                  as: String.self)
        }
    }

    func myRequests() async throws -> [DataSubjectRequest] {
        try await call {
            try await backend.rpc("my_data_subject_requests", as: [DataSubjectRequestDTO].self).map {
                DataSubjectRequest(requestNo: $0.requestNo, type: DataRequestType(rawValue: $0.type.lowercased()), status: $0.status,
                                   receivedAt: DateParser.parse($0.receivedAt), dueAt: DateParser.parse($0.dueAt), response: $0.responseSummary)
            }
        }
    }

    private static func info(_ dto: LegalDocumentInfoDTO) -> LegalDocumentInfo? {
        LegalKind(rawValue: dto.kind).map { LegalDocumentInfo(docType: dto.docType, kind: $0, version: dto.version, title: dto.title) }
    }

    private func call<T>(_ body: () async throws -> T) async throws -> T {
        do { return try await body() } catch { throw ErrorMapping.appError(error) }
    }
}

struct AccountService {
    private let backend = Backend.shared

    /// Schedules the deletion; returns when it becomes permanent (the grace period can be cancelled).
    func requestDeletion() async throws -> Date {
        do {
            let dto = try await backend.invoke(AppConfig.deleteAccountFunction,
                                               body: ["confirm": .string(AppConfig.deleteAccountConfirmation)],
                                               as: DeletionScheduleDTO.self)
            guard let date = DateParser.parse(dto.scheduledFor) else { throw AppError.server }
            return date
        } catch {
            throw ErrorMapping.appError(error)
        }
    }
}

struct ModerationService {
    private let backend = Backend.shared

    func report(target: ReportTarget, id: String, reason: ReportReason, details: String?) async throws {
        let trimmed = details?.trimmingCharacters(in: .whitespacesAndNewlines)
        do {
            try await backend.rpcVoid("report_content", [
                "p_target_kind": .string(target.rawValue),
                "p_target_id": .string(id),
                "p_reason": .string(reason.rawValue),
                "p_details": .optional(trimmed?.isEmpty == false ? trimmed : nil),
            ])
        } catch {
            throw ErrorMapping.appError(error)
        }
    }

    func block(userId: String) async throws {
        do { try await backend.rpcVoid("block_user", ["p_user_id": .string(userId)]) } catch { throw ErrorMapping.appError(error) }
    }
}

struct NotificationService {
    private let backend = Backend.shared

    func list() async throws -> [AppNotification] {
        do {
            return try await backend.rpc("list_notifications", as: [NotificationDTO].self).compactMap { dto in
                // A kind added on the server after this app version is skipped, not an error for the whole list.
                guard let kind = NotificationKind(rawValue: dto.kind) else { return nil }
                return AppNotification(
                    id: dto.id, kind: kind, createdAt: DateParser.parse(dto.createdAt) ?? Date(), read: dto.readAt != nil,
                    actorName: dto.actorFullName ?? dto.actorUsername.map { "@" + $0 },
                    conversationId: dto.conversationId, postId: dto.postId
                )
            }
        } catch {
            throw ErrorMapping.appError(error)
        }
    }

    /// nil marks every notification read.
    func markRead(ids: [String]?) async throws {
        do {
            try await backend.rpcVoid("mark_notifications_read", ["p_ids": ids.map { .array($0.map { .string($0) }) } ?? .null])
        } catch {
            throw ErrorMapping.appError(error)
        }
    }
}

struct CommunityService {
    static let pageSize = 20
    private let backend = Backend.shared

    func feed(scope: PostScope, category: PostCategory?, cursor: FeedCursor?) async throws -> FeedPage {
        try await page("list_posts", [
            "p_scope": .string(scope.rawValue),
            "p_category": .optional(category?.rawValue),
        ], cursor: cursor)
    }

    func post(id: String) async throws -> Post {
        try await call {
            guard let post = try await backend.rpc("get_post", ["p_post_id": .string(id)], as: [PostDTO].self).first else {
                throw AppError.notFound
            }
            return post.toDomain()
        }
    }

    func comments(postId: String) async throws -> [Comment] {
        try await call {
            try await backend.rpc("list_comments", ["p_post_id": .string(postId)], as: [CommentDTO].self).map {
                Comment(id: $0.id, body: $0.body, createdAt: DateParser.parse($0.createdAt) ?? Date(), isMine: $0.isMine,
                        author: Author(id: $0.authorId, fullName: $0.authorFullName, username: $0.authorUsername, university: nil))
            }
        }
    }

    /// Text posts; photos, polls, events and listings are written from the iOS-2 composer on.
    func createPost(scope: PostScope, category: PostCategory, body: String) async throws -> String {
        try await call {
            try await backend.rpc("create_post", [
                "p_scope": .string(scope.rawValue),
                "p_body": .string(body.trimmingCharacters(in: .whitespacesAndNewlines)),
                "p_category": .string(category.rawValue),
            ], as: String.self)
        }
    }

    func deletePost(id: String) async throws {
        try await call { try await backend.rpcVoid("delete_post", ["p_post_id": .string(id)]) }
    }

    func addComment(postId: String, body: String) async throws {
        try await call {
            try await backend.rpcVoid("add_comment", ["p_post_id": .string(postId), "p_body": .string(body.trimmingCharacters(in: .whitespacesAndNewlines))])
        }
    }

    func deleteComment(id: String) async throws {
        try await call { try await backend.rpcVoid("delete_comment", ["p_comment_id": .string(id)]) }
    }

    /// Returns the new like count.
    func setLiked(postId: String, liked: Bool) async throws -> Int {
        try await call { try await backend.rpc("set_post_like", ["p_post_id": .string(postId), "p_liked": .bool(liked)], as: Int.self) }
    }

    func setSaved(postId: String, saved: Bool) async throws {
        try await call { try await backend.rpcVoid("set_post_saved", ["p_post_id": .string(postId), "p_saved": .bool(saved)]) }
    }

    /// nil withdraws the vote.
    func vote(pollId: String, optionId: String?) async throws {
        try await call { try await backend.rpcVoid("vote_poll", ["p_poll_id": .string(pollId), "p_option_id": .optional(optionId)]) }
    }

    /// Returns the new attendee count.
    func setAttending(postId: String, attending: Bool) async throws -> Int {
        try await call {
            try await backend.rpc("set_event_attendance", ["p_post_id": .string(postId), "p_attending": .bool(attending)], as: Int.self)
        }
    }

    func setSold(postId: String, sold: Bool) async throws {
        try await call { try await backend.rpcVoid("set_listing_sold", ["p_post_id": .string(postId), "p_sold": .bool(sold)]) }
    }

    func savedPosts() async throws -> [Post] {
        try await call { try await backend.rpc("list_saved_posts", as: [PostDTO].self).map { $0.toDomain() } }
    }

    func searchPosts(query: String) async throws -> [Post] {
        try await call { try await backend.rpc("search_posts", ["p_query": .string(query)], as: [PostDTO].self).map { $0.toDomain() } }
    }

    func searchPeople(query: String) async throws -> [PersonSummary] {
        try await call {
            try await backend.rpc("search_people", ["p_query": .string(query)], as: [PersonDTO].self).map {
                PersonSummary(id: $0.userId, fullName: $0.fullName, username: $0.username, university: $0.university, department: $0.department)
            }
        }
    }

    func userProfile(id: String) async throws -> UserProfile {
        try await call {
            guard let dto = try await backend.rpc("get_user_profile", ["p_user_id": .string(id)], as: [UserProfileDTO].self).first else {
                throw AppError.notFound
            }
            return UserProfile(id: dto.userId, fullName: dto.fullName, username: dto.username, university: dto.university,
                               department: dto.department, bio: dto.bio, postCount: dto.postCount, isMe: dto.isMe)
        }
    }

    func userPosts(userId: String, cursor: FeedCursor?) async throws -> FeedPage {
        try await page("list_user_posts", ["p_user_id": .string(userId)], cursor: cursor)
    }

    func tagPosts(tag: String, cursor: FeedCursor?) async throws -> FeedPage {
        try await page("list_tag_posts", ["p_tag": .string(tag)], cursor: cursor)
    }

    func popularTags() async throws -> [TagCount] {
        try await call {
            try await backend.rpc("popular_tags", ["p_limit": .integer(12)], as: [TagCountDTO].self).map { TagCount(tag: $0.tag, postCount: $0.postCount) }
        }
    }

    func suggestMentions(query: String, scope: PostScope) async throws -> [MentionSuggestion] {
        try await call {
            try await backend.rpc("suggest_mentions", ["p_query": .string(query), "p_scope": .string(scope.rawValue)],
                                  as: [MentionSuggestionDTO].self).map {
                MentionSuggestion(id: $0.userId, username: $0.username, displayName: $0.displayName, university: $0.university)
            }
        }
    }

    func resolveUsername(_ username: String) async throws -> String {
        try await call { try await backend.rpc("resolve_username", ["p_username": .string(username)], as: String.self) }
    }

    /// Post photos live in a private bucket; they are downloaded with the signed-in session.
    func photo(path: String) async throws -> Data {
        try await call {
            try await backend.requireClient().storage.from(AppConfig.postMediaBucket).download(path: path)
        }
    }

    /// A full page means there may be more; the last post is the next cursor.
    private func page(_ function: String, _ params: [String: AnyJSON], cursor: FeedCursor?) async throws -> FeedPage {
        try await call {
            var all = params
            all["p_before_created_at"] = .optional(cursor?.createdAt)
            all["p_before_id"] = .optional(cursor?.id)
            all["p_limit"] = .integer(Self.pageSize)
            let rows = try await backend.rpc(function, all, as: [PostDTO].self)
            let next = rows.count == Self.pageSize ? rows.last.map { FeedCursor(createdAt: $0.createdAt, id: $0.id) } : nil
            return FeedPage(posts: rows.map { $0.toDomain() }, next: next)
        }
    }

    private func call<T>(_ body: () async throws -> T) async throws -> T {
        do { return try await body() } catch { throw ErrorMapping.appError(error) }
    }
}
