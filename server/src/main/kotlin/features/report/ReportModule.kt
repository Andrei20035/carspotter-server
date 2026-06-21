package features.report

import org.koin.dsl.module

val reportModule = module {
    single<IReportDAO> { ReportDAO() }
    single<IReportService> { ReportService(get()) }
}
