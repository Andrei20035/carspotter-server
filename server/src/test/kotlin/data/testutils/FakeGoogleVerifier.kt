package data.testutils

import com.carspotter.features.auth.GoogleTokenVerifier

class FakeGoogleTokenVerifier : GoogleTokenVerifier {
    override fun verifyAndExtractSub(googleIdToken: String): String? {
        return if (googleIdToken == "gid1") "google123" else null
    }
}
