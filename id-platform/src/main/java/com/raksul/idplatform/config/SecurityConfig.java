package com.raksul.idplatform.config;

import com.raksul.idplatform.security.ZeroTrustFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           ZeroTrustFilter zeroTrustFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/.well-known/**", "/jwks.json", "/api/health").permitAll()
                .requestMatchers("/oauth2/authorize", "/oauth2/authorize-register", "/oauth2/token", "/oauth2/register", "/oauth2/clients").permitAll()
                .requestMatchers("/api/auth/login", "/api/auth/register").permitAll()
                .requestMatchers("/api/migration/**").permitAll()
                .requestMatchers("/h2-console/**", "/error").permitAll()
                .requestMatchers("/css/**", "/js/**", "/images/**").permitAll()
                .requestMatchers("/templates/**").permitAll()
                .anyRequest().permitAll()
            )
            .headers(headers -> headers.frameOptions(fo -> fo.disable()))
            .addFilterBefore(zeroTrustFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
