package testutils

import com.carspotter.config.configureSecurity
import com.carspotter.config.configureSerialization
import com.carspotter.features.auth.AuthDAO
import com.carspotter.features.auth.AuthService
import com.carspotter.features.auth.GoogleTokenVerifier
import com.carspotter.features.auth.IAuthDAO
import com.carspotter.features.auth.IAuthService
import com.carspotter.features.auth.JwtService
import com.carspotter.features.auth.authRoutes
import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin

object TestEnv {
    const val JWT_SECRET = "test-secret-please-change"
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
    val koinTestModule = module {
        single<IAuthDAO> { AuthDAO() }
        single<GoogleTokenVerifier> { googleTokenVerifier }
        single<IAuthService> { AuthService(get(), get()) }
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
 * Helper pentru a opri Koin între teste (important, altfel a doua rulare crapă).
 */
fun stopKoinSafely() {
    try {
        stopKoin()
    } catch (_: Throwable) {
        // idempotent
    }
}