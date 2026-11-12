package com.ziv.config;

import com.ziv.common.ApiResponseUtil;
import com.ziv.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

@Configuration
public class SecurityConfig {
    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper,
            SecurityContextRepository contexts, CsrfTokenRepository csrf) throws Exception {
        // CSRF stays enabled for cookie-based authentication.
        http.authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.POST, "/api/sendcode", "/api/users", "/api/users/register",
                                "/api/sessions", "/api/sessions/loginbypassword", "/api/sessions/loginbycode").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/health", "/api/csrf-token", "/actuator/health", "/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/articles", "/api/articles/*").permitAll()
                        .anyRequest().authenticated())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .securityContext(context -> context.securityContextRepository(contexts))
                .csrf(config -> config.csrfTokenRepository(csrf))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                writeError(objectMapper, request, response, ErrorCode.UNAUTHORIZED, exception))
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(objectMapper, request, response, ErrorCode.FORBIDDEN, exception)));
        return http.build();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        return new HttpSessionCsrfTokenRepository();
    }

    private static void writeError(ObjectMapper mapper, HttpServletRequest request, HttpServletResponse response,
                                   ErrorCode errorCode, Exception exception) throws IOException {
        log.warn("Security rejection: method={} path={} status={} type={} message={}",
                request.getMethod(), request.getRequestURI(), errorCode.getCode(), exception.getClass().getSimpleName(), errorCode.getMessage());
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), ApiResponseUtil.error(errorCode));
    }
}
