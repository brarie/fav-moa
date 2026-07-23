package moa.auth

import com.nimbusds.jose.jwk.source.ImmutableSecret
import moa.common.AppProperties
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.crypto.spec.SecretKeySpec

@Service
class JwtService(props: AppProperties) {
    private val key = SecretKeySpec(props.auth.jwtSecret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
    private val encoder: JwtEncoder = NimbusJwtEncoder(ImmutableSecret(key))
    private val decoder: JwtDecoder = NimbusJwtDecoder.withSecretKey(key).build()
    private val accessTtl: Duration = props.auth.accessTtl

    fun issueAccess(user: User): AccessToken {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .subject(user.id.toString())
            .id(UUID.randomUUID().toString())
            .issuedAt(now)
            .expiresAt(now.plus(accessTtl))
            .claim("email", user.email)
            .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        val token = encoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue
        return AccessToken(token, accessTtl.seconds)
    }

    /** 서명·만료 검증 포함. 실패 시 [org.springframework.security.oauth2.jwt.JwtException] */
    fun decode(token: String): Jwt = decoder.decode(token)
}

data class AccessToken(val token: String, val expiresInSeconds: Long)
