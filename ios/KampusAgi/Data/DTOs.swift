import Foundation

// JSON rows as the database returns them (snake_case keys converted by JSONCoding.decoder).

struct ProfileDTO: Decodable {
    static let columns = "id,email,full_name,username,university_id,department,account_status,bio,show_full_name,universities(name)"
    let id: String
    let email: String
    let fullName: String?
    let username: String?
    let universityId: String?
    let department: String?
    let accountStatus: String
    let bio: String?
    let showFullName: Bool?
    let universities: UniversityNameDTO?
}

struct UniversityNameDTO: Decodable { let name: String }

struct PostDTO: Decodable {
    let id: String
    let scope: String
    let category: String?
    let body: String
    let createdAt: String
    let likeCount: Int
    let commentCount: Int
    let likedByMe: Bool
    let isMine: Bool
    let authorId: String
    let authorFullName: String?
    let authorUsername: String?
    let authorUniversity: String?
    let savedByMe: Bool?
    let media: [String]?
    let poll: PollDTO?
    let event: PostEventDTO?
    let listing: ListingDTO?
}

struct PollDTO: Decodable {
    let id: String
    let closesAt: String?
    let totalVotes: Int?
    let myOptionId: String?
    let options: [PollOptionDTO]?
}

struct PollOptionDTO: Decodable {
    let id: String
    let label: String
    let votes: Int
}

struct PostEventDTO: Decodable {
    let startsAt: String
    let endsAt: String?
    let location: String?
    let attendeeCount: Int?
    let attending: Bool?
}

struct ListingDTO: Decodable {
    let priceKurus: Int
    let sold: Bool?
}

struct CommentDTO: Decodable {
    let id: String
    let body: String
    let createdAt: String
    let isMine: Bool
    let authorId: String
    let authorFullName: String?
    let authorUsername: String?
}

struct PersonDTO: Decodable {
    let userId: String
    let fullName: String?
    let username: String?
    let university: String?
    let department: String?
}

struct UserProfileDTO: Decodable {
    let userId: String
    let fullName: String?
    let username: String?
    let university: String?
    let department: String?
    let bio: String?
    let postCount: Int
    let isMe: Bool
}

struct MentionSuggestionDTO: Decodable {
    let userId: String
    let username: String
    let displayName: String?
    let university: String?
}

struct TagCountDTO: Decodable {
    let tag: String
    let postCount: Int
}

struct NotificationDTO: Decodable {
    let id: String
    let kind: String
    let createdAt: String
    let readAt: String?
    let actorFullName: String?
    let actorUsername: String?
    let conversationId: String?
    let postId: String?
}

struct VerificationDTO: Decodable {
    let status: String
    let rejectionReason: String?
}

struct LegalDocumentInfoDTO: Decodable {
    let docType: String
    let kind: String
    let version: Int
    let title: String
}

struct LegalDocumentDTO: Decodable {
    let docType: String
    let version: Int
    let title: String
    let content: String
    let publishedAt: String
}

struct ConsentStatusDTO: Decodable {
    let docType: String
    let title: String
    let activeVersion: Int
    let granted: Bool
}

struct DataSubjectRequestDTO: Decodable {
    let requestNo: String
    let type: String
    let status: String
    let receivedAt: String
    let dueAt: String
    let responseSummary: String?
}

struct DataExportDTO: Decodable {
    let jsonUrl: String
    let htmlUrl: String
    let expiresAt: String
}

struct DeletionScheduleDTO: Decodable {
    let scheduledFor: String?
}

extension PostDTO {
    func toDomain() -> Post {
        Post(
            id: id,
            scope: PostScope(rawValue: scope) ?? .general,
            // A category added on the server later shows as general until the app knows it.
            category: category.flatMap(PostCategory.init(rawValue:)) ?? .general,
            body: body,
            createdAt: DateParser.parse(createdAt) ?? Date(),
            likeCount: likeCount,
            commentCount: commentCount,
            likedByMe: likedByMe,
            savedByMe: savedByMe ?? false,
            isMine: isMine,
            author: Author(id: authorId, fullName: authorFullName, username: authorUsername, university: authorUniversity),
            media: media ?? [],
            poll: poll.map { dto in
                Poll(
                    id: dto.id,
                    closesAt: DateParser.parse(dto.closesAt),
                    totalVotes: dto.totalVotes ?? 0,
                    myOptionId: dto.myOptionId,
                    options: (dto.options ?? []).map { PollOption(id: $0.id, label: $0.label, votes: $0.votes) }
                )
            },
            event: event.flatMap { dto in
                DateParser.parse(dto.startsAt).map {
                    PostEvent(startsAt: $0, endsAt: DateParser.parse(dto.endsAt), location: dto.location,
                              attendeeCount: dto.attendeeCount ?? 0, attending: dto.attending ?? false)
                }
            },
            listing: listing.map { Listing(priceKurus: $0.priceKurus, sold: $0.sold ?? false) }
        )
    }
}
