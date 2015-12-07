package org.bentocorp.houston.config

import org.bentocorp.filter.ResyncInterceptor
import org.bentocorp.security.AuthenticationInterceptor
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.{InterceptorRegistry, EnableWebMvc, WebMvcConfigurerAdapter}

// The @Configuration annotation indicates that a class declares one or more @Bean methods and may be processed by the
// Spring container to generate bean definitions at runtime
@Configuration
@EnableWebMvc
class WebMvcConfig extends WebMvcConfigurerAdapter {

  final val logger = LoggerFactory.getLogger(classOf[WebMvcConfig])

  @Autowired
  var authenticationInterceptor: AuthenticationInterceptor = null

  @Autowired
  var resyncInterceptor: ResyncInterceptor = null

  override def addInterceptors(registry: InterceptorRegistry) {
    super.addInterceptors(registry)
    logger.debug("Adding custom interceptor - AuthenticationInterceptor")
    // a single asterisk matches 0 or more characters up to the occurrence of a "/" character which servers as a path
    // separator, while two (2) asterisks matches 0 or more characters including path separators
    registry.addInterceptor(authenticationInterceptor)
            .addPathPatterns("/**")
            .excludePathPatterns("/error", "/api/authenticate", "/ping", "/admin/getForcedUpdateInfo")
    registry.addInterceptor(resyncInterceptor).addPathPatterns("/api/order/**", "/api/driver/**")
  }
}
