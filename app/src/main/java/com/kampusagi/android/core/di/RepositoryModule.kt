package com.kampusagi.android.core.di

import com.kampusagi.android.data.document.ContentResolverDocumentReader
import com.kampusagi.android.data.image.ContentResolverImageEncoder
import com.kampusagi.android.data.push.PushRepositoryImpl
import com.kampusagi.android.data.repository.ProfileMediaRepositoryImpl
import com.kampusagi.android.data.repository.AccountRepositoryImpl
import com.kampusagi.android.data.repository.AnnouncementRepositoryImpl
import com.kampusagi.android.data.repository.AdminRepositoryImpl
import com.kampusagi.android.data.repository.AuthRepositoryImpl
import com.kampusagi.android.data.repository.ChatRepositoryImpl
import com.kampusagi.android.data.repository.CommunityRepositoryImpl
import com.kampusagi.android.data.repository.GroupRepositoryImpl
import com.kampusagi.android.data.repository.NoteRepositoryImpl
import com.kampusagi.android.data.repository.ReputationRepositoryImpl
import com.kampusagi.android.data.repository.ModerationRepositoryImpl
import com.kampusagi.android.data.repository.NotificationRepositoryImpl
import com.kampusagi.android.data.repository.PremiumRepositoryImpl
import com.kampusagi.android.data.repository.ProfileRepositoryImpl
import com.kampusagi.android.data.repository.RequirementRepositoryImpl
import com.kampusagi.android.data.repository.UniversityRepositoryImpl
import com.kampusagi.android.data.repository.VerificationRepositoryImpl
import com.kampusagi.android.domain.repository.PushRepository
import com.kampusagi.android.domain.repository.AccountRepository
import com.kampusagi.android.domain.repository.AnnouncementRepository
import com.kampusagi.android.domain.repository.AdminRepository
import com.kampusagi.android.domain.repository.AuthRepository
import com.kampusagi.android.domain.repository.ChatRepository
import com.kampusagi.android.domain.repository.CommunityRepository
import com.kampusagi.android.domain.repository.DocumentReader
import com.kampusagi.android.domain.repository.GroupRepository
import com.kampusagi.android.domain.repository.NoteRepository
import com.kampusagi.android.domain.repository.ReputationRepository
import com.kampusagi.android.domain.repository.ImageEncoder
import com.kampusagi.android.domain.repository.ProfileMediaRepository
import com.kampusagi.android.domain.repository.ModerationRepository
import com.kampusagi.android.domain.repository.NotificationRepository
import com.kampusagi.android.domain.repository.PremiumRepository
import com.kampusagi.android.domain.repository.ProfileRepository
import com.kampusagi.android.domain.repository.RequirementRepository
import com.kampusagi.android.domain.repository.UniversityRepository
import com.kampusagi.android.domain.repository.VerificationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository
    @Binds abstract fun bindProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository
    @Binds abstract fun bindProfileMediaRepository(impl: ProfileMediaRepositoryImpl): ProfileMediaRepository
    @Binds abstract fun bindImageEncoder(impl: ContentResolverImageEncoder): ImageEncoder
    @Binds abstract fun bindUniversityRepository(impl: UniversityRepositoryImpl): UniversityRepository
    @Binds abstract fun bindVerificationRepository(impl: VerificationRepositoryImpl): VerificationRepository
    @Binds abstract fun bindAdminRepository(impl: AdminRepositoryImpl): AdminRepository
    @Binds abstract fun bindCommunityRepository(impl: CommunityRepositoryImpl): CommunityRepository
    @Binds abstract fun bindRequirementRepository(impl: RequirementRepositoryImpl): RequirementRepository
    @Binds abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository
    @Binds abstract fun bindNotificationRepository(impl: NotificationRepositoryImpl): NotificationRepository
    @Binds abstract fun bindPremiumRepository(impl: PremiumRepositoryImpl): PremiumRepository
    @Binds abstract fun bindModerationRepository(impl: ModerationRepositoryImpl): ModerationRepository
    @Binds abstract fun bindAccountRepository(impl: AccountRepositoryImpl): AccountRepository
    @Binds abstract fun bindDocumentReader(impl: ContentResolverDocumentReader): DocumentReader
    @Binds abstract fun bindGroupRepository(impl: GroupRepositoryImpl): GroupRepository
    @Binds abstract fun bindNoteRepository(impl: NoteRepositoryImpl): NoteRepository
    @Binds abstract fun bindReputationRepository(impl: ReputationRepositoryImpl): ReputationRepository
    @Binds abstract fun bindAnnouncementRepository(impl: AnnouncementRepositoryImpl): AnnouncementRepository
    @Binds abstract fun bindPushRepository(impl: PushRepositoryImpl): PushRepository
}
