package moa.auth

import jakarta.servlet.http.HttpServletRequest
import moa.common.AppProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthFilter: JwtAuthFilter,
    private val oAuth2LoginSuccessHandler: OAuth2LoginSuccessHandler,
    private val props: AppProperties,
) {
    @Bean
    fun filterChain(http: HttpSecurity, clientRegistrations: ClientRegistrationRepository): SecurityFilterChain {
        val googleOnlyResolver = googleOnlyAuthorizationRequestResolver(clientRegistrations)
        http {
            // 인증은 Bearer JWT + SameSite=Lax httpOnly 쿠키 → CSRF 토큰 불필요
            csrf { disable() }
            cors { }
            // OAuth 인가 요청 state 보관에만 세션 사용, API는 무상태
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.IF_REQUIRED }
            authorizeHttpRequests {
                authorize("/auth/google", permitAll)
                authorize("/auth/*/callback", permitAll)
                authorize("/auth/refresh", permitAll)
                authorize("/auth/logout", permitAll)
                authorize("/actuator/health", permitAll)
                authorize("/error", permitAll)
                authorize(anyRequest, authenticated)
            }
            oauth2Login {
                // 시작 URL을 /auth/google로 제한 — /auth/refresh 등이 registrationId로 오인되지 않도록
                authorizationEndpoint { authorizationRequestResolver = googleOnlyResolver }
                redirectionEndpoint { baseUri = "/auth/*/callback" }
                authenticationSuccessHandler = oAuth2LoginSuccessHandler
                authenticationFailureHandler =
                    SimpleUrlAuthenticationFailureHandler("${props.frontendUrl}/login?error=oauth")
            }
            exceptionHandling { authenticationEntryPoint = HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED) }
            addFilterBefore<UsernamePasswordAuthenticationFilter>(jwtAuthFilter)
        }
        return http.build()
    }

    private fun googleOnlyAuthorizationRequestResolver(
        clientRegistrations: ClientRegistrationRepository,
    ): OAuth2AuthorizationRequestResolver {
        val delegate = DefaultOAuth2AuthorizationRequestResolver(clientRegistrations, "/auth")
        return object : OAuth2AuthorizationRequestResolver {
            override fun resolve(request: HttpServletRequest): OAuth2AuthorizationRequest? =
                if (request.requestURI == GOOGLE_LOGIN_URI) delegate.resolve(request) else null

            override fun resolve(request: HttpServletRequest, clientRegistrationId: String): OAuth2AuthorizationRequest? =
                if (request.requestURI == GOOGLE_LOGIN_URI) delegate.resolve(request, clientRegistrationId) else null
        }
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration().apply {
            allowedOrigins = listOf(props.frontendUrl)
            allowedMethods = listOf("GET", "POST", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true
        }
        return UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration("/**", config) }
    }

    companion object {
        private const val GOOGLE_LOGIN_URI = "/auth/google"
    }
}
