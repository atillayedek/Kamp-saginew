import SwiftUI

/// Screens reachable from many places; every tab's NavigationStack knows them all.
enum AppRoute: Hashable {
    case post(String)
    case user(String)
    case tag(String)
    case mention(String)
    case legal(String)
    case savedPosts
    case privacy
    case settings

    /// Links inside post and comment text ("kampusagi-app://tag/kampus", ".../user/ayse").
    static func from(url: URL) -> AppRoute? {
        guard url.scheme == LinkedText.scheme, let value = url.pathComponents.dropFirst().first, !value.isEmpty else { return nil }
        switch url.host {
        case "tag": return .tag(value)
        case "user": return .mention(value)
        default: return nil
        }
    }
}

/// A tab's navigation stack with every shared destination and in-text link handling.
struct RoutedStack<Root: View>: View {
    @State private var path: [AppRoute] = []
    @ViewBuilder let root: () -> Root

    var body: some View {
        NavigationStack(path: $path) {
            root()
                .navigationDestination(for: AppRoute.self) { route in
                    switch route {
                    case .post(let id): PostDetailView(postId: id)
                    case .user(let id): UserProfileView(userId: id)
                    case .tag(let tag): TagPostsView(tag: tag)
                    case .mention(let username): MentionView(username: username) { userId in
                        path.removeLast()
                        path.append(.user(userId))
                    }
                    case .legal(let type): LegalDocumentView(docType: type)
                    case .savedPosts: SavedPostsView()
                    case .privacy: PrivacyView()
                    case .settings: SettingsView()
                    }
                }
        }
        .environment(\.openURL, OpenURLAction { url in
            if let route = AppRoute.from(url: url) {
                path.append(route)
                return .handled
            }
            return .systemAction
        })
    }
}

/// Post and comment text with #tags and @mentions as tappable links (handled by RoutedStack).
enum LinkedText {
    static let scheme = "kampusagi-app"

    static func attributed(_ text: String) -> AttributedString {
        var result = AttributedString()
        for segment in BodyLinks.parse(text) {
            var part = AttributedString(segment.text)
            switch segment {
            case .plain: break
            case .tag(_, let key):
                part.link = URL(string: "\(scheme)://tag/\(key)")
                part.foregroundColor = Brand.blue
            case .mention(_, let username):
                let encoded = username.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? username
                part.link = URL(string: "\(scheme)://user/\(encoded)")
                part.foregroundColor = Brand.blue
            }
            result += part
        }
        return result
    }
}
