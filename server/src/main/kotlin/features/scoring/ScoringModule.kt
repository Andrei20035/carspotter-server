package com.carspotter.features.scoring

import features.activity.IActivityDAO
import org.koin.dsl.module

val scoringModule = module {
    single<IScoringDao> { ScoringDaoImpl() }
    single<IScoringService> { ScoringServiceImpl(get(), get(), get(), get<IActivityDAO>()) }
}
