import SwiftUI

/// Posts one under another with paging, pull-to-refresh and the shared post actions.
struct PostListView<Header: View>: View {
    let model: PostListModel
    var emptyTitle = "Henüz gönderi yok."
    var emptyText = "İlk gönderiyi sen paylaş."
    @ViewBuilder var header: () -> Header

    var body: some View {
        List {
            header()
            switch model.state {
            case .loading:
                ProgressView().frame(maxWidth: .infinity).listRowSeparator(.hidden)
            case .failed(let error):
                ErrorStateView(error: error, title: "Gönderiler yüklenemedi") { Task { await model.load() } }
                    .listRowSeparator(.hidden)
            case .loaded:
                if model.posts.isEmpty {
                    ContentUnavailableView(emptyTitle, systemImage: "text.bubble", description: Text(emptyText))
                        .listRowSeparator(.hidden)
                }
                ForEach(model.posts) { post in
                    PostRow(post: post, model: model)
                        .task { await model.loadMoreIfNeeded(after: post) }
                }
                if model.isLoadingMore {
                    ProgressView().frame(maxWidth: .infinity).listRowSeparator(.hidden)
                }
            }
        }
        .listStyle(.plain)
        .refreshable { await model.load() }
        .alert("İşlem yapılamadı", isPresented: Binding(get: { model.actionError != nil }, set: { if !$0 { model.actionError = nil } })) {
            Button("Tamam", role: .cancel) {}
        } message: {
            Text(model.actionError?.message ?? "")
        }
    }
}

extension PostListView where Header == EmptyView {
    init(model: PostListModel, emptyTitle: String = "Henüz gönderi yok.", emptyText: String = "İlk gönderiyi sen paylaş.") {
        self.init(model: model, emptyTitle: emptyTitle, emptyText: emptyText) { EmptyView() }
    }
}

