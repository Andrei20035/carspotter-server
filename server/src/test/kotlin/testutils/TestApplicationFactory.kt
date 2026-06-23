package testutils

import com.carspotter.config.configureSecurity
import com.carspotter.config.configureSerialization
import com.carspotter.config.configureAuthStatusPages
import com.carspotter.core.storage.IStorageService
import com.carspotter.core.storage.LocalImageStorageService
import com.carspotter.features.auth.AuthDAO
import com.carspotter.features.auth.AuthService
import com.carspotter.features.auth.GoogleTokenVerifier
import com.carspotter.features.auth.IAuthDAO
import com.carspotter.features.auth.IAuthService
import com.carspotter.features.auth.JwtService
import com.carspotter.features.auth.authRoutes
import com.carspotter.features.auth.RefreshTokenGenerator
import com.carspotter.features.auth.session.AuthSessionDAO
import com.carspotter.features.auth.session.IAuthSessionDAO
import com.carspotter.features.auth.session.ISessionService
import com.carspotter.features.auth.session.SessionService
import com.carspotter.features.car_model.CarModelDAO
import com.carspotter.features.car_model.CarModelService
import com.carspotter.features.car_model.ICarModelDAO
import com.carspotter.features.car_model.ICarModelService
import com.carspotter.features.car_model.carModelRoutes
import com.carspotter.features.leaderboard.ILeaderboardDAO
import com.carspotter.features.leaderboard.ILeaderboardService
import com.carspotter.features.leaderboard.ILeaderboardSnapshotDAO
import com.carspotter.features.leaderboard.LeaderboardDAO
import com.carspotter.features.leaderboard.LeaderboardService
import com.carspotter.features.leaderboard.LeaderboardSnapshotDAO
import com.carspotter.features.leaderboard.adminLeaderboardRoutes
import com.carspotter.features.leaderboard.leaderboardRoutes
import com.carspotter.features.post.IPostDAO
import com.carspotter.features.post.IPostService
import com.carspotter.features.post.PostDAO
import com.carspotter.features.post.PostServiceImpl
import com.carspotter.features.post.postRoutes
import com.carspotter.features.scoring.IScoringDao
import com.carspotter.features.scoring.IScoringService
import com.carspotter.features.scoring.ScoringDaoImpl
import com.carspotter.features.scoring.ScoringServiceImpl
import com.carspotter.features.user.IUserDAO
import com.carspotter.features.user.IUserService
import com.carspotter.features.user.UserDao
import com.carspotter.features.user.UserService
import com.carspotter.features.user.userRoutes
import com.carspotter.features.user_car.IUserCarDAO
import com.carspotter.features.user_car.IUserCarService
import com.carspotter.features.user_car.UserCarDAO
import com.carspotter.features.user_car.UserCarServiceImpl
import com.carspotter.features.user_car.userCarRoutes
import features.comment.CommentDAO
import features.comment.ICommentDAO
import features.comment.CommentService
import features.comment.ICommentService
import features.comment.commentRoutes
import features.like.ILikeDAO
import features.like.ILikeService
import features.like.LikeDAO
import features.like.LikeService
import features.like.likeRoutes
import features.report.IReportDAO
import features.report.IReportService
import features.report.ReportDAO
import features.report.ReportService
import features.report.reportRoutes
import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.ktor.ext.getKoin
import java.nio.file.Files

object TestEnv {
    val JWT_SECRET: String = "test-${java.util.UUID.randomUUID()}"
    const val JWT_ISSUER = "test-issuer"
    const val JWT_AUDIENCE = "test-audience"
}

/**
 * Seteaz-un set minim de variabile de mediu pentru testele de route.
 * Îl apelăm în @BeforeAll înainte de testApplication.
 */
fun setTestEnv() {
    // Hack pentru setarea env în test (JVM-internal, doar pentru teste)
    // Alternativ: folosește system properties și citește din ele în cod.
    setEnv("JWT_SECRET", TestEnv.JWT_SECRET)
    setEnv("JWT_ISSUER", TestEnv.JWT_ISSUER)
    setEnv("JWT_AUDIENCE", TestEnv.JWT_AUDIENCE)
}

