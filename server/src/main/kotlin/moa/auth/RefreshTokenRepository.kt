package moa.auth

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface RefreshTokenRepository : JpaRepository<RefreshToken, UUID> {
    fun findByTokenHash(tokenHash: String): RefreshToken?

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.family = :family and t.revokedAt is null")
    fun revokeFamily(family: UUID, now: Instant)
}
