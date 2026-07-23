package moa.auth

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users")
class User(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(nullable = false, unique = true)
    var email: String,
    var name: String? = null,
    @Column(name = "avatar_url")
    var avatarUrl: String? = null,
    @Column(name = "google_id", nullable = false, unique = true, updatable = false)
    val googleId: String,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