/**
 * Setează o variabilă de mediu pentru procesul JVM curent.
 * Funcționează pe majoritatea JDK-urilor prin reflecție asupra mapei interne.
 */
@Suppress("UNCHECKED_CAST")
private fun setEnv(key: String, value: String) {
    try {
        val env = System.getenv()
        val cl = env.javaClass
        val field = cl.getDeclaredField("m")
        field.isAccessible = true
        val writable = field.get(env) as MutableMap<String, String>
        writable[key] = value
    } catch (_: Exception) {
        // Fallback: system property. Dacă ajungem aici, codul care citește
        // System.getenv() nu va vedea valoarea; e responsabilitatea apelantului
        // să folosească un workaround.
        System.setProperty(key, value)
    }
}

/**
 * Construiește modulul Ktor pentru testele de route.
 * NU invocă configureDatabases (DB-ul e deja pornit de TestDatabaseFactory).
 * NU invocă configureSockets / configureHTTP (inutile pentru testele /auth).
 */
fun Application.testAuthModule(googleTokenVerifier: GoogleTokenVerifier) {
    val uploadsDir = Files.createTempDirectory("auth-route-test-uploads")
    val koinTestModule = module {
        single<IAuthDAO> { AuthDAO() }
        single<IAuthSessionDAO> { AuthSessionDAO() }
        single { RefreshTokenGenerator() }
        single<ISessionService> { SessionService(get(), get()) }
        single<IUserDAO> { UserDao() }
        single<IStorageService> { LocalImageStorageService(uploadsDir, "http://localhost:8080") }
        single<IUserService> { UserService(get(), get()) }
        single<GoogleTokenVerifier> { googleTokenVerifier }
        single<IAuthService> { AuthService(get(), get(), get()) }
        single {
            JwtService(
                jwtSecret = TestEnv.JWT_SECRET,
                jwtIssuer = TestEnv.JWT_ISSUER,
                jwtAudience = TestEnv.JWT_AUDIENCE
            )
        }
    }

    install(Koin) {
        modules(koinTestModule)
    }

    configureSerialization()
    configureAuthStatusPages()
    configureSecurity(getKoin().get())

    install(RoutingRoot)

    routing {
        route("/api") {
            authRoutes()
        }
    }
}

/**
 * Modul Ktor pentru testele rutei /car-models.
 * NU invocă configureDatabases (DB-ul e deja pornit de TestDatabaseFactory).
 * NU pornește Koin pentru auth — ține doar dependențele necesare aici.
 */
fun Application.testCarModelModule() {
    val koinTestModule = module {
        single<IAuthSessionDAO> { AuthSessionDAO() }
        single<ICarModelDAO> { CarModelDAO() }
        single<ICarModelService> { CarModelService(get()) }
    }

    install(Koin) {
        modules(koinTestModule)
    }

    configureSerialization()

    install(RoutingRoot)

    routing {
        route("/api") {
            carModelRoutes()
        }
    }
}

/**
 * Modul Ktor pentru testele rutei /posts/{postId}/comments și /comments/{id}.
 * Folosește același config JWT ca testele de auth.
 */
fun Application.testCommentModule() {
    val uploadsDir = Files.createTempDirectory("comment-route-test-uploads")
    val koinTestModule = module {
        single<ICommentDAO> { CommentDAO() }
        single<IPostDAO> { PostDAO() }
        single<IUserDAO> { UserDao() }
        single<IAuthSessionDAO> { AuthSessionDAO() }
        single { RefreshTokenGenerator() }
        single<ISessionService> { SessionService(get(), get()) }
        single<IStorageService> { LocalImageStorageService(uploadsDir, "http://localhost:8080") }
        single<IScoringDao> { ScoringDaoImpl() }
        single<IScoringService> { ScoringServiceImpl(get(), get(), get()) }
        single<ICommentService> { CommentService(get(), get(), get(), get()) }
        single {
            JwtService(
                jwtSecret = TestEnv.JWT_SECRET,
                jwtIssuer = TestEnv.JWT_ISSUER,
                jwtAudience = TestEnv.JWT_AUDIENCE
            )
        }
    }

    install(Koin) { modules(koinTestModule) }

    configureSerialization()
    configureSecurity(getKoin().get())  // instalează autentificarea "jwt" cu setările din TestEnv

    install(RoutingRoot)

    routing {
        route("/api") {
            commentRoutes()
        }
    }
}

