package com.sample.hexagonal.sample.adapter.inbound.controller.advice

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import java.time.LocalDateTime

@ControllerAdvice
class ApiControllerAdvice : ResponseEntityExceptionHandler() {
    private val logger = KotlinLogging.logger { }

    @ExceptionHandler(Exception::class)
    fun handleException(exception: Exception): ProblemDetail {
        logger.error(exception) { "Unhandled exception while processing request" }

        return ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "요청을 처리하는 중 오류가 발생했습니다.",
        ).apply {
            setProperty("timestamp", LocalDateTime.now())
        }
    }
}
