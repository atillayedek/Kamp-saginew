package com.kampusagi.android.core.di

import com.kampusagi.android.data.repository.AuthRepositoryImpl
import com.kampusagi.android.data.repository.ProfileRepositoryImpl
import com.kampusagi.android.data.repository.UniversityRepositoryImpl
import com.kampusagi.android.domain.repository.AuthRepository
import com.kampusagi.android.domain.repository.ProfileRepository
import com.kampusagi.android.domain.repository.UniversityRepository
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
}
