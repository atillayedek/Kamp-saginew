import SwiftUI

// MARK: - Feed

struct FeedView: View {
    @State private var scope: PostScope = .university
    @State private var category: PostCategory?
    @State private var model: PostListModel?
    @State private var composing = false

    var body: some View {
        Group {
            if let model {
                PostListView(
                    model: model,
                    emptyTitle: category == nil ? "Henüz gönderi yok." : "Bu kategoride henüz gönderi yok.",
                    emptyText: scope == .university ? "Üniversitenden henüz kimse paylaşım yapmadı. İlk gönderiyi sen paylaş." : "İlk gönderiyi sen paylaş."
                ) {
                    filters.listRowSeparator(.hidden)
                }
            } else {
                ProgressView()
            }
        }
        .navigationTitle("Topluluk")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button { composing = true } label: { Image(systemName: "square.and.pencil") }
                    .accessibilityLabel("Gönderi paylaş")
            }
        }
        .sheet(isPresented: $composing) {
            ComposeView(initialScope: scope) { Task { await model?.load() } }
        }
        .task(id: "\(scope.rawValue)-\(category?.rawValue ?? "all")") {
            let scope = scope, category = category
            let fresh = PostListModel { cursor in try await CommunityService().feed(scope: scope, category: category, cursor: cursor) }
            model = fresh
            await fresh.load()
        }
    }

    private var filters: some View {
        VStack(spacing: Spacing.sm) {
            Picker("Akış", selection: $scope) {
                Text("Üniversitem").tag(PostScope.university)
                Text("Genel").tag(PostScope.general)
            }
            .pickerStyle(.segmented)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Spacing.sm) {
                    chip("Tümü", symbol: "square.grid.2x2", selected: category == nil) { category = nil }
                    ForEach(PostCategory.allCases) { item in
                        chip(item.title, symbol: item.symbol, selected: category == item) { category = item }
                    }
                }
            }
        }
    }

    private func chip(_ title: String, symbol: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Label(title, systemImage: symbol)
                .font(.subheadline)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(selected ? Brand.blue : Color(uiColor: .secondarySystemFill), in: Capsule())
                .foregroundStyle(selected ? .white : .primary)
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Post detail

struct PostDetailView: View {
    let postId: String
    @State private var post: Post?
    @State private var comments: [Comment] = []
    @State private var error: AppError?
    @State private var model = PostListModel(all: { [] })
    @State private var commentText = ""
    @State private var runner = ActionRunner()
    @State private var mentions = MentionSuggester()
    @State private var reportingComment: Comment?
    @State private var personalDataWarning: Set<PersonalDataDetector.Kind> = []
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        Group {
            if let error, post == nil {
                ErrorStateView(error: error) { Task { await load() } }
            } else if let post = model.posts.first ?? post {
                List {
                    PostRow(post: post, model: model, isDetail: true)
                    Section("Yorumlar") {
                        if comments.isEmpty {
                            Text("Henüz yorum yok. İlk yorumu sen yaz.").foregroundStyle(.secondary)
                        }
                        ForEach(comments) { comment in
                            CommentRow(comment: comment)
                                .swipeActions {
                                    if comment.isMine {
                                        Button("Sil", role: .destructive) { deleteComment(comment) }
                                    } else {
                                        Button("Şikâyet et") { reportingComment = comment }.tint(.orange)
                                    }
                                }
                        }
                    }
                }
                .listStyle(.plain)
                .refreshable { await load() }
                .safeAreaInset(edge: .bottom) { composer(scope: post.scope) }
            } else {
                ProgressView()
            }
        }
        .navigationTitle("Gönderi")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
        .onReceive(NotificationCenter.default.publisher(for: .postRemoved)) { note in
            if note.object as? String == postId { dismiss() }
        }
        .sheet(item: $reportingComment) { ReportSheet(target: .comment, id: $0.id) }
        .personalDataAlert(kinds: $personalDataWarning) { sendComment(checked: true) }
    }

    private func composer(scope: PostScope) -> some View {
        VStack(spacing: Spacing.xs) {
            MentionSuggestionList(suggestions: mentions.suggestions) { commentText = mentions.complete(commentText, with: $0) }
            InlineError(error: runner.error).padding(.horizontal)
            HStack(alignment: .bottom) {
                TextField("Yorum yaz", text: $commentText, axis: .vertical)
                    .lineLimit(1...4)
                    .textFieldStyle(.roundedBorder)
                    .onChange(of: commentText) { _, text in mentions.textChanged(text, scope: scope) }
                Button {
                    sendComment(checked: false)
                } label: {
                    Image(systemName: "arrow.up.circle.fill").font(.title)
                }
                .disabled(!PostTextValidator.isValidComment(commentText) || runner.isRunning)
                .accessibilityLabel("Yorumu gönder")
            }
            .padding(.horizontal)
            .padding(.vertical, Spacing.sm)
        }
        .background(.bar)
    }

    private func load() async {
        error = nil
        let community = CommunityService()
        do {
            async let loadedPost = community.post(id: postId)
            async let loadedComments = community.comments(postId: postId)
            let (p, c) = try await (loadedPost, loadedComments)
            post = p
            comments = c
            model = PostListModel(all: { [p] })
            await model.load()
        } catch {
            self.error = ErrorMapping.appError(error)
        }
    }

    private func sendComment(checked: Bool) {
        if !checked {
            let found = PersonalDataDetector.find(commentText)
            if !found.isEmpty { personalDataWarning = found; return }
        }
        let text = commentText
        runner.run {
            try await CommunityService().addComment(postId: postId, body: text)
            commentText = ""
            mentions.clear()
            await load()
        }
    }

    private func deleteComment(_ comment: Comment) {
        runner.run {
            try await CommunityService().deleteComment(id: comment.id)
            comments.removeAll { $0.id == comment.id }
        }
    }
}

