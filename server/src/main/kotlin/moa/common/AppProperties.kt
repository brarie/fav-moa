package moa.common

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "app")
data class AppProperties(val frontendUrl: String, val auth: Auth) {
    data class Auth(val jwtSecret: String, val accessTtl: Duration, val refreshTtl: Duration, val cookieSecure: Boolean)
}
