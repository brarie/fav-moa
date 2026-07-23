package moa

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfig {
    @Bean
    @ServiceConnection
    fun postgres(): PostgreSQLContainer<*> =
        PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"))

    @Bean
    @ServiceConnection(name = "redis")
    fun redis(): GenericContainer<*> = GenericContainer(DockerImageName.parse("redis:7-alpine")).withExposedPorts(REDIS_PORT)

    companion object {
        private const val REDIS_PORT = 6379
    }
}
