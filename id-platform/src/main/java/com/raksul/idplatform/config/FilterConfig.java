package com.raksul.idplatform.config;

import com.raksul.idplatform.repository.AuthTokenRepository;
import com.raksul.idplatform.security.ZeroTrustFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {

    @Bean
    public ZeroTrustFilter zeroTrustFilter(JwksConfig jwksConfig, AuthTokenRepository tokenRepository) {
        return new ZeroTrustFilter(jwksConfig, tokenRepository);
    }
}
