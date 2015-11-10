package org.bentocorp.houston.config

import org.slf4j.LoggerFactory
//import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
//import org.springframework.security.config.annotation.web.builders.HttpSecurity
//import org.springframework.security.config.annotation.web.configuration.{EnableWebSecurity, WebSecurityConfigurerAdapter}
//import org.springframework.security.config.annotation.web.servlet.configuration.EnableWebMvcSecurity

// If Spring Security is on the classpath (check pom file), then the application is secured by default with "basic"
// authentication on all HTTP endpoints
//@Configuration
//@EnableWebSecurity // Switch off Spring Boot's default configuration
class SecurityConfig /* extends WebSecurityConfigurerAdapter */ {

  final val logger = LoggerFactory.getLogger(classOf[SecurityConfig])

  // When using an embedded servlet container (as with Spring Boot), we can register Servlets and Filters directly
  // as Spring beans
//  @Bean
//  def authenticationFilter: Filter = new AuthenticationFilter

//  @Autowired
//  var bentoAuthenticationProvider: BentoAuthenticationProvider = null
//
  // Tell Spring to invoke this method with an injected instance of AuthenticationManagerBuilder
//  @Autowired
//  def configureGlobal(builder: AuthenticationManagerBuilder) {
    // Use our own authentication provider
//    builder.authenticationProvider(bentoAuthenticationProvider)
//  }

//  @throws(classOf[Exception])
//  override protected def configure(httpSecurity:  HttpSecurity) {
//    logger.debug("Preparing custom security configurations")
    // Each matcher will be considered in the order they are declared
//    httpSecurity
//      .authorizeRequests()
//        .antMatchers("/test/authenticate").permitAll()
//        .antMatchers("/api/admin/**").hasRole("ADMIN")
//      .antMatchers("/db/**").access("hasRole('ADMIN') and hasRole('DBA')")
//        .anyRequest().authenticated()
//
//
//  }
}
