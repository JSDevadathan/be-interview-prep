package com.edstem.interviewprep.security;

import com.edstem.interviewprep.controller.ProductController;
import com.edstem.interviewprep.controller.RedirectController;
import com.edstem.interviewprep.enums.Role;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    private static final String[] PUBLIC_AUTH_PATHS = {"/api/auth/register", "/api/auth/login"};
    private static final String ADMIN_USERS_PATH = "/api/users";

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JsonSecurityErrorHandler securityErrorHandler,
            JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        // CSRF protection is off because clients send the token in an Authorization header that browsers never
        // attach on their own, and there is no session cookie to ride.
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, PUBLIC_AUTH_PATHS).permitAll()
                        .requestMatchers(HttpMethod.GET, RedirectController.SHORT_CODE_PATH).permitAll()
                        .requestMatchers(HttpMethod.HEAD, RedirectController.SHORT_CODE_PATH).permitAll()
                        .requestMatchers(ADMIN_USERS_PATH).hasRole(Role.ADMIN.name())
                        .requestMatchers(HttpMethod.PUT, ProductController.PRODUCT_PATH).hasRole(Role.ADMIN.name())
                        .requestMatchers(HttpMethod.DELETE, ProductController.PRODUCT_PATH).hasRole(Role.ADMIN.name())
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
