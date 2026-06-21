package testutils

import com.carspotter.config.configureSecurity
import com.carspotter.config.configureSerialization
import com.carspotter.core.storage.IStorageService
import com.carspotter.core.storage.LocalImageStorageService
import com.carspotter.features.auth.AuthDAO
import com.carspotter.features.auth.AuthService
import com.carspotter.features.auth.GoogleTokenVerifier
import com.carspotter.features.auth.IAuthDAO
import com.carspotter.features.auth.IAuthService
import com.carspotter.features.auth.JwtService
import com.carspotter.features.auth.authRoutes
import com.carspotter.features.car_model.CarModelDAO
import com.carspotter.features.car_model.CarModelService
import com.carspotter.features.car_model.ICarModelDAO
import com.carspotter.features.car_model.ICarModelService
import com.carspotter.features.car_model.carModelRoutes
import com.carspotter.features.post.IPostDAO
import com.carspotter.features.post.IPostService
import com.carspotter.features.post.PostDAO
import com.carspotter.features.post.PostServiceImpl
import com.carspotter.features.post.postRoutes
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
    configureSecurity()

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
        single<IStorageService> { LocalImageStorageService(uploadsDir, "http://localhost:8080") }
        single<ICommentService> { CommentService(get(), get()) }
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
    configureSecurity()  // instalează autentificarea "jwt" cu setările din TestEnv

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
        single<ILikeService> { LikeService(get()) }
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
    configureSecurity()

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
    configureSecurity()

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
        single<IStorageService> { LocalImageStorageService(uploadsDir, "http://localhost:8080") }
        single<IPostService> { PostServiceImpl(get(), get(), get(), get(), get()) }
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
    configureSecurity()

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
    configureSecurity()

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
    configureSecurity()

    install(RoutingRoot)

    routing {
        route("/api") {
            userRoutes()
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
