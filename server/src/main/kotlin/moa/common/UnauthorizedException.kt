package moa.common

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class UnauthorizedException(reason: String) : ResponseStatusException(HttpStatus.UNAUTHORIZED, reason)