/**
 * Modul Ktor pentru testele rutelor /posts/{postId}/likes.
 * Folosește același config JWT ca testele de auth.
 */
fun Application.testLikeModule() {
    val koinTestModule = module {
        single<ILikeDAO> { LikeDAO() }
        single<IPostDAO> { PostDAO() }
        single<IUserDAO> { UserDao() }
        single<IAuthSessionDAO> { AuthSessionDAO() }
        single { RefreshTokenGenerator() }
        single<ISessionService> { SessionService(get(), get()) }
        single<IScoringDao> { ScoringDaoImpl() }
        single<IScoringService> { ScoringServiceImpl(get(), get(), get()) }
        single<ILikeService> { LikeService(get(), get(), get()) }
        single {
            JwtService(
                jwtSecret = TestEnv.JWT_SECRET,
                jwtIssuer = TestEnv.JWT_ISSUER,
                jwtAudience = TestEnv.JWT_AUDIENCE
            )
        }
    }

    install(Koin) { modules(koinTestModule) }

    configureSerialization()
    configureSecurity(getKoin().get())

    install(RoutingRoot)

    routing {
        route("/api") {
            likeRoutes()
        }
    }
}

/**
 * Modul Ktor pentru testele rutei /posts/{postId}/reports.
 * Folosește același config JWT ca testele de auth.
 */
fun Application.testReportModule() {
    val koinTestModule = module {
        single<IReportDAO> { ReportDAO() }
        single<IAuthSessionDAO> { AuthSessionDAO() }
        single { RefreshTokenGenerator() }
        single<ISessionService> { SessionService(get(), get()) }
        single<IReportService> { ReportService(get()) }
        single {
            JwtService(
                jwtSecret = TestEnv.JWT_SECRET,
                jwtIssuer = TestEnv.JWT_ISSUER,
                jwtAudience = TestEnv.JWT_AUDIENCE
            )
        }
    }

    install(Koin) { modules(koinTestModule) }

    configureSerialization()
    configureSecurity(getKoin().get())

    install(RoutingRoot)

    routing {
        route("/api") {
            reportRoutes()
        }
    }
}

fun Application.testPostModule() {
    val uploadsDir = Files.createTempDirectory("posts-route-test-uploads")
    val koinTestModule = module {
        single<ICarModelDAO> { CarModelDAO() }
        single<IPostDAO> { PostDAO() }
        single<ILikeDAO> { LikeDAO() }
        single<ICommentDAO> { CommentDAO() }
        single<IUserDAO> { UserDao() }
        single<IAuthSessionDAO> { AuthSessionDAO() }
        single { RefreshTokenGenerator() }
        single<ISessionService> { SessionService(get(), get()) }
        single<IStorageService> { LocalImageStorageService(uploadsDir, "http://localhost:8080") }
        single<IScoringDao> { ScoringDaoImpl() }
        single<IScoringService> { ScoringServiceImpl(get(), get(), get()) }
        single<IPostService> { PostServiceImpl(get(), get(), get(), get(), get(), get(), get()) }
        single {
            JwtService(
                jwtSecret = TestEnv.JWT_SECRET,
                jwtIssuer = TestEnv.JWT_ISSUER,
                jwtAudience = TestEnv.JWT_AUDIENCE
            )
        }
    }

    install(Koin) { modules(koinTestModule) }

    configureSerialization()
    configureSecurity(getKoin().get())

    install(RoutingRoot)

    routing {
        route("/api") {
            postRoutes()
        }
    }
}

