import SwiftUI

// MARK: - Tabs

struct MainTabView: View {
    let profile: Profile
    @State private var notifications = NotificationsModel()

    var body: some View {
        TabView {
            RoutedStack { FeedView() }
                .tabItem { Label("Topluluk", systemImage: "person.3") }
            RoutedStack { SearchView() }
                .tabItem { Label("Ara", systemImage: "magnifyingglass") }
            RoutedStack { NotificationsView(model: notifications) }
                .tabItem { Label("Bildirimler", systemImage: "bell") }
                .badge(notifications.unreadCount)
            RoutedStack { MyProfileView(profile: profile) }
                .tabItem { Label("Profil", systemImage: "person.crop.circle") }
        }
        .task { await notifications.load() }
    }
}

// MARK: - Search

struct SearchView: View {
    enum Tab: String, CaseIterable { case posts = "Gönderiler", people = "Kişiler" }

    @State private var query = ""
    @State private var tab: Tab = .posts
    @State private var people: [PersonSummary] = []
    @State private var peopleError: AppError?
    @State private var popular: [TagCount] = []
    @State private var model: PostListModel?

    private var trimmed: String { query.trimmingCharacters(in: .whitespaces) }
    private var typedTag: String? { trimmed.hasPrefix("#") ? BodyLinks.tagKey(trimmed) : nil }

    var body: some View {
        Group {
            if trimmed.count < 2 {
                List {
                    if !popular.isEmpty {
                        Section("Bu haftanın popüler etiketleri") {
                            ForEach(popular) { tag in
                                NavigationLink(value: AppRoute.tag(tag.tag)) {
                                    LabeledContent("#" + tag.tag, value: "\(tag.postCount)")
                                }
                            }
                        }
                    }
                    Section {
                        Text("Aramak için en az 2 harf yaz. Türkçe karakterler fark etmez.").foregroundStyle(.secondary)
                    }
                }
            } else {
                VStack(spacing: 0) {
                    if let typedTag {
                        NavigationLink(value: AppRoute.tag(typedTag)) {
                            Label("#\(typedTag) etiketine git", systemImage: "number").frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .padding()
                    }
                    Picker("Sonuçlar", selection: $tab) {
                        ForEach(Tab.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                    }
                    .pickerStyle(.segmented)
                    .padding(.horizontal)
                    if tab == .posts, let model {
                        PostListView(model: model, emptyTitle: "Sonuç yok", emptyText: "Başka kelimelerle dene.")
                    } else if tab == .people {
                        peopleList
                    }
                    Spacer(minLength: 0)
                }
            }
        }
        .navigationTitle("Ara")
        .searchable(text: $query, prompt: "Gönderi, kişi ya da #etiket")
        .task(id: trimmed) { await search() }
        .task { popular = (try? await CommunityService().popularTags()) ?? [] }
    }

    private var peopleList: some View {
        List {
            if let peopleError { InlineError(error: peopleError) }
            if people.isEmpty && peopleError == nil {
                ContentUnavailableView.search(text: trimmed)
            }
            ForEach(people) { person in
                NavigationLink(value: AppRoute.user(person.id)) {
                    HStack {
                        AvatarView(name: person.fullName ?? person.username, size: 36)
                        VStack(alignment: .leading) {
                            Text(person.fullName ?? "@" + (person.username ?? "")).font(.subheadline.weight(.semibold))
                            Text([person.username.map { "@" + $0 }, person.university].compactMap { $0 }.joined(separator: " · "))
                                .font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
            }
        }
        .listStyle(.plain)
    }

    private func search() async {
        let text = trimmed
        guard text.count >= 2 else { return }
        try? await Task.sleep(for: .milliseconds(350))
        guard !Task.isCancelled else { return }
        let fresh = PostListModel(all: { try await CommunityService().searchPosts(query: text) })
        model = fresh
        async let posts: Void = fresh.load()
        do {
            people = try await CommunityService().searchPeople(query: text)
            peopleError = nil
        } catch {
            peopleError = ErrorMapping.appError(error)
        }
        await posts
    }
}

// MARK: - Notifications

@MainActor
@Observable
final class NotificationsModel {
    private(set) var items: [AppNotification] = []
    private(set) var error: AppError?
    private(set) var loaded = false

    var unreadCount: Int { items.filter { !$0.read }.count }

    func load() async {
        do {
            items = try await NotificationService().list()
            error = nil
        } catch {
            self.error = ErrorMapping.appError(error)
        }
        loaded = true
    }

    func markAllRead() async {
        guard unreadCount > 0 else { return }
        do {
            try await NotificationService().markRead(ids: nil)
            await load()
        } catch {
            self.error = ErrorMapping.appError(error)
        }
    }
}

struct NotificationsView: View {
    let model: NotificationsModel

    var body: some View {
        Group {
            if let error = model.error, model.items.isEmpty {
                ErrorStateView(error: error) { Task { await model.load() } }
            } else if !model.loaded {
                ProgressView()
            } else if model.items.isEmpty {
                ContentUnavailableView("Bildirim yok", systemImage: "bell.slash", description: Text("Yeni yorum, etiket ve kararlar burada görünür."))
            } else {
                List(model.items) { item in
                    row(item)
                }
                .listStyle(.plain)
            }
        }
        .navigationTitle("Bildirimler")
        .toolbar {
            Button("Tümünü okundu say") { Task { await model.markAllRead() } }
                .disabled(model.unreadCount == 0)
        }
        .refreshable { await model.load() }
    }

    @ViewBuilder
    private func row(_ item: AppNotification) -> some View {
        let content = HStack(alignment: .top, spacing: Spacing.sm) {
            Image(systemName: item.symbol)
                .foregroundStyle(item.read ? Color.secondary : Brand.blue)
                .frame(width: 28)
            VStack(alignment: .leading, spacing: 2) {
                Text(item.text).fontWeight(item.read ? .regular : .semibold)
                Text(Formatting.relative(item.createdAt)).font(.caption).foregroundStyle(.secondary)
            }
        }
        if let postId = item.postId {
            NavigationLink(value: AppRoute.post(postId)) { content }
        } else if [.contentRemoved, .accountSuspended, .appealDecided, .dsrAnswered].contains(item.kind) {
            NavigationLink(value: AppRoute.privacy) { content }
        } else {
            content
        }
    }
}
