package com.estilovivo.app.feature.social.feed

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Módulo Koin de Feed / Historias / Retos / Tendencias / Perfil de otra usuaria. */
val feedModule = module {
    single<MeProvider> { AuthMeProvider(get()) }
    single<ViewedStories> { PrefsViewedStories(androidContext()) }

    viewModel { FeedViewModel(looks = get(), social = get(), meProvider = get()) }
    viewModel { StoriesViewModel(stories = get(), garments = get(), viewed = get(), meProvider = get()) }
    viewModel { StoryViewerViewModel(stories = get(), viewed = get()) }
    viewModel { ChallengeViewModel(challenges = get(), meProvider = get()) }
    viewModel { ChallengeHistoryViewModel(challenges = get()) }
    viewModel { TrendsViewModel(trends = get()) }
    viewModel { (userId: String) -> UserProfileViewModel(userId = userId, social = get(), chat = get(), meProvider = get()) }
}
