package com.sakinah.deen.di

import com.sakinah.deen.auth.AuthRepository
import com.sakinah.deen.auth.InMemoryAuthRepository
import com.sakinah.deen.profile.InMemoryUserProfileRepository
import com.sakinah.deen.profile.UserProfileRepository
import com.sakinah.deen.settings.SettingsRepository
import com.sakinah.deen.settings.SettingsRepositoryImpl
import org.koin.dsl.module

val sharedModule = module {
    single<AuthRepository> { InMemoryAuthRepository() }
    single<UserProfileRepository> { InMemoryUserProfileRepository() }
    single<SettingsRepository> { SettingsRepositoryImpl() }
}