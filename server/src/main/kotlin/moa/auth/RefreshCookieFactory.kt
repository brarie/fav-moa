package moa.auth

import moa.common.AppProperties
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class RefreshCookieFactory(private val props: AppProperties) {
    fun create(value: String): ResponseCookie = base(value, props.auth.refreshTtl)

    fun expire(): ResponseCookie = base("", Duration.ZERO)

    private fun base(value: String, maxAge: Duration): ResponseCookie = ResponseCookie.from(NAME, value)
        .httpOnly(true)
        .secure(props.auth.cookieSecure)
        .path("/auth")
        .sameSite("Lax")
        .maxAge(maxAge)
        .build()

    companion object {
        const val NAME = "refresh_token"
    }
}
