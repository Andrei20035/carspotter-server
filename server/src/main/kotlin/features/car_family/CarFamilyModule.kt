package com.revio.server.features.car_family

import com.revio.server.features.car_model.ICarModelDAO
import org.koin.dsl.module

val carFamilyModule = module {
    single<ICarFamilyDAO> { CarFamilyDAO() }
    single<ICarFamilyService> { CarFamilyService(get(), get<ICarModelDAO>()) }
}
