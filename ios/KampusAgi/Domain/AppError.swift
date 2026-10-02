import Foundation

/// Errors the UI can explain. Raw exceptions, HTTP codes and SQL errors never leave the data layer
/// (same set and wording as the Android app's AppError).
enum AppError: Error, Equatable {
    case network
    case invalidCredentials
    case emailNotConfirmed
    case emailInUse
    case weakPassword
    case samePassword
    case rateLimited
    case sessionExpired
    case notConfigured
    case usernameTaken
    case universityNotFound
    case profileLocked
    case invalidInput
    case documentTooLarge
    case documentNotPdf
    case documentUnreadable
    case imageUnreadable
    case verificationNotAllowed
    case adminRequired
    case rejectionReasonRequired
    case verificationNotPending
    case accountNotApproved
    case tooManyActiveRequirements
    case requirementDailyLimit
    case pushNotConfigured
    case recipientNotAvailable
    case billingUnavailable
    case billingNotConfigured
    case planNotAvailable
    case purchaseNotActive
    case purchaseNotForAccount
    case purchaseCancelled
    case pollClosed
    case eventEnded
    case tooManySaved
    case premiumRequired
    case promoCodeInvalid
    case promoCodeUsed
    case promoCodeExhausted
    case tooManyGroups
    case groupFull
    case ownerCannotLeave
    case groupNotAllowed
    case sensitiveTag
    case rightsDeclarationRequired
    case appealExists
    case notFound
    case server
    case unknown

    /// Turkish text shown to the person.
    var message: String {
        switch self {
        case .network: return "İnternet bağlantısı yok ya da sunucuya ulaşılamıyor. Bağlantını kontrol edip tekrar dene."
        case .invalidCredentials: return "E-posta ya da şifre hatalı."
        case .emailNotConfirmed: return "E-posta adresin henüz doğrulanmamış. Gelen kutundaki bağlantıya dokun."
        case .emailInUse: return "Bu e-posta ile zaten bir hesap var."
        case .weakPassword: return "Bu şifre yeterince güçlü değil. Daha uzun ya da farklı bir şifre dene."
        case .samePassword: return "Yeni şifre eskisiyle aynı olamaz."
        case .rateLimited: return "Çok fazla deneme yapıldı. Biraz bekleyip tekrar dene."
        case .sessionExpired: return "Oturumunun süresi doldu. Lütfen tekrar giriş yap."
        case .notConfigured: return "Bu sürüm sunucuya bağlı değil."
        case .usernameTaken: return "Bu kullanıcı adı alınmış."
        case .universityNotFound: return "Seçilen üniversite artık listede yok. Listeyi yenileyip tekrar seç."
        case .profileLocked: return "Doğrulama başladığı için profil bilgilerin artık değiştirilemez."
        case .invalidInput: return "Girilen bilgilerden biri geçersiz. Alanları kontrol et."
        case .documentTooLarge: return "Belge 10 MB'tan büyük olamaz."
        case .documentNotPdf: return "Seçilen dosya geçerli bir PDF değil."
        case .documentUnreadable: return "Seçilen dosya okunamadı. Başka bir dosya dene."
        case .imageUnreadable: return "Fotoğraf açılamadı. Başka bir fotoğraf seç."
        case .verificationNotAllowed: return "Şu an yeni belge yüklenemez. Durumunu yenileyip tekrar dene."
        case .adminRequired: return "Bu işlem için yönetici yetkisi gerekiyor."
        case .rejectionReasonRequired: return "Ret nedeni 3–500 karakter olmalı."
        case .verificationNotPending: return "Bu başvuru zaten sonuçlandırılmış."
        case .accountNotApproved: return "Bu özellik yalnızca doğrulanmış öğrenciler içindir."
        case .tooManyActiveRequirements: return "Aktif ilan sınırına ulaştın. Önce eski bir ilanı kapat."
        case .requirementDailyLimit: return "Bugün çok fazla ilan açtın. Yarın tekrar dene."
        case .pushNotConfigured: return "Anlık bildirimler bu sürümde yapılandırılmamış."
        case .recipientNotAvailable: return "Bu öğrenciye şu an mesaj gönderilemiyor."
        case .billingUnavailable: return "Google Play ödeme hizmetine ulaşılamadı. Tekrar dene."
        case .billingNotConfigured: return "Satın alma doğrulaması henüz yapılandırılmamış."
        case .planNotAvailable: return "Bu plan şu an sunulmuyor."
        case .purchaseNotActive: return "Satın alma aktif değil ya da süresi dolmuş."
        case .purchaseNotForAccount: return "Bu satın alma başka bir KampüsAğı hesabına ait."
        case .purchaseCancelled: return "Satın alma iptal edildi."
        case .pollClosed: return "Bu anket kapandı."
        case .eventEnded: return "Bu etkinlik sona erdi."
        case .tooManySaved: return "En fazla 1000 gönderi kaydedebilirsin."
        case .premiumRequired: return "Bu özellik Premium üyelere özel."
        case .promoCodeInvalid: return "Bu kod geçerli değil ya da süresi dolmuş."
        case .promoCodeUsed: return "Bu kodu daha önce kullandın."
        case .promoCodeExhausted: return "Bu kodun kullanım hakkı dolmuş."
        case .tooManyGroups: return "Grup veya kanal sınırına ulaştın."
        case .groupFull: return "Bu grup dolu."
        case .ownerCannotLeave: return "Kurucu gruptan ayrılamaz; istersen grubu silebilirsin."
        case .groupNotAllowed: return "Bu işlem için yetkin yok."
        case .sensitiveTag: return "Etiketler din, mezhep, etnik köken, siyasi görüş, sağlık, cinsel yönelim gibi özel nitelikli bilgileri içeremez. Bu etiketi kaldır."
        case .rightsDeclarationRequired: return "Notu yüklemek için hak sahibi olduğunu ya da paylaşma iznin olduğunu onaylaman gerekiyor."
        case .appealExists: return "Bu karara zaten itiraz ettin; sonucu bildirimlerde göreceksin."
        case .notFound: return "İstenen kayıt bulunamadı."
        case .server: return "Sunucuda bir sorun oluştu. Biraz sonra tekrar dene."
        case .unknown: return "Beklenmeyen bir hata oluştu. Tekrar dene."
        }
    }
}

/// Problems in a form, found before anything is sent.
enum InputError: Hashable {
    case emailInvalid
    case passwordRequired
    case passwordTooShort
    case passwordsDoNotMatch
    case birthDateRequired
    case underage
    case noticeNotRead
    case termsNotAccepted

    var message: String {
        switch self {
        case .emailInvalid: return "Geçerli bir e-posta adresi gir."
        case .passwordRequired: return "Şifreni gir."
        case .passwordTooShort: return "Şifre çok kısa."
        case .passwordsDoNotMatch: return "Şifreler aynı değil."
        case .birthDateRequired: return "Doğum tarihini seç."
        case .underage: return "KampüsAğı yalnızca 18 yaşını doldurmuş kişiler içindir; bu yüzden hesap açılamıyor."
        case .noticeNotRead: return "Devam etmek için Aydınlatma Metni'ni okuduğunu işaretle."
        case .termsNotAccepted: return "Devam etmek için Kullanım Koşulları'nı ve Topluluk Kuralları'nı kabul etmelisin."
        }
    }
}
