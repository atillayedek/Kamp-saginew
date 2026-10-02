import Foundation
import Observation

extension Notification.Name {
    /// A post changed (liked, saved, voted…) on one screen; other lists update their copy.
    static let postChanged = Notification.Name("KampusAgi.postChanged")
    /// A post was deleted or its author blocked (`object` is the post id, or the author id with `userInfo["author"]`).
    static let postRemoved = Notification.Name("KampusAgi.postRemoved")
}

/// A paged list of posts (feed, tag page, profile, saved) and the actions on them.
@MainActor
@Observable
final class PostListModel {
    enum State: Equatable {
        case loading
        case loaded
        case failed(AppError)
    }

    private(set) var posts: [Post] = []
    private(set) var state: State = .loading
    private(set) var isLoadingMore = false
    var actionError: AppError?

    @ObservationIgnored private var next: FeedCursor?
    @ObservationIgnored private var generation = 0
    @ObservationIgnored private let fetch: (FeedCursor?) async throws -> FeedPage
    @ObservationIgnored private let community = CommunityService()
    @ObservationIgnored private var observers: [NSObjectProtocol] = []

    init(fetch: @escaping (FeedCursor?) async throws -> FeedPage) {
        self.fetch = fetch
        observers.append(NotificationCenter.default.addObserver(forName: .postChanged, object: nil, queue: .main) { [weak self] note in
            guard let post = note.object as? Post else { return }
            MainActor.assumeIsolated { self?.replace(post, announce: false) }
        })
        observers.append(NotificationCenter.default.addObserver(forName: .postRemoved, object: nil, queue: .main) { [weak self] note in
            guard let id = note.object as? String else { return }
            MainActor.assumeIsolated {
                if note.userInfo?["author"] as? Bool == true {
                    self?.posts.removeAll { $0.author.id == id }
                } else {
                    self?.posts.removeAll { $0.id == id }
                }
            }
        })
    }

    /// A list loaded in one call (saved posts, search).
    convenience init(all: @escaping () async throws -> [Post]) {
        self.init(fetch: { _ in FeedPage(posts: try await all(), next: nil) })
    }

    func load() async {
        generation += 1
        let current = generation
        if posts.isEmpty { state = .loading }
        do {
            let page = try await fetch(nil)
            guard current == generation else { return }
            posts = page.posts
            next = page.next
            state = .loaded
        } catch {
            guard current == generation else { return }
            let mapped = ErrorMapping.appError(error)
            if posts.isEmpty { state = .failed(mapped) } else { actionError = mapped }
        }
    }

    /// Called when the last row appears.
    func loadMoreIfNeeded(after post: Post) async {
        guard post.id == posts.last?.id, let cursor = next, !isLoadingMore else { return }
        isLoadingMore = true
        defer { isLoadingMore = false }
        do {
            let page = try await fetch(cursor)
            let known = Set(posts.map(\.id))
            posts += page.posts.filter { !known.contains($0.id) }
            next = page.next
        } catch {
            actionError = ErrorMapping.appError(error)
        }
    }

    var hasMore: Bool { next != nil }

    deinit {
        for observer in observers { NotificationCenter.default.removeObserver(observer) }
    }

    // MARK: Actions (optimistic where the server returns the new state)

    func toggleLike(_ post: Post) {
        var changed = post
        changed.likedByMe.toggle()
        changed.likeCount += changed.likedByMe ? 1 : -1
        replace(changed)
        Task {
            do {
                var confirmed = changed
                confirmed.likeCount = try await community.setLiked(postId: post.id, liked: changed.likedByMe)
                replace(confirmed)
            } catch {
                replace(post)
                actionError = ErrorMapping.appError(error)
            }
        }
    }

    func toggleSave(_ post: Post) {
        var changed = post
        changed.savedByMe.toggle()
        replace(changed)
        Task {
            do {
                try await community.setSaved(postId: post.id, saved: changed.savedByMe)
            } catch {
                replace(post)
                actionError = ErrorMapping.appError(error)
            }
        }
    }

    func vote(_ post: Post, optionId: String?) {
        guard let poll = post.poll else { return }
        Task {
            do {
                try await community.vote(pollId: poll.id, optionId: optionId)
                replace(try await community.post(id: post.id))
            } catch {
                actionError = ErrorMapping.appError(error)
            }
        }
    }

    func toggleAttending(_ post: Post) {
        guard var event = post.event else { return }
        event.attending.toggle()
        Task {
            do {
                event.attendeeCount = try await community.setAttending(postId: post.id, attending: event.attending)
                var changed = post
                changed.event = event
                replace(changed)
            } catch {
                actionError = ErrorMapping.appError(error)
            }
        }
    }

    func toggleSold(_ post: Post) {
        guard var listing = post.listing else { return }
        listing.sold.toggle()
        Task {
            do {
                try await community.setSold(postId: post.id, sold: listing.sold)
                var changed = post
                changed.listing = listing
                replace(changed)
            } catch {
                actionError = ErrorMapping.appError(error)
            }
        }
    }

    func delete(_ post: Post) {
        Task {
            do {
                try await community.deletePost(id: post.id)
                NotificationCenter.default.post(name: .postRemoved, object: post.id)
            } catch {
                actionError = ErrorMapping.appError(error)
            }
        }
    }

    func blockAuthor(of post: Post) {
        Task {
            do {
                try await ModerationService().block(userId: post.author.id)
                NotificationCenter.default.post(name: .postRemoved, object: post.author.id, userInfo: ["author": true])
            } catch {
                actionError = ErrorMapping.appError(error)
            }
        }
    }

    private func replace(_ post: Post, announce: Bool = true) {
        guard let index = posts.firstIndex(where: { $0.id == post.id }) else { return }
        posts[index] = post
        if announce { NotificationCenter.default.post(name: .postChanged, object: post) }
    }
}
