import SwiftUI

/// Error with a retry button, using the system's empty-state layout.
struct ErrorStateView: View {
    let error: AppError
    var title = "Yüklenemedi"
    let retry: () -> Void

    var body: some View {
        ContentUnavailableView {
            Label(title, systemImage: error == .network ? "wifi.slash" : "exclamationmark.triangle")
        } description: {
            Text(error.message)
        } actions: {
            Button("Tekrar dene", action: retry).buttonStyle(.borderedProminent)
        }
    }
}

/// A one-line error under a form or list, used for failed actions (the content stays visible).
struct InlineError: View {
    let error: AppError?

    var body: some View {
        if let error {
            Label(error.message, systemImage: "exclamationmark.circle")
                .font(.footnote)
                .foregroundStyle(Brand.danger)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

/// Wraps an async action so a button shows progress and cannot be tapped twice.
@MainActor
@Observable
final class ActionRunner {
    private(set) var isRunning = false
    var error: AppError?

    func run(_ action: @escaping () async throws -> Void) {
        guard !isRunning else { return }
        isRunning = true
        error = nil
        Task {
            do {
                try await action()
            } catch {
                self.error = ErrorMapping.appError(error)
            }
            isRunning = false
        }
    }
}

extension Text {
    /// Text with markdown links built at run time (interpolated links are not parsed by LocalizedStringKey).
    init(markdown: String) {
        self.init((try? AttributedString(markdown: markdown)) ?? AttributedString(markdown))
    }
}
