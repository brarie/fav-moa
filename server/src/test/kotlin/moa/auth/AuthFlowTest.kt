package moa.auth

import jakarta.servlet.http.Cookie
import moa.TestcontainersConfig
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig::class)
class AuthFlowTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var authService: AuthService

    @Autowired lateinit var userRepository: UserRepository

    private fun login(googleId: String = "g-${UUID.randomUUID()}"): Pair<String, String> {
        val refresh = authService.completeLogin(
            AuthService.GoogleProfile(
                googleId = googleId,
                email = "$googleId@example.com",
                name = "테스터",
                avatarUrl = null,
            ),
        )
        return googleId to refresh
    }

    private fun refreshCall(raw: String) = mockMvc.post("/auth/refresh") { cookie(Cookie(RefreshCookieFactory.NAME, raw)) }

    private fun extractRefreshCookie(setCookieHeader: String): String =
        setCookieHeader.substringAfter("${RefreshCookieFactory.NAME}=").substringBefore(";")

    @Test
    fun `같은 구글 계정으로 두 번 로그인해도 사용자는 하나만 생성된다`() {
        val (googleId, first) = login()
        val second = authService.completeLogin(
            AuthService.GoogleProfile(googleId, "$googleId@example.com", "테스터", null),
        )
        assertNotEquals(first, second)
        assertNotNull(userRepository.findByGoogleId(googleId))
        assertEquals(1, userRepository.findAll().count { it.googleId == googleId })
    }

    @Test
    fun `refresh 회전 - 새 access와 새 refresh를 받고 access로 보호 API를 호출할 수 있다`() {
        val (googleId, refresh) = login()

        val result = refreshCall(refresh).andExpect { status { isOk() } }.andReturn()
        val body = result.response.contentAsString
        val accessToken = Regex("\"accessToken\":\"([^\"]+)\"").find(body)!!.groupValues[1]
        val newRefresh = extractRefreshCookie(result.response.getHeader(HttpHeaders.SET_COOKIE)!!)
        assertNotEquals(refresh, newRefresh)
        assertTrue(body.contains("$googleId@example.com"))

        mockMvc.get("/auth/me") { header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken") }
            .andExpect {
                status { isOk() }
                jsonPath("$.email") { value("$googleId@example.com") }
            }
    }

    @Test
    fun `회전된 refresh를 재사용하면 체인 전체가 무효화된다`() {
        val (_, refresh0) = login()
        val rotated = refreshCall(refresh0).andExpect { status { isOk() } }.andReturn()
        val refresh1 = extractRefreshCookie(rotated.response.getHeader(HttpHeaders.SET_COOKIE)!!)

        // 이미 회전된 refresh0 재사용 → 재사용 감지
        refreshCall(refresh0).andExpect { status { isUnauthorized() } }
        // 같은 family의 최신 토큰(refresh1)도 무효화되어야 함
        refreshCall(refresh1).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `로그아웃하면 refresh 체인이 무효화되고 access는 블랙리스트로 차단된다`() {
        val (_, refresh0) = login()
        val rotated = refreshCall(refresh0).andExpect { status { isOk() } }.andReturn()
        val body = rotated.response.contentAsString
        val accessToken = Regex("\"accessToken\":\"([^\"]+)\"").find(body)!!.groupValues[1]
        val refresh1 = extractRefreshCookie(rotated.response.getHeader(HttpHeaders.SET_COOKIE)!!)

        // 로그아웃 전에는 access가 통과
        mockMvc.get("/auth/me") { header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken") }
            .andExpect { status { isOk() } }

        mockMvc.post("/auth/logout") {
            cookie(Cookie(RefreshCookieFactory.NAME, refresh1))
            header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
        }.andExpect { status { isNoContent() } }

        mockMvc.get("/auth/me") { header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken") }
            .andExpect { status { isUnauthorized() } }
        refreshCall(refresh1).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `OAuth 시작 엔드포인트는 구글 인가 페이지로 리다이렉트한다`() {
        val result = mockMvc.get("/auth/google").andExpect { status { is3xxRedirection() } }.andReturn()
        assertTrue(result.response.redirectedUrl!!.contains("accounts.google.com"))
    }
}
