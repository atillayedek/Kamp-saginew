import XCTest
@testable import KampusAgi

/// Same cases as the Android unit tests (BodyLinksTest, UseCaseTest, PersonalDataDetectorTest) and the
/// SQL test 021, so the three clients and the database agree.
final class BodyLinksTests: XCTestCase {
    private func links(_ text: String) -> [BodyLinks.Segment] {
        BodyLinks.parse(text).filter { if case .plain = $0 { return false } else { return true } }
    }

    func testMentionsAndTagsAreFoundAsTheDatabaseFindsThem() {
        let text = "#Kampüs etkinliği için @mt_mehmet ve @MT_ODTU gelsin. e-posta a@mt_mehmet.com #1 #ab #Bahar_Şenliği"
        XCTAssertEqual(links(text), [
            .tag(text: "#Kampüs", key: "kampus"),
            .mention(text: "@mt_mehmet", username: "mt_mehmet"),
            .mention(text: "@MT_ODTU", username: "mt_odtu"),
            .tag(text: "#ab", key: "ab"),
            .tag(text: "#Bahar_Şenliği", key: "bahar_senligi"),
        ])
    }

    func testSegmentsRebuildTheOriginalText() {
        let text = "Selam @ayse. Bugün #sınav var, C# değil; @ab kısa. Son: @mehmet"
        XCTAssertEqual(BodyLinks.parse(text).map(\.text).joined(), text)
    }

    func testSentenceEndingDotIsNotUnderlinedButKeptForLookup() {
        XCTAssertEqual(links("Selam @ayse."), [.mention(text: "@ayse", username: "ayse.")])
    }

    func testGluedOrTooShortMarkersAreNotLinks() {
        XCTAssertEqual(links("C# ve a@b.com ve @ab ve #1 ve ##"), [])
    }

    func testTagKeysFoldTurkishLetters() {
        XCTAssertEqual(BodyLinks.tagKey("#SINAV_Haftası"), "sinav_haftasi")
        XCTAssertEqual(BodyLinks.tagKey("Çağrı"), "cagri")
        XCTAssertNil(BodyLinks.tagKey("#12"))
        XCTAssertNil(BodyLinks.tagKey("#a"))
        XCTAssertNil(BodyLinks.tagKey("#bu da"))
    }

    func testMentionBeingTypedIsTheLastWordAfterAnAtSign() {
        XCTAssertEqual(BodyLinks.mentionBeingTyped("Merhaba @"), "")
        XCTAssertEqual(BodyLinks.mentionBeingTyped("Merhaba @Ays"), "ays")
        XCTAssertNil(BodyLinks.mentionBeingTyped("Merhaba @ayse "))
        XCTAssertNil(BodyLinks.mentionBeingTyped("mail@ays"))
        XCTAssertEqual(BodyLinks.completeMention("Merhaba @ay", username: "ayse_y"), "Merhaba @ayse_y ")
        XCTAssertEqual(BodyLinks.completeMention("text", username: "ayse_y"), "text")
    }
}

final class PolicyTests: XCTestCase {
    private func day(_ text: String) -> Date {
        var components = DateComponents()
        let parts = text.split(separator: "-").compactMap { Int($0) }
        components.year = parts[0]; components.month = parts[1]; components.day = parts[2]; components.hour = 12
        return Calendar.turkey.date(from: components)!
    }

    func testTurning18TodayIsOldEnough() {
        XCTAssertTrue(AgePolicy.isOldEnough(birthDate: day("2008-10-03"), today: day("2026-10-03"), minAge: 18))
        XCTAssertFalse(AgePolicy.isOldEnough(birthDate: day("2008-10-04"), today: day("2026-10-03"), minAge: 18))
        XCTAssertFalse(AgePolicy.isOldEnough(birthDate: day("2030-01-01"), today: day("2026-10-03"), minAge: 18))
    }

    func testSignUpNeedsEveryRequiredAnswer() {
        let errors = AuthInputValidator.validateSignUp(email: "x", password: "short", confirm: "other", birthDate: nil,
                                                       minAge: 18, noticeRead: false, termsAccepted: false)
        XCTAssertEqual(errors, [.emailInvalid, .passwordTooShort, .passwordsDoNotMatch, .birthDateRequired, .noticeNotRead, .termsNotAccepted])
        XCTAssertEqual(AuthInputValidator.validateSignUp(email: "a@b.edu.tr", password: "12345678", confirm: "12345678",
                                                         birthDate: day("2000-01-01"), minAge: 18, noticeRead: true,
                                                         termsAccepted: true, today: day("2026-10-03")), [])
    }

    func testUsernamesAreNormalised() {
        XCTAssertEqual(ProfileInputValidator.normalizeUsername("  Ayse_Y "), "ayse_y")
        XCTAssertTrue(ProfileInputValidator.isValidUsername("ayse.y_1"))
        XCTAssertFalse(ProfileInputValidator.isValidUsername("ay"))
        XCTAssertFalse(ProfileInputValidator.isValidUsername("ayşe"))
    }
}