struct CommentRow: View {
    let comment: Comment

    var body: some View {
        HStack(alignment: .top, spacing: Spacing.sm) {
            NavigationLink(value: AppRoute.user(comment.author.id)) {
                AvatarView(name: comment.author.fullName ?? comment.author.username, size: 32)
            }
            .buttonStyle(.plain)
            VStack(alignment: .leading, spacing: 2) {
                HStack {
                    Text(comment.author.displayName).font(.subheadline.weight(.semibold))
                    Text(Formatting.relative(comment.createdAt)).font(.caption).foregroundStyle(.secondary)
                }
                Text(LinkedText.attributed(comment.body)).font(.subheadline)
            }
        }
    }
}

// MARK: - Compose

struct ComposeView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var scope: PostScope
    @State private var category: PostCategory = .general
    @State private var text = ""
    @State private var runner = ActionRunner()
    @State private var mentions = MentionSuggester()
    @State private var personalDataWarning: Set<PersonalDataDetector.Kind> = []
    let onPosted: () -> Void

    init(initialScope: PostScope, onPosted: @escaping () -> Void) {
        _scope = State(initialValue: initialScope)
        self.onPosted = onPosted
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Picker("Kimler görsün?", selection: $scope) {
                        Text("Üniversitem").tag(PostScope.university)
                        Text("Tüm üniversiteler").tag(PostScope.general)
                    }
                    Picker("Kategori", selection: $category) {
                        ForEach(PostCategory.allCases) { Label($0.title, systemImage: $0.symbol).tag($0) }
                    }
                }
                Section {
                    TextField("Ne paylaşmak istiyorsun? #konu ekleyebilir, @kullanıcıadı ile birini etiketleyebilirsin.",
                              text: $text, axis: .vertical)
                        .lineLimit(6...14)
                        .onChange(of: text) { _, value in
                            if value.count > PostTextValidator.maxPostLength { text = String(value.prefix(PostTextValidator.maxPostLength)) }
                            mentions.textChanged(text, scope: scope)
                        }
                    MentionSuggestionList(suggestions: mentions.suggestions) { text = mentions.complete(text, with: $0) }
                } footer: {
                    HStack {
                        Text("Fotoğraf, anket, etkinlik ve ilan eklemek yakında iOS'ta.")
                        Spacer()
                        Text("\(text.count) / \(PostTextValidator.maxPostLength)").monospacedDigit()
                    }
                }
                Section { InlineError(error: runner.error) }
            }
            .navigationTitle("Gönderi")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Vazgeç") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Paylaş") { post(checked: false) }
                        .disabled(!PostTextValidator.isValidPost(text) || runner.isRunning)
                }
            }
            .personalDataAlert(kinds: $personalDataWarning) { post(checked: true) }
        }
    }

    private func post(checked: Bool) {
        if !checked {
            let found = PersonalDataDetector.find(text)
            if !found.isEmpty { personalDataWarning = found; return }
        }
        runner.run {
            _ = try await CommunityService().createPost(scope: scope, category: category, body: text)
            onPosted()
            dismiss()
        }
    }
}

// MARK: - Mentions while typing

@MainActor
@Observable
final class MentionSuggester {
    private(set) var suggestions: [MentionSuggestion] = []
    @ObservationIgnored private var task: Task<Void, Never>?