struct PostRow: View {
    let post: Post
    let model: PostListModel
    var isDetail = false
    @State private var reporting = false
    @State private var confirmingDelete = false
    @State private var confirmingBlock = false

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            header
            Text(LinkedText.attributed(post.body))
                .font(.body)
                .lineLimit(isDetail ? nil : 12)
                .fixedSize(horizontal: false, vertical: true)
            if !post.media.isEmpty { PostPhotosView(paths: post.media) }
            if let poll = post.poll { PollView(poll: poll) { model.vote(post, optionId: $0) } }
            if let event = post.event { EventView(event: event) { model.toggleAttending(post) } }
            if let listing = post.listing {
                ListingView(listing: listing, isMine: post.isMine) { model.toggleSold(post) }
            }
            actions
        }
        .padding(.vertical, Spacing.xs)
        .background {
            if !isDetail { NavigationLink(value: AppRoute.post(post.id)) { EmptyView() }.opacity(0) }
        }
        .sheet(isPresented: $reporting) { ReportSheet(target: .post, id: post.id) }
        .confirmationDialog("Gönderi silinsin mi?", isPresented: $confirmingDelete, titleVisibility: .visible) {
            Button("Sil", role: .destructive) { model.delete(post) }
        }
        .confirmationDialog("\(post.author.displayName) engellensin mi?", isPresented: $confirmingBlock, titleVisibility: .visible) {
            Button("Engelle", role: .destructive) { model.blockAuthor(of: post) }
        } message: {
            Text("Birbirinizin paylaşımlarını görmezsiniz ve mesajlaşamazsınız. Engellediğin kişiye bildirilmez.")
        }
    }

    private var header: some View {
        HStack(alignment: .top, spacing: Spacing.sm) {
            NavigationLink(value: AppRoute.user(post.author.id)) {
                AvatarView(name: post.author.fullName ?? post.author.username)
            }
            .buttonStyle(.plain)
            VStack(alignment: .leading, spacing: 2) {
                Text(post.author.displayName).font(.subheadline.weight(.semibold))
                Text([post.author.university, Formatting.relative(post.createdAt)].compactMap { $0 }.joined(separator: " · "))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            Spacer(minLength: 0)
            if post.category != .general {
                Label(post.category.title, systemImage: post.category.symbol)
                    .font(.caption2.weight(.medium))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(Brand.softBlue, in: Capsule())
                    .foregroundStyle(Brand.blue)
            }
            Menu {
                if post.isMine {
                    Button("Sil", systemImage: "trash", role: .destructive) { confirmingDelete = true }
                } else {
                    Button("Şikâyet et", systemImage: "exclamationmark.bubble") { reporting = true }
                    Button("Engelle", systemImage: "hand.raised", role: .destructive) { confirmingBlock = true }
                }
            } label: {
                Image(systemName: "ellipsis").frame(width: 28, height: 28).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .foregroundStyle(.secondary)
        }
    }

    private var actions: some View {
        HStack(spacing: Spacing.lg) {
            Button {
                model.toggleLike(post)
            } label: {
                Label("\(post.likeCount)", systemImage: post.likedByMe ? "heart.fill" : "heart")
                    .foregroundStyle(post.likedByMe ? Brand.danger : .secondary)
            }
            .accessibilityLabel(post.likedByMe ? "Beğeniyi geri al" : "Beğen")
            Label("\(post.commentCount)", systemImage: "bubble.right").foregroundStyle(.secondary)
            Spacer()
            Button {
                model.toggleSave(post)
            } label: {
                Image(systemName: post.savedByMe ? "bookmark.fill" : "bookmark")
                    .foregroundStyle(post.savedByMe ? Brand.blue : .secondary)
            }
            .accessibilityLabel(post.savedByMe ? "Kaydedilenlerden çıkar" : "Kaydet")
        }
        .buttonStyle(.borderless)
        .font(.subheadline)
        .sensoryFeedback(.selection, trigger: post.likedByMe)
    }
}

// MARK: - Photos

/// Photos live in a private bucket; each is downloaded with the session and kept in memory.
struct PostPhotosView: View {
    let paths: [String]

    var body: some View {
        TabView {
            ForEach(paths, id: \.self) { path in
                PostPhoto(path: path)
            }
        }
        .tabViewStyle(.page(indexDisplayMode: paths.count > 1 ? .automatic : .never))
        .frame(height: 260)
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }
}

private struct PostPhoto: View {
    let path: String
    @State private var image: UIImage?
    @State private var failed = false

    var body: some View {
        ZStack {
            Color(uiColor: .secondarySystemBackground)
            if let image {
                Image(uiImage: image).resizable().scaledToFill()
            } else if failed {
                Image(systemName: "photo.badge.exclamationmark").foregroundStyle(.secondary)
            } else {
                ProgressView()
            }
        }
        .clipped()
        .task(id: path) { await load() }
    }

    private func load() async {
        if let cached = PhotoCache.shared.object(forKey: path as NSString) {
            image = cached
            return
        }
        do {
            let data = try await CommunityService().photo(path: path)
            guard let decoded = UIImage(data: data) else { failed = true; return }
            PhotoCache.shared.setObject(decoded, forKey: path as NSString)
            image = decoded
        } catch {
            failed = true
        }
    }
}

enum PhotoCache {
    static let shared: NSCache<NSString, UIImage> = {
        let cache = NSCache<NSString, UIImage>()
        cache.countLimit = 120
        return cache
    }()
}

// MARK: - Poll, event, listing

struct PollView: View {
    let poll: Poll
    let vote: (String?) -> Void

    private var showResults: Bool { poll.myOptionId != nil || poll.isClosed }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            ForEach(poll.options) { option in
                Button {
                    guard !poll.isClosed else { return }
                    vote(option.id == poll.myOptionId ? nil : option.id)
                } label: {
                    ZStack(alignment: .leading) {
                        GeometryReader { proxy in
                            RoundedRectangle(cornerRadius: 10)
                                .fill(option.id == poll.myOptionId ? Brand.blue.opacity(0.25) : Brand.softBlue)
                                .frame(width: showResults ? proxy.size.width * fraction(option) : 0)
                        }
                        HStack {
                            Text(option.label)
                            Spacer()
                            if showResults { Text("%\(Int((fraction(option) * 100).rounded()))").monospacedDigit() }
                            if option.id == poll.myOptionId { Image(systemName: "checkmark.circle.fill").foregroundStyle(Brand.blue) }
                        }
                        .padding(.horizontal, Spacing.sm)
                    }
                    .frame(height: 38)
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color(uiColor: .separator)))
                }
                .buttonStyle(.plain)
                .disabled(poll.isClosed)
            }
            Text(poll.isClosed ? "\(poll.totalVotes) oy · Anket kapandı" : "\(poll.totalVotes) oy")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }

    private func fraction(_ option: PollOption) -> CGFloat {
        poll.totalVotes == 0 ? 0 : CGFloat(option.votes) / CGFloat(poll.totalVotes)
    }
}

