package com.kampusagi.android.core.di

import com.kampusagi.android.data.document.ContentResolverDocumentReader
import com.kampusagi.android.data.repository.AdminRepositoryImpl
import com.kampusagi.android.data.repository.AuthRepositoryImpl
import com.kampusagi.android.data.repository.CommunityRepositoryImpl
import com.kampusagi.android.data.repository.ProfileRepositoryImpl
import com.kampusagi.android.data.repository.RequirementRepositoryImpl
import com.kampusagi.android.data.repository.UniversityRepositoryImpl
import com.kampusagi.android.data.repository.VerificationRepositoryImpl
import com.kampusagi.android.domain.repository.AdminRepository
import com.kampusagi.android.domain.repository.AuthRepository
import com.kampusagi.android.domain.repository.CommunityRepository
import com.kampusagi.android.domain.repository.DocumentReader
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
    @Binds abstract fun bindUniversityRepository(impl: UniversityRepositoryImpl): UniversityRepository
    @Binds abstract fun bindVerificationRepository(impl: VerificationRepositoryImpl): VerificationRepository
    @Binds abstract fun bindAdminRepository(impl: AdminRepositoryImpl): AdminRepository
    @Binds abstract fun bindCommunityRepository(impl: CommunityRepositoryImpl): CommunityRepository
    @Binds abstract fun bindRequirementRepository(impl: RequirementRepositoryImpl): RequirementRepository
    @Binds abstract fun bindDocumentReader(impl: ContentResolverDocumentReader): DocumentReader
}