    func textChanged(_ text: String, scope: PostScope) {
        task?.cancel()
        guard let typed = BodyLinks.mentionBeingTyped(text), !typed.isEmpty else {
            suggestions = []
            return
        }
        task = Task {
            try? await Task.sleep(for: .milliseconds(250))
            guard !Task.isCancelled else { return }
            do {
                suggestions = try await CommunityService().suggestMentions(query: typed, scope: scope)
            } catch {
                // Suggestions are a convenience; a failed lookup just shows none.
                print("Mention suggestions failed: \(ErrorMapping.appError(error))")
                suggestions = []
            }
        }
    }

    func complete(_ text: String, with suggestion: MentionSuggestion) -> String {
        clear()
        return BodyLinks.completeMention(text, username: suggestion.username)
    }

    func clear() {
        task?.cancel()
        suggestions = []
    }
}

struct MentionSuggestionList: View {
    let suggestions: [MentionSuggestion]
    let pick: (MentionSuggestion) -> Void

    var body: some View {
        if !suggestions.isEmpty {
            VStack(alignment: .leading, spacing: 0) {
                ForEach(suggestions) { person in
                    Button {
                        pick(person)
                    } label: {
                        HStack {
                            AvatarView(name: person.displayName ?? person.username, size: 28)
                            VStack(alignment: .leading) {
                                Text("@" + person.username).font(.subheadline.weight(.medium))
                                Text([person.displayName, person.university].compactMap { $0 }.joined(separator: " · "))
                                    .font(.caption).foregroundStyle(.secondary).lineLimit(1)
                            }
                            Spacer()
                        }
                        .padding(.vertical, 6)
                        .padding(.horizontal, Spacing.sm)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    if person.id != suggestions.last?.id { Divider() }
                }
            }
            .background(Brand.card, in: RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color(uiColor: .separator)))
            .padding(.horizontal, Spacing.sm)
        }
    }
}

// MARK: - Personal data warning

extension View {
    /// "Kişisel verilerini paylaşmak üzeresin" before sending text with an ID number, IBAN or phone number.
    func personalDataAlert(kinds: Binding<Set<PersonalDataDetector.Kind>>, sendAnyway: @escaping () -> Void) -> some View {
        alert("Kişisel verilerini paylaşmak üzeresin", isPresented: Binding(get: { !kinds.wrappedValue.isEmpty }, set: { if !$0 { kinds.wrappedValue = [] } })) {
            Button("Vazgeç", role: .cancel) {}
            Button("Yine de gönder") { sendAnyway() }
        } message: {
            let labels = PersonalDataDetector.Kind.allCases.filter { kinds.wrappedValue.contains($0) }.map(\.label)
            Text("Yazdığın metinde \(labels.joined(separator: ", ")) var gibi görünüyor. Bu bilgiler karşı tarafça kaydedilebilir ve kötüye kullanılabilir. Yine de göndermek istiyor musun?")
        }
    }
}

// MARK: - Tags, mentions, saved posts

struct TagPostsView: View {
    let tag: String
    @State private var model: PostListModel

    init(tag: String) {
        self.tag = tag
        _model = State(initialValue: PostListModel { cursor in try await CommunityService().tagPosts(tag: tag, cursor: cursor) })
    }

    var body: some View {
        PostListView(model: model, emptyTitle: "Bu etikette gönderi yok",
                     emptyText: "Görebildiğin gönderilerde bu etiket henüz kullanılmamış.")
            .navigationTitle("#" + tag)
            .task { if model.posts.isEmpty { await model.load() } }
    }
}

/// "@username" tapped in a post or comment: finds the profile, then hands over to it.
struct MentionView: View {
    let username: String
    let found: (String) -> Void
    @State private var error: AppError?

    var body: some View {
        Group {
            if let error {
                if error == .notFound {
                    ContentUnavailableView("Bu kullanıcı bulunamadı", systemImage: "person.slash", description: Text(error.message))
                } else {
                    ErrorStateView(error: error, title: "Profil açılamadı") { Task { await resolve() } }
                }
            } else {
                ProgressView()
            }
        }
        .navigationTitle("@" + username.trimmingCharacters(in: CharacterSet(charactersIn: ".")))
        .task { await resolve() }
    }

    private func resolve() async {
        error = nil
        do { found(try await CommunityService().resolveUsername(username)) } catch { self.error = ErrorMapping.appError(error) }
    }
}

struct SavedPostsView: View {
    @State private var model = PostListModel(all: { try await CommunityService().savedPosts() })

    var body: some View {
        PostListView(model: model, emptyTitle: "Kaydedilen gönderi yok", emptyText: "Gönderilerdeki yer imi simgesiyle kaydet.")
            .navigationTitle("Kaydedilenler")
            .task { await model.load() }
    }
}
