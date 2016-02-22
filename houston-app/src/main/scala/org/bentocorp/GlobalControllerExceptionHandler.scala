package org.bentocorp

import javax.servlet.http.HttpServletRequest

import org.bentocorp.houston.config.BentoConfig
import org.bentocorp.houston.util.EmailUtils
import org.slf4j.{Logger, LoggerFactory}
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.annotation.AnnotationUtils
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.{ControllerAdvice, ExceptionHandler, ResponseBody, ResponseStatus}

/**
 * See section on controller-based exception handling at {@link https://spring.io/blog/2013/11/01/exception-handling-in-spring-mvc}
 */
@ControllerAdvice
class GlobalControllerExceptionHandler {

    val logger: Logger = LoggerFactory.getLogger(classOf[GlobalControllerExceptionHandler])

    @Autowired var config: BentoConfig = _

    /**
     * Handler methods annotated with @ExceptionHandler can have very flexible signatures. See Spring JavaDoc for an
     * exhaustive list of acceptable parameters
     */
    @ExceptionHandler(Array(classOf[Exception]))
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR) // 500
    @ResponseBody
    def handleUncaughtException(req: HttpServletRequest, exc: Exception): String = {
        // If the exception is annotated with @ResponseStatus, rethrow it and let the Spring framework handle it
        if (AnnotationUtils.findAnnotation(exc.getClass, classOf[ResponseStatus]) != null) {
            throw exc
        }
        // Our exception emails will come from "engalert-[local|dev|<your_custom_env>]@bentonow.com
        // If on prod, from will just be "engalert@bentonow.com"
        var from = "engalert"
        val env: String = config.getString("env")
        if ("prod" == env) {
            from += "@bentonow.com"
        } else {
            from += s"-${env}@bentonow.com"
        }
        val body: String = exc.getStackTrace.mkString("<br>")
        EmailUtils.send("api",
                        config.getString("mailgun.key"),
                        from,
                        "email@bento.pagerduty.com", // Configure PagerDuty to only alert on emails coming from "engalert@bentonow.com"
                        "EXCEPTION: " + exc.getMessage,
                        body)

        logger.error(exc.getMessage, exc)

        exc.getMessage
    }
}
