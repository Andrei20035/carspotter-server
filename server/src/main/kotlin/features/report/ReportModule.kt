package features.report

import com.revio.server.features.post.IPostService
import org.koin.dsl.module

val reportModule = module {
    single<IReportDAO> { ReportDAO() }
    single<IReportService> { ReportService(get(), get<IPostService>()) }
}
