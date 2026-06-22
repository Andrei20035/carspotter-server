package com.carspotter.features.scoring

import org.koin.dsl.module

val scoringModule = module {
    single<IScoringService> { ScoringServiceImpl(get(), get()) }
}
