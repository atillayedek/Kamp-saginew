import SwiftUI

@main
struct KampusAgiApp: App {
    @State private var session = SessionStore()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(session)
                .tint(Brand.blue)
                .onOpenURL { url in Task { await session.handle(url: url) } }
                .task { session.start() }
        }
    }
}