fun Application.testUserCarModule() {
    val uploadsDir = Files.createTempDirectory("user-car-route-test-uploads")
    val koinTestModule = module {
        single<ICarModelDAO> { CarModelDAO() }
        single<IUserCarDAO> { UserCarDAO() }
        single<IAuthSessionDAO> { AuthSessionDAO() }
        single { RefreshTokenGenerator() }
        single<ISessionService> { SessionService(get(), get()) }
        single<IStorageService> { LocalImageStorageService(uploadsDir, "http://localhost:8080") }
        single<IUserCarService> { UserCarServiceImpl(get(), get(), get()) }
        single {
            JwtService(
                jwtSecret = TestEnv.JWT_SECRET,
                jwtIssuer = TestEnv.JWT_ISSUER,
                jwtAudience = TestEnv.JWT_AUDIENCE
            )
        }
    }

    install(Koin) { modules(koinTestModule) }

    configureSerialization()
    configureSecurity(getKoin().get())

    install(RoutingRoot)

    routing {
        route("/api") {
            userCarRoutes()
        }
    }
}

fun Application.testUserModule() {
    val uploadsDir = Files.createTempDirectory("user-route-test-uploads")
    val koinTestModule = module {
        single<IUserDAO> { UserDao() }
        single<IAuthSessionDAO> { AuthSessionDAO() }
        single { RefreshTokenGenerator() }
        single<ISessionService> { SessionService(get(), get()) }
        single<IStorageService> { LocalImageStorageService(uploadsDir, "http://localhost:8080") }
        single<IUserService> { UserService(get(), get()) }
        single {
            JwtService(
                jwtSecret = TestEnv.JWT_SECRET,
                jwtIssuer = TestEnv.JWT_ISSUER,
                jwtAudience = TestEnv.JWT_AUDIENCE
            )
        }
    }

    install(Koin) { modules(koinTestModule) }

    configureSerialization()
    configureSecurity(getKoin().get())

    install(RoutingRoot)

    routing {
        route("/api") {
            userRoutes()
        }
    }
}

fun Application.testLeaderboardModule() {
    val uploadsDir = Files.createTempDirectory("leaderboard-route-test-uploads")
    val koinTestModule = module {
        single<ILeaderboardDAO> { LeaderboardDAO() }
        single<ILeaderboardSnapshotDAO> { LeaderboardSnapshotDAO() }
        single<IAuthSessionDAO> { AuthSessionDAO() }
        single { RefreshTokenGenerator() }
        single<ISessionService> { SessionService(get(), get()) }
        single<IStorageService> { LocalImageStorageService(uploadsDir, "http://localhost:8080") }
        single<ILeaderboardService> { LeaderboardService(get(), get(), get()) }
        single {
            JwtService(
                jwtSecret = TestEnv.JWT_SECRET,
                jwtIssuer = TestEnv.JWT_ISSUER,
                jwtAudience = TestEnv.JWT_AUDIENCE,
            )
        }
    }

    install(Koin) { modules(koinTestModule) }

    configureSerialization()
    configureSecurity(getKoin().get())

    install(RoutingRoot)

    routing {
        route("/api") {
            leaderboardRoutes()
        }
    }
}

fun Application.testAdminLeaderboardModule(adminToken: String) {
    val koinTestModule = module {
        single<IAuthSessionDAO> { AuthSessionDAO() }
        single<ILeaderboardSnapshotDAO> { LeaderboardSnapshotDAO() }
    }

    install(Koin) { modules(koinTestModule) }

    configureSerialization()

    install(RoutingRoot)

    routing {
        route("/api") {
            adminLeaderboardRoutes(adminTokenProvider = { adminToken })
        }
    }
}

/**
 * Helper pentru a opri Koin între teste (important, altfel a doua rulare crapă).
 */
fun stopKoinSafely() {
    try {
        stopKoin()
    } catch (_: Throwable) {
        // idempotent
    }
}
