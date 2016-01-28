package org.bentocorp.houston.config

import org.bentocorp.filter.ResyncInterceptor
import org.bentocorp.security.AuthenticationInterceptor
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.{Bean, Configuration}
import org.springframework.ui.freemarker.FreeMarkerConfigurationFactory
import org.springframework.web.servlet.ViewResolver
import org.springframework.web.servlet.config.annotation.{DefaultServletHandlerConfigurer, InterceptorRegistry, EnableWebMvc, WebMvcConfigurerAdapter}
import org.springframework.web.servlet.view.InternalResourceViewResolver
import org.springframework.web.servlet.view.freemarker.{FreeMarkerConfigurer, FreeMarkerViewResolver}

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

  @Bean
  def getViewResolver: ViewResolver = {
    val resolver = new FreeMarkerViewResolver
    resolver.setCache(false)
    resolver.setPrefix("")
    resolver.setSuffix(".ftl")
    resolver.setContentType("text/html; charset=UTF-8")
    resolver
  }

  @Bean
  def freeMarkerConfig: FreeMarkerConfigurer = {
    val factory = new FreeMarkerConfigurationFactory
    factory.setTemplateLoaderPaths("classpath:templates", "src/main/resource/templates")
    factory.setDefaultEncoding("UTF-8")
    val result = new FreeMarkerConfigurer()
    result.setConfiguration(factory.createConfiguration())
    result
  }
}
