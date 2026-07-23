package moa.auth

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * refresh token은 원문이 아닌 SHA-256 해시로만 저장한다.
 * 같은 로그인 세션에서 회전된 토큰들은 동일한 [family]를 공유하며,
 * 이미 회전되어 폐기된 토큰이 다시 제출되면(탈취 의심) family 전체를 무효화한다.
 */
@Entity
@Table(name = "refresh_tokens")
class RefreshToken(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "user_id", nullable = false)
    val userId: UUID,
    @Column(name = "token_hash", nullable = false, unique = true)
    val tokenHash: String,
    @Column(nullable = false)
    val family: UUID,
    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
    @Column(name = "revoked_at")
    var revokedAt: Instant? = null,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
) {
    val revoked: Boolean
        get() = revokedAt != null

    val expired: Boolean
        get() = expiresAt.isBefore(Instant.now())
}
