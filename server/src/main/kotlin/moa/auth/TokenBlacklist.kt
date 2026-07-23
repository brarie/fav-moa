package moa.auth

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

/** 로그아웃된 access token의 jti를 남은 유효기간 동안만 보관한다. */
@Component
class TokenBlacklist(private val redis: StringRedisTemplate) {
    fun add(jti: String, ttl: Duration) {
        if (ttl.isNegative || ttl.isZero) return
        redis.opsForValue().set(KEY_PREFIX + jti, "1", ttl)
    }

    fun contains(jti: String): Boolean = redis.hasKey(KEY_PREFIX + jti)

    companion object {
        private const val KEY_PREFIX = "auth:blacklist:"
    }
}
