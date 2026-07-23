package moa.auth

import moa.common.AppProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Service
class RefreshTokenService(private val repository: RefreshTokenRepository, private val props: AppProperties) {
    private val random = SecureRandom()

    /** 새 refresh token 발급. [family]를 넘기지 않으면 새 회전 체인을 시작한다. 원문을 반환하고 해시만 저장. */
    fun issue(userId: UUID, family: UUID = UUID.randomUUID()): String {
        val raw = generateRaw()
        repository.save(
            RefreshToken(
                userId = userId,
                tokenHash = hash(raw),
                family = family,
                expiresAt = Instant.now().plus(props.auth.refreshTtl),
            ),
        )
        return raw
    }

    /**
     * 회전 결과를 예외가 아닌 값으로 반환한다 — 재사용 감지 시의 family 무효화가
     * 예외 롤백에 휩쓸리지 않고 반드시 커밋되도록 하기 위함.
     */
    @Transactional
    fun rotate(raw: String): RotationOutcome {
        val token = repository.findByTokenHash(hash(raw)) ?: return RotationOutcome.Invalid
        if (token.revoked) {
            // 이미 회전되어 폐기된 토큰이 다시 제출됨 → 탈취 의심, 체인 전체 무효화
            repository.revokeFamily(token.family, Instant.now())
            return RotationOutcome.ReuseDetected
        }
        if (token.expired) {
            token.revokedAt = Instant.now()
            return RotationOutcome.Expired
        }
        token.revokedAt = Instant.now()
        return RotationOutcome.Rotated(token.userId, issue(token.userId, token.family))
    }

    @Transactional
    fun revokeChain(raw: String) {
        val token = repository.findByTokenHash(hash(raw)) ?: return
        repository.revokeFamily(token.family, Instant.now())
    }

    private fun generateRaw(): String {
        val bytes = ByteArray(RAW_BYTES)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hash(raw: String): String = MessageDigest.getInstance("SHA-256")
        .digest(raw.toByteArray())
        .joinToString("") { "%02x".format(it) }

    companion object {
        private const val RAW_BYTES = 32
    }
}

sealed interface RotationOutcome {
    data class Rotated(val userId: UUID, val newRefreshToken: String) : RotationOutcome

    data object ReuseDetected : RotationOutcome

    data object Invalid : RotationOutcome

    data object Expired : RotationOutcome
}