final class PersonalDataDetectorTests: XCTestCase {
    func testIdentityNumbersAreRecognisedByTheirChecksum() {
        XCTAssertTrue(PersonalDataDetector.isTcIdentityNumber("10000000146"))
        XCTAssertFalse(PersonalDataDetector.isTcIdentityNumber("10000000147"))
        XCTAssertEqual(PersonalDataDetector.find("kimlik 10000000146"), [.tcIdentityNumber])
    }

    func testTurkishIbansWithOrWithoutSpaces() {
        XCTAssertEqual(PersonalDataDetector.find("TR33 0006 1005 1978 6457 8413 26"), [.iban])
        XCTAssertEqual(PersonalDataDetector.find("TR330006100519786457841326"), [.iban])
    }

    func testMobileNumbers() {
        XCTAssertEqual(PersonalDataDetector.find("ara 0532 123 45 67"), [.phoneNumber])
        XCTAssertEqual(PersonalDataDetector.find("+90 532 123 4567"), [.phoneNumber])
        XCTAssertEqual(PersonalDataDetector.find("2026 yılında 15 kişi"), [])
    }
}

final class DataTests: XCTestCase {
    func testPostgresTimestampsParse() {
        let expected = Date(timeIntervalSince1970: 1_790_877_793.123)
        XCTAssertEqual(DateParser.parse("2026-10-01T18:03:13.123456+00:00")!.timeIntervalSince1970, expected.timeIntervalSince1970, accuracy: 0.001)
        XCTAssertEqual(DateParser.parse("2026-10-01 18:03:13.1+00")!.timeIntervalSince1970, 1_790_877_793.1, accuracy: 0.001)
        XCTAssertEqual(DateParser.parse("2026-10-01T18:03:13Z")!.timeIntervalSince1970, 1_790_877_793, accuracy: 0.001)
        XCTAssertNil(DateParser.parse(nil))
        XCTAssertNil(DateParser.parse("bugün"))
    }

    func testDatabaseCodesMapToTheSameErrorsAsAndroid() {
        XCTAssertEqual(ErrorMapping.knownCode("p0001: username_taken"), .usernameTaken)
        XCTAssertEqual(ErrorMapping.knownCode("approved_student_required"), .accountNotApproved)
        XCTAssertEqual(ErrorMapping.knownCode("invalid_tag"), .invalidInput)
        XCTAssertEqual(ErrorMapping.knownCode("sensitive_tag"), .sensitiveTag)
        XCTAssertNil(ErrorMapping.knownCode("something else"))
        XCTAssertEqual(ErrorMapping.functionError(status: 400, body: #"{"error":"document_too_large"}"#), .documentTooLarge)
        XCTAssertEqual(ErrorMapping.functionError(status: 503, body: ""), .server)
        XCTAssertEqual(ErrorMapping.appError(URLError(.notConnectedToInternet)), .network)
    }

    func testAuthLinksReadQueryAndFragment() {
        let url = URL(string: "kampusagi://auth-callback?type=recovery#error_code=otp_expired&error_description=Email+link+is+invalid")!
        let params = AuthService.parameters(of: url)
        XCTAssertEqual(params["type"], "recovery")
        XCTAssertEqual(params["error_code"], "otp_expired")
    }

    func testInTextLinksOpenRoutes() {
        XCTAssertEqual(AppRoute.from(url: URL(string: "kampusagi-app://tag/kampus")!), .tag("kampus"))
        XCTAssertEqual(AppRoute.from(url: URL(string: "kampusagi-app://user/ayse.")!), .mention("ayse."))
        XCTAssertNil(AppRoute.from(url: URL(string: "https://example.com/tag/x")!))
    }

    func testListingPricesInTurkish() {
        XCTAssertEqual(Listing(priceKurus: 0, sold: false).priceText, "Ücretsiz")
        XCTAssertTrue(Listing(priceKurus: 125_000, sold: false).priceText.contains("1.250"))
    }
}

final class SessionStorageTests: XCTestCase {
    /// A Keychain without access, as in a build without a code signature.
    private struct UnavailableKeychain: SessionBackingStore {
        struct Denied: Error {}
        func store(key: String, value: Data) throws { throw Denied() }
        func retrieve(key: String) throws -> Data? { throw Denied() }
        func remove(key: String) throws { throw Denied() }
    }

    func testSessionSurvivesAKeychainThatCannotBeUsed() throws {
        let storage = SessionStorage(persistent: UnavailableKeychain())
        XCTAssertNil(try storage.retrieve(key: "session"))
        try storage.store(key: "session", value: Data("token".utf8))
        XCTAssertEqual(try storage.retrieve(key: "session"), Data("token".utf8))
        try storage.remove(key: "session")
        XCTAssertNil(try storage.retrieve(key: "session"))
    }
}
