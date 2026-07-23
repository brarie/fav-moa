package moa.auth

import moa.common.UnauthorizedException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class AuthService(
    private val users: UserRepository,
    private val refreshTokens: RefreshTokenService,
    private val jwt: JwtService,
    private val blacklist: TokenBlacklist,
) {
    /** OAuth 콜백 성공 시: googleId 기준 upsert 후 새 회전 체인의 refresh token 원문을 반환. */
    @Transactional
    fun completeLogin(profile: GoogleProfile): String {
        val user = users.findByGoogleId(profile.googleId)?.apply {
            email = profile.email
            name = profile.name
            avatarUrl = profile.avatarUrl
        } ?: users.save(
            User(
                email = profile.email,
                name = profile.name,
                avatarUrl = profile.avatarUrl,
                googleId = profile.googleId,
            ),
        )
        return refreshTokens.issue(user.id)
    }

    fun refresh(raw: String): RefreshResult {
        val rotated = when (val outcome = refreshTokens.rotate(raw)) {
            is RotationOutcome.Rotated -> outcome
            RotationOutcome.ReuseDetected -> throw UnauthorizedException("재사용이 감지되어 세션이 무효화되었습니다. 다시 로그인해 주세요.")
            RotationOutcome.Expired -> throw UnauthorizedException("만료된 refresh token입니다.")
            RotationOutcome.Invalid -> throw UnauthorizedException("유효하지 않은 refresh token입니다.")
        }
        val user = users.findByIdOrNull(rotated.userId)
            ?: throw UnauthorizedException("사용자를 찾을 수 없습니다.")
        return RefreshResult(user, jwt.issueAccess(user), rotated.newRefreshToken)
    }

    /** refresh 체인 무효화 + 제출된 access token을 남은 유효기간 동안 블랙리스트에 등록. */
    fun logout(refreshRaw: String?, accessToken: String?) {
        refreshRaw?.let { refreshTokens.revokeChain(it) }
        accessToken?.let { token ->
            runCatching { jwt.decode(token) }.getOrNull()?.let { decoded ->
                val jti = decoded.id ?: return@let
                val expiresAt = decoded.expiresAt ?: return@let
                blacklist.add(jti, Duration.between(Instant.now(), expiresAt))
            }
        }
    }

    fun findUser(id: UUID): User = users.findByIdOrNull(id) ?: throw UnauthorizedException("사용자를 찾을 수 없습니다.")

    data class GoogleProfile(val googleId: String, val email: String, val name: String?, val avatarUrl: String?)

    data class RefreshResult(val user: User, val access: AccessToken, val refreshToken: String)
}
