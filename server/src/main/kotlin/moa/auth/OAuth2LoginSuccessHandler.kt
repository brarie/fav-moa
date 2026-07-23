package moa.auth

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import moa.common.AppProperties
import org.springframework.http.HttpHeaders
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component

/**
 * 구글 OAuth 성공 → User upsert → refresh 쿠키 심고 프론트로 리다이렉트.
 * access token은 URL에 노출하지 않는다 — 프론트 콜백 페이지가 POST /auth/refresh로 교환.
 */
@Component
class OAuth2LoginSuccessHandler(
    private val authService: AuthService,
    private val cookies: RefreshCookieFactory,
    private val props: AppProperties,
) : AuthenticationSuccessHandler {
    override fun onAuthenticationSuccess(request: HttpServletRequest, response: HttpServletResponse, authentication: Authentication) {
        val principal = authentication.principal as OAuth2User
        val profile = AuthService.GoogleProfile(
            googleId = requireNotNull(principal.getAttribute("sub")) { "구글 응답에 sub가 없습니다" },
            email = requireNotNull(principal.getAttribute("email")) { "구글 응답에 email이 없습니다" },
            name = principal.getAttribute("name"),
            avatarUrl = principal.getAttribute("picture"),
        )
        val refreshToken = authService.completeLogin(profile)
        response.addHeader(HttpHeaders.SET_COOKIE, cookies.create(refreshToken).toString())
        response.sendRedirect("${props.frontendUrl}/auth/callback")
    }
}
