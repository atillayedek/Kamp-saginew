import Foundation
import Supabase

/// The single Supabase client, or nil when the build has no backend configuration.
final class Backend {
    static let shared = Backend()

    let client: SupabaseClient?

    private init() {
        guard AppConfig.isBackendConfigured, let url = URL(string: AppConfig.supabaseURL) else {
            client = nil
            return
        }
        client = SupabaseClient(
            supabaseURL: url,
            supabaseKey: AppConfig.supabaseAnonKey,
            options: SupabaseClientOptions(auth: .init(redirectToURL: AppConfig.authRedirectURL, flowType: .pkce))
        )
    }

    func requireClient() throws -> SupabaseClient {
        guard let client else { throw AppError.notConfigured }
        return client
    }

    /// Calls a database function and decodes its JSON result with snake_case keys.
    func rpc<T: Decodable>(_ function: String, _ params: [String: AnyJSON] = [:], as type: T.Type = T.self) async throws -> T {
        let client = try requireClient()
        let response = try await client.rpc(function, params: params).execute()
        return try JSONCoding.decoder.decode(T.self, from: response.data)
    }

    /// Calls a database function whose result is not needed.
    func rpcVoid(_ function: String, _ params: [String: AnyJSON] = [:]) async throws {
        let client = try requireClient()
        _ = try await client.rpc(function, params: params).execute()
    }

    /// Calls an Edge Function; a non-2xx `{"error": "<code>"}` answer becomes an AppError.
    func invoke<T: Decodable>(_ function: String, body: [String: AnyJSON], as type: T.Type = T.self) async throws -> T {
        let client = try requireClient()
        do {
            return try await client.functions.invoke(function, options: FunctionInvokeOptions(body: body)) { data, _ in
                try JSONCoding.decoder.decode(T.self, from: data)
            }
        } catch let FunctionsError.httpError(code, data) {
            throw ErrorMapping.functionError(status: code, body: String(decoding: data, as: UTF8.self))
        }
    }
}

enum JSONCoding {
    static let decoder: JSONDecoder = {
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        return decoder
    }()
}

extension AnyJSON {
    /// `nil` becomes JSON null, so optional parameters are sent explicitly.
    static func optional(_ value: String?) -> AnyJSON { value.map { .string($0) } ?? .null }
    static func optional(_ value: Int?) -> AnyJSON { value.map { .integer($0) } ?? .null }
}

/// Timestamps from PostgreSQL ("2026-10-01T18:03:13.123456+00:00") and Auth.
enum DateParser {
    private static let withFraction: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()
    private static let plain: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter
    }()

    static func parse(_ text: String?) -> Date? {
        guard var value = text?.trimmingCharacters(in: .whitespaces), !value.isEmpty else { return nil }
        // "2026-10-01 18:03:13+00" (text casts) -> ISO form.
        if value.count > 10, value[value.index(value.startIndex, offsetBy: 10)] == " " {
            value.replaceSubrange(value.index(value.startIndex, offsetBy: 10)...value.index(value.startIndex, offsetBy: 10), with: "T")
        }
        // "+00" -> "+00:00"
        if let match = value.range(of: "[+-]\\d{2}$", options: .regularExpression) {
            value.replaceSubrange(match, with: value[match] + ":00")
        }
        // Fractions of 1-9 digits -> exactly 3 (ISO8601DateFormatter expects milliseconds).
        if let match = value.range(of: "\\.\\d+", options: .regularExpression) {
            let digits = value[match].dropFirst()
            let millis = String((digits + "000").prefix(3))
            value.replaceSubrange(match, with: "." + millis)
            return withFraction.date(from: value)
        }
        return plain.date(from: value)
    }

    /// "2026-10-01" for date-only fields such as the birth date.
    static func dayString(_ date: Date, calendar: Calendar = .turkey) -> String {
        let parts = calendar.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", parts.year ?? 0, parts.month ?? 0, parts.day ?? 0)
    }
}
