package moa.auth

import jakarta.servlet.http.HttpServletResponse
import moa.common.UnauthorizedException
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/auth")
class AuthController(private val authService: AuthService, private val cookies: RefreshCookieFactory) {
    /** refresh 쿠키 → 회전 + 새 access 발급. 프론트 OAuth 콜백 페이지도 이걸로 최초 access를 얻는다. */
    @PostMapping("/refresh")
    fun refresh(
        @CookieValue(RefreshCookieFactory.NAME, required = false) refreshToken: String?,
        response: HttpServletResponse,
    ): TokenResponse {
        if (refreshToken.isNullOrBlank()) throw UnauthorizedException("refresh token이 없습니다.")
        val result = authService.refresh(refreshToken)
        response.addHeader(HttpHeaders.SET_COOKIE, cookies.create(result.refreshToken).toString())
        return TokenResponse(
            accessToken = result.access.token,
            expiresInSeconds = result.access.expiresInSeconds,
            user = UserResponse.from(result.user),
        )
    }

    @PostMapping("/logout")
    fun logout(
        @CookieValue(RefreshCookieFactory.NAME, required = false) refreshToken: String?,
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        response: HttpServletResponse,
    ): ResponseEntity<Void> {
        authService.logout(refreshToken, authorization?.removePrefix("Bearer "))
        response.addHeader(HttpHeaders.SET_COOKIE, cookies.expire().toString())
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal principal: AuthenticatedUser?): UserResponse {
        val id = principal?.id ?: throw UnauthorizedException("인증이 필요합니다.")
        return UserResponse.from(authService.findUser(id))
    }
}

data class TokenResponse(val accessToken: String, val expiresInSeconds: Long, val user: UserResponse)

data class UserResponse(val id: UUID, val email: String, val name: String?, val avatarUrl: String?) {
    companion object {
        fun from(user: User): UserResponse = UserResponse(id = user.id, email = user.email, name = user.name, avatarUrl = user.avatarUrl)
    }
}
