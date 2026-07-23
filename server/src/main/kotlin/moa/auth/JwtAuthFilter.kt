package moa.auth

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/** Bearer access token을 검증해 SecurityContext를 채운다. 실패 시 익명으로 통과(인가 단계에서 거절). */
@Component
class JwtAuthFilter(private val jwt: JwtService, private val blacklist: TokenBlacklist) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (header != null && header.startsWith(BEARER)) {
            val decoded = runCatching { jwt.decode(header.removePrefix(BEARER)) }.getOrNull()
            val jti = decoded?.id
            if (decoded != null && jti != null && !blacklist.contains(jti)) {
                val principal = AuthenticatedUser(UUID.fromString(decoded.subject))
                val authorities = listOf(SimpleGrantedAuthority("ROLE_USER"))
                SecurityContextHolder.getContext().authentication =
                    UsernamePasswordAuthenticationToken(principal, null, authorities)
            }
        }
        filterChain.doFilter(request, response)
    }

    companion object {
        private const val BEARER = "Bearer "
    }
}

data class AuthenticatedUser(val id: UUID)