struct EventView: View {
    let event: PostEvent
    let toggle: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: Spacing.sm) {
            Image(systemName: "calendar").font(.title3).foregroundStyle(Brand.blue)
            VStack(alignment: .leading, spacing: 2) {
                Text(Formatting.dateTime(event.startsAt)).font(.subheadline.weight(.semibold))
                if let location = event.location { Text(location).font(.caption).foregroundStyle(.secondary) }
                Text("\(event.attendeeCount) kişi katılıyor").font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            Button(event.attending ? "Katılıyorum" : "Katıl", action: toggle)
                .buttonStyle(.bordered)
                .tint(event.attending ? Brand.success : Brand.blue)
                .disabled((event.endsAt ?? event.startsAt) < Date())
        }
        .padding(Spacing.sm)
        .background(Brand.softBlue.opacity(0.5), in: RoundedRectangle(cornerRadius: 12))
    }
}

struct ListingView: View {
    let listing: Listing
    let isMine: Bool
    let toggleSold: () -> Void

    var body: some View {
        HStack {
            Label(listing.priceText, systemImage: "tag").font(.headline)
            if listing.sold {
                Text("Satıldı").font(.caption.bold()).padding(.horizontal, 8).padding(.vertical, 2)
                    .background(Color(uiColor: .systemGray5), in: Capsule())
            }
            Spacer()
            if isMine {
                Button(listing.sold ? "Satışa geri al" : "Satıldı olarak işaretle", action: toggleSold).buttonStyle(.bordered)
            }
        }
    }
}

// MARK: - Reporting

struct ReportSheet: View {
    let target: ReportTarget
    let id: String
    @Environment(\.dismiss) private var dismiss
    @State private var reason: ReportReason?
    @State private var details = ""
    @State private var runner = ActionRunner()
    @State private var sent = false

    var body: some View {
        NavigationStack {
            Form {
                Section("Neden şikâyet ediyorsun?") {
                    Picker("Neden", selection: $reason) {
                        ForEach(ReportReason.allCases) { Text($0.title).tag(Optional($0)) }
                    }
                    .pickerStyle(.inline)
                    .labelsHidden()
                }
                Section {
                    TextField("Ayrıntı (isteğe bağlı)", text: $details, axis: .vertical).lineLimit(3...6)
                } footer: {
                    if reason == .personalDataLeak || reason == .personalityRights {
                        Text("Bu şikâyetler öncelikli incelenir.")
                    }
                }
                Section { InlineError(error: runner.error) }
            }
            .navigationTitle("Şikâyet et")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Vazgeç") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Gönder") {
                        guard let reason else { return }
                        runner.run {
                            try await ModerationService().report(target: target, id: id, reason: reason, details: details)
                            sent = true
                        }
                    }
                    .disabled(reason == nil || runner.isRunning)
                }
            }
            .alert("Şikâyetin alındı", isPresented: $sent) {
                Button("Tamam") { dismiss() }
            } message: {
                Text("Moderasyon ekibi inceleyecek. Teşekkürler.")
            }
        }
    }
}
